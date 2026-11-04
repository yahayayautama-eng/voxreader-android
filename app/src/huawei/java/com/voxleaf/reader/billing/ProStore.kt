package com.voxleaf.reader.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.huawei.hmf.tasks.Task
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Pro unlock through HMS In-App Purchases: one non-consumable product, [PRODUCT_ID], created
 * in AppGallery Connect. HMS Core does the networking, so the app itself keeps no INTERNET
 * permission. The last known state is cached in settings so Pro works offline; [refresh] reconciles
 * it with the store whenever HMS Core can answer, and leaves it alone when it can't.
 *
 * ponytail: purchase signatures are not verified on the device. That needs the console's IAP public
 * key and only stops casual tampering; add it if piracy shows up.
 */
@Singleton
class ProStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsManager
) {
    val isPro: Flow<Boolean> = settings.proUnlockedFlow

    private val _price = MutableStateFlow<String?>(null)

    /** Localized price from the store, null until it answers. */
    val price: StateFlow<String?> = _price.asStateFlow()

    suspend fun refresh() {
        val client = Iap.getIapClient(context)
        runCatching {
            client.obtainOwnedPurchases(OwnedPurchasesReq().apply { priceType = NON_CONSUMABLE }).await()
        }.onSuccess { owned ->
            settings.setProUnlocked(owned.inAppPurchaseDataList.orEmpty().any(::isProPurchase))
        }.onFailure { Log.w(TAG, "Owned purchases unavailable; keeping cached state", it) }
        runCatching {
            client.obtainProductInfo(
                ProductInfoReq().apply {
                    priceType = NON_CONSUMABLE
                    productIds = listOf(PRODUCT_ID)
                }
            ).await()
        }.onSuccess { info -> _price.value = info.productInfoList?.firstOrNull()?.price }
    }

    /**
     * What to launch for a purchase: the store's payment screen, or HUAWEI ID sign-in when that has
     * to come first. Null when there is nothing to launch: HMS Core is missing, or Pro is already
     * owned (then the cached state is refreshed instead).
     */
    suspend fun purchaseIntent(activity: Activity): IntentSender? = try {
        Iap.getIapClient(activity).createPurchaseIntent(
            PurchaseIntentReq().apply {
                productId = PRODUCT_ID
                priceType = NON_CONSUMABLE
            }
        ).await().status.resolution?.intentSender
    } catch (error: IapApiException) {
        if (error.statusCode == OrderStatusCode.ORDER_PRODUCT_OWNED) {
            refresh()
            null
        } else {
            error.status.resolution?.intentSender
        }
    } catch (error: Exception) {
        Log.w(TAG, "In-app purchases unavailable", error)
        null
    }

    suspend fun onPurchaseResult(data: Intent?) {
        val result = runCatching { Iap.getIapClient(context).parsePurchaseResultInfoFromIntent(data) }.getOrNull()
        when (result?.returnCode) {
            OrderStatusCode.ORDER_STATE_SUCCESS ->
                if (isProPurchase(result.inAppPurchaseData)) settings.setProUnlocked(true)
            OrderStatusCode.ORDER_PRODUCT_OWNED -> refresh()
        }
    }

    private fun isProPurchase(json: String?): Boolean = json != null && runCatching {
        InAppPurchaseData(json).let {
            it.productId == PRODUCT_ID && it.purchaseState == InAppPurchaseData.PurchaseState.PURCHASED
        }
    }.getOrDefault(false)

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    companion object {
        /** Must match the non-consumable product ID in AppGallery Connect. */
        const val PRODUCT_ID = "vox_pro_unlock"
        private const val NON_CONSUMABLE = IapClient.PriceType.IN_APP_NONCONSUMABLE
        private const val TAG = "ProStore"
    }
}

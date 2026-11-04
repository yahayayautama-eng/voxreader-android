package com.voxleaf.reader.billing

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play build: no store billing yet, and its free tier has no listening limit
 * (DAILY_LISTEN_LIMIT_SECONDS = 0), so nothing here is ever offered. Same API as the huawei
 * flavor's ProStore so shared code compiles against either.
 */
@Singleton
class ProStore @Inject constructor(settings: AppSettingsManager) {
    val isPro: Flow<Boolean> = settings.proUnlockedFlow
    val price: StateFlow<String?> = MutableStateFlow(null)

    suspend fun refresh() = Unit

    suspend fun purchaseIntent(activity: Activity): IntentSender? = null

    suspend fun onPurchaseResult(data: Intent?) = Unit
}

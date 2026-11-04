package com.voxleaf.reader.billing

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.voxleaf.reader.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Returns an action that starts the Pro purchase: the store's payment screen, or account sign-in
 * when the store asks for that first. Shows a short message when purchases aren't available here.
 */
@Composable
fun rememberProPurchase(store: ProStore): () -> Unit {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch { store.onPurchaseResult(result.data) }
    }
    return {
        scope.launch {
            val intent = activity?.let { store.purchaseIntent(it) }
            when {
                intent != null -> launcher.launch(IntentSenderRequest.Builder(intent).build())
                !store.isPro.first() -> Toast.makeText(context, R.string.pro_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}

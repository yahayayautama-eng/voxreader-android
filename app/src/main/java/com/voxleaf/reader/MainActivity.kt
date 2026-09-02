package com.voxleaf.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxleaf.reader.core.ui.VoxLeafApp
import com.voxleaf.reader.tts.TtsManager
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var settingsManager: AppSettingsManager

    private val incomingDocumentUris = MutableStateFlow<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingDocumentUris.value = extractIncomingDocumentUris(intent).map(Uri::toString)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.hideContentInRecentsFlow.collect { hide ->
                    if (hide) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            // The stored preference is three-state: an explicit LIGHT/DARK choice, or SYSTEM,
            // which defers to the device setting. Read here rather than inside the app tree so a
            // change recomposes the whole theme, including window chrome.
            val themePreference by settingsManager.themeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val useDarkTheme = when (themePreference) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = useDarkTheme) {
                VoxLeafApp(
                    ttsManager = ttsManager,
                    incomingDocumentUris = incomingDocumentUris,
                    onIncomingDocumentsConsumed = {
                        incomingDocumentUris.value = emptyList()
                        intent.action = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDocumentUris.value = extractIncomingDocumentUris(intent).map(Uri::toString)
    }
}

internal fun extractIncomingDocumentUris(intent: Intent?): List<Uri> {
    if (intent == null) return emptyList()
    val acceptedAction = intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)
    if (!acceptedAction) return emptyList()
    val uris = buildList {
        intent.data?.let(::add)
        intent.clipData?.let { clip ->
            repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
        }
        @Suppress("DEPRECATION")
        when (intent.action) {
            Intent.ACTION_SEND -> (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(::add)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                    ?.filterIsInstance<Uri>()
                    ?.let(::addAll)
        }
    }
    return uris.distinctBy(Uri::toString).take(MAX_SHARED_DOCUMENTS)
}

private const val MAX_SHARED_DOCUMENTS = 20

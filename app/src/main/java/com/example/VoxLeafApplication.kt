package com.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@HiltAndroidApp
class VoxLeafApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        removeRenderedAudiobooks()
    }

    /**
     * Deletes chapter audio left behind by the batch renderer that used to pre-generate whole books.
     * Playback streams now, so nothing reads these files and nothing will ever write them again —
     * on a long book they can run to hundreds of megabytes, which is not something to leave sitting
     * in app storage after an upgrade. Cheap to attempt and a no-op once the directories are gone.
     */
    private fun removeRenderedAudiobooks() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                listOf("audiobooks", "audiobook-runtime", "kokoro-runtime").forEach { name ->
                    File(filesDir, name).takeIf { it.exists() }?.deleteRecursively()
                }
            }
        }
    }
}

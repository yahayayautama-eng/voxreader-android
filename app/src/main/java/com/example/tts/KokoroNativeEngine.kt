package com.example.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Offline kittenTTS engine backed by Babylon.cpp; it never uses Android system TTS. */
@Singleton
class KokoroNativeEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {
    override val id: String get() = EngineId.OFFLINE.storageKey
    override val displayName: String get() = "Offline neural voice"
    override val requiresNetwork: Boolean get() = false

    override suspend fun listVoices(): List<EngineVoice> =
        ALL_VOICES.map { EngineVoice(id = it, displayName = voiceDisplayName(it), locale = "en-US") }

    /** [TtsEngine] entry point; delegates to the original signature so no existing caller changes. */
    override suspend fun synthesize(text: String, voiceId: String, speed: Float): File? =
        synthesize(text = text, speed = speed, voicePath = voiceId)

    private val runtimeDir = File(context.filesDir, "kokoro-runtime")
    private var initialized = false
    private var libraryLoadFailed = false

    suspend fun synthesize(text: String, speed: Float, voicePath: String = DEFAULT_VOICE): File? = withContext(Dispatchers.IO) {
        if (text.isBlank() || !ensureInitialized()) return@withContext null
        maybeRemoveStaleAudio()
        val output = File(runtimeDir, "audio-${System.nanoTime()}.wav")
        if (nativeSynthesize(text, File(runtimeDir, voicePath).path, speed.coerceIn(0.5f, 2f), output.path) && output.exists()) {
            output
        } else {
            output.delete()
            null
        }
    }

    @Synchronized
    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        if (libraryLoadFailed) return false
        initialized = runCatching {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("babylon")
            System.loadLibrary("kokoro_bridge")
            runtimeDir.mkdirs()
            ASSETS.forEach { copyAsset(it) }
            nativeInitialize(
                File(runtimeDir, PHONEMIZER).path,
                File(runtimeDir, DICTIONARY).path,
                File(runtimeDir, MODEL).path
            )
        }.onFailure { error ->
            libraryLoadFailed = true
            Log.e(TAG, "Unable to initialize bundled Kokoro runtime", error)
        }.getOrDefault(false)
        return initialized
    }

    private fun copyAsset(path: String) {
        val output = File(runtimeDir, path)
        if (output.exists()) return
        output.parentFile?.mkdirs()
        context.assets.open("babylon/$path").use { input -> output.outputStream().use(input::copyTo) }
    }

    @Volatile
    private var lastStaleSweep = 0L

    /**
     * A directory listing on every sentence was measurable overhead in the middle of the buffering
     * loop that keeps playback ahead of the voice — every synthesis call was paying for an I/O scan
     * that only ever needs to run once in a while. Once per [STALE_SWEEP_INTERVAL_MS] is enough to
     * keep leaked files from a killed process from accumulating.
     */
    private fun maybeRemoveStaleAudio() {
        val now = System.currentTimeMillis()
        if (now - lastStaleSweep < STALE_SWEEP_INTERVAL_MS) return
        lastStaleSweep = now
        val cutoff = now - STALE_AUDIO_AGE_MS
        runtimeDir.listFiles { file -> file.name.startsWith("audio-") && file.extension == "wav" }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private external fun nativeInitialize(phonemizerPath: String, dictionaryPath: String, modelPath: String): Boolean
    private external fun nativeSynthesize(text: String, voicePath: String, speed: Float, outputPath: String): Boolean

    companion object {
        const val MODEL_VERSION = "kitten-tts-1"
        const val DEFAULT_VOICE = "voices/kitten/en-US-bella.bin"
        val ALL_VOICES = listOf(
            "voices/kitten/en-US-bella.bin",
            "voices/kitten/en-US-bruno.bin",
            "voices/kitten/en-US-hugo.bin",
            "voices/kitten/en-US-jasper.bin",
            "voices/kitten/en-US-kiki.bin",
            "voices/kitten/en-US-leo.bin",
            "voices/kitten/en-US-luna.bin",
            "voices/kitten/en-US-rosie.bin",
        )

        fun voiceDisplayName(path: String): String =
            path.substringAfterLast("/")
                .removeSuffix(".bin")
                .removePrefix("en-US-")
                .replaceFirstChar { it.uppercase() }

        private const val TAG = "KokoroNativeEngine"
        private const val PHONEMIZER = "models/open-phonemizer.onnx"
        private const val DICTIONARY = "data/dictionary.json"
        private const val MODEL = "models/kitten-tts.onnx"
        private const val STALE_AUDIO_AGE_MS = 60 * 60 * 1000L
        private const val STALE_SWEEP_INTERVAL_MS = 5 * 60 * 1000L
        private val ASSETS = listOf(PHONEMIZER, DICTIONARY, MODEL) + ALL_VOICES
    }
}

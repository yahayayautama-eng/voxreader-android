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
        synthesizeInternal(text, speed, voicePath, runtimeDir, sweepStale = true)
    }

    /** Audiobook segments live outside the live playback cache, which may delete played files. */
    suspend fun synthesizeForAudiobook(text: String, speed: Float, voicePath: String = DEFAULT_VOICE): File? =
        withContext(Dispatchers.IO) {
            synthesizeInternal(text, speed, voicePath, audiobookRuntimeDir, sweepStale = false)
        }

    /**
     * Segments are deleted as each chapter commits, but a killed/crashed worker leaves them
     * behind — this directory is deliberately never swept during normal synthesis (see
     * [sweepStale]=false above), so nothing else ever removes them. Call once per worker run.
     */
    suspend fun cleanupAudiobookRuntime(): Unit = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - STALE_AUDIO_AGE_MS
        audiobookRuntimeDir.listFiles { file -> file.name.startsWith("audio-") && file.extension == "wav" }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun synthesizeInternal(
        text: String,
        speed: Float,
        voicePath: String,
        outputDir: File,
        sweepStale: Boolean
    ): File? {
        if (text.isBlank() || !ensureInitialized()) return null
        outputDir.mkdirs()
        if (sweepStale) maybeRemoveStaleAudio(outputDir)
        val output = File(outputDir, "audio-${System.nanoTime()}.wav")
        val voice = File(runtimeDir, voicePath)
        val nativeResult = runCatching {
                nativeSynthesize(normalizeForNative(text), voice.path, speed.coerceIn(0.5f, 2f), output.path)
        }.onFailure { error ->
            Log.e(TAG, "Native synthesis call failed", error)
        }.getOrDefault(false)
        return if (nativeResult && output.exists() && output.length() > WAV_HEADER_BYTES) {
            output
        } else {
            Log.e(TAG, "Native synthesis produced no audio: voice=${voice.path} voiceExists=${voice.isFile} output=${output.path} outputBytes=${output.length()}")
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
        output.parentFile?.mkdirs()
        context.assets.open("babylon/$path").use { input ->
            // Runtime assets survive APK upgrades. Re-copy a truncated/stale asset instead of
            // letting a partial model make every synthesis attempt retry forever.
            if (output.isFile && output.length() == input.available().toLong()) return
            output.outputStream().use(input::copyTo)
        }
    }

    private fun normalizeForNative(text: String): String = text
        .replace('\u2018', '\'').replace('\u2019', '\'')
        .replace('\u201C', '"').replace('\u201D', '"')
        .replace('\u2013', '-').replace('\u2014', '-')
        .replace("\u2026", "...")

    @Volatile
    private var lastStaleSweep = 0L

    /**
     * A directory listing on every sentence was measurable overhead in the middle of the buffering
     * loop that keeps playback ahead of the voice — every synthesis call was paying for an I/O scan
     * that only ever needs to run once in a while. Once per [STALE_SWEEP_INTERVAL_MS] is enough to
     * keep leaked files from a killed process from accumulating.
     */
    private fun maybeRemoveStaleAudio(directory: File) {
        val now = System.currentTimeMillis()
        if (now - lastStaleSweep < STALE_SWEEP_INTERVAL_MS) return
        lastStaleSweep = now
        val cutoff = now - STALE_AUDIO_AGE_MS
        directory.listFiles { file -> file.name.startsWith("audio-") && file.extension == "wav" }
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
        private const val WAV_HEADER_BYTES = 44L
        private val ASSETS = listOf(PHONEMIZER, DICTIONARY, MODEL) + ALL_VOICES
    }

    private val audiobookRuntimeDir = File(context.filesDir, "audiobook-runtime")
}

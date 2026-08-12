package com.example.tts

import android.content.Context
import android.util.Log
import com.example.audiobook.AudiobookSynthesizer
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline neural speech from a Piper/VITS model run through sherpa-onnx.
 *
 * Replaces the hand-maintained native stack (a vendored ONNX Runtime, a JNI bridge, and a separate
 * phonemizer model): sherpa-onnx ships its own prebuilt native library and does phonemization
 * internally via espeak-ng, so the only assets this needs are the acoustic model, its token table,
 * and the espeak data directory.
 *
 * The bundled model is multi-speaker, which is what lets the voice picker offer a range of narrators
 * from one file rather than one file per voice.
 */
@Singleton
class SherpaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine, AudiobookSynthesizer {
    override val id: String get() = EngineId.OFFLINE.storageKey
    override val displayName: String get() = "Offline neural voice"
    override val requiresNetwork: Boolean get() = false

    private val runtimeDir = File(context.filesDir, "sherpa-runtime")
    private val audiobookOutputDir = File(context.filesDir, "audiobook-runtime")
    private val liveOutputDir = File(context.filesDir, "sherpa-playback")

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var initializationFailed = false

    override suspend fun listVoices(): List<EngineVoice> = withContext(Dispatchers.IO) {
        // The model carries hundreds of speakers. Surfacing all of them would be a wall of numbers
        // with no way to tell them apart, so the picker offers a curated set of named presets and
        // the rest stay reachable only by an explicit speaker id.
        val available = ensureInitialized()?.numSpeakers() ?: return@withContext emptyList()
        NARRATOR_PRESETS.filter { it.speakerId < available }
            .map { preset ->
                EngineVoice(
                    id = voiceId(preset.speakerId),
                    displayName = preset.displayName,
                    locale = "en-US"
                )
            }
    }

    override suspend fun synthesize(text: String, voiceId: String, speed: Float): File? =
        withContext(Dispatchers.IO) { generate(text, voiceId, speed, liveOutputDir) }

    override suspend fun synthesizeForAudiobook(text: String, speed: Float, voicePath: String): File? =
        withContext(Dispatchers.IO) { generate(text, voicePath, speed, audiobookOutputDir) }

    override suspend fun cleanupAudiobookRuntime(): Unit = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - STALE_AUDIO_AGE_MS
        audiobookOutputDir.listFiles { file -> file.name.startsWith("audio-") && file.extension == "wav" }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun generate(text: String, voiceId: String, speed: Float, outputDir: File): File? {
        if (text.isBlank()) return null
        val engine = ensureInitialized() ?: return null
        outputDir.mkdirs()
        val output = File(outputDir, "audio-${System.nanoTime()}.wav")
        return try {
            val audio = engine.generate(text, speakerIdOf(voiceId), speed.coerceIn(MIN_SPEED, MAX_SPEED))
            // save() writes a RIFF/WAV file, which is what every downstream consumer — MediaPlayer,
            // WavChapterAssembler, AacChapterEncoder — already expects.
            if (audio.save(output.absolutePath) && output.length() > WAV_HEADER_BYTES) {
                output
            } else {
                Log.e(TAG, "Synthesis produced no audio: speaker=${speakerIdOf(voiceId)} bytes=${output.length()}")
                output.delete()
                null
            }
        } catch (error: Exception) {
            Log.e(TAG, "Synthesis failed", error)
            output.delete()
            null
        }
    }

    @Synchronized
    private fun ensureInitialized(): OfflineTts? {
        tts?.let { return it }
        if (initializationFailed) return null
        return runCatching {
            // espeak-ng reads its data directory off the real filesystem, so it cannot stay inside
            // the APK; the model and token table are small enough to copy alongside it rather than
            // maintaining two access paths.
            extractAssetsIfNeeded()
            OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = File(runtimeDir, MODEL).absolutePath,
                            tokens = File(runtimeDir, TOKENS).absolutePath,
                            dataDir = File(runtimeDir, ESPEAK_DATA).absolutePath
                        ),
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                        provider = "cpu"
                    )
                )
            )
        }.onFailure { error ->
            initializationFailed = true
            Log.e(TAG, "Unable to initialize sherpa-onnx TTS", error)
        }.getOrNull()?.also { tts = it }
    }

    /**
     * Copies the bundled model tree out of assets on first run. Guarded by a marker file holding the
     * asset version rather than by mere existence: an upgrade that ships a different model must
     * overwrite the old one, and a copy interrupted halfway must not be mistaken for a finished one.
     */
    private fun extractAssetsIfNeeded() {
        val marker = File(runtimeDir, MARKER)
        if (marker.isFile && marker.readText() == MODEL_VERSION) return
        runtimeDir.deleteRecursively()
        runtimeDir.mkdirs()
        copyAssetTree(ASSET_ROOT, runtimeDir)
        marker.writeText(MODEL_VERSION)
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use(input::copyTo)
            }
            return
        }
        target.mkdirs()
        children.forEach { child -> copyAssetTree("$assetPath/$child", File(target, child)) }
    }

    private fun speakerIdOf(voiceId: String): Int =
        voiceId.substringAfterLast(':', "").toIntOrNull() ?: DEFAULT_SPEAKER_ID

    companion object {
        const val MODEL_VERSION = "piper-libritts-r-medium-int8-1"
        private const val ASSET_ROOT = "sherpa"
        private const val MODEL = "model.onnx"
        private const val TOKENS = "tokens.txt"
        private const val ESPEAK_DATA = "espeak-ng-data"
        private const val MARKER = ".model-version"
        private const val TAG = "SherpaTtsEngine"
        private const val WAV_HEADER_BYTES = 44L
        private const val STALE_AUDIO_AGE_MS = 60 * 60 * 1000L
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2f
        private const val DEFAULT_SPEAKER_ID = 79

        const val VOICE_ID_PREFIX = "sherpa:"

        /**
         * Voice ids carry the speaker id so a selection survives a model upgrade that renames presets.
         * The prefix is also what retires a voice id persisted by the previous engine: an id that
         * doesn't match is treated as unset and falls back to [DEFAULT_VOICE], so no migration step
         * is needed for the settings store.
         */
        fun voiceId(speakerId: Int): String = "${VOICE_ID_PREFIX}libritts:$speakerId"

        val DEFAULT_VOICE: String = voiceId(DEFAULT_SPEAKER_ID)

        data class NarratorPreset(val speakerId: Int, val displayName: String)

        /**
         * Hand-picked speakers from the bundled multi-speaker model. Chosen for narration: even pace,
         * clear diction, no strong accent colouring. Ids are stable properties of the model file.
         */
        val NARRATOR_PRESETS = listOf(
            NarratorPreset(79, "Ada"),
            NarratorPreset(109, "Bennett"),
            NarratorPreset(1263, "Clara"),
            NarratorPreset(92, "Dorian"),
            NarratorPreset(233, "Elise"),
            NarratorPreset(500, "Felix"),
            NarratorPreset(696, "Greta"),
            NarratorPreset(830, "Hugo")
        )
    }
}

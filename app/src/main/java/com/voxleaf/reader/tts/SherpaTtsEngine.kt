package com.voxleaf.reader.tts

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline neural speech run through sherpa-onnx, from two bundled models:
 *
 * - A Piper/VITS LibriTTS model: English only, small and fast, the default narrator.
 * - Kokoro v1.1: English and Mandarin, much larger and slower, but the only bundled voice that
 *   reads Chinese and the higher-quality choice for English.
 *
 * sherpa-onnx ships its own prebuilt native library and phonemizes internally via espeak-ng, so the
 * assets are just each model, its token table, and one shared espeak data directory. The two models
 * must share that directory: sherpa-onnx initializes espeak-ng once per process, and whichever
 * model loads first decides the data path for both.
 *
 * Each model loads on first use, so a reader who never picks Kokoro and never opens Chinese text
 * never pays its memory or the time to unpack it.
 */
@Singleton
class SherpaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {
    override val id: String get() = EngineId.OFFLINE.storageKey
    override val displayName: String get() = "Offline neural voice"
    override val requiresNetwork: Boolean get() = false

    private val runtimeDir = File(context.filesDir, "sherpa-runtime")
    private val kokoroDir = File(runtimeDir, KOKORO_DIR)
    private val liveOutputDir = File(context.filesDir, "sherpa-playback")

    private val loaded = arrayOfNulls<OfflineTts>(VoiceModel.entries.size)
    private val failed = BooleanArray(VoiceModel.entries.size)

    /** Kokoro's files are fetched separately (scripts/fetch-kokoro.sh), so a build can lack them. */
    private val hasKokoro: Boolean by lazy {
        context.assets.list("$ASSET_ROOT/$KOKORO_DIR").orEmpty().contains(MODEL)
    }

    /**
     * Kokoro needs about 700 MB while it speaks, enough to get the app killed on a small phone. Those
     * phones keep the light Piper voices in the picker; Chinese text still tries Kokoro, because it
     * is the only model that can read it at all.
     */
    private val kokoroFitsDevice: Boolean by lazy {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        !activityManager.isLowRamDevice && memory.totalMem >= MIN_KOKORO_RAM_BYTES
    }

    override suspend fun listVoices(): List<EngineVoice> = withContext(Dispatchers.IO) {
        // The Piper model carries hundreds of speakers. Surfacing all of them would be a wall of
        // numbers with no way to tell them apart, so the picker offers a curated set of named presets
        // and the rest stay reachable only by an explicit speaker id.
        val available = ensureInitialized(VoiceModel.PIPER)?.numSpeakers() ?: return@withContext emptyList()
        val piper = NARRATOR_PRESETS.filter { it.speakerId < available }
            .map { preset ->
                EngineVoice(
                    id = voiceId(preset.speakerId),
                    displayName = preset.displayName,
                    locale = "en-US"
                )
            }
        // Kokoro is listed without loading it: its 160 MB model should load only when it speaks.
        val kokoro = if (hasKokoro && kokoroFitsDevice) {
            KOKORO_PRESETS.map { EngineVoice(id = kokoroVoiceId(it.speakerId), displayName = it.displayName, locale = it.locale) }
        } else {
            emptyList()
        }
        piper + kokoro
    }

    override suspend fun synthesize(text: String, voiceId: String, speed: Float): File? =
        withContext(Dispatchers.IO) { generate(text, voiceId, speed, liveOutputDir) }

    private fun generate(text: String, voiceId: String, speed: Float, outputDir: File): File? {
        if (text.isBlank()) return null
        val route = route(text, voiceId)
        val engine = ensureInitialized(route.model) ?: return null
        outputDir.mkdirs()
        val output = File(outputDir, "audio-${System.nanoTime()}.wav")
        val spoken = if (route.model == VoiceModel.KOKORO) kokoroText(text) else text
        return try {
            val audio = engine.generate(spoken, route.speakerId, speed.coerceIn(MIN_SPEED, MAX_SPEED))
            // save() writes a RIFF/WAV file, which is what MediaPlayer expects.
            if (audio.save(output.absolutePath) && output.length() > WAV_HEADER_BYTES) {
                output
            } else {
                Log.e(TAG, "Synthesis produced no audio: $route bytes=${output.length()}")
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
    private fun ensureInitialized(model: VoiceModel): OfflineTts? {
        loaded[model.ordinal]?.let { return it }
        if (failed[model.ordinal]) return null
        return runCatching {
            // espeak-ng reads its data directory off the real filesystem, so it cannot stay inside
            // the APK; the models are copied alongside it rather than maintaining two access paths.
            // ponytail: Kokoro's copy doubles its ~170 MB on disk; load it from assets via an
            // AssetManager if storage complaints come in (needs a device test of mixed paths).
            extractBaseIfNeeded()
            val modelConfig = when (model) {
                VoiceModel.PIPER -> OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(runtimeDir, MODEL).absolutePath,
                        tokens = File(runtimeDir, TOKENS).absolutePath,
                        dataDir = File(runtimeDir, ESPEAK_DATA).absolutePath
                    ),
                    numThreads = threads(),
                    provider = "cpu"
                )
                VoiceModel.KOKORO -> {
                    extractKokoroIfNeeded()
                    OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = File(kokoroDir, MODEL).absolutePath,
                            voices = File(kokoroDir, "voices.bin").absolutePath,
                            tokens = File(kokoroDir, TOKENS).absolutePath,
                            dataDir = File(runtimeDir, ESPEAK_DATA).absolutePath,
                            lexicon = listOf("lexicon-us-en.txt", "lexicon-zh.txt")
                                .joinToString(",") { File(kokoroDir, it).absolutePath }
                        ),
                        numThreads = threads(),
                        provider = "cpu"
                    )
                }
            }
            OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = modelConfig,
                    // Reads Chinese numbers, dates and phone numbers the way a person would.
                    ruleFsts = if (model == VoiceModel.KOKORO) {
                        listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst")
                            .joinToString(",") { File(kokoroDir, it).absolutePath }
                    } else {
                        ""
                    }
                )
            )
        }.onFailure { error ->
            failed[model.ordinal] = true
            Log.e(TAG, "Unable to initialize sherpa-onnx TTS ($model)", error)
        }.getOrNull()?.also { loaded[model.ordinal] = it }
    }

    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    /**
     * Copies the Piper model and the shared espeak data out of assets on first run. Guarded by a
     * marker file holding the asset version rather than by mere existence: an upgrade that ships a
     * different model must overwrite the old one, and a copy interrupted halfway must not be
     * mistaken for a finished one. Kokoro is skipped here and unpacked only when first used.
     */
    private fun extractBaseIfNeeded() {
        val marker = File(runtimeDir, MARKER)
        if (marker.isFile && marker.readText() == MODEL_VERSION) return
        runtimeDir.deleteRecursively()
        runtimeDir.mkdirs()
        context.assets.list(ASSET_ROOT).orEmpty()
            .filter { it != KOKORO_DIR }
            .forEach { child -> copyAssetTree("$ASSET_ROOT/$child", File(runtimeDir, child)) }
        marker.writeText(MODEL_VERSION)
    }

    private fun extractKokoroIfNeeded() {
        val marker = File(kokoroDir, MARKER)
        if (marker.isFile && marker.readText() == KOKORO_VERSION) return
        kokoroDir.deleteRecursively()
        copyAssetTree("$ASSET_ROOT/$KOKORO_DIR", kokoroDir)
        marker.writeText(KOKORO_VERSION)
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

    internal enum class VoiceModel { PIPER, KOKORO }

    internal data class Route(val model: VoiceModel, val speakerId: Int)

    companion object {
        const val MODEL_VERSION = "piper-libritts-r-medium-int8-2"
        const val KOKORO_VERSION = "kokoro-int8-multi-lang-v1_1"
        private const val ASSET_ROOT = "sherpa"
        private const val KOKORO_DIR = "kokoro"
        private const val MODEL = "model.onnx"
        private const val TOKENS = "tokens.txt"
        private const val ESPEAK_DATA = "espeak-ng-data"
        private const val MARKER = ".model-version"
        private const val TAG = "SherpaTtsEngine"
        private const val WAV_HEADER_BYTES = 44L
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2f
        private const val DEFAULT_SPEAKER_ID = 79
        private const val MIN_KOKORO_RAM_BYTES = 3L * 1024 * 1024 * 1024

        /** Kokoro v1.1 speaker ids: 0–2 English female, 3–57 Chinese female, 58–102 Chinese male. */
        private const val FIRST_CHINESE_SPEAKER = 3
        private const val DEFAULT_KOKORO_ENGLISH_SPEAKER = 0
        private const val DEFAULT_CHINESE_SPEAKER = 3

        const val VOICE_ID_PREFIX = "sherpa:"
        private const val KOKORO_PREFIX = "${VOICE_ID_PREFIX}kokoro:"

        /**
         * Voice ids carry the speaker id so a selection survives a model upgrade that renames presets.
         * The prefix is also what retires a voice id persisted by the previous engine: an id that
         * doesn't match is treated as unset and falls back to [DEFAULT_VOICE], so no migration step
         * is needed for the settings store.
         */
        fun voiceId(speakerId: Int): String = "${VOICE_ID_PREFIX}libritts:$speakerId"

        fun kokoroVoiceId(speakerId: Int): String = "$KOKORO_PREFIX$speakerId"

        val DEFAULT_VOICE: String = voiceId(DEFAULT_SPEAKER_ID)

        /**
         * Picks the model and speaker for one sentence. The text's language decides first, because
         * Piper cannot read Chinese at all and an English narrator reading Chinese sounds wrong; the
         * chosen voice is honoured whenever it fits that language. Choosing any Kokoro voice keeps
         * English on Kokoro too, so the higher-quality choice carries across a mixed book.
         */
        internal fun route(text: String, voiceId: String): Route {
            val kokoroSpeaker = voiceId.takeIf { it.startsWith(KOKORO_PREFIX) }
                ?.substringAfterLast(':')?.toIntOrNull()
            return when {
                text.any(::isHan) -> Route(
                    VoiceModel.KOKORO,
                    kokoroSpeaker?.takeIf { it >= FIRST_CHINESE_SPEAKER } ?: DEFAULT_CHINESE_SPEAKER
                )
                kokoroSpeaker != null -> Route(
                    VoiceModel.KOKORO,
                    kokoroSpeaker.takeIf { it < FIRST_CHINESE_SPEAKER } ?: DEFAULT_KOKORO_ENGLISH_SPEAKER
                )
                else -> Route(VoiceModel.PIPER, voiceId.substringAfterLast(':', "").toIntOrNull() ?: DEFAULT_SPEAKER_ID)
            }
        }

        /** The voice that actually speaks [text], which differs from the chosen one for Chinese text. */
        fun spokenVoiceId(text: String, voiceId: String): String = route(text, voiceId).let {
            if (it.model == VoiceModel.KOKORO) kokoroVoiceId(it.speakerId) else voiceId(it.speakerId)
        }

        /**
         * Kokoro's token table holds only ASCII punctuation, so full-width Chinese marks reach it as
         * unknown tokens and lose their pause. They are swapped for the ASCII mark with the same pause.
         */
        internal fun kokoroText(text: String): String = buildString(text.length) {
            text.forEach { char -> append(KOKORO_PUNCTUATION[char] ?: char) }
        }

        private val KOKORO_PUNCTUATION = mapOf(
            '，' to ',', '、' to ',', '。' to '.', '！' to '!', '？' to '?', '：' to ':', '；' to ';',
            '（' to '(', '）' to ')', '「' to '“', '」' to '”', '『' to '“', '』' to '”',
            '《' to '“', '》' to '”', '【' to '(', '】' to ')', '～' to '—'
        )

        private fun isHan(char: Char): Boolean =
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

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

        internal data class KokoroPreset(val speakerId: Int, val displayName: String, val locale: String)

        // ponytail: the Chinese picks are the first speakers of each range, not yet chosen by ear;
        // listen on a device and swap in the clearest narrators.
        internal val KOKORO_PRESETS = listOf(
            KokoroPreset(0, "Maple (HD)", "en-US"),
            KokoroPreset(1, "Sol (HD)", "en-US"),
            KokoroPreset(2, "Vale (HD)", "en-GB"),
            KokoroPreset(3, "女声 1 (HD)", "zh-CN"),
            KokoroPreset(4, "女声 2 (HD)", "zh-CN"),
            KokoroPreset(58, "男声 1 (HD)", "zh-CN"),
            KokoroPreset(59, "男声 2 (HD)", "zh-CN")
        )
    }
}

package com.voxleaf.reader.tts

import com.voxleaf.reader.BuildConfig
import java.io.File

/** One voice a [TtsEngine] can speak with. */
data class EngineVoice(
    val id: String,
    val displayName: String,
    val locale: String,
    val gender: String? = null
)

/**
 * A source of synthesized speech. [KokoroNativeEngine] runs entirely on-device; [EdgeTtsEngine]
 * calls out to Microsoft's online voices. [TtsManager] holds one of each and switches between them
 * by the user's engine setting — nothing else in the app depends on which one is active.
 */
interface TtsEngine {
    val id: String
    val displayName: String
    val requiresNetwork: Boolean

    /** Voices this engine can speak with; may hit the network the first time for an online engine. */
    suspend fun listVoices(): List<EngineVoice>

    /** Null return means this sentence failed to synthesize; the caller treats that as a soft error. */
    suspend fun synthesize(text: String, voiceId: String, speed: Float): File?
}

enum class EngineId(val storageKey: String) {
    OFFLINE("offline"),
    EDGE("edge");

    companion object {
        // A saved "edge" choice falls back to offline on builds that ship without online voices.
        fun fromStorageKey(key: String?): EngineId =
            entries.firstOrNull { it.storageKey == key && (it == OFFLINE || BuildConfig.ONLINE_VOICES) } ?: OFFLINE
    }
}

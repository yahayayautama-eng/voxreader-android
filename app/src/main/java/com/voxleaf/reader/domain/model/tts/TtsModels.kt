package com.voxleaf.reader.domain.model.tts

data class TextChunk(
    val id: String,
    val text: String
)

data class SynthesisRequest(
    val text: String,
    val voiceId: String,
    val speed: Float = 1.0f
)

data class SynthesisResult(
    val audioFilePath: String,
    val sampleRate: Int,
    val audioDurationMs: Long,
    val inferenceTimeMs: Long,
    val realTimeFactor: Float,
    val tokenCount: Int,
    val memoryUsedMb: Float
)

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Installed(val path: String, val version: String, val sizeMb: Float) : ModelState
    data class Error(val message: String) : ModelState
}

data class TtsVoice(
    val id: String,
    val displayName: String = id,
    val language: String = "en-US"
)

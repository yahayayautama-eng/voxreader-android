package com.example.audiobook

import java.io.File

/**
 * The only speech-synthesis capability [GenerateAudiobookWorker] needs.
 *
 * Deliberately narrower than `TtsEngine`: audiobook segments must be written somewhere the live
 * playback cache never sweeps, which is a property of the audiobook path specifically and not of
 * every engine. Depending on this instead of on a concrete engine is also what lets the generation
 * pipeline be exercised in JVM tests without native inference.
 */
interface AudiobookSynthesizer {
    /** Null means this sentence failed to synthesize; the caller treats that as a soft error. */
    suspend fun synthesizeForAudiobook(text: String, speed: Float, voicePath: String): File?

    /** Removes segments abandoned by a killed or crashed run. Called once per worker run. */
    suspend fun cleanupAudiobookRuntime()
}

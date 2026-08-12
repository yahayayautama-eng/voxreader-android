package com.example.audiobook

import java.io.File
import java.io.RandomAccessFile

/**
 * In-memory stand-in for [AudiobookSynthesizer] used by [GenerateAudiobookWorkerTest]. Writes real,
 * minimal WAV files so the worker's actual [WavChapterAssembler] / [AacChapterEncoder] pipeline runs
 * unmodified end to end, with no native inference involved.
 */
class FakeAudiobookSynthesizer(private val outputDir: File) : AudiobookSynthesizer {
    var callCount = 0
        private set
    val callsByText = mutableMapOf<String, Int>()
    var cleanupCallCount = 0
        private set

    private val alwaysFailingTexts = mutableSetOf<String>()

    /** Invoked after every successful synthesis; tests use this to simulate a mid-run stop. */
    var afterCall: ((callCount: Int, text: String) -> Unit)? = null

    /** Every call for this exact sentence text will return null. */
    fun alwaysFail(text: String) {
        alwaysFailingTexts += text
    }

    override suspend fun synthesizeForAudiobook(text: String, speed: Float, voicePath: String): File? {
        callCount++
        callsByText[text] = (callsByText[text] ?: 0) + 1
        if (text in alwaysFailingTexts) return null
        outputDir.mkdirs()
        val file = File(outputDir, "seg-$callCount.wav")
        writeSilentWav(file, durationMsFor(text))
        afterCall?.invoke(callCount, text)
        return file
    }

    override suspend fun cleanupAudiobookRuntime() {
        cleanupCallCount++
    }

    private fun durationMsFor(text: String) = (text.length * 20).coerceIn(100, 2000)

    private fun writeSilentWav(file: File, durationMs: Int) {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val dataBytes = byteRate * durationMs / 1000
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            f.writeBytes("RIFF")
            writeLe(f, 36 + dataBytes)
            f.writeBytes("WAVE")
            f.writeBytes("fmt ")
            writeLe(f, 16)
            writeLe(f, 1, 2)
            writeLe(f, CHANNELS, 2)
            writeLe(f, SAMPLE_RATE)
            writeLe(f, byteRate)
            writeLe(f, CHANNELS * BITS_PER_SAMPLE / 8, 2)
            writeLe(f, BITS_PER_SAMPLE, 2)
            f.writeBytes("data")
            writeLe(f, dataBytes)
            f.write(ByteArray(dataBytes))
        }
    }

    private fun writeLe(f: RandomAccessFile, value: Int, bytes: Int = 4) {
        for (i in 0 until bytes) f.write((value shr (8 * i)) and 0xff)
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }
}

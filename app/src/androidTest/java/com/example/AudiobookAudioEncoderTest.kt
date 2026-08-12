package com.example

import android.media.MediaExtractor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.audiobook.AacChapterEncoder
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AudiobookAudioEncoderTest {
    @Test
    fun encodesPcmWavToPlayableM4a() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "encoder-test").apply { mkdirs() }
        val wav = File(directory, "input.wav")
        val m4a = File(directory, "output.m4a")
        try {
            writeSilentWav(wav, sampleRate = 22_050, durationMs = 1_000)
            AacChapterEncoder.encodeWav(wav, m4a)
            assertTrue(m4a.isFile && m4a.length() > 0)
            MediaExtractor().also { extractor ->
                extractor.setDataSource(m4a.absolutePath)
                assertTrue(extractor.trackCount > 0)
                extractor.release()
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeSilentWav(file: File, sampleRate: Int, durationMs: Int) {
        val channels = 1
        val bits = 16
        val pcmBytes = sampleRate * channels * bits / 8 * durationMs / 1_000
        val header = ByteArray(44)
        fun putInt(offset: Int, value: Int) {
            ByteBuffer.wrap(header, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value)
        }
        fun putShort(offset: Int, value: Short) {
            ByteBuffer.wrap(header, offset, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(value)
        }
        "RIFF".forEachIndexed { index, char -> header[index] = char.code.toByte() }
        "WAVEfmt ".forEachIndexed { index, char -> header[8 + index] = char.code.toByte() }
        "data".forEachIndexed { index, char -> header[36 + index] = char.code.toByte() }
        putInt(4, 36 + pcmBytes)
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, channels.toShort())
        putInt(24, sampleRate)
        putInt(28, sampleRate * channels * bits / 8)
        putShort(32, (channels * bits / 8).toShort())
        putShort(34, bits.toShort())
        putInt(40, pcmBytes)
        file.outputStream().use { output -> output.write(header); output.write(ByteArray(pcmBytes)) }
    }
}

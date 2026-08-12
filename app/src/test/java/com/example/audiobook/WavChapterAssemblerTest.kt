package com.example.audiobook

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class WavChapterAssemblerTest {
    @Test
    fun `assembles segments and returns ordered cues`() {
        val dir = Files.createTempDirectory("voxleaf-wav").toFile()
        try {
            val first = wav(dir, "one", 100)
            val second = wav(dir, "two", 200)
            val output = File(dir, "chapter.wav")
            val cues = WavChapterAssembler.assemble(listOf(first, second), output)
            assertEquals(listOf(WavCue(0, 1), WavCue(1, 3)), cues)
            assertEquals(44L + 300L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun wav(dir: File, name: String, pcmBytes: Int): File {
        val file = File(dir, "$name.wav")
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        putShort(header, 22, 1); putInt(header, 24, 100_000); putInt(header, 28, 100_000); putShort(header, 34, 8)
        file.outputStream().use { it.write(header); it.write(ByteArray(pcmBytes)) }
        return file
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value shr 8).toByte(); bytes[offset + 2] = (value shr 16).toByte(); bytes[offset + 3] = (value shr 24).toByte()
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value shr 8).toByte()
    }
}

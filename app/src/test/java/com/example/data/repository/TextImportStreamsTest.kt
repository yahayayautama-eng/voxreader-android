package com.example.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextImportStreamsTest {
    @Test
    fun `copyBounded copies all bytes within the limit`() {
        val input = "VoxLeaf text".encodeToByteArray()
        val output = ByteArrayOutputStream()

        val copied = TextImportStreams.copyBounded(ByteArrayInputStream(input), output, maxBytes = input.size.toLong())

        assertEquals(input.size.toLong(), copied)
        assertArrayEquals(input, output.toByteArray())
    }

    @Test
    fun `copyBounded accepts a stream exactly at the limit`() {
        val input = ByteArray(16) { it.toByte() }
        val output = ByteArrayOutputStream()

        val copied = TextImportStreams.copyBounded(ByteArrayInputStream(input), output, maxBytes = 16)

        assertEquals(16, copied)
    }

    @Test
    fun `copyBounded rejects bytes beyond the limit`() {
        val input = ByteArray(17)

        assertThrows(TextImportTooLargeException::class.java) {
            TextImportStreams.copyBounded(ByteArrayInputStream(input), ByteArrayOutputStream(), maxBytes = 16)
        }
    }
}

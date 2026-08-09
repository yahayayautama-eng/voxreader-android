package com.example.data.repository

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val MAX_TEXT_IMPORT_BYTES = 100L * 1024 * 1024

class TextImportTooLargeException : IOException()

object TextImportStreams {
    fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long = MAX_TEXT_IMPORT_BYTES
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return copied
            if (copied + count > maxBytes) throw TextImportTooLargeException()
            output.write(buffer, 0, count)
            copied += count
        }
    }
}

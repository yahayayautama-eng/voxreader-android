package com.example.audiobook

import java.io.File
import java.io.RandomAccessFile

data class WavCue(val startMs: Long, val endMs: Long)

object WavChapterAssembler {
    fun assemble(inputs: List<File>, output: File): List<WavCue> {
        require(inputs.isNotEmpty()) { "No audio segments" }
        val first = RandomAccessFile(inputs.first(), "r")
        val header = ByteArray(44)
        first.readFully(header)
        val sampleRate = littleInt(header, 24)
        val byteRate = littleInt(header, 28)
        val channels = littleShort(header, 22)
        val bits = littleShort(header, 34)
        first.close()
        var dataBytes = 0L
        var elapsedMs = 0L
        val cues = mutableListOf<WavCue>()
        output.parentFile?.mkdirs()
        RandomAccessFile(output, "rw").use { target ->
            target.setLength(0)
            target.write(header)
            inputs.forEach { input ->
                val pcmBytes = input.length() - 44L
                RandomAccessFile(input, "r").use { source ->
                    source.seek(44)
                    val buffer = ByteArray(16 * 1024)
                    var remaining = pcmBytes
                    while (remaining > 0) {
                        val count = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) break
                        target.write(buffer, 0, count)
                        remaining -= count
                    }
                }
                val duration = pcmBytes * 1000L / byteRate.coerceAtLeast(1)
                cues += WavCue(elapsedMs, elapsedMs + duration)
                elapsedMs += duration
                dataBytes += pcmBytes
            }
            target.seek(4)
            writeLittleInt(target, (36L + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            target.seek(40)
            writeLittleInt(target, dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        require(sampleRate > 0 && byteRate > 0 && channels > 0 && bits > 0) { "Invalid WAV header" }
        return cues
    }

    private fun littleInt(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun littleShort(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun writeLittleInt(file: RandomAccessFile, value: Int) {
        file.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
    }
}

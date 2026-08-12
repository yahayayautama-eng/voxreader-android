package com.example.audiobook

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFileStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val root = File(context.filesDir, "audiobooks")

    fun bookDirectory(bookId: String): File = File(root, bookId).also { it.mkdirs() }

    fun chapterFile(bookId: String, chapterIndex: Int): File =
        File(bookDirectory(bookId), "chapter-${chapterIndex.toString().padStart(3, '0')}.wav")

    fun tempChapterFile(bookId: String, chapterIndex: Int): File =
        File(bookDirectory(bookId), "chapter-${chapterIndex.toString().padStart(3, '0')}.tmp")

    fun commit(temp: File, final: File) {
        require(temp.exists() && temp.length() > 44) { "Generated audio is empty" }
        check(temp.renameTo(final)) { "Could not commit generated audio" }
    }

    fun checksum(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    fun deleteBook(bookId: String) {
        bookDirectory(bookId).deleteRecursively()
    }
}

object StorageEstimator {
    fun estimateAudioBytes(textBytes: Long, bitrateBitsPerSecond: Long = 48_000L): Long {
        val estimatedSeconds = (textBytes / 14L).coerceAtLeast(1L)
        return estimatedSeconds * bitrateBitsPerSecond / 8L
    }

    fun hasRoom(freeBytes: Long, estimatedBytes: Long, safetyMarginBytes: Long = 250L * 1024 * 1024): Boolean =
        freeBytes >= estimatedBytes + safetyMarginBytes
}

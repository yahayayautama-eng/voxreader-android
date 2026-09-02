package com.voxleaf.reader.data.repository

import android.content.Context
import com.voxleaf.reader.data.local.AppDatabase
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryBackupManagerTest {
    private val manager = LibraryBackupManager(mockk<Context>(relaxed = true), mockk<AppDatabase>(relaxed = true))

    @Test
    fun `valid backup graph passes validation`() {
        manager.validate(payload())
    }

    @Test
    fun `dangling passage is rejected before restore transaction`() {
        val invalid = payload().copy(chunks = listOf(TextChunkEntity("chunk", "missing", 0, "Text.")))
        assertThrows(IllegalArgumentException::class.java) { manager.validate(invalid) }
    }

    @Test
    fun `unsupported backup version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { manager.validate(payload().copy(version = 99)) }
    }

    private fun payload() = LibraryBackupPayload(
        exportedAt = 1L,
        books = listOf(BookEntity("book", "Title", "Author", "", "General", "#000000", 1, false)),
        sections = listOf(SectionEntity("section", "book", 1, "One", 1)),
        chunks = listOf(TextChunkEntity("chunk", "section", 0, "Text.")),
        progress = listOf(ReadingProgressEntity("book", 0, 0)),
        bookmarks = emptyList(),
        highlights = emptyList(),
        listeningDays = emptyList()
    )
}

package com.voxleaf.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.data.local.AppDatabase
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.BookmarkEntity
import com.voxleaf.reader.data.local.entity.HighlightEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import com.voxleaf.reader.domain.usecase.StructureResult
import com.voxleaf.reader.domain.usecase.StructureSectionDraft
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SectionStructureApplyTest {
    private lateinit var database: AppDatabase
    private lateinit var importer: TextBookImporterImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = TextBookImporterImpl(context, database, mockk(relaxed = true), mockk(relaxed = true))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `transactional apply remaps progress bookmarks and highlights by passage text`() = runTest {
        database.bookDao().insertBook(BookEntity("book", "Title", "Author", "", "General", "#000000", 1, false))
        database.bookDao().insertSections(listOf(SectionEntity("old", "book", 1, "Old", 1)))
        database.bookDao().insertTextChunks(
            listOf(
                TextChunkEntity("a", "old", 0, "Keep this sentence."),
                TextChunkEntity("b", "old", 1, "Move me.")
            )
        )
        database.bookDao().insertReadingProgress(ReadingProgressEntity("book", 0, 1))
        database.bookmarkDao().insertBookmark(BookmarkEntity("mark", "book", 0, 1, "Old", "Move me.", 1, null))
        database.highlightDao().insertHighlight(HighlightEntity("high", "book", 0, 1, "Old", "Move me.", 0, null, 1))

        val result = importer.apply(
            "book",
            listOf(
                draft("new-a", "Moved", "Move me."),
                draft("new-b", "Kept", "Keep this sentence.")
            )
        )

        assertEquals(StructureResult.Applied, result)
        val restored = database.bookDao().getBookById("book")!!
        assertEquals(0, restored.progress?.currentChapterIndex)
        assertEquals(0, restored.progress?.currentPosition)
        assertEquals("Moved", database.bookmarkDao().getBookmarksForBookNow("book").single().chapterTitle)
        assertEquals(0, database.highlightDao().getHighlightsForBookNow("book").single().chapterIndex)
        assertTrue(restored.sections.all { it.section.isManuallyEdited })
    }

    private fun draft(id: String, title: String, content: String) = StructureSectionDraft(
        id = id,
        title = title,
        content = content,
        detectionSource = "MANUAL",
        detectionConfidence = 1f,
        detectionReason = "Reviewed by user",
        startAnchor = "",
        endAnchor = "",
        isManuallyEdited = true
    )
}

package com.voxleaf.reader.feature.bookmarks

import com.voxleaf.reader.domain.repository.Bookmark
import com.voxleaf.reader.domain.repository.Highlight
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemsLogicTest {
    private fun bookmark(
        id: String,
        chapter: Int,
        sentence: Int,
        text: String,
        timestamp: Long,
        note: String? = null
    ) = Bookmark(id, "book-1", chapter, "Chapter ${chapter + 1}", text, timestamp, note, sentence)

    private fun highlight(
        id: String,
        chapter: Int,
        sentence: Int,
        text: String,
        timestamp: Long,
        note: String? = null
    ) = Highlight(id, "book-1", chapter, sentence, "Chapter ${chapter + 1}", text, 0, note, timestamp)

    @Test
    fun `bookmark search uses book chapter passage and note metadata`() {
        val shelf = BookmarkShelf(
            "book-1",
            "The Book",
            listOf(
                bookmark("one", 0, 1, "A quiet opening", 1, note = "Revisit this"),
                bookmark("two", 1, 3, "Another passage", 2)
            )
        )

        assertEquals(listOf("one", "two"), filterAndSortBookmarks(listOf(shelf), "Book", SavedItemsSort.NEWEST)
            .single().bookmarks.map { it.id }.sorted())
        assertEquals("two", filterAndSortBookmarks(listOf(shelf), "Chapter 2", SavedItemsSort.NEWEST)
            .single().bookmarks.single().id)
        assertEquals("one", filterAndSortBookmarks(listOf(shelf), "Revisit", SavedItemsSort.NEWEST)
            .single().bookmarks.single().id)
        assertEquals("one", filterAndSortBookmarks(listOf(shelf), "quiet", SavedItemsSort.NEWEST)
            .single().bookmarks.single().id)
    }

    @Test
    fun `bookmark sort supports newest book and reading location`() {
        val alpha = BookmarkShelf(
            "a",
            "Alpha",
            listOf(bookmark("late", 2, 4, "Late", 20), bookmark("early", 0, 1, "Early", 10))
        )
        val zulu = BookmarkShelf("z", "Zulu", listOf(bookmark("newest", 0, 0, "Newest", 30)))

        assertEquals("Zulu", filterAndSortBookmarks(listOf(alpha, zulu), "", SavedItemsSort.NEWEST).first().bookTitle)
        assertEquals("Alpha", filterAndSortBookmarks(listOf(zulu, alpha), "", SavedItemsSort.BOOK).first().bookTitle)
        assertEquals(
            listOf("early", "late"),
            filterAndSortBookmarks(listOf(alpha), "", SavedItemsSort.LOCATION).single().bookmarks.map { it.id }
        )
    }

    @Test
    fun `highlight search and location sorting use stored passage metadata`() {
        val shelf = HighlightShelf(
            "book-1",
            "Book",
            listOf(
                highlight("late", 2, 3, "Later text", 20),
                highlight("early", 0, 1, "Earlier text", 10, note = "Important")
            )
        )

        val filtered = filterAndSortHighlights(listOf(shelf), "Important", SavedItemsSort.NEWEST)
        assertEquals("early", filtered.single().highlights.single().id)
        assertEquals(
            listOf("early", "late"),
            filterAndSortHighlights(listOf(shelf), "", SavedItemsSort.LOCATION).single().highlights.map { it.id }
        )
    }
}

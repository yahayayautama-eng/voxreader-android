package com.voxleaf.reader.feature.search

import com.voxleaf.reader.domain.repository.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {
    private val books = listOf(
        Book(
            id = "dune",
            title = "Dune",
            author = "Frank Herbert",
            description = "Politics on Arrakis",
            genre = "Science Fiction",
            totalChapters = 10,
            currentChapterIndex = 8
        ),
        Book(
            id = "gardens",
            title = "Gardens",
            author = "A Writer",
            description = "A desert travel diary",
            genre = "Travel",
            totalChapters = 10,
            currentChapterIndex = 2
        ),
        Book(
            id = "blank-author",
            title = "Anonymous Notes",
            author = "",
            description = "Collected essays",
            genre = "Essays"
        )
    )

    @Test
    fun `blank query is a prompt and genre filters are derived from library metadata`() {
        val state = buildSearchState(books, "", null, SearchSort.RELEVANCE)

        assertTrue(state.isPrompt)
        assertTrue(state.results.isEmpty())
        assertEquals(listOf("Essays", "Science Fiction", "Travel"), state.availableGenres)
    }

    @Test
    fun `metadata matches explain the field and never inspect chapter content`() {
        val title = buildSearchState(books, "Dune", null, SearchSort.RELEVANCE)
        val author = buildSearchState(books, "Herbert", null, SearchSort.RELEVANCE)
        val description = buildSearchState(books, "desert", null, SearchSort.RELEVANCE)
        val genre = buildSearchState(books, "Travel", null, SearchSort.RELEVANCE)

        assertEquals(SearchMatch.TITLE, title.results.single().match)
        assertEquals(SearchMatch.AUTHOR, author.results.single().match)
        assertEquals(listOf("gardens"), description.results.map { it.book.id })
        assertEquals(SearchMatch.DESCRIPTION, description.results.single().match)
        assertEquals(SearchMatch.GENRE, genre.results.single().match)
    }

    @Test
    fun `real genre filter and progress sorting compose deterministically`() {
        val scienceBooks = books + books.first().copy(
            id = "foundation",
            title = "Foundation",
            currentChapterIndex = 3
        )
        val state = buildSearchState(
            scienceBooks,
            query = "",
            selectedGenre = "Science Fiction",
            sort = SearchSort.PROGRESS
        )

        assertEquals(listOf("dune", "foundation"), state.results.map { it.book.id })
        assertTrue(state.results.all { it.book.genre == "Science Fiction" })
    }

    @Test
    fun `stale genre selection is omitted instead of creating a ghost filter`() {
        val state = buildSearchState(books, "", "Missing", SearchSort.TITLE)

        assertNull(state.selectedGenre)
        assertTrue(state.isPrompt)
    }
}

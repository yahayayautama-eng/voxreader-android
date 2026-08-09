package com.example.feature.bookmarks

import com.example.domain.repository.Highlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsMarkdownTest {

    private fun highlight(
        chapter: String,
        text: String,
        note: String? = null,
        sentenceIndex: Int = 0
    ) = Highlight(
        id = "$chapter-$sentenceIndex",
        bookId = "book-1",
        chapterIndex = 0,
        sentenceIndex = sentenceIndex,
        chapterTitle = chapter,
        text = text,
        colorIndex = 0,
        note = note,
        timestamp = 0L
    )

    @Test
    fun `groups by book then chapter and quotes the passage`() {
        val markdown = buildHighlightsMarkdown(
            listOf(
                HighlightShelf(
                    bookId = "book-1",
                    bookTitle = "On Writing",
                    highlights = listOf(
                        highlight("Chapter One", "The road goes ever on.", sentenceIndex = 0),
                        highlight("Chapter One", "And on.", sentenceIndex = 1),
                        highlight("Chapter Two", "A second thought.", note = "worth revisiting")
                    )
                )
            )
        )

        assertTrue(markdown.startsWith("# Vox Reader highlights"))
        assertTrue(markdown.contains("## On Writing"))
        assertEquals(1, Regex("### Chapter One").findAll(markdown).count())
        assertTrue(markdown.contains("> The road goes ever on."))
        assertTrue(markdown.contains("> A second thought."))
        assertTrue(markdown.contains("worth revisiting"))
    }

    @Test
    fun `a blank note adds no empty paragraph`() {
        val markdown = buildHighlightsMarkdown(
            listOf(HighlightShelf("book-1", "Book", listOf(highlight("Ch", "Text.", note = "   "))))
        )
        assertTrue(markdown.contains("> Text."))
        assertTrue(!markdown.contains("\n\n\n"))
    }

    @Test
    fun `no highlights still produces a valid document`() {
        assertEquals("# Vox Reader highlights\n", buildHighlightsMarkdown(emptyList()))
    }
}

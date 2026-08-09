package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextExtractorTest {

    @Test
    fun `splits block elements into paragraphs`() {
        val paragraphs = HtmlTextExtractor.extract("<p>One.</p><p>Two.</p><div>Three.</div>")
        assertEquals(listOf("One.", "Two.", "Three."), paragraphs.map { it.text })
    }

    @Test
    fun `marks headings so chapters can be titled`() {
        val paragraphs = HtmlTextExtractor.extract("<h2>The Title</h2><p>Body text.</p>")
        assertEquals("The Title", paragraphs[0].text)
        assertTrue(paragraphs[0].isHeading)
        assertFalse(paragraphs[1].isHeading)
    }

    @Test
    fun `survives unclosed tags that no XML parser would accept`() {
        // This is why MOBI can't reuse the EPUB pull parser.
        val paragraphs = HtmlTextExtractor.extract("<p>First<p>Second<br>Third")
        assertEquals(listOf("First", "Second", "Third"), paragraphs.map { it.text })
    }

    @Test
    fun `drops script and style content`() {
        val paragraphs = HtmlTextExtractor.extract(
            "<style>p { color: red }</style><script>alert('x')</script><p>Real text.</p>"
        )
        assertEquals(listOf("Real text."), paragraphs.map { it.text })
    }

    @Test
    fun `collapses whitespace inside a paragraph`() {
        val paragraphs = HtmlTextExtractor.extract("<p>Spread   across\n  lines.</p>")
        assertEquals("Spread across lines.", paragraphs[0].text)
    }

    @Test
    fun `resolves named and numeric entities`() {
        assertEquals("Tom & Jerry", HtmlTextExtractor.unescape("Tom &amp; Jerry"))
        assertEquals("café", HtmlTextExtractor.unescape("caf&#233;"))
        assertEquals("café", HtmlTextExtractor.unescape("caf&#xE9;"))
        assertEquals("a — b", HtmlTextExtractor.unescape("a &mdash; b"))
    }

    @Test
    fun `leaves an unknown entity alone rather than mangling it`() {
        assertEquals("&notreal;", HtmlTextExtractor.unescape("&notreal;"))
    }

    @Test
    fun `emits nothing for markup with no text`() {
        assertTrue(HtmlTextExtractor.extract("<div><span></span></div>").isEmpty())
    }
}

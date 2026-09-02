package com.voxleaf.reader.data.repository

import com.voxleaf.reader.data.repository.PdfBookParser.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfFontSizeSectionsTest {

    private fun body(text: String, page: Int = 0) = TextLine(text, 11f, page)
    private fun heading(text: String, page: Int = 0) = TextLine(text, 18f, page)

    private fun paddedBody(count: Int, page: Int = 0) =
        (1..count).map { body("Body sentence number $it carrying enough text to weigh the size vote.", page) }

    @Test
    fun `splits at lines set larger than the body`() {
        val lines = buildList {
            add(heading("Chapter One"))
            addAll(paddedBody(12))
            add(heading("Chapter Two", page = 1))
            addAll(paddedBody(12, page = 1))
        }

        val sections = PdfBookParser.sectionsFromFontSize(lines)

        assertEquals(2, sections?.size)
        assertEquals("Chapter One", sections?.get(0)?.title)
        assertEquals("Chapter Two", sections?.get(1)?.title)
    }

    /**
     * The bug this whole path exists for: a running header repeats at body size on every page, so
     * size-based detection must ignore it where string-frequency heuristics were fooled into
     * treating it as a chapter start.
     */
    @Test
    fun `running headers set at body size are not headings`() {
        val lines = buildList {
            add(heading("The Real Chapter"))
            repeat(4) { page ->
                add(body("A Study of Running Headers", page))
                addAll(paddedBody(6, page))
            }
            add(heading("Another Real Chapter", page = 5))
            addAll(paddedBody(8, page = 5))
        }

        val sections = PdfBookParser.sectionsFromFontSize(lines)

        assertEquals(2, sections?.size)
        assertEquals("The Real Chapter", sections?.get(0)?.title)
        assertEquals("Another Real Chapter", sections?.get(1)?.title)
    }

    @Test
    fun `uniformly set document yields no sections`() {
        assertNull(PdfBookParser.sectionsFromFontSize(paddedBody(40)))
    }

    @Test
    fun `too few lines to judge yields no sections`() {
        assertNull(PdfBookParser.sectionsFromFontSize(listOf(heading("Title"), body("One line."))))
    }

    /** A slide deck or poster is mostly large type; the ratio has stopped carrying structure. */
    @Test
    fun `mostly large type yields no sections`() {
        val lines = (1..30).map { TextLine("Large line $it", if (it % 3 == 0) 11f else 20f, it) }
        assertNull(PdfBookParser.sectionsFromFontSize(lines))
    }

    /** A drop cap must not promote the paragraph it opens. */
    @Test
    fun `single oversized glyph does not promote a body line`() {
        val lines = buildList {
            add(heading("Chapter One"))
            addAll(paddedBody(12))
            add(heading("Chapter Two", page = 1))
            addAll(paddedBody(12, page = 1))
        }
        val sections = PdfBookParser.sectionsFromFontSize(lines)
        assertEquals(2, sections?.size)
        assertEquals(false, sections?.any { it.title.startsWith("Body sentence") })
    }
}

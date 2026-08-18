package com.voxleaf.reader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfOutlineSectionsTest {

    private val pages = listOf("cover", "one a", "one b", "", "two a", "three a")

    @Test
    fun `slices pages at bookmark boundaries`() {
        val sections = PdfBookParser.sectionsFromOutline(
            pages,
            listOf(
                PdfBookParser.OutlineEntry("Chapter One", 1),
                PdfBookParser.OutlineEntry("Chapter Two", 4),
                PdfBookParser.OutlineEntry("Chapter Three", 5)
            )
        )

        assertEquals(listOf("Chapter One", "Chapter Two", "Chapter Three"), sections?.map { it.title })
        // Blank page 3 is dropped from the body but still counted when locating the next boundary.
        assertEquals("one a\n\none b", sections?.get(0)?.content)
        assertEquals("two a", sections?.get(1)?.content)
        assertEquals("three a", sections?.get(2)?.content)
    }

    @Test
    fun `ignores an outline too thin to be a table of contents`() {
        assertNull(PdfBookParser.sectionsFromOutline(pages, emptyList()))
        assertNull(
            PdfBookParser.sectionsFromOutline(pages, listOf(PdfBookParser.OutlineEntry("Only", 0)))
        )
    }

    @Test
    fun `drops bookmarks that resolve to no text and out-of-range pages`() {
        val sections = PdfBookParser.sectionsFromOutline(
            pages,
            listOf(
                PdfBookParser.OutlineEntry("Front", 0),
                PdfBookParser.OutlineEntry("Empty", 3),
                PdfBookParser.OutlineEntry("Rest", 4),
                PdfBookParser.OutlineEntry("Past the end", 99)
            )
        )

        assertEquals(listOf("Front", "Rest"), sections?.map { it.title })
        assertEquals("two a\n\nthree a", sections?.get(1)?.content)
    }
}

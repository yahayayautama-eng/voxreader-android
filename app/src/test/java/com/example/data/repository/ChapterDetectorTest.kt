package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterDetectorTest {

    @Test
    fun `splits on labelled chapter headings`() {
        val sections = ChapterDetector.split(
            """
            Chapter 1: The Arrival

            He reached the harbour before dawn and waited for the tide to turn.

            Chapter 2: The Letter

            The envelope had no stamp, only a name written in a hand he knew.
            """.trimIndent()
        )

        assertEquals(listOf("Chapter 1: The Arrival", "Chapter 2: The Letter"), sections?.map { it.title })
        assertEquals(true, sections?.get(0)?.content?.startsWith("He reached the harbour"))
    }

    @Test
    fun `keeps content before the first heading`() {
        val sections = ChapterDetector.split(
            """
            A preface long enough to be worth keeping around as its own section.

            Chapter 1

            First body.

            Chapter 2

            Second body.
            """.trimIndent()
        )

        assertEquals("Opening", sections?.first()?.title)
    }

    @Test
    fun `returns null when the document has no headings`() {
        assertNull(
            ChapterDetector.split(
                "Plain prose with no structure at all, running on for a while.\n\nAnother paragraph."
            )
        )
    }

    @Test
    fun `returns null on a single heading rather than inventing chapters`() {
        assertNull(ChapterDetector.split("Chapter 1\n\nOnly one section here."))
    }

    @Test
    fun `ignores an all-caps line buried in body text`() {
        assertNull(
            ChapterDetector.split(
                "He shouted STOP RIGHT THERE\nand the horse kept going anyway.\nNothing else happened."
            )
        )
    }

    @Test
    fun `ignores a 4-digit year leaking in from a running header`() {
        assertNull(
            ChapterDetector.split(
                "1994 met with a strong response, both supportive and critical.\n" +
                    "Reviewers praised the case studies but disputed the interpretation.\n" +
                    "The debate continued for years afterward without resolution."
            )
        )
    }

    @Test
    fun `does not split a single-chapter article on its own title and subtitle`() {
        // Two isolated all-caps lines (a title and a kicker/subtitle) each look like a heading in
        // isolation, but with nothing after the second one except unbroken prose there's only one
        // real section here — this is the exact shape of a blog-style PDF import that has no chapters.
        val text = """
            SATURN, CERN, AND THE CUBE OF ILLUSION

            DIGGING INTO THE CORE

            Baphomet was a god that the Knights Templars were accused of worshipping.

            The story continues for several more paragraphs of unbroken prose without
            any further headings anywhere in the rest of the document at all.
        """.trimIndent()
        assertNull(ChapterDetector.split(text))
    }
}

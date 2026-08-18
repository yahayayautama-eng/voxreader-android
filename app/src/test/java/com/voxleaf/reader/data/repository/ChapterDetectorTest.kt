package com.voxleaf.reader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `splits on spelled-out number headings and multi-line titles`() {
        val text = """
            PROLOGUE

            In the beginning, long before the stars awoke.

            CHAPTER ONE
            The Boy Who Lived

            Mr. and Mrs. Dursley of number four Privet Drive were proud to say.

            CHAPTER TWO
            The Vanishing Glass

            Nearly ten years had passed since the Dursleys had woken up.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertEquals(3, sections?.size)
        assertEquals("PROLOGUE", sections?.get(0)?.title)
        assertEquals("CHAPTER ONE: The Boy Who Lived", sections?.get(1)?.title)
        assertEquals("CHAPTER TWO: The Vanishing Glass", sections?.get(2)?.title)
    }

    @Test
    fun `splits on Roman numerals and named sections`() {
        val text = """
            Introduction

            This is the introduction to the work.

            I

            First chapter content.

            II

            Second chapter content.

            Epilogue

            Concluding remarks.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertEquals(4, sections?.size)
        assertEquals("Introduction", sections?.get(0)?.title)
        assertEquals("I", sections?.get(1)?.title)
        assertEquals("II", sections?.get(2)?.title)
        assertEquals("Epilogue", sections?.get(3)?.title)
    }

    @Test
    fun `splits on multi-lingual chapter markers and CJK`() {
        val text = """
            Chapitre 1: L'Enfance

            Il était une fois un petit garçon qui vivait dans les bois.

            Capítulo 2: El Viaje

            El viaje comenzó al amanecer bajo un cielo despejado.

            Kapitel 3: Die Entdeckung

            Es war ein kalter Wintermorgen im tiefen Wald.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertEquals(3, sections?.size)
        assertEquals("Chapitre 1: L'Enfance", sections?.get(0)?.title)
        assertEquals("Capítulo 2: El Viaje", sections?.get(1)?.title)
        assertEquals("Kapitel 3: Die Entdeckung", sections?.get(2)?.title)
    }

    @Test
    fun `filters out repeating running page headers`() {
        val text = """
            Chapter 1: The First Step

            Page 1 content starts here and goes on for several sentences describing the scene in detail.

            Chapter 1: The First Step

            Page 2 content continues the first chapter with further narrative.

            Chapter 2: The Horizon

            Page 3 starts a completely new chapter with fresh events.

            Chapter 2: The Horizon

            Page 4 continues chapter two.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertEquals(2, sections?.size)
        assertEquals("Chapter 1: The First Step", sections?.get(0)?.title)
        assertEquals("Chapter 2: The Horizon", sections?.get(1)?.title)
    }

    // === NEW TESTS FOR BUG FIXES ===

    @Test
    fun `Bug 1 - subtitle line consumption preserves first sentence of body`() {
        // Before the fix, single-line titles with ":" would eat the first line of body text
        val text = """
            Chapter 1: The Dark Forest

            The trees stood tall in the moonlight and cast long shadows.

            Chapter 2: The River

            Water flowed swiftly beneath the ancient stone bridge.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertEquals(2, sections?.size)
        // The first sentence must NOT be eaten
        assertTrue(sections?.get(0)?.content?.contains("trees stood tall") == true)
        assertTrue(sections?.get(1)?.content?.contains("Water flowed swiftly") == true)
    }

    @Test
    fun `does not consume an immediate sentence after a bare chapter label`() {
        val text = """
            Chapter 1
            The first sentence belongs to the chapter body.
            More body text follows on the next line.

            Chapter 2
            The second chapter starts here.
            More text follows in chapter two.
        """.trimIndent()

        val sections = ChapterDetector.split(text)

        assertEquals(listOf("Chapter 1", "Chapter 2"), sections?.map { it.title })
        assertTrue(sections?.get(0)?.content?.contains("The first sentence belongs") == true)
        assertTrue(sections?.get(1)?.content?.contains("The second chapter starts") == true)
    }

    @Test
    fun `keeps numbered body lists inside their chapter`() {
        val text = """
            Chapter 1

            The chapter explains the following process in detail.
            1. First item remains body text.
            2. Second item remains body text.

            Chapter 2

            The next chapter begins with its own body.
        """.trimIndent()

        val sections = ChapterDetector.split(text)

        assertEquals(2, sections?.size)
        assertTrue(sections?.get(0)?.content?.contains("1. First item remains body text.") == true)
        assertTrue(sections?.get(0)?.content?.contains("2. Second item remains body text.") == true)
    }

    @Test
    fun `Bug 2 - detects lowercase Roman numerals`() {
        val text = """
            Preface

            A brief introductory note about the text.

            i

            First chapter content with lowercase roman numeral heading.

            ii

            Second chapter content with lowercase roman numeral heading.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertNotNull(sections)
        assertTrue(sections!!.size >= 3)
    }

    @Test
    fun `Bug 5 - alternating running headers are stripped`() {
        // Alternating [A, B, A, B, A, B] pattern from facing pages
        val lines = mutableListOf<String>()
        lines += "Chapter 1: Introduction"
        lines += ""
        for (pageNum in 1..10) {
            // Alternate header between book title and chapter title
            if (pageNum % 2 == 0) {
                lines += "THE GREAT NOVEL"
                lines += ""
            } else {
                lines += "Chapter 1: Introduction"
                lines += ""
            }
            lines += "Content on page $pageNum with enough text to be meaningful and to form a real paragraph."
            lines += ""
        }
        lines += "Chapter 2: The Journey Begins"
        lines += ""
        for (pageNum in 11..20) {
            if (pageNum % 2 == 0) {
                lines += "THE GREAT NOVEL"
                lines += ""
            } else {
                lines += "Chapter 2: The Journey Begins"
                lines += ""
            }
            lines += "Content on page $pageNum describing the journey with enough detail to fill a paragraph."
            lines += ""
        }

        val sections = ChapterDetector.split(lines.joinToString("\n"))
        // Should not create dozens of micro-chapters from running headers
        assertNotNull(sections)
        assertTrue("Expected <= 5 chapters but got ${sections!!.size}", sections.size <= 5)
    }

    @Test
    fun `Bug 7 - isChapterLabel matches numbered headings`() {
        assertTrue(ChapterDetector.isChapterLabel("1. Introduction"))
        assertTrue(ChapterDetector.isChapterLabel("Chapter 1"))
        assertTrue(ChapterDetector.isChapterLabel("Prologue"))
        assertTrue(ChapterDetector.isChapterLabel("IV"))
    }

    @Test
    fun `Bug 8 - extended number words work in chapter headings`() {
        val text = """
            Chapter Eleventh

            The eleventh chapter begins with a surprising twist.

            Chapter Twentieth

            In the twentieth chapter, the hero faces a final challenge.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertNotNull(sections)
        assertEquals(2, sections!!.size)
    }

    @Test
    fun `scene break splitting works with triple asterisks`() {
        val scene1 = "A".repeat(300)
        val scene2 = "B".repeat(300)
        val scene3 = "C".repeat(300)
        val text = "$scene1\n\n***\n\n$scene2\n\n***\n\n$scene3"

        val sections = ChapterDetector.splitBySceneBreaks(text)
        assertNotNull(sections)
        assertEquals(3, sections!!.size)
        assertEquals("Scene 1", sections[0].title)
        assertEquals("Scene 2", sections[1].title)
        assertEquals("Scene 3", sections[2].title)
    }

    @Test
    fun `scene break splitting returns null for too few breaks`() {
        val text = "Some content\n\n***\n\nMore content"
        assertNull(ChapterDetector.splitBySceneBreaks(text))
    }

    @Test
    fun `scene break splitting returns null for very short sections`() {
        val text = "Short\n\n***\n\nAlso short\n\n***\n\nToo short"
        assertNull(ChapterDetector.splitBySceneBreaks(text))
    }

    @Test
    fun `TOC loop prevention skips plaintext table of contents`() {
        val text = """
            Table of Contents
            Chapter 1: The Beginning
            Chapter 2: The Journey
            Chapter 3: The End

            Chapter 1: The Beginning

            The story began on a cold winter morning when the snow covered everything in sight.

            Chapter 2: The Journey

            They traveled through mountains and valleys for many days and nights.

            Chapter 3: The End

            At last, they arrived at the ancient city and found what they were looking for.
        """.trimIndent()

        val sections = ChapterDetector.split(text)
        assertNotNull(sections)
        assertEquals(3, sections!!.size)
        // Each section should have real body content, not just a TOC line
        assertTrue(sections[0].content.length > 30)
    }
}

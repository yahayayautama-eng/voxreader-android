package com.voxleaf.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsChapterQueueTest {
    @Test
    fun `queue skips empty chapters and returns parsed sentences`() {
        val chapters = listOf(
            chapter(0, "Finished."),
            chapter(1, "   "),
            chapter(2, "First sentence. Second sentence.")
        )

        val next = nextPlayableChapter(chapters, afterIndex = 0)

        assertEquals(2, next?.first)
        assertEquals(listOf("First sentence.", "Second sentence."), next?.second)
        assertNull(nextPlayableChapter(chapters, afterIndex = 2))
    }

    private fun chapter(index: Int, text: String) = TtsChapter(
        nowPlaying = NowPlaying("book", "Book", index, "Chapter ${index + 1}"),
        text = text
    )
}

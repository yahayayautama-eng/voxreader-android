package com.voxleaf.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsTextParserTest {
    @Test
    fun `english splits on terminal punctuation followed by whitespace`() {
        assertEquals(listOf("One.", "Two?", "Three!"), TtsTextParser.sentences("One. Two? Three!"))
    }

    @Test
    fun `chinese splits after full-width marks and keeps closing quotes with their sentence`() {
        assertEquals(
            listOf("今天下雨了。", "他说：「我们走吧！」", "好吗？"),
            TtsTextParser.sentences("今天下雨了。他说：「我们走吧！」好吗？")
        )
    }
}

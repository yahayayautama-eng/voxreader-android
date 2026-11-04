package com.voxleaf.reader.tts

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningTrackerTest {

    @Test
    fun `week starts on the Monday on or before today`() {
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(monday, ListeningTracker.weekStart(monday))
        assertEquals(monday, ListeningTracker.weekStart(LocalDate.of(2026, 11, 4)))
        assertEquals(monday, ListeningTracker.weekStart(LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 11, 9), ListeningTracker.weekStart(LocalDate.of(2026, 11, 9)))
    }
}

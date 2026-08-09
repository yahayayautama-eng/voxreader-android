package com.example.feature.stats

import com.example.domain.repository.ListeningDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ListeningStatsTest {

    private val today = LocalDate.of(2026, 3, 15)

    private fun days(vararg entries: Pair<LocalDate, Int>) = entries.map { ListeningDay(it.first, it.second) }

    @Test
    fun `counts a streak running up to today`() {
        val summary = summarize(
            days(
                today to 600,
                today.minusDays(1) to 300,
                today.minusDays(2) to 120
            ),
            today
        )

        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.longestStreak)
        assertEquals(1020, summary.totalSeconds)
        assertEquals(3, summary.activeDays)
    }

    @Test
    fun `a streak ending yesterday still counts today`() {
        // Otherwise every streak would read as broken each morning before the first session.
        val summary = summarize(days(today.minusDays(1) to 600, today.minusDays(2) to 600), today)
        assertEquals(2, summary.currentStreak)
    }

    @Test
    fun `a gap of two days breaks the current streak but not the record`() {
        val summary = summarize(
            days(
                today.minusDays(3) to 60,
                today.minusDays(4) to 60,
                today.minusDays(5) to 60,
                today.minusDays(6) to 60
            ),
            today
        )

        assertEquals(0, summary.currentStreak)
        assertEquals(4, summary.longestStreak)
    }

    @Test
    fun `streaks span a month boundary`() {
        val march1 = LocalDate.of(2026, 3, 1)
        val summary = summarize(
            days(march1 to 60, march1.minusDays(1) to 60, march1.minusDays(2) to 60),
            march1
        )
        assertEquals(3, summary.currentStreak)
    }

    @Test
    fun `heatmap covers whole weeks and ends today`() {
        val summary = summarize(days(today to 60), today, weeks = 4)

        assertEquals(today, summary.cells.last().date)
        assertEquals(java.time.DayOfWeek.MONDAY, summary.cells.first().date.dayOfWeek)
        // Whole weeks back from today's week, minus the days after today in the current week.
        assertEquals(4 * 7 - (7 - today.dayOfWeek.value), summary.cells.size)
    }

    @Test
    fun `intensity levels rise with time listened`() {
        assertEquals(0, DayCell(today, 0).level)
        assertEquals(1, DayCell(today, 60).level)
        assertEquals(2, DayCell(today, 10 * 60).level)
        assertEquals(3, DayCell(today, 30 * 60).level)
        assertEquals(4, DayCell(today, 2 * 3600).level)
    }

    @Test
    fun `formats durations for a stat tile`() {
        assertEquals("—", formatDuration(0))
        assertEquals("<1m", formatDuration(30))
        assertEquals("12m", formatDuration(12 * 60))
        assertEquals("4h 12m", formatDuration(4 * 3600 + 12 * 60))
    }
}

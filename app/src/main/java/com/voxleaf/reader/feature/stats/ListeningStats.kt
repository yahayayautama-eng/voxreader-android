package com.voxleaf.reader.feature.stats

import com.voxleaf.reader.domain.repository.ListeningDay
import java.time.LocalDate

/**
 * Everything the stats screen shows, derived in one pass so the UI holds no counting logic.
 *
 * Kept free of Android and Compose types on purpose: streaks and calendar arithmetic are exactly the
 * kind of thing that breaks silently at month boundaries, and this way they're unit-testable.
 */
data class ListeningSummary(
    val totalSeconds: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,
    /** Oldest to newest, one cell per day, gaps filled with zero so the heatmap grid stays aligned. */
    val cells: List<DayCell>
)

data class DayCell(val date: LocalDate, val seconds: Int) {
    /** 0 = untouched, 1..4 = increasing intensity. Fixed thresholds beat relative ones for a
     *  personal heatmap: a good day should look the same in week one and week fifty. */
    val level: Int
        get() = when {
            seconds <= 0 -> 0
            seconds < 5 * 60 -> 1
            seconds < 20 * 60 -> 2
            seconds < 60 * 60 -> 3
            else -> 4
        }
}

/**
 * @param today the reference date; passed in rather than read from the clock so the streak rules can
 *   be tested, and so "today" means the same thing everywhere in one composition.
 * @param weeks how far back the heatmap reaches.
 */
fun summarize(days: List<ListeningDay>, today: LocalDate, weeks: Int = 26): ListeningSummary {
    val secondsByDate = days.associate { it.date to it.seconds }

    // The grid reads in columns of whole weeks, so it starts on the Monday of the earliest week shown.
    val firstDay = today.minusWeeks((weeks - 1).toLong())
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val cells = generateSequence(firstDay) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { DayCell(it, secondsByDate[it] ?: 0) }
        .toList()

    val activeDates = secondsByDate.filterValues { it > 0 }.keys

    // A streak that includes today is still running; one that ended yesterday is still "current" until
    // the day is out, otherwise every streak would appear broken each morning before the first session.
    var currentStreak = 0
    var cursor = if (today in activeDates) today else today.minusDays(1)
    while (cursor in activeDates) {
        currentStreak++
        cursor = cursor.minusDays(1)
    }

    var longestStreak = 0
    var run = 0
    activeDates.sorted().fold<LocalDate, LocalDate?>(null) { previous, date ->
        run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
        longestStreak = maxOf(longestStreak, run)
        date
    }

    return ListeningSummary(
        totalSeconds = days.sumOf { it.seconds },
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        activeDays = activeDates.size,
        cells = cells
    )
}

/** "4h 12m", "12m", "just started" — short enough for a stat tile. */
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        seconds <= 0 -> "—"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

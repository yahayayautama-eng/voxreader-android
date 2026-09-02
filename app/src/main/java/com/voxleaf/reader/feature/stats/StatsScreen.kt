package com.voxleaf.reader.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.core.ui.components.EmptyState
import com.voxleaf.reader.core.ui.components.toDisplayTitle
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.ui.theme.SpineColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class BookListening(val bookId: String, val title: String, val seconds: Int)

data class StatsUiState(
    val summary: ListeningSummary = ListeningSummary(0, 0, 0, 0, emptyList()),
    val books: List<BookListening> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    bookRepository: BookRepository
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        bookRepository.getListeningDays(),
        bookRepository.getListeningByBook(),
        bookRepository.getBooks()
    ) { days, byBook, books ->
        val titleById = books.associate { it.id to it.title }
        StatsUiState(
            summary = summarize(days, LocalDate.now()),
            books = byBook.map { (bookId, seconds) ->
                // History outlives the book it came from, so a deleted book still shows its time.
                BookListening(bookId, titleById[bookId] ?: "Removed document", seconds)
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )
}

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.summary.totalSeconds == 0) {
        EmptyState(
            title = "No listening yet",
            message = "Play any book with the offline voice and your daily minutes, streaks, and per-book totals appear here.",
            icon = Icons.Outlined.Insights
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Listened", formatDuration(state.summary.totalSeconds), Modifier.fillMaxWidth())
                StatTile("Streak", "${state.summary.currentStreak}d", Modifier.fillMaxWidth())
                StatTile("Best", "${state.summary.longestStreak}d", Modifier.fillMaxWidth())
            }
        }
        item { Heatmap(state.summary.cells) }
        if (state.books.isNotEmpty()) {
            item {
                Text(
                    text = "By book",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(state.books.size, key = { state.books[it].bookId }) { index ->
                val entry = state.books[index]
                BookTotalRow(entry, state.summary.totalSeconds)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.testTag("stat_tile_${label.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Weeks run left to right, weekdays top to bottom — the layout people already read on GitHub. It
 * scrolls horizontally rather than shrinking cells, because a squeezed cell stops being tappable
 * and stops being legible at the same moment.
 */
@Composable
private fun Heatmap(cells: List<DayCell>) {
    val weeks = cells.chunked(7)
    Column {
        Text(
            text = "Last ${weeks.size} weeks",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .testTag("listening_heatmap")
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(levelColor(cell.level))
                                .semantics {
                                    contentDescription = "${cell.date}: ${formatDuration(cell.seconds)} listened"
                                }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(levelColor(level))
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = "More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun levelColor(level: Int) = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceContainerHighest
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun BookTotalRow(entry: BookListening, totalSeconds: Int) {
    val share = if (totalSeconds > 0) entry.seconds.toFloat() / totalSeconds else 0f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title.toDisplayTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatDuration(entry.seconds),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SpineColor.forKey(entry.bookId).fill)
                )
            }
        }
    }
}

package com.example.feature.bookmarks

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.core.ui.components.ConfirmationDialog
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.toDisplayTitle
import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.repository.Highlight
import com.example.ui.theme.HighlightColor
import com.example.ui.theme.SpineColor
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VoxLeafSerif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** A bookmark without its book is orphaned; the shelf grouping is what makes the list navigable. */
data class BookmarkShelf(
    val bookId: String,
    val bookTitle: String,
    val bookmarks: List<Bookmark>
)

/** The same grouping for marked passages, ordered as they appear in the book rather than by date. */
data class HighlightShelf(
    val bookId: String,
    val bookTitle: String,
    val highlights: List<Highlight>
)

/**
 * Notes are only worth taking if they can leave. Markdown because it pastes intact into Obsidian,
 * Notion, and every plain editor — no exporter per destination.
 */
fun buildHighlightsMarkdown(shelves: List<HighlightShelf>): String = buildString {
    appendLine("# Vox Reader highlights")
    shelves.forEach { shelf ->
        appendLine()
        appendLine("## ${shelf.bookTitle}")
        shelf.highlights
            .groupBy { it.chapterTitle }
            .forEach { (chapterTitle, items) ->
                appendLine()
                appendLine("### $chapterTitle")
                items.forEach { highlight ->
                    appendLine()
                    appendLine("> ${highlight.text.trim()}")
                    highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                        appendLine()
                        appendLine(note.trim())
                    }
                }
            }
    }
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    val shelves: StateFlow<List<BookmarkShelf>> =
        combine(bookRepository.getBookmarks(), bookRepository.getBooks()) { bookmarks, books ->
            val titleById = books.associate { it.id to it.title }
            bookmarks
                .sortedByDescending { it.timestamp }
                .groupBy { it.bookId }
                .map { (bookId, items) ->
                    BookmarkShelf(
                        bookId = bookId,
                        bookTitle = titleById[bookId] ?: "Removed document",
                        bookmarks = items
                    )
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val highlightShelves: StateFlow<List<HighlightShelf>> =
        combine(bookRepository.getHighlights(), bookRepository.getBooks()) { highlights, books ->
            val titleById = books.associate { it.id to it.title }
            highlights
                .groupBy { it.bookId }
                .map { (bookId, items) ->
                    HighlightShelf(
                        bookId = bookId,
                        bookTitle = titleById[bookId] ?: "Removed document",
                        // Reading order, so an export reads like the book and not like a activity log.
                        highlights = items.sortedWith(compareBy({ it.chapterIndex }, { it.sentenceIndex }))
                    )
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            bookRepository.removeBookmark(id)
        }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch {
            bookRepository.removeHighlight(id)
        }
    }
}

private enum class AnnotationTab(val label: String) { Places("Saved places"), Highlights("Highlights") }

@Composable
fun BookmarksScreen(
    onNavigateToReader: (Bookmark) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val highlightShelves by viewModel.highlightShelves.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Bookmark?>(null) }
    var pendingHighlightDelete by remember { mutableStateOf<Highlight?>(null) }
    var tab by remember { mutableStateOf(AnnotationTab.Places) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp)
        ) {
            Text(
                text = "Marks",
                fontFamily = VoxLeafSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // Export only makes sense for passages; a saved place has nothing to paste elsewhere.
            if (tab == AnnotationTab.Highlights && highlightShelves.isNotEmpty()) {
                IconButton(
                    onClick = { shareHighlights(context, buildHighlightsMarkdown(highlightShelves)) },
                    modifier = Modifier.testTag("export_highlights_button")
                ) {
                    Icon(
                        Icons.Outlined.IosShare,
                        contentDescription = "Export highlights as Markdown",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            AnnotationTab.entries.forEach { entry ->
                val selected = entry == tab
                Surface(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clickable { tab = entry }
                        .testTag("annotation_tab_${entry.name.lowercase()}")
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                AnnotationTab.Places -> if (shelves.isEmpty()) {
                    EmptyState(
                        title = "No saved places yet",
                        message = "While listening, tap the bookmark icon to save where you are. Your marks land here, grouped by document.",
                        icon = Icons.Outlined.Bookmarks
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        shelves.forEach { shelf ->
                            item(key = "shelf_${shelf.bookId}") {
                                ShelfHeader(shelf.bookId, shelf.bookTitle, shelf.bookmarks.size)
                            }
                            items(
                                count = shelf.bookmarks.size,
                                key = { shelf.bookmarks[it].id }
                            ) { index ->
                                val bookmark = shelf.bookmarks[index]
                                BookmarkRow(
                                    bookmark = bookmark,
                                    spine = SpineColor.forKey(shelf.bookId),
                                    onClick = { onNavigateToReader(bookmark) },
                                    onDelete = { pendingDelete = bookmark }
                                )
                            }
                            item(key = "gap_${shelf.bookId}") { Spacer(modifier = Modifier.height(14.dp)) }
                        }
                    }
                }

                AnnotationTab.Highlights -> if (highlightShelves.isEmpty()) {
                    EmptyState(
                        title = "No highlights yet",
                        message = "Long-press any sentence while reading to mark it in one of five colours and attach a note.",
                        icon = Icons.Outlined.FormatQuote
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        highlightShelves.forEach { shelf ->
                            item(key = "hl_shelf_${shelf.bookId}") {
                                ShelfHeader(shelf.bookId, shelf.bookTitle, shelf.highlights.size)
                            }
                            items(
                                count = shelf.highlights.size,
                                key = { shelf.highlights[it].id }
                            ) { index ->
                                val highlight = shelf.highlights[index]
                                HighlightRow(
                                    highlight = highlight,
                                    onClick = {
                                        onNavigateToReader(
                                            Bookmark(
                                                id = highlight.id,
                                                bookId = highlight.bookId,
                                                chapterIndex = highlight.chapterIndex,
                                                sentenceIndex = highlight.sentenceIndex,
                                                chapterTitle = highlight.chapterTitle,
                                                textSnippet = highlight.text
                                            )
                                        )
                                    },
                                    onDelete = { pendingHighlightDelete = highlight }
                                )
                            }
                            item(key = "hl_gap_${shelf.bookId}") { Spacer(modifier = Modifier.height(14.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Removing a mark cannot be undone, so it asks before it acts.
    pendingDelete?.let { bookmark ->
        ConfirmationDialog(
            title = "Remove bookmark?",
            text = "This deletes the saved place in \"${bookmark.chapterTitle}\". Your reading progress stays where it is.",
            confirmText = "Remove",
            dismissText = "Cancel",
            onConfirm = {
                viewModel.deleteBookmark(bookmark.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
    pendingHighlightDelete?.let { highlight ->
        ConfirmationDialog(
            title = "Remove highlight?",
            text = "This deletes the marked passage and its note. The text itself stays in the book.",
            confirmText = "Remove",
            dismissText = "Cancel",
            onConfirm = {
                viewModel.deleteHighlight(highlight.id)
                pendingHighlightDelete = null
            },
            onDismiss = { pendingHighlightDelete = null }
        )
    }
}

/** Hands the markdown to whatever the user already uses; Vox Reader writes no files of its own. */
private fun shareHighlights(context: Context, markdown: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, "Vox Reader highlights")
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(intent, "Export highlights"))
}

@Composable
private fun ShelfHeader(bookId: String, bookTitle: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SpineColor.forKey(bookId).fill)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = bookTitle.toDisplayTitle(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun HighlightRow(
    highlight: Highlight,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val marker = HighlightColor.at(highlight.colorIndex)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("highlight_card_${highlight.id}")
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // The colour is the point of the mark, so it survives into the list as the card's edge.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(marker.fill)
            )
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlight.chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = highlight.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 21.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_highlight_${highlight.id}")
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove highlight",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    spine: SpineColor,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("bookmark_card_${bookmark.id}")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(spine.fill)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bookmark.chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSavedAt(bookmark.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bookmark.textSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                bookmark.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Destructive, so it stays quiet until reached for rather than shouting in error red.
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_bookmark_${bookmark.id}")
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove bookmark",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatSavedAt(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

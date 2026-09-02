package com.voxleaf.reader.feature.bookmarks

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.R
import com.voxleaf.reader.core.ui.components.ConfirmationDialog
import com.voxleaf.reader.core.ui.components.EmptyState
import com.voxleaf.reader.core.ui.components.toDisplayTitle
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.repository.Bookmark
import com.voxleaf.reader.domain.repository.Highlight
import com.voxleaf.reader.ui.theme.BrandItalic
import com.voxleaf.reader.ui.theme.HighlightColor
import com.voxleaf.reader.ui.theme.SpineColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class BookmarkShelf(
    val bookId: String,
    val bookTitle: String,
    val bookmarks: List<Bookmark>
)

data class HighlightShelf(
    val bookId: String,
    val bookTitle: String,
    val highlights: List<Highlight>
)

fun buildHighlightsMarkdown(shelves: List<HighlightShelf>): String = buildString {
    appendLine("# Vox Reader highlights")
    shelves.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { shelf: HighlightShelf -> shelf.bookTitle.trim() }
            .thenBy { it.bookId }
    ).forEach { shelf ->
        appendLine()
        appendLine("## ${shelf.bookTitle}")
        shelf.highlights
            .sortedWith(compareBy({ it.chapterIndex }, { it.sentenceIndex }, { it.timestamp }, { it.id }))
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

enum class SavedItemsTab(@param:StringRes val labelRes: Int) {
    BOOKMARKS(R.string.bookmarks_tab_bookmarks),
    HIGHLIGHTS(R.string.bookmarks_tab_highlights)
}

enum class SavedItemsSort(@param:StringRes val labelRes: Int) {
    NEWEST(R.string.bookmarks_sort_newest),
    BOOK(R.string.bookmarks_sort_book),
    LOCATION(R.string.bookmarks_sort_location)
}

data class BookmarksUiState(
    val selectedTab: SavedItemsTab = SavedItemsTab.BOOKMARKS,
    val query: String = "",
    val selectedSort: SavedItemsSort = SavedItemsSort.NEWEST,
    val allBookmarkShelves: List<BookmarkShelf> = emptyList(),
    val bookmarkShelves: List<BookmarkShelf> = emptyList(),
    val allHighlightShelves: List<HighlightShelf> = emptyList(),
    val highlightShelves: List<HighlightShelf> = emptyList()
) {
    val visibleBookmarkCount: Int get() = bookmarkShelves.sumOf { it.bookmarks.size }
    val visibleHighlightCount: Int get() = highlightShelves.sumOf { it.highlights.size }
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {
    private val selectedTab = MutableStateFlow(SavedItemsTab.BOOKMARKS)
    private val query = MutableStateFlow("")
    private val selectedSort = MutableStateFlow(SavedItemsSort.NEWEST)

    private val bookmarkShelves = combine(
        bookRepository.getBookmarks(),
        bookRepository.getBooks()
    ) { bookmarks, books ->
        val titleById = books.associate { it.id to it.title }
        bookmarks.groupBy { it.bookId }.map { (bookId, items) ->
            BookmarkShelf(bookId, titleById[bookId].orEmpty(), items)
        }
    }
    private val highlightShelves = combine(
        bookRepository.getHighlights(),
        bookRepository.getBooks()
    ) { highlights, books ->
        val titleById = books.associate { it.id to it.title }
        highlights.groupBy { it.bookId }.map { (bookId, items) ->
            HighlightShelf(bookId, titleById[bookId].orEmpty(), items)
        }
    }

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarkShelves,
        highlightShelves,
        selectedTab,
        query,
        selectedSort
    ) { allBookmarks, allHighlights, tab, currentQuery, sort ->
        BookmarksUiState(
            selectedTab = tab,
            query = currentQuery,
            selectedSort = sort,
            allBookmarkShelves = allBookmarks,
            bookmarkShelves = filterAndSortBookmarks(allBookmarks, currentQuery, sort),
            allHighlightShelves = allHighlights,
            highlightShelves = filterAndSortHighlights(allHighlights, currentQuery, sort)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarksUiState()
    )

    fun selectTab(tab: SavedItemsTab) {
        selectedTab.value = tab
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortSelect(sort: SavedItemsSort) {
        selectedSort.value = sort
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { bookRepository.removeBookmark(id) }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch { bookRepository.removeHighlight(id) }
    }
}

internal fun filterAndSortBookmarks(
    shelves: List<BookmarkShelf>,
    query: String,
    sort: SavedItemsSort
): List<BookmarkShelf> {
    val normalized = query.trim()
    return shelves.mapNotNull { shelf ->
        val matches = shelf.bookmarks.filter { bookmark ->
            normalized.isEmpty() || shelf.bookTitle.contains(normalized, ignoreCase = true) ||
                bookmark.chapterTitle.contains(normalized, ignoreCase = true) ||
                bookmark.textSnippet.contains(normalized, ignoreCase = true) ||
                bookmark.note?.contains(normalized, ignoreCase = true) == true
        }
        if (matches.isEmpty()) null else shelf.copy(bookmarks = sortBookmarks(matches, sort))
    }.sortedWith(bookmarkShelfComparator(sort))
}

internal fun filterAndSortHighlights(
    shelves: List<HighlightShelf>,
    query: String,
    sort: SavedItemsSort
): List<HighlightShelf> {
    val normalized = query.trim()
    return shelves.mapNotNull { shelf ->
        val matches = shelf.highlights.filter { highlight ->
            normalized.isEmpty() || shelf.bookTitle.contains(normalized, ignoreCase = true) ||
                highlight.chapterTitle.contains(normalized, ignoreCase = true) ||
                highlight.text.contains(normalized, ignoreCase = true) ||
                highlight.note?.contains(normalized, ignoreCase = true) == true
        }
        if (matches.isEmpty()) null else shelf.copy(highlights = sortHighlights(matches, sort))
    }.sortedWith(highlightShelfComparator(sort))
}

private fun sortBookmarks(items: List<Bookmark>, sort: SavedItemsSort): List<Bookmark> = when (sort) {
    SavedItemsSort.NEWEST, SavedItemsSort.BOOK -> items.sortedByDescending { it.timestamp }
    SavedItemsSort.LOCATION -> items.sortedWith(compareBy({ it.chapterIndex }, { it.sentenceIndex }))
}

private fun sortHighlights(items: List<Highlight>, sort: SavedItemsSort): List<Highlight> = when (sort) {
    SavedItemsSort.NEWEST, SavedItemsSort.BOOK -> items.sortedByDescending { it.timestamp }
    SavedItemsSort.LOCATION -> items.sortedWith(compareBy({ it.chapterIndex }, { it.sentenceIndex }))
}

private fun bookmarkShelfComparator(sort: SavedItemsSort): Comparator<BookmarkShelf> = when (sort) {
    SavedItemsSort.NEWEST -> compareByDescending { it.bookmarks.maxOfOrNull(Bookmark::timestamp) ?: 0L }
    SavedItemsSort.BOOK, SavedItemsSort.LOCATION ->
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.bookTitle.trim() }
}

private fun highlightShelfComparator(sort: SavedItemsSort): Comparator<HighlightShelf> = when (sort) {
    SavedItemsSort.NEWEST -> compareByDescending { it.highlights.maxOfOrNull(Highlight::timestamp) ?: 0L }
    SavedItemsSort.BOOK, SavedItemsSort.LOCATION ->
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.bookTitle.trim() }
}

@Composable
fun BookmarksScreen(
    onNavigateToReader: (Bookmark) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BookmarksScreenContent(
        uiState = uiState,
        onTabSelect = viewModel::selectTab,
        onQueryChange = viewModel::onQueryChange,
        onSortSelect = viewModel::onSortSelect,
        onNavigateToReader = onNavigateToReader,
        onDeleteBookmark = viewModel::deleteBookmark,
        onDeleteHighlight = viewModel::deleteHighlight
    )
}

@Composable
internal fun BookmarksScreenContent(
    uiState: BookmarksUiState,
    onTabSelect: (SavedItemsTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortSelect: (SavedItemsSort) -> Unit,
    onNavigateToReader: (Bookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit
) {
    var pendingBookmarkDelete by remember { mutableStateOf<Bookmark?>(null) }
    var pendingHighlightDelete by remember { mutableStateOf<Highlight?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            BookmarksHeader(uiState, context)
            SavedItemsTabs(uiState.selectedTab, onTabSelect)
            SavedItemsControls(uiState, onQueryChange, onSortSelect)
            SavedItemsList(
                uiState = uiState,
                onNavigateToReader = onNavigateToReader,
                onDeleteBookmark = { pendingBookmarkDelete = it },
                onDeleteHighlight = { pendingHighlightDelete = it }
            )
        }
    }

    pendingBookmarkDelete?.let { bookmark ->
        ConfirmationDialog(
            title = stringResource(R.string.bookmark_remove_title),
            text = stringResource(R.string.bookmark_remove_message, bookmark.chapterTitle),
            confirmText = stringResource(R.string.saved_item_remove),
            dismissText = stringResource(R.string.saved_item_cancel),
            onConfirm = { onDeleteBookmark(bookmark.id); pendingBookmarkDelete = null },
            onDismiss = { pendingBookmarkDelete = null }
        )
    }
    pendingHighlightDelete?.let { highlight ->
        ConfirmationDialog(
            title = stringResource(R.string.highlight_remove_title),
            text = stringResource(R.string.highlight_remove_message),
            confirmText = stringResource(R.string.saved_item_remove),
            dismissText = stringResource(R.string.saved_item_cancel),
            onConfirm = { onDeleteHighlight(highlight.id); pendingHighlightDelete = null },
            onDismiss = { pendingHighlightDelete = null }
        )
    }
}

@Composable
private fun BookmarksHeader(uiState: BookmarksUiState, context: Context) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.title_bookmarks),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = BrandItalic,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            if (uiState.selectedTab == SavedItemsTab.HIGHLIGHTS && uiState.allHighlightShelves.isNotEmpty()) {
                val title = stringResource(R.string.highlights_export_title)
                val chooser = stringResource(R.string.highlights_export_chooser)
                IconButton(
                    onClick = {
                        shareHighlights(
                            context,
                            buildHighlightsMarkdown(uiState.allHighlightShelves),
                            title,
                            chooser
                        )
                    },
                    modifier = Modifier.testTag("export_highlights_button")
                ) {
                    Icon(
                        Icons.Outlined.IosShare,
                        contentDescription = stringResource(R.string.highlights_export_all)
                    )
                }
            }
        }
        if (uiState.selectedTab == SavedItemsTab.HIGHLIGHTS && uiState.allHighlightShelves.isNotEmpty()) {
            Text(
                text = stringResource(R.string.highlights_export_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SavedItemsTabs(selectedTab: SavedItemsTab, onTabSelect: (SavedItemsTab) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 20.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        SavedItemsTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelect(tab) },
                text = {
                    Text(
                        stringResource(tab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("saved_items_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun SavedItemsControls(
    uiState: BookmarksUiState,
    onQueryChange: (String) -> Unit,
    onSortSelect: (SavedItemsSort) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.bookmarks_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (uiState.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.bookmarks_clear_search))
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Box {
            val sortLabel = stringResource(uiState.selectedSort.labelRes)
            val sortDescription = stringResource(R.string.bookmarks_sort_description, sortLabel)
            TextButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.semantics { contentDescription = sortDescription }
            ) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bookmarks_sort_current, sortLabel), maxLines = 2)
            }
            DropdownMenu(sortExpanded, onDismissRequest = { sortExpanded = false }) {
                SavedItemsSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes)) },
                        onClick = { sortExpanded = false; onSortSelect(sort) }
                    )
                }
            }
        }
        val count = if (uiState.selectedTab == SavedItemsTab.BOOKMARKS) {
            pluralStringResource(
                R.plurals.bookmark_result_count,
                uiState.visibleBookmarkCount,
                uiState.visibleBookmarkCount
            )
        } else {
            pluralStringResource(
                R.plurals.highlight_result_count,
                uiState.visibleHighlightCount,
                uiState.visibleHighlightCount
            )
        }
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedItemsList(
    uiState: BookmarksUiState,
    onNavigateToReader: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit
) {
    val showingBookmarks = uiState.selectedTab == SavedItemsTab.BOOKMARKS
    val sourceEmpty = if (showingBookmarks) {
        uiState.allBookmarkShelves.isEmpty()
    } else {
        uiState.allHighlightShelves.isEmpty()
    }
    val resultsEmpty = if (showingBookmarks) {
        uiState.bookmarkShelves.isEmpty()
    } else {
        uiState.highlightShelves.isEmpty()
    }
    when {
        sourceEmpty -> EmptyState(
            title = stringResource(
                if (showingBookmarks) R.string.bookmarks_empty_title else R.string.highlights_empty_title
            ),
            message = stringResource(
                if (showingBookmarks) R.string.bookmarks_empty_message else R.string.highlights_empty_message
            ),
            icon = if (showingBookmarks) Icons.Outlined.Bookmarks else Icons.Outlined.FormatQuote,
            modifier = Modifier.fillMaxSize()
        )
        resultsEmpty -> EmptyState(
            title = stringResource(
                if (showingBookmarks) {
                    R.string.bookmarks_no_results_title
                } else {
                    R.string.highlights_no_results_title
                }
            ),
            message = stringResource(R.string.saved_items_no_results_message),
            icon = Icons.Outlined.Search,
            modifier = Modifier.fillMaxSize()
        )
        showingBookmarks -> BookmarkList(uiState.bookmarkShelves, onNavigateToReader, onDeleteBookmark)
        else -> HighlightList(uiState.highlightShelves, onNavigateToReader, onDeleteHighlight)
    }
}

@Composable
private fun BookmarkList(
    shelves: List<BookmarkShelf>,
    onNavigateToReader: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        shelves.forEach { shelf ->
            item(key = "bookmark_shelf_${shelf.bookId}") {
                ShelfHeader(shelf.bookId, shelf.bookTitle, shelf.bookmarks.size)
            }
            items(shelf.bookmarks.size, key = { shelf.bookmarks[it].id }) { index ->
                val bookmark = shelf.bookmarks[index]
                BookmarkRow(
                    bookmark,
                    SpineColor.forKey(shelf.bookId),
                    onClick = { onNavigateToReader(bookmark) },
                    onDelete = { onDelete(bookmark) }
                )
            }
            item(key = "bookmark_gap_${shelf.bookId}") { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun HighlightList(
    shelves: List<HighlightShelf>,
    onNavigateToReader: (Bookmark) -> Unit,
    onDelete: (Highlight) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        shelves.forEach { shelf ->
            item(key = "highlight_shelf_${shelf.bookId}") {
                ShelfHeader(shelf.bookId, shelf.bookTitle, shelf.highlights.size)
            }
            items(shelf.highlights.size, key = { shelf.highlights[it].id }) { index ->
                val highlight = shelf.highlights[index]
                HighlightRow(
                    highlight,
                    onClick = {
                        onNavigateToReader(
                            Bookmark(
                                id = highlight.id,
                                bookId = highlight.bookId,
                                chapterIndex = highlight.chapterIndex,
                                sentenceIndex = highlight.sentenceIndex,
                                chapterTitle = highlight.chapterTitle,
                                textSnippet = highlight.text,
                                timestamp = highlight.timestamp
                            )
                        )
                    },
                    onDelete = { onDelete(highlight) }
                )
            }
            item(key = "highlight_gap_${shelf.bookId}") { Spacer(Modifier.height(14.dp)) }
        }
    }
}

private fun shareHighlights(context: Context, markdown: String, title: String, chooser: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(intent, chooser))
}

@Composable
private fun ShelfHeader(bookId: String, bookTitle: String, count: Int) {
    val displayTitle = bookTitle.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.saved_item_removed_document)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        Box(
            modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp))
                .background(SpineColor.forKey(bookId).fill)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            displayTitle.toDisplayTitle(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HighlightRow(highlight: Highlight, onClick: () -> Unit, onDelete: () -> Unit) {
    val marker = HighlightColor.at(highlight.colorIndex)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .testTag("highlight_card_${highlight.id}")
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(marker.fill))
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                SavedItemText(
                    chapterTitle = highlight.chapterTitle,
                    chapterIndex = highlight.chapterIndex,
                    sentenceIndex = highlight.sentenceIndex,
                    timestamp = highlight.timestamp,
                    text = highlight.text,
                    note = highlight.note,
                    maxTextLines = 4,
                    modifier = Modifier.weight(1f)
                )
                RemoveButton(R.string.highlight_remove, "delete_highlight_${highlight.id}", onDelete)
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, spine: SpineColor, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .testTag("bookmark_card_${bookmark.id}")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(spine.fill))
            Spacer(Modifier.width(8.dp))
            SavedItemText(
                chapterTitle = bookmark.chapterTitle,
                chapterIndex = bookmark.chapterIndex,
                sentenceIndex = bookmark.sentenceIndex,
                timestamp = bookmark.timestamp,
                text = bookmark.textSnippet,
                note = bookmark.note,
                maxTextLines = 3,
                modifier = Modifier.weight(1f)
            )
            RemoveButton(R.string.bookmark_remove, "delete_bookmark_${bookmark.id}", onDelete)
        }
    }
}

@Composable
private fun SavedItemText(
    chapterTitle: String,
    chapterIndex: Int,
    sentenceIndex: Int,
    timestamp: Long,
    text: String,
    note: String?,
    maxTextLines: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            chapterTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.saved_item_location, chapterIndex + 1, sentenceIndex + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.saved_item_saved_date, formatSavedAt(timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 21.sp,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis
        )
        note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemoveButton(@StringRes descriptionRes: Int, testTag: String, onDelete: () -> Unit) {
    IconButton(onClick = onDelete, modifier = Modifier.testTag(testTag)) {
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(descriptionRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun formatSavedAt(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

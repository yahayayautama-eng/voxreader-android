package com.voxleaf.reader.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.R
import com.voxleaf.reader.core.ui.components.BookSpine
import com.voxleaf.reader.core.ui.components.EmptyState
import com.voxleaf.reader.core.ui.components.LoadingState
import com.voxleaf.reader.core.ui.components.toDisplayTitle
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.ui.theme.BrandItalic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

enum class SearchSort(@param:StringRes val labelRes: Int) {
    RELEVANCE(R.string.search_sort_relevance),
    TITLE(R.string.search_sort_title),
    AUTHOR(R.string.search_sort_author),
    PROGRESS(R.string.search_sort_progress)
}

enum class SearchMatch(@param:StringRes val labelRes: Int) {
    TITLE(R.string.search_match_title),
    AUTHOR(R.string.search_match_author),
    DESCRIPTION(R.string.search_match_description),
    GENRE(R.string.search_match_genre)
}

data class SearchResultItem(val book: Book, val match: SearchMatch?)

sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Ready(
        val query: String,
        val availableGenres: List<String>,
        val selectedGenre: String?,
        val selectedSort: SearchSort,
        val libraryIsEmpty: Boolean,
        val results: List<SearchResultItem>
    ) : SearchUiState {
        val isPrompt: Boolean get() = !libraryIsEmpty && query.isBlank() && selectedGenre == null
    }
}

private sealed interface BooksLoad {
    data class Success(val books: List<Book>) : BooksLoad
    data class Error(val message: String) : BooksLoad
}

@HiltViewModel
class SearchViewModel @Inject constructor(bookRepository: BookRepository) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedGenre = MutableStateFlow<String?>(null)
    private val selectedSort = MutableStateFlow(SearchSort.RELEVANCE)
    private val booksLoad = bookRepository.getBooks()
        .map<List<Book>, BooksLoad> { BooksLoad.Success(it) }
        .catch { error -> emit(BooksLoad.Error(error.message.orEmpty())) }

    val uiState: StateFlow<SearchUiState> = combine(
        booksLoad,
        searchQuery,
        selectedGenre,
        selectedSort
    ) { load, query, genre, sort ->
        when (load) {
            is BooksLoad.Error -> SearchUiState.Error(load.message)
            is BooksLoad.Success -> buildSearchState(load.books, query, genre, sort)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState.Loading
    )

    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onGenreSelect(genre: String?) {
        selectedGenre.value = genre
    }

    fun onSortSelect(sort: SearchSort) {
        selectedSort.value = sort
    }
}

internal fun buildSearchState(
    books: List<Book>,
    query: String,
    selectedGenre: String?,
    sort: SearchSort
): SearchUiState.Ready {
    val genres = books.map { it.genre.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
    val effectiveGenre = selectedGenre?.takeIf { selected ->
        genres.any { it.equals(selected, ignoreCase = true) }
    }
    val normalizedQuery = query.trim()
    val results = if (normalizedQuery.isEmpty() && effectiveGenre == null) {
        emptyList()
    } else {
        books.asSequence()
            .filter { effectiveGenre == null || it.genre.equals(effectiveGenre, ignoreCase = true) }
            .mapNotNull { book ->
                if (normalizedQuery.isEmpty()) {
                    SearchResultItem(book, null)
                } else {
                    matchBookMetadata(book, normalizedQuery)?.let { SearchResultItem(book, it) }
                }
            }
            .sortedWith(searchComparator(sort, normalizedQuery))
            .toList()
    }
    return SearchUiState.Ready(
        query = query,
        availableGenres = genres,
        selectedGenre = effectiveGenre,
        selectedSort = sort,
        libraryIsEmpty = books.isEmpty(),
        results = results
    )
}

private fun matchBookMetadata(book: Book, query: String): SearchMatch? = when {
    book.title.contains(query, ignoreCase = true) -> SearchMatch.TITLE
    book.author.contains(query, ignoreCase = true) -> SearchMatch.AUTHOR
    book.genre.contains(query, ignoreCase = true) -> SearchMatch.GENRE
    book.description.contains(query, ignoreCase = true) -> SearchMatch.DESCRIPTION
    else -> null
}

private fun searchComparator(sort: SearchSort, query: String): Comparator<SearchResultItem> = when (sort) {
    SearchSort.RELEVANCE -> compareBy<SearchResultItem> { relevanceRank(it.book, query) }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.book.title.trim() }
    SearchSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.book.title.trim() }
    SearchSort.AUTHOR -> compareBy<SearchResultItem> { it.book.author.isBlank() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.book.author.trim() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.book.title.trim() }
    SearchSort.PROGRESS -> compareByDescending<SearchResultItem> { it.book.searchProgress() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.book.title.trim() }
}

private fun relevanceRank(book: Book, query: String): Int = when {
    query.isEmpty() -> 0
    book.title.startsWith(query, ignoreCase = true) -> 0
    book.title.contains(query, ignoreCase = true) -> 1
    book.author.startsWith(query, ignoreCase = true) -> 2
    book.author.contains(query, ignoreCase = true) -> 3
    book.genre.contains(query, ignoreCase = true) -> 4
    else -> 5
}

private fun Book.searchProgress(): Float = if (totalChapters > 0) {
    (currentChapterIndex.toFloat() / totalChapters).coerceIn(0f, 1f)
} else {
    0f
}

@Composable
fun SearchScreen(
    onNavigateToBook: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onGenreSelect = viewModel::onGenreSelect,
        onSortSelect = viewModel::onSortSelect,
        onNavigateToBook = onNavigateToBook
    )
}

@Composable
internal fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onGenreSelect: (String?) -> Unit,
    onSortSelect: (SearchSort) -> Unit,
    onNavigateToBook: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.title_search),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = BrandItalic,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.search_metadata_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            when (uiState) {
                SearchUiState.Loading -> LoadingState(
                    modifier = Modifier.weight(1f),
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.search_loading)
                )
                is SearchUiState.Error -> EmptyState(
                    title = androidx.compose.ui.res.stringResource(R.string.search_error_title),
                    message = uiState.message.ifBlank {
                        androidx.compose.ui.res.stringResource(R.string.search_error_message)
                    },
                    icon = Icons.Outlined.Search,
                    modifier = Modifier.weight(1f)
                )
                is SearchUiState.Ready -> SearchReadyContent(
                    uiState, onQueryChange, onGenreSelect, onSortSelect, onNavigateToBook
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SearchReadyContent(
    uiState: SearchUiState.Ready,
    onQueryChange: (String) -> Unit,
    onGenreSelect: (String?) -> Unit,
    onSortSelect: (SearchSort) -> Unit,
    onNavigateToBook: (String) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = uiState.query,
        onValueChange = onQueryChange,
        placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (uiState.query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, androidx.compose.ui.res.stringResource(R.string.search_clear))
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().testTag("search_screen_input")
    )
    if (uiState.availableGenres.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = uiState.selectedGenre == null,
                    onClick = { onGenreSelect(null) },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.search_all_genres)) }
                )
            }
            items(uiState.availableGenres, key = { it }) { genre ->
                FilterChip(
                    selected = genre.equals(uiState.selectedGenre, ignoreCase = true),
                    onClick = { onGenreSelect(genre) },
                    label = { Text(genre, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
    Box {
        val sortLabel = androidx.compose.ui.res.stringResource(uiState.selectedSort.labelRes)
        val sortDescription = androidx.compose.ui.res.stringResource(
            R.string.search_sort_description,
            sortLabel
        )
        TextButton(
            onClick = { sortExpanded = true },
            modifier = Modifier.semantics { contentDescription = sortDescription }
        ) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.search_sort_current, sortLabel))
        }
        DropdownMenu(sortExpanded, onDismissRequest = { sortExpanded = false }) {
            SearchSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(sort.labelRes)) },
                    onClick = { sortExpanded = false; onSortSelect(sort) }
                )
            }
        }
    }
    when {
        uiState.libraryIsEmpty -> EmptyState(
            title = androidx.compose.ui.res.stringResource(R.string.search_empty_title),
            message = androidx.compose.ui.res.stringResource(R.string.search_empty_message),
            icon = Icons.Outlined.Search,
            modifier = Modifier.weight(1f)
        )
        uiState.isPrompt -> EmptyState(
            title = androidx.compose.ui.res.stringResource(R.string.search_prompt_title),
            message = androidx.compose.ui.res.stringResource(R.string.search_prompt_message),
            icon = Icons.Outlined.Search,
            modifier = Modifier.weight(1f)
        )
        uiState.results.isEmpty() -> EmptyState(
            title = androidx.compose.ui.res.stringResource(R.string.search_no_results_title),
            message = androidx.compose.ui.res.stringResource(R.string.search_no_results_message),
            icon = Icons.Outlined.Search,
            modifier = Modifier.weight(1f)
        )
        else -> {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.search_result_count, uiState.results.size, uiState.results.size
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.results, key = { it.book.id }) { result ->
                    SearchResultRow(result) { onNavigateToBook(result.book.id) }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
private fun SearchResultRow(result: SearchResultItem, onClick: () -> Unit) {
    val book = result.book
    val progress = (book.searchProgress() * 100).roundToInt()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("search_result_${book.id}")
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            BookSpine(
                title = book.title,
                bookId = book.id,
                coverPath = book.coverImagePath,
                showTitle = false,
                modifier = Modifier.size(width = 68.dp, height = 98.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title.toDisplayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        book.genre.takeIf { it.isNotBlank() },
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.search_chapter_count, book.totalChapters, book.totalChapters
                        ),
                        androidx.compose.ui.res.stringResource(R.string.search_progress, progress)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                result.match?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(it.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

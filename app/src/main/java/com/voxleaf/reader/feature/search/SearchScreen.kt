package com.voxleaf.reader.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.core.ui.components.BookSpine
import com.voxleaf.reader.core.ui.components.EmptyState
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<Book>> = _searchQuery
        .flatMapLatest { query ->
            bookRepository.searchBooks(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun toggleFavorite(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(bookId)
        }
    }
}

@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun SearchScreen(
    onNavigateToBook: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val genreSuggestions = listOf("Classic", "Romance", "Gothic", "Mystery", "Fiction")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Find a page",
            fontFamily = com.voxleaf.reader.ui.theme.VoxLeafSerif,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Title, author, or subject") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }
            } else null,
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_screen_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Genre filter suggestions
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(genreSuggestions) { genre ->
                FilterChip(
                    selected = searchQuery == genre,
                    onClick = { viewModel.onQueryChange(if (searchQuery == genre) "" else genre) },
                    label = { Text(genre) },
                    modifier = Modifier.testTag("genre_chip_$genre")
                )
            }
        }

        if (searchResults.isEmpty()) {
            EmptyState(
                message = if (searchQuery.isBlank()) "Search across your private library" else "No documents match '$searchQuery'",
                icon = Icons.Outlined.Search,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(searchResults, key = { it.id }) { book ->
                    BookSpine(
                        title = book.title,
                        bookId = book.id,
                        coverPath = book.coverImagePath,
                        progress = if (book.totalChapters > 0) {
                            (book.currentChapterIndex.toFloat() / book.totalChapters).coerceIn(0f, 1f)
                        } else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clickable { onNavigateToBook(book.id) }
                    )
                }
            }
        }
    }
}

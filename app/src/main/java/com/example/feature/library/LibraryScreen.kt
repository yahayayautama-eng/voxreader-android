package com.example.feature.library

import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.ErrorState
import com.example.core.ui.components.LoadingState
import com.example.domain.repository.Book

@Composable
fun LibraryScreen(
    onNavigateToBook: (String) -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreenContent(
        uiState = uiState,
        onAction = { action ->
            viewModel.handleAction(action)
            if (action is LibraryUiAction.OnBookClick) {
                onNavigateToBook(action.bookId)
            } else if (action is LibraryUiAction.OnAddBookClick) {
                onNavigateToImport()
            }
        }
    )
}

@Composable
fun LibraryScreenContent(
    uiState: LibraryUiState,
    onAction: (LibraryUiAction) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(LibraryUiAction.OnAddBookClick) },
                modifier = Modifier.testTag("add_book_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is LibraryUiState.Loading -> LoadingState()
                is LibraryUiState.Error -> ErrorState(
                    message = uiState.message,
                    onRetry = { onAction(LibraryUiAction.OnRetryClick) }
                )
                is LibraryUiState.Empty -> EmptyState(
                    message = stringResource(id = R.string.title_library) + " is empty",
                    icon = Icons.Default.Book
                )
                is LibraryUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Row with View Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Library", style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = { onAction(LibraryUiAction.OnToggleViewMode) }) {
                                Icon(
                                    imageVector = if (uiState.isGridView) Icons.Default.Menu else Icons.Default.Check,
                                    contentDescription = "Toggle View"
                                )
                            }
                        }

                        // Search bar
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { onAction(LibraryUiAction.OnSearchQueryChange(it)) },
                            placeholder = { Text("Search books or authors...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("library_search_input")
                        )

                        // Category Filter Chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            items(uiState.categories) { category ->
                                val selected = category == uiState.selectedCategory
                                FilterChip(
                                    selected = selected,
                                    onClick = { onAction(LibraryUiAction.OnCategorySelect(category)) },
                                    label = { Text(category) },
                                    modifier = Modifier.testTag("category_chip_$category")
                                )
                            }
                        }

                        if (uiState.allBooks.isEmpty()) {
                            EmptyState(
                                message = "No books found matching criteria",
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            if (uiState.isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (uiState.recentBooks.isNotEmpty()) {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                            Text(
                                                "Recent",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }
                                        items(uiState.recentBooks, key = { "recent_${it.id}" }) { book ->
                                            BookCard(
                                                book = book,
                                                onClick = { onAction(LibraryUiAction.OnBookClick(book.id)) },
                                                onFavoriteToggle = { onAction(LibraryUiAction.OnToggleFavorite(book.id)) }
                                            )
                                        }
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "All Books",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }
                                    }
                                    items(uiState.allBooks, key = { it.id }) { book ->
                                        BookCard(
                                            book = book,
                                            onClick = { onAction(LibraryUiAction.OnBookClick(book.id)) },
                                            onFavoriteToggle = { onAction(LibraryUiAction.OnToggleFavorite(book.id)) }
                                        )
                                    }
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (uiState.recentBooks.isNotEmpty()) {
                                        item {
                                            Text(
                                                "Recent",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }
                                        items(uiState.recentBooks, key = { "recent_${it.id}" }) { book ->
                                            BookCard(
                                                book = book,
                                                onClick = { onAction(LibraryUiAction.OnBookClick(book.id)) },
                                                onFavoriteToggle = { onAction(LibraryUiAction.OnToggleFavorite(book.id)) }
                                            )
                                        }
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "All Books",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }
                                    }
                                    items(uiState.allBooks, key = { it.id }) { book ->
                                        BookCard(
                                            book = book,
                                            onClick = { onAction(LibraryUiAction.OnBookClick(book.id)) },
                                            onFavoriteToggle = { onAction(LibraryUiAction.OnToggleFavorite(book.id)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverColor = try {
        Color(android.graphics.Color.parseColor(book.coverColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Book cover representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(coverColor),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("favorite_button_${book.id}")
                        ) {
                            Icon(
                                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (book.isFavorite) Color.Red else Color.White
                            )
                        }
                    }

                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Progress and Genre Footer
            Column(modifier = Modifier.padding(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = book.genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val progress = if (book.totalChapters > 0) {
                    (book.currentChapterIndex + 1).toFloat() / book.totalChapters
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${book.totalChapters} Chapters • ${(progress * 100).toInt()}% Read",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

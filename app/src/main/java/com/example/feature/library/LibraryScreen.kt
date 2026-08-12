package com.example.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.BookSpine
import com.example.core.ui.components.BookSpineThumb
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.ErrorState
import com.example.core.ui.components.ShelfSkeleton
import com.example.core.ui.components.toDisplayTitle
import com.example.domain.repository.Book
import com.example.ui.theme.PaleGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VoxLeafSerif

/** The FAB plus bottom nav eat this much; the last grid row must clear both. */
private val ScrollBottomClearance = 130.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onNavigateToBook: (String) -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreenContent(
        uiState = uiState,
        sharedScope = sharedScope,
        animatedScope = animatedScope,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreenContent(
    uiState: LibraryUiState,
    onAction: (LibraryUiAction) -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(LibraryUiAction.OnAddBookClick) },
                containerColor = SignalOrange,
                contentColor = MaterialTheme.colorScheme.background,
                icon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null) },
                text = { Text("Import", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_book_fab")
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (uiState) {
                is LibraryUiState.Loading -> ShelfSkeleton(columns = 3)
                is LibraryUiState.Error -> ErrorState(
                    message = uiState.message,
                    onRetry = { onAction(LibraryUiAction.OnRetryClick) }
                )
                is LibraryUiState.Empty -> EmptyState(
                    title = "Build your shelf",
                    message = "Import a PDF, EPUB, DOCX, or a scanned page. Vox Reader reads it aloud on-device — nothing leaves your phone.",
                    actionLabel = "Import a document",
                    onAction = { onAction(LibraryUiAction.OnAddBookClick) },
                    showShelf = true
                )
                is LibraryUiState.Success -> LibraryShelf(uiState, onAction, sharedScope, animatedScope)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryShelf(
    uiState: LibraryUiState.Success,
    onAction: (LibraryUiAction) -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Spines stay a readable size instead of stretching: more shelf across on wider windows.
        val columns = when {
            maxWidth < 600.dp -> 3
            maxWidth < 840.dp -> 5
            else -> 7
        }
        val horizontalPadding = if (maxWidth < 600.dp) 20.dp else 32.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 20.dp,
                bottom = ScrollBottomClearance
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            fullWidth(columns) { LibraryHeader(uiState.searchQuery, onAction) }

            if (uiState.recentBooks.isNotEmpty()) {
                fullWidth(columns) { SectionLabel("Now listening") }
                items(
                    uiState.recentBooks,
                    key = { "recent_${it.id}" },
                    span = { GridItemSpan(columns) }
                ) { book ->
                    NowListeningCard(book) { onAction(LibraryUiAction.OnBookClick(book.id)) }
                }
            }

            fullWidth(columns) {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("The library")
            }
            fullWidth(columns) { CategoryChips(uiState, onAction) }

            if (uiState.allBooks.isEmpty()) {
                fullWidth(columns) {
                    EmptyState(
                        message = if (uiState.searchQuery.isNotBlank()) {
                            "Nothing matches \"${uiState.searchQuery}\". Try a different word, or clear the search."
                        } else {
                            "No documents in ${uiState.selectedCategory}. Pick another filter, or import something new."
                        },
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.height(220.dp)
                    )
                }
            } else {
                items(uiState.allBooks, key = { it.id }) { book ->
                    SpineCell(
                        book = book,
                        onClick = { onAction(LibraryUiAction.OnBookClick(book.id)) },
                        onFavoriteToggle = { onAction(LibraryUiAction.OnToggleFavorite(book.id)) },
                        sharedScope = sharedScope,
                        animatedScope = animatedScope
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(
    columns: Int,
    content: @Composable () -> Unit
) = item(span = { GridItemSpan(columns) }) { content() }

@Composable
private fun LibraryHeader(searchQuery: String, onAction: (LibraryUiAction) -> Unit) {
    Column {
        Text(
            text = "Vox Reader",
            fontFamily = VoxLeafSerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "A private library with an offline voice.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onAction(LibraryUiAction.OnSearchQueryChange(it)) },
            placeholder = { Text("Search your library", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextTertiary) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedBorderColor = SignalOrange
            ),
            modifier = Modifier.fillMaxWidth().testTag("library_search_input")
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.08.em,
        color = TextTertiary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun NowListeningCard(book: Book, onClick: () -> Unit) {
    val progress = book.progressFraction()
    // The grid hands its slot a fixed width, so the cap only takes effect inside a box that absorbs it.
    Box(modifier = Modifier.fillMaxWidth()) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        // A resume card is a fixed-size object, not a banner; stretching it across a tablet strands its content.
        modifier = Modifier.widthIn(max = 560.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookSpineThumb(
                title = book.title,
                bookId = book.id,
                coverPath = book.coverImagePath,
                modifier = Modifier.width(42.dp).height(58.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title.toDisplayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${book.author} · Ch ${book.currentChapterIndex + 1} of ${book.totalChapters}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.audiobookStatus != "READY" && book.audiobookStatus != "NONE") {
                    Text(
                        when (book.audiobookStatus) {
                            "CONVERTING" -> "Creating audiobook · ${book.audiobookProgressPercent}%"
                            "FAILED" -> "Audiobook conversion failed"
                            else -> "Audiobook conversion queued"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.audiobookStatus == "FAILED") MaterialTheme.colorScheme.error else SignalOrange
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = PaleGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = "Resume ${book.title}" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(SignalOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CategoryChips(uiState: LibraryUiState.Success, onAction: (LibraryUiAction) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        items(uiState.categories) { category ->
            val selected = category == uiState.selectedCategory
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .clickable { onAction(LibraryUiAction.OnCategorySelect(category)) }
                    .testTag("category_chip_$category"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) SignalOrange else Color.Transparent)
                        .then(
                            if (selected) Modifier
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpineCell(
    book: Book,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A spine is a physical object on a shelf; pressing it should feel like taking hold of it.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "spinePress"
    )

    Box(modifier = Modifier.testTag("book_card_${book.id}")) {
        BookSpine(
            title = book.title,
            bookId = book.id,
            coverPath = book.coverImagePath,
            progress = book.progressFraction(),
            sharedScope = sharedScope,
            animatedScope = animatedScope,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        )
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("favorite_button_${book.id}")
        ) {
            Icon(
                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (book.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (book.isFavorite) SignalOrange else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun Book.progressFraction(): Float =
    if (totalChapters > 0) (currentChapterIndex.toFloat() / totalChapters).coerceIn(0f, 1f) else 0f

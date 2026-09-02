package com.voxleaf.reader.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxleaf.reader.R
import com.voxleaf.reader.core.ui.LibraryMaxContentWidth
import com.voxleaf.reader.core.ui.libraryColumnCount
import com.voxleaf.reader.core.ui.components.BookSpine
import com.voxleaf.reader.core.ui.components.BookSpineThumb
import com.voxleaf.reader.core.ui.components.EmptyState
import com.voxleaf.reader.core.ui.components.ErrorState
import com.voxleaf.reader.core.ui.components.ShelfSkeleton
import com.voxleaf.reader.core.ui.components.toDisplayTitle
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.ui.theme.Eyebrow
import com.voxleaf.reader.ui.theme.PaleGreen
import com.voxleaf.reader.ui.theme.BrandItalic
import kotlin.math.roundToInt

/** The FAB plus bottom nav eat this much; the last grid row must clear both. */
// Must clear the bottom navigation bar and the Import button stacked above it; at 130dp the
// button sat on top of the last row's title.
private val ScrollBottomClearance = 184.dp

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
    val successState = uiState as? LibraryUiState.Success
    val isSelectionMode = successState?.isSelectionMode == true
    BackHandler(enabled = isSelectionMode) {
        onAction(LibraryUiAction.OnClearSelection)
    }

    Scaffold(
        modifier = Modifier
            .testTag("library_root")
            .onPreviewKeyEvent { event ->
                if (
                    isSelectionMode &&
                    event.key == Key.Escape &&
                    event.type == KeyEventType.KeyDown
                ) {
                    onAction(LibraryUiAction.OnClearSelection)
                    true
                } else {
                    false
                }
            },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (successState?.isSelectionMode == true) {
                SelectionContextBar(successState, onAction)
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { onAction(LibraryUiAction.OnAddBookClick) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null) },
                    text = { Text("Import", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("add_book_fab")
                )
            }
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

    if (successState?.showDeleteConfirmation == true) {
        DeleteSelectionConfirmation(successState, onAction)
    }
    if ((successState?.deleteFailureCount ?: 0) > 0) {
        DeleteFailureDialog(successState!!, onAction)
    }
}

@Composable
private fun SelectionContextBar(
    uiState: LibraryUiState.Success,
    onAction: (LibraryUiAction) -> Unit
) {
    val displayedIds = uiState.allBooks.mapTo(mutableSetOf()) { it.id }
    val allDisplayedSelected = displayedIds.isNotEmpty() &&
        displayedIds.all(uiState.selectedBookIds::contains)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("library_selection_bar")
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LibraryMaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onAction(LibraryUiAction.OnClearSelection) },
                    enabled = !uiState.isDeleting
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.library_close))
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.library_selection_count,
                        uiState.selectionCount,
                        uiState.selectionCount
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("library_selection_count")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onAction(LibraryUiAction.OnSelectAll(displayedIds)) },
                    enabled = displayedIds.isNotEmpty() && !uiState.isDeleting
                ) {
                    Text(
                        stringResource(
                            if (allDisplayedSelected) {
                                R.string.library_deselect_all
                            } else {
                                R.string.library_select_all
                            }
                        )
                    )
                }
                TextButton(
                    onClick = { onAction(LibraryUiAction.OnRequestDelete) },
                    enabled = uiState.selectionCount > 0 && !uiState.isDeleting,
                    modifier = Modifier.testTag("library_delete_selected")
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.library_delete))
                }
            }
        }
    }
}

@Composable
private fun DeleteSelectionConfirmation(
    uiState: LibraryUiState.Success,
    onAction: (LibraryUiAction) -> Unit
) {
    val count = uiState.selectionCount
    AlertDialog(
        onDismissRequest = { onAction(LibraryUiAction.OnCancelDelete) },
        title = {
            Text(pluralStringResource(R.plurals.library_delete_title, count, count))
        },
        text = {
            Text(pluralStringResource(R.plurals.library_delete_message, count, count))
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(LibraryUiAction.OnConfirmDelete) },
                enabled = count > 0 && !uiState.isDeleting,
                modifier = Modifier.testTag("library_confirm_delete")
            ) {
                Text(stringResource(R.string.library_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(LibraryUiAction.OnCancelDelete) }) {
                Text(stringResource(R.string.library_cancel))
            }
        }
    )
}

@Composable
private fun DeleteFailureDialog(
    uiState: LibraryUiState.Success,
    onAction: (LibraryUiAction) -> Unit
) {
    val count = uiState.deleteFailureCount
    AlertDialog(
        onDismissRequest = { onAction(LibraryUiAction.OnDismissDeleteFailure) },
        title = { Text(stringResource(R.string.library_delete_failure_title)) },
        text = {
            Text(pluralStringResource(R.plurals.library_delete_failure_message, count, count))
        },
        confirmButton = {
            TextButton(onClick = { onAction(LibraryUiAction.OnDismissDeleteFailure) }) {
                Text(stringResource(R.string.library_close))
            }
        }
    )
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
        val contentWidth = minOf(maxWidth, LibraryMaxContentWidth)
        val columns = libraryColumnCount(contentWidth)
        val horizontalPadding = if (contentWidth < 600.dp) 20.dp else 32.dp

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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = LibraryMaxContentWidth)
                .fillMaxSize()
                .testTag("library_grid_$columns")
        ) {
            if (!uiState.isSelectionMode) {
                fullWidth(columns) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LibraryHeader(uiState.searchQuery, onAction)
                    }
                }
            }

            if (!uiState.isSelectionMode && uiState.recentBooks.isNotEmpty()) {
                fullWidth(columns) { SectionLabel("Now listening") }
                items(
                    uiState.recentBooks,
                    key = { "recent_${it.id}" },
                    span = { GridItemSpan(columns) }
                ) { book ->
                    NowListeningCard(book) { onAction(LibraryUiAction.OnBookClick(book.id)) }
                }
            }

            fullWidth(columns) { Spacer(modifier = Modifier.height(4.dp)) }
            if (!uiState.isSelectionMode) {
                fullWidth(columns) { LibraryControls(uiState, onAction) }
            }

            if (uiState.allBooks.isEmpty()) {
                fullWidth(columns) {
                    EmptyState(
                        message = if (uiState.searchQuery.isNotBlank()) {
                            "Nothing matches \"${uiState.searchQuery}\". Try a different word, or clear the search."
                        } else {
                            stringResource(R.string.library_no_filter_results)
                        },
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.height(220.dp)
                    )
                }
            } else {
                items(uiState.allBooks, key = { it.id }) { book ->
                    SpineCell(
                        book = book,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = book.id in uiState.selectedBookIds,
                        onClick = {
                            onAction(
                                if (uiState.isSelectionMode) {
                                    LibraryUiAction.OnToggleBookSelection(book.id)
                                } else {
                                    LibraryUiAction.OnBookClick(book.id)
                                }
                            )
                        },
                        onLongClick = { onAction(LibraryUiAction.OnSelectBook(book.id)) },
                        onSelect = {
                            onAction(
                                if (book.id in uiState.selectedBookIds) {
                                    LibraryUiAction.OnToggleBookSelection(book.id)
                                } else {
                                    LibraryUiAction.OnSelectBook(book.id)
                                }
                            )
                        },
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
    // Arranged like a masthead rather than a title stacked on a sentence: the wordmark carries the
    // page, a hairline closes it, and the strapline sits under the rule as small tracked caps so it
    // reads as a subtitle instead of competing with the first book on screen.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Vox Reader",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = BrandItalic,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onAction(LibraryUiAction.OnSearchQueryChange(it)) },
            placeholder = { Text(stringResource(R.string.library_search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary
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
        style = Eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            // A resume card is a fixed-size object, not a banner; stretching it across a tablet strands its content.
            modifier = Modifier.widthIn(max = 560.dp).clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookSpineThumb(
                    title = book.title,
                    bookId = book.id,
                    coverPath = book.coverImagePath,
                    modifier = Modifier.width(44.dp).height(62.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title.toDisplayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        book.readingMetadata(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = "Resume ${book.title}" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryControls(uiState: LibraryUiState.Success, onAction: (LibraryUiAction) -> Unit) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val selectedSortLabel = stringResource(uiState.selectedSort.labelRes)
    val sortDescription = stringResource(R.string.library_sort_description, selectedSortLabel)
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Box {
            TextButton(
                onClick = { sortMenuExpanded = true },
                modifier = Modifier
                    .testTag("library_sort_button")
                    .semantics {
                        contentDescription = sortDescription
                    }
            ) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.library_sort))
                Spacer(modifier = Modifier.width(4.dp))
                Text(selectedSortLabel, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                LibrarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes)) },
                        onClick = {
                            sortMenuExpanded = false
                            onAction(LibraryUiAction.OnSortSelect(sort))
                        }
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.filters, key = { it.name }) { filter ->
                LibraryFilterChip(
                    label = stringResource(filter.labelRes),
                    selected = filter == uiState.selectedFilter && uiState.selectedMetadataCategory == null,
                    testTag = "library_filter_${filter.name.lowercase()}",
                    onClick = { onAction(LibraryUiAction.OnFilterSelect(filter)) }
                )
            }
            items(uiState.metadataCategories, key = { "genre_$it" }) { category ->
                LibraryFilterChip(
                    label = category,
                    selected = category == uiState.selectedMetadataCategory,
                    testTag = "library_filter_genre_${category.lowercase()}",
                    onClick = { onAction(LibraryUiAction.OnMetadataCategorySelect(category)) }
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.background(Color.Transparent)
                        .border(1.dp, Color(0xFF334155), CircleShape)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SpineCell(
    book: Book,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = book.libraryProgressFraction()
    val progressPercent = (progress * 100).roundToInt()
    val stateLabel = stringResource(
        if (isSelected) R.string.library_selected else R.string.library_not_selected
    )
    val moreOptionsLabel = stringResource(
        R.string.library_more_options,
        book.title.toDisplayTitle()
    )
    // A spine is a physical object on a shelf; pressing it should feel like taking hold of it.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "spinePress"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .testTag("book_card_${book.id}")
                .semantics(mergeDescendants = true) {
                    contentDescription = book.accessibilityDescription()
                    role = Role.Button
                    if (isSelectionMode) {
                        selected = isSelected
                        stateDescription = stateLabel
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                }
        ) {
            BookSpine(
                title = book.title,
                bookId = book.id,
                coverPath = book.coverImagePath,
                progress = progress,
                sharedScope = sharedScope,
                animatedScope = animatedScope,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.title.toDisplayTitle(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.library_progress_percent, progressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .testTag("book_overflow_${book.id}")
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = moreOptionsLabel,
                tint = Color.White.copy(alpha = 0.9f)
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isSelected) R.string.library_deselect else R.string.library_select
                        )
                    )
                },
                onClick = {
                    menuExpanded = false
                    onSelect()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (book.isFavorite) {
                                R.string.library_remove_favorite
                            } else {
                                R.string.library_add_favorite
                            }
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (book.isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = null
                    )
                },
                onClick = {
                    menuExpanded = false
                    onFavoriteToggle()
                }
            )
        }
    }
}

private fun Book.progressFraction(): Float =
    libraryProgressFraction()

private fun Book.readingMetadata(): String =
    listOfNotNull(
        author.takeIf { it.isNotBlank() },
        "Ch ${currentChapterIndex + 1} of $totalChapters"
    ).joinToString(" · ")

private fun Book.accessibilityDescription(): String =
    listOfNotNull(
        title.toDisplayTitle(),
        author.takeIf { it.isNotBlank() },
        "${(progressFraction() * 100).roundToInt()} percent complete"
    ).joinToString(", ")

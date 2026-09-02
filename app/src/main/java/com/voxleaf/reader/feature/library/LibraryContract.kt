package com.voxleaf.reader.feature.library

import androidx.annotation.StringRes
import com.voxleaf.reader.R
import com.voxleaf.reader.domain.repository.Book

enum class LibrarySort(@param:StringRes val labelRes: Int) {
    RECENT(R.string.library_sort_recent),
    TITLE(R.string.library_sort_title),
    AUTHOR(R.string.library_sort_author),
    PROGRESS(R.string.library_sort_progress)
}

enum class LibraryFilter(@param:StringRes val labelRes: Int) {
    ALL(R.string.library_filter_all),
    IN_PROGRESS(R.string.library_filter_in_progress),
    UNREAD(R.string.library_filter_unread),
    FAVORITES(R.string.library_filter_favorites)
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val recentBooks: List<Book> = emptyList(),
        val allBooks: List<Book> = emptyList(),
        val isGridView: Boolean = true,
        val filters: List<LibraryFilter> = LibraryFilter.entries,
        val metadataCategories: List<String> = emptyList(),
        val selectedFilter: LibraryFilter = LibraryFilter.ALL,
        val selectedMetadataCategory: String? = null,
        val selectedSort: LibrarySort = LibrarySort.RECENT,
        val selectedBookIds: Set<String> = emptySet(),
        val showDeleteConfirmation: Boolean = false,
        val deleteFailureCount: Int = 0,
        val isDeleting: Boolean = false,
        val searchQuery: String = ""
    ) : LibraryUiState {
        val isSelectionMode: Boolean get() = selectedBookIds.isNotEmpty()
        val selectionCount: Int get() = selectedBookIds.size
    }
    data class Error(val message: String) : LibraryUiState
    data object Empty : LibraryUiState
}

sealed interface LibraryUiAction {
    data class OnBookClick(val bookId: String) : LibraryUiAction
    data class OnSelectBook(val bookId: String) : LibraryUiAction
    data class OnToggleBookSelection(val bookId: String) : LibraryUiAction
    data class OnSelectAll(val bookIds: Set<String>) : LibraryUiAction
    data object OnClearSelection : LibraryUiAction
    data object OnRequestDelete : LibraryUiAction
    data object OnCancelDelete : LibraryUiAction
    data object OnConfirmDelete : LibraryUiAction
    data object OnDismissDeleteFailure : LibraryUiAction
    data object OnAddBookClick : LibraryUiAction
    data object OnRetryClick : LibraryUiAction
    data class OnFilterSelect(val filter: LibraryFilter) : LibraryUiAction
    data class OnMetadataCategorySelect(val category: String) : LibraryUiAction
    data class OnSortSelect(val sort: LibrarySort) : LibraryUiAction
    data class OnSearchQueryChange(val query: String) : LibraryUiAction
    data class OnToggleFavorite(val bookId: String) : LibraryUiAction
    data object OnToggleViewMode : LibraryUiAction
}

internal sealed interface LibrarySelectionAction {
    data class Select(val bookId: String) : LibrarySelectionAction
    data class Toggle(val bookId: String) : LibrarySelectionAction
    data class ToggleAll(val bookIds: Set<String>) : LibrarySelectionAction
    data object Clear : LibrarySelectionAction
}

internal fun reduceLibrarySelection(
    selectedBookIds: Set<String>,
    action: LibrarySelectionAction
): Set<String> = when (action) {
    is LibrarySelectionAction.Select -> selectedBookIds + action.bookId
    is LibrarySelectionAction.Toggle -> if (action.bookId in selectedBookIds) {
        selectedBookIds - action.bookId
    } else {
        selectedBookIds + action.bookId
    }
    is LibrarySelectionAction.ToggleAll -> if (
        action.bookIds.isNotEmpty() && action.bookIds.all(selectedBookIds::contains)
    ) {
        selectedBookIds - action.bookIds
    } else {
        selectedBookIds + action.bookIds
    }
    LibrarySelectionAction.Clear -> emptySet()
}

internal fun Book.libraryProgressFraction(): Float =
    if (totalChapters > 0) {
        (currentChapterIndex.toFloat() / totalChapters).coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun Book.isUnreadInLibrary(): Boolean = currentChapterIndex == 0 && currentPosition == 0

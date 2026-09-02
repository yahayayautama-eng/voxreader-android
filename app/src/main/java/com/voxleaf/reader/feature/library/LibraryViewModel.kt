package com.voxleaf.reader.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    private val _selectedMetadataCategory = MutableStateFlow<String?>(null)
    private val _selectedSort = MutableStateFlow(LibrarySort.RECENT)
    private val _searchQuery = MutableStateFlow("")
    private val _isGridView = MutableStateFlow(false)
    private val selectedBookIds = savedStateHandle.getStateFlow(
        SELECTED_BOOK_IDS_KEY,
        arrayListOf<String>()
    )
    private val showDeleteConfirmation = savedStateHandle.getStateFlow(
        SHOW_DELETE_CONFIRMATION_KEY,
        false
    )
    private val _deleteFailureCount = MutableStateFlow(0)
    private val _isDeleting = MutableStateFlow(false)
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            val controls = combine(
                _selectedFilter,
                _selectedMetadataCategory,
                _selectedSort,
                _searchQuery,
                _isGridView
            ) { filter, category, sort, query, isGridView ->
                LibraryControls(filter, category, sort, query, isGridView)
            }
            val selection = combine(
                selectedBookIds,
                showDeleteConfirmation,
                _deleteFailureCount,
                _isDeleting
            ) { selectedIds, showConfirmation, failureCount, isDeleting ->
                LibrarySelection(
                    selectedIds.toSet(),
                    showConfirmation,
                    failureCount,
                    isDeleting
                )
            }

            val books = bookRepository.getBooks().onEach { currentBooks ->
                reconcileSelection(currentBooks.mapTo(mutableSetOf()) { it.id })
            }

            combine(
                books,
                controls,
                selection
            ) { books, controlsState, selectionState ->
                var filtered = books.asSequence()
                val metadataCategories = books
                    .map { it.genre.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }

                filtered = when (controlsState.filter) {
                    LibraryFilter.ALL -> filtered
                    LibraryFilter.IN_PROGRESS -> filtered.filter { !it.isUnreadInLibrary() }
                    LibraryFilter.UNREAD -> filtered.filter(Book::isUnreadInLibrary)
                    LibraryFilter.FAVORITES -> filtered.filter { it.isFavorite }
                }

                controlsState.metadataCategory?.let { category ->
                    filtered = filtered.filter { it.genre.equals(category, ignoreCase = true) }
                }

                if (controlsState.query.isNotBlank()) {
                    filtered = filtered.filter {
                        it.title.contains(controlsState.query, ignoreCase = true) ||
                            it.author.contains(controlsState.query, ignoreCase = true)
                    }
                }

                val filteredBooks = filtered.toList()
                val recent = filteredBooks
                    .filter { it.currentChapterIndex > 0 || it.currentPosition > 0 }
                    .sortedWith(recentBookComparator)
                    .take(2)
                val all = filteredBooks.sortedWith(comparatorFor(controlsState.sort))

                if (books.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(
                        recentBooks = recent,
                        allBooks = all,
                        isGridView = controlsState.isGridView,
                        metadataCategories = metadataCategories,
                        selectedFilter = controlsState.filter,
                        selectedMetadataCategory = controlsState.metadataCategory,
                        selectedSort = controlsState.sort,
                        selectedBookIds = selectionState.selectedBookIds,
                        showDeleteConfirmation = selectionState.showDeleteConfirmation,
                        deleteFailureCount = selectionState.deleteFailureCount,
                        isDeleting = selectionState.isDeleting,
                        searchQuery = controlsState.query
                    )
                }
            }
                .catch { e ->
                    _uiState.value = LibraryUiState.Error(e.message ?: "Failed to load books")
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun handleAction(action: LibraryUiAction) {
        when (action) {
            is LibraryUiAction.OnFilterSelect -> {
                _selectedFilter.value = action.filter
                _selectedMetadataCategory.value = null
            }
            is LibraryUiAction.OnMetadataCategorySelect -> {
                _selectedFilter.value = LibraryFilter.ALL
                _selectedMetadataCategory.value = action.category
            }
            is LibraryUiAction.OnSortSelect -> {
                _selectedSort.value = action.sort
            }
            is LibraryUiAction.OnSearchQueryChange -> {
                _searchQuery.value = action.query
            }
            is LibraryUiAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    bookRepository.toggleFavorite(action.bookId)
                }
            }
            LibraryUiAction.OnAddBookClick -> {
                // Navigation is owned by the screen host.
            }
            LibraryUiAction.OnRetryClick -> {
                loadBooks()
            }
            is LibraryUiAction.OnBookClick -> {
                // Handled in UI navigation
            }
            is LibraryUiAction.OnSelectBook -> updateSelection(
                LibrarySelectionAction.Select(action.bookId)
            )
            is LibraryUiAction.OnToggleBookSelection -> updateSelection(
                LibrarySelectionAction.Toggle(action.bookId)
            )
            is LibraryUiAction.OnSelectAll -> updateSelection(
                LibrarySelectionAction.ToggleAll(action.bookIds)
            )
            LibraryUiAction.OnClearSelection -> clearSelection()
            LibraryUiAction.OnRequestDelete -> {
                if (selectedBookIds.value.isNotEmpty() && !_isDeleting.value) {
                    savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = true
                }
            }
            LibraryUiAction.OnCancelDelete -> {
                savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = false
            }
            LibraryUiAction.OnConfirmDelete -> deleteSelectedBooks()
            LibraryUiAction.OnDismissDeleteFailure -> _deleteFailureCount.value = 0
            LibraryUiAction.OnToggleViewMode -> {
                _isGridView.value = !_isGridView.value
            }
        }
    }

    private fun updateSelection(action: LibrarySelectionAction) {
        if (_isDeleting.value) return
        val updated = reduceLibrarySelection(selectedBookIds.value.toSet(), action)
        savedStateHandle[SELECTED_BOOK_IDS_KEY] = ArrayList(updated.sorted())
        if (updated.isEmpty()) {
            savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = false
        }
    }

    private fun clearSelection() {
        if (_isDeleting.value) return
        savedStateHandle[SELECTED_BOOK_IDS_KEY] = arrayListOf<String>()
        savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = false
        _deleteFailureCount.value = 0
    }

    private fun deleteSelectedBooks() {
        if (_isDeleting.value) return
        val deletionIds = selectedBookIds.value.distinct().sorted()
        if (deletionIds.isEmpty()) return

        _isDeleting.value = true
        savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = false
        viewModelScope.launch {
            var failureCount = 0
            var remainingIds = selectedBookIds.value.toSet()
            deletionIds.forEach { bookId ->
                try {
                    bookRepository.removeBook(bookId)
                    remainingIds = remainingIds - bookId
                    savedStateHandle[SELECTED_BOOK_IDS_KEY] = ArrayList(remainingIds.sorted())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    failureCount += 1
                }
            }

            _deleteFailureCount.value = failureCount
            _isDeleting.value = false
        }
    }

    private fun reconcileSelection(currentBookIds: Set<String>) {
        val restoredIds = selectedBookIds.value.toSet()
        val validIds = restoredIds.intersect(currentBookIds)
        if (validIds != restoredIds) {
            savedStateHandle[SELECTED_BOOK_IDS_KEY] = ArrayList(validIds.sorted())
            if (validIds.isEmpty()) {
                savedStateHandle[SHOW_DELETE_CONFIRMATION_KEY] = false
            }
        }
    }

    private fun comparatorFor(sort: LibrarySort): Comparator<Book> = when (sort) {
        LibrarySort.RECENT -> recentBookComparator
        LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        LibrarySort.AUTHOR -> compareBy<Book> { it.author.isBlank() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.author.trim() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        LibrarySort.PROGRESS -> compareByDescending<Book> { it.currentChapterIndex }
            .thenByDescending { it.currentPosition }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
    }

    private data class LibraryControls(
        val filter: LibraryFilter,
        val metadataCategory: String?,
        val sort: LibrarySort,
        val query: String,
        val isGridView: Boolean
    )

    private data class LibrarySelection(
        val selectedBookIds: Set<String>,
        val showDeleteConfirmation: Boolean,
        val deleteFailureCount: Int,
        val isDeleting: Boolean
    )

    private companion object {
        const val SELECTED_BOOK_IDS_KEY = "library_selected_book_ids"
        const val SHOW_DELETE_CONFIRMATION_KEY = "library_show_delete_confirmation"

        val recentBookComparator: Comparator<Book> =
            compareByDescending<Book> { it.lastProgressUpdatedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
    }
}

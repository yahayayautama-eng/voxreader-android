package com.example.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")
    private val _isGridView = MutableStateFlow(false)
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            combine(
                bookRepository.getBooks(),
                _selectedCategory,
                _searchQuery,
                _isGridView
            ) { books, category, query, isGridView ->
                var filtered = books
                val categories = listOf("All", "Favorites") + books
                    .map { it.genre.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }

                if (category == "Favorites") {
                    filtered = filtered.filter { it.isFavorite }
                } else if (category != "All") {
                    filtered = filtered.filter { it.genre.contains(category, ignoreCase = true) }
                }

                if (query.isNotBlank()) {
                    filtered = filtered.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.author.contains(query, ignoreCase = true)
                    }
                }
                
                val recent = filtered
                    .filter { it.currentChapterIndex > 0 || it.currentPosition > 0 }
                    .sortedWith(
                        compareByDescending<com.example.domain.repository.Book> { it.currentChapterIndex }
                            .thenByDescending { it.currentPosition }
                    )
                    .take(2)
                val all = filtered

                if (books.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(
                        recentBooks = recent,
                        allBooks = all,
                        isGridView = isGridView,
                        categories = categories,
                        selectedCategory = category,
                        searchQuery = query
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
            is LibraryUiAction.OnCategorySelect -> {
                _selectedCategory.value = action.category
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
            LibraryUiAction.OnToggleViewMode -> {
                _isGridView.value = !_isGridView.value
            }
        }
    }

}

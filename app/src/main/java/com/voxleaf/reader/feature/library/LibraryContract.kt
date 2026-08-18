package com.voxleaf.reader.feature.library

import com.voxleaf.reader.domain.repository.Book

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val recentBooks: List<Book> = emptyList(),
        val allBooks: List<Book> = emptyList(),
        val isGridView: Boolean = true,
        val categories: List<String> = listOf("All", "Favorites"),
        val selectedCategory: String = "All",
        val searchQuery: String = ""
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
    data object Empty : LibraryUiState
}

sealed interface LibraryUiAction {
    data class OnBookClick(val bookId: String) : LibraryUiAction
    data object OnAddBookClick : LibraryUiAction
    data object OnRetryClick : LibraryUiAction
    data class OnCategorySelect(val category: String) : LibraryUiAction
    data class OnSearchQueryChange(val query: String) : LibraryUiAction
    data class OnToggleFavorite(val bookId: String) : LibraryUiAction
    data object OnToggleViewMode : LibraryUiAction
}

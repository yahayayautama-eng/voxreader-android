package com.example.feature.bookdetails

import com.example.domain.repository.Book

sealed interface BookDetailsUiState {
    data object Loading : BookDetailsUiState
    data class Success(val book: Book) : BookDetailsUiState
    data class Error(val message: String) : BookDetailsUiState
}

sealed interface BookDetailsUiAction {
    data object OnToggleFavorite : BookDetailsUiAction
    data class OnChapterClick(val chapterIndex: Int) : BookDetailsUiAction
}

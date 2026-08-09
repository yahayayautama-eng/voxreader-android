package com.example.feature.bookdetails

import com.example.domain.repository.Book

sealed interface BookDetailsUiState {
    data object Loading : BookDetailsUiState
    data class Success(
        val book: Book,
        val isEditingMetadata: Boolean = false,
        val isDeleted: Boolean = false,
        val errorMessage: String? = null,
        val isRedetectingChapters: Boolean = false,
        val redetectMessage: String? = null
    ) : BookDetailsUiState
    data class Error(val message: String) : BookDetailsUiState
}

sealed interface BookDetailsUiAction {
    data object OnToggleFavorite : BookDetailsUiAction
    data class OnChapterClick(val chapterIndex: Int) : BookDetailsUiAction
    data object OnEditMetadata : BookDetailsUiAction
    data object OnCancelMetadataEdit : BookDetailsUiAction
    data class OnSaveMetadata(
        val title: String,
        val author: String,
        val description: String,
        val genre: String
    ) : BookDetailsUiAction
    data object OnDeleteBook : BookDetailsUiAction
    data object OnRedetectChapters : BookDetailsUiAction
    data object OnDismissRedetectMessage : BookDetailsUiAction
}

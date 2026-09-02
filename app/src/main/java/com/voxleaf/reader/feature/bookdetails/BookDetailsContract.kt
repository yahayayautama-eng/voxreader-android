package com.voxleaf.reader.feature.bookdetails

import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.usecase.StructureSectionDraft

sealed interface BookDetailsUiState {
    data object Loading : BookDetailsUiState
    data class Success(
        val book: Book,
        val isEditingMetadata: Boolean = false,
        val isDeleted: Boolean = false,
        val errorMessage: String? = null,
        val isRedetectingChapters: Boolean = false,
        val redetectMessage: String? = null,
        val structureDrafts: List<StructureSectionDraft>? = null,
        val structureDiffSummary: String? = null,
        val isApplyingStructure: Boolean = false
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
    data class OnRenameStructureSection(val index: Int, val title: String) : BookDetailsUiAction
    data class OnToggleIgnoreStructureSection(val index: Int) : BookDetailsUiAction
    data class OnMergeStructureSection(val index: Int) : BookDetailsUiAction
    data class OnSplitStructureSection(val index: Int) : BookDetailsUiAction
    data class OnMoveStructureSection(val from: Int, val to: Int) : BookDetailsUiAction
    data object OnApplyStructure : BookDetailsUiAction
    data object OnCancelStructureReview : BookDetailsUiAction
}

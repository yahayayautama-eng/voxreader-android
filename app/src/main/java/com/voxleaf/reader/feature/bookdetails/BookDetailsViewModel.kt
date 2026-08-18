package com.voxleaf.reader.feature.bookdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.usecase.RedetectChaptersUseCase
import com.voxleaf.reader.domain.usecase.RedetectResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val redetectChaptersUseCase: RedetectChaptersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookDetailsUiState>(BookDetailsUiState.Loading)
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private var currentBookId: String? = null

    fun loadBook(bookId: String) {
        currentBookId = bookId
        _uiState.value = BookDetailsUiState.Loading
        viewModelScope.launch { refreshBook(bookId) }
    }

    /** Suspends until the reload lands in [_uiState], unlike [loadBook] which fires and forgets. */
    private suspend fun refreshBook(bookId: String) {
        val book = bookRepository.getBookById(bookId)
        _uiState.value = if (book != null) {
            BookDetailsUiState.Success(book)
        } else {
            BookDetailsUiState.Error("Book not found")
        }
    }

    fun handleAction(action: BookDetailsUiAction) {
        when (action) {
            BookDetailsUiAction.OnToggleFavorite -> {
                val bookId = currentBookId ?: return
                viewModelScope.launch {
                    bookRepository.toggleFavorite(bookId)
                    refreshBook(bookId)
                }
            }
            is BookDetailsUiAction.OnChapterClick -> {
                val bookId = currentBookId ?: return
                viewModelScope.launch {
                    bookRepository.updateBookProgress(bookId, action.chapterIndex, 0)
                }
            }
            BookDetailsUiAction.OnEditMetadata -> {
                _uiState.value = (_uiState.value as? BookDetailsUiState.Success)
                    ?.copy(isEditingMetadata = true)
                    ?: _uiState.value
            }
            BookDetailsUiAction.OnCancelMetadataEdit -> {
                _uiState.value = (_uiState.value as? BookDetailsUiState.Success)
                    ?.copy(isEditingMetadata = false, errorMessage = null)
                    ?: _uiState.value
            }
            is BookDetailsUiAction.OnSaveMetadata -> {
                val bookId = currentBookId ?: return
                val title = action.title.trim()
                if (title.isBlank()) {
                    _uiState.value = (_uiState.value as? BookDetailsUiState.Success)
                        ?.copy(errorMessage = "Title is required.")
                        ?: _uiState.value
                    return
                }
                viewModelScope.launch {
                    bookRepository.updateBookMetadata(
                        bookId = bookId,
                        title = title,
                        author = action.author.trim().ifBlank { "Unknown Author" },
                        description = action.description.trim(),
                        genre = action.genre.trim().ifBlank { "General" }
                    )
                    refreshBook(bookId)
                }
            }
            BookDetailsUiAction.OnDeleteBook -> {
                val bookId = currentBookId ?: return
                viewModelScope.launch {
                    bookRepository.removeBook(bookId)
                    val state = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                    _uiState.value = state.copy(isDeleted = true)
                }
            }
            BookDetailsUiAction.OnRedetectChapters -> {
                val bookId = currentBookId ?: return
                val state = _uiState.value as? BookDetailsUiState.Success ?: return
                _uiState.value = state.copy(isRedetectingChapters = true, redetectMessage = null)
                viewModelScope.launch {
                    when (val result = redetectChaptersUseCase(bookId)) {
                        is RedetectResult.Success -> {
                            refreshBook(bookId)
                            val refreshed = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            _uiState.value = refreshed.copy(redetectMessage = "Chapters updated.")
                        }
                        is RedetectResult.Error -> {
                            val current = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            _uiState.value = current.copy(
                                isRedetectingChapters = false,
                                redetectMessage = result.message
                            )
                        }
                    }
                }
            }
            BookDetailsUiAction.OnDismissRedetectMessage -> {
                val state = _uiState.value as? BookDetailsUiState.Success ?: return
                _uiState.value = state.copy(redetectMessage = null)
            }
        }
    }
}

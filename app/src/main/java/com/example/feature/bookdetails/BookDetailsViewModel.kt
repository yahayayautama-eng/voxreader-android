package com.example.feature.bookdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookDetailsUiState>(BookDetailsUiState.Loading)
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private var currentBookId: String? = null

    fun loadBook(bookId: String) {
        currentBookId = bookId
        viewModelScope.launch {
            _uiState.value = BookDetailsUiState.Loading
            val book = bookRepository.getBookById(bookId)
            if (book != null) {
                _uiState.value = BookDetailsUiState.Success(book)
            } else {
                _uiState.value = BookDetailsUiState.Error("Book not found")
            }
        }
    }

    fun handleAction(action: BookDetailsUiAction) {
        when (action) {
            BookDetailsUiAction.OnToggleFavorite -> {
                val bookId = currentBookId ?: return
                viewModelScope.launch {
                    bookRepository.toggleFavorite(bookId)
                    loadBook(bookId)
                }
            }
            is BookDetailsUiAction.OnChapterClick -> {
                val bookId = currentBookId ?: return
                viewModelScope.launch {
                    bookRepository.updateBookProgress(bookId, action.chapterIndex, 0)
                }
            }
        }
    }
}

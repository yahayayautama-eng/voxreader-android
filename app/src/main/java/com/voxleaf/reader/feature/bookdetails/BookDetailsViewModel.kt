package com.voxleaf.reader.feature.bookdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.usecase.RedetectChaptersUseCase
import com.voxleaf.reader.domain.usecase.RedetectResult
import com.voxleaf.reader.domain.usecase.SectionStructureUseCase
import com.voxleaf.reader.domain.usecase.StructureResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val redetectChaptersUseCase: RedetectChaptersUseCase,
    private val sectionStructureUseCase: SectionStructureUseCase
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
                    when (val result = sectionStructureUseCase.analyze(bookId)) {
                        is StructureResult.Preview -> {
                            val current = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            val diff = result.value
                            _uiState.value = current.copy(
                                isRedetectingChapters = false,
                                structureDrafts = diff.proposed,
                                structureDiffSummary = "${diff.addedCount} added · ${diff.removedCount} removed · ${diff.changedCount} changed"
                            )
                        }
                        is StructureResult.Error -> {
                            val current = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            _uiState.value = current.copy(
                                isRedetectingChapters = false,
                                redetectMessage = result.message
                            )
                        }
                        StructureResult.Applied -> Unit
                    }
                }
            }
            BookDetailsUiAction.OnDismissRedetectMessage -> {
                val state = _uiState.value as? BookDetailsUiState.Success ?: return
                _uiState.value = state.copy(redetectMessage = null)
            }
            is BookDetailsUiAction.OnRenameStructureSection -> updateStructureDrafts { drafts ->
                drafts.mapIndexed { index, draft ->
                    if (index == action.index) draft.copy(title = action.title, isManuallyEdited = true) else draft
                }
            }
            is BookDetailsUiAction.OnToggleIgnoreStructureSection -> updateStructureDrafts { drafts ->
                drafts.mapIndexed { index, draft ->
                    if (index == action.index) draft.copy(isIgnored = !draft.isIgnored, isManuallyEdited = true) else draft
                }
            }
            is BookDetailsUiAction.OnMergeStructureSection -> updateStructureDrafts { drafts ->
                if (action.index !in 1 until drafts.size) drafts else drafts.mapIndexed { index, draft ->
                    if (index == action.index) draft.copy(isIgnored = true, isManuallyEdited = true) else draft
                }
            }
            is BookDetailsUiAction.OnSplitStructureSection -> updateStructureDrafts { drafts ->
                val original = drafts.getOrNull(action.index) ?: return@updateStructureDrafts drafts
                val sentences = original.content.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                if (sentences.size < 2) {
                    val state = _uiState.value as? BookDetailsUiState.Success
                    if (state != null) _uiState.value = state.copy(redetectMessage = "This section is too short to split safely.")
                    drafts
                } else {
                    val midpoint = (sentences.size / 2).coerceAtLeast(1)
                    val first = original.copy(
                        content = sentences.take(midpoint).joinToString(" "),
                        isManuallyEdited = true
                    )
                    val second = original.copy(
                        id = UUID.randomUUID().toString(),
                        title = "${original.title} — continued",
                        content = sentences.drop(midpoint).joinToString(" "),
                        isManuallyEdited = true
                    )
                    drafts.toMutableList().apply {
                        this[action.index] = first
                        add(action.index + 1, second)
                    }
                }
            }
            is BookDetailsUiAction.OnMoveStructureSection -> updateStructureDrafts { drafts ->
                if (action.from !in drafts.indices || action.to !in drafts.indices) drafts
                else drafts.toMutableList().apply { add(action.to, removeAt(action.from)) }
                    .map { it.copy(isManuallyEdited = true) }
            }
            BookDetailsUiAction.OnApplyStructure -> {
                val bookId = currentBookId ?: return
                val state = _uiState.value as? BookDetailsUiState.Success ?: return
                val drafts = state.structureDrafts ?: return
                _uiState.value = state.copy(isApplyingStructure = true, redetectMessage = null)
                viewModelScope.launch {
                    when (val result = sectionStructureUseCase.apply(bookId, drafts)) {
                        StructureResult.Applied -> {
                            refreshBook(bookId)
                            val refreshed = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            _uiState.value = refreshed.copy(redetectMessage = "Structure updated. Saved reading places were remapped.")
                        }
                        is StructureResult.Error -> {
                            val current = _uiState.value as? BookDetailsUiState.Success ?: return@launch
                            _uiState.value = current.copy(isApplyingStructure = false, redetectMessage = result.message)
                        }
                        is StructureResult.Preview -> Unit
                    }
                }
            }
            BookDetailsUiAction.OnCancelStructureReview -> {
                val state = _uiState.value as? BookDetailsUiState.Success ?: return
                _uiState.value = state.copy(structureDrafts = null, structureDiffSummary = null, redetectMessage = null)
            }
        }
    }

    private inline fun updateStructureDrafts(
        transform: (List<com.voxleaf.reader.domain.usecase.StructureSectionDraft>) -> List<com.voxleaf.reader.domain.usecase.StructureSectionDraft>
    ) {
        val state = _uiState.value as? BookDetailsUiState.Success ?: return
        val drafts = state.structureDrafts ?: return
        _uiState.value = state.copy(structureDrafts = transform(drafts))
    }
}

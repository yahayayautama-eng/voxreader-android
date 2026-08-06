package com.example.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.datastore.AppSettingsManager
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.model.tts.TextChunk
import com.example.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val playbackController: PlaybackController,
    private val appSettingsManager: AppSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var sentences: List<String> = emptyList()

    init {
        observeTtsState()
        
        viewModelScope.launch {
            appSettingsManager.ttsRateFlow.collect { rate ->
                _uiState.update { state -> state.copy(ttsRate = rate) }
            }
        }
        viewModelScope.launch {
            appSettingsManager.ttsVoiceFlow.collect { voice ->
                _uiState.update { state -> state.copy(ttsVoice = voice) }
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            playbackController.playbackState.collect { playbackState ->
                _uiState.update {
                    it.copy(
                        isTtsPlaying = playbackState.isPlaying,
                        isTtsPaused = !playbackState.isPlaying,
                        currentSentenceIndex = playbackState.currentChunkIndex,
                        ttsRate = playbackState.speed
                    )
                }
            }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val book = bookRepository.getBookById(bookId)
            if (book != null) {
                val chapterIndex = book.currentChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                val chapter = book.chapters.getOrNull(chapterIndex)
                sentences = parseSentences(chapter?.content ?: "")
                val position = book.currentPosition.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        book = book,
                        currentChapterIndex = chapterIndex,
                        currentChapter = chapter,
                        currentSentenceIndex = position,
                        textChunks = sentences.mapIndexed { index, text ->
                            TextChunk(id = "$bookId:$chapterIndex:$index", text = text)
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Book not found") }
            }
        }
    }

    private fun parseSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    }

    fun handleAction(action: ReaderUiAction) {
        when (action) {
            ReaderUiAction.OnPlayPauseTts -> {
                val state = _uiState.value
                if (state.isTtsPlaying) {
                    playbackController.pause()
                } else {
                    val book = state.book ?: return
                    playbackController.play(book.id, state.currentChapterIndex, state.currentSentenceIndex, state.ttsRate, state.ttsVoice)
                }
            }
            ReaderUiAction.OnStopTts -> {
                playbackController.stop()
            }
            is ReaderUiAction.OnChangeChapter -> {
                val book = _uiState.value.book ?: return
                val newIndex = action.newIndex.coerceIn(0, book.chapters.size - 1)
                playbackController.stop()
                val newChapter = book.chapters.getOrNull(newIndex)
                sentences = parseSentences(newChapter?.content ?: "")
                _uiState.update {
                    it.copy(
                        currentChapterIndex = newIndex,
                        currentChapter = newChapter,
                        currentSentenceIndex = 0,
                        textChunks = sentences.mapIndexed { index, text ->
                            TextChunk(id = "${book.id}:$newIndex:$index", text = text)
                        },
                        aiSummary = null
                    )
                }
                saveProgress(book.id, newIndex, 0)
            }
            is ReaderUiAction.OnChangeTheme -> {
                _uiState.update { it.copy(readerTheme = action.theme) }
            }
            is ReaderUiAction.OnChangeFontSize -> {
                val newSize = (_uiState.value.fontSizeSp + action.deltaSp).coerceIn(12, 32)
                _uiState.update { it.copy(fontSizeSp = newSize) }
            }
            is ReaderUiAction.OnChangeTtsRate -> {
                playbackController.setSpeed(action.rate)
                viewModelScope.launch {
                    appSettingsManager.setTtsRate(action.rate)
                }
            }
            is ReaderUiAction.OnAddBookmark -> {
                val state = _uiState.value
                val book = state.book ?: return
                val chapter = state.currentChapter ?: return
                val currentText = sentences.getOrNull(state.currentSentenceIndex)
                    ?: chapter.content.take(120)

                viewModelScope.launch {
                    val bookmark = Bookmark(
                        id = "bm_${System.currentTimeMillis()}",
                        bookId = book.id,
                        chapterIndex = state.currentChapterIndex,
                        chapterTitle = chapter.title,
                        textSnippet = currentText,
                        note = action.note.ifBlank { null }
                    )
                    bookRepository.addBookmark(bookmark)
                    _uiState.update { it.copy(bookmarkAddedMessage = "Bookmark saved!") }
                    delay(2000)
                    _uiState.update { it.copy(bookmarkAddedMessage = null) }
                }
            }
            ReaderUiAction.OnGenerateAiSummary -> {
                val chapter = _uiState.value.currentChapter ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isGeneratingAiSummary = true) }
                    delay(1200) // Simulate processing / AI response
                    val summary = "• Key Focus: ${chapter.title}\n" +
                            "• Main Theme: Characters navigate key events and dialogue.\n" +
                            "• AI Insight: This section highlights core motifs and sets up central conflicts in ${chapter.title}."
                    _uiState.update {
                        it.copy(
                            isGeneratingAiSummary = false,
                            aiSummary = summary
                        )
                    }
                }
            }
            ReaderUiAction.OnDismissAiSummary -> {
                _uiState.update { it.copy(aiSummary = null) }
            }
            ReaderUiAction.OnTogglePlayerLayout -> {
                _uiState.update { it.copy(isPlayerExpanded = !it.isPlayerExpanded) }
            }
            ReaderUiAction.OnPreviousSentence -> {
                val newIndex = (_uiState.value.currentSentenceIndex - 1).coerceAtLeast(0)
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate, state.ttsVoice)
                }
            }
            ReaderUiAction.OnNextSentence -> {
                val newIndex = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxOf(0, sentences.size - 1))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate, state.ttsVoice)
                }
            }
            ReaderUiAction.OnSkipBack -> {
                val newIndex = (_uiState.value.currentSentenceIndex - 3).coerceAtLeast(0)
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate, state.ttsVoice)
                }
            }
            ReaderUiAction.OnSkipForward -> {
                val newIndex = (_uiState.value.currentSentenceIndex + 3).coerceAtMost(maxOf(0, sentences.size - 1))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate, state.ttsVoice)
                }
            }
            ReaderUiAction.OnVoiceSettings -> {
            }
            is ReaderUiAction.OnSleepTimer -> {
                _uiState.update { it.copy(sleepTimerMinutes = action.minutes) }
            }
        }
    }

    private fun saveProgress(bookId: String, chapterIndex: Int, sentenceIndex: Int) {
        viewModelScope.launch {
            bookRepository.updateBookProgress(bookId, chapterIndex, sentenceIndex)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

package com.example.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.datastore.AppSettingsManager
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.repository.Highlight
import com.example.domain.model.tts.TextChunk
import com.example.tts.EngineId
import com.example.tts.ListeningTracker
import com.example.tts.NowPlaying
import com.example.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val ttsManager: TtsManager,
    private val appSettingsManager: AppSettingsManager,
    private val listeningTracker: ListeningTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var sentences: List<String> = emptyList()
    private var highlightsJob: Job? = null
    private var allHighlights: List<Highlight> = emptyList()
    private var lastPlaybackCompletionId = ttsManager.state.value.playbackCompletionId

    init {
        observeTtsState()

        // Combined rather than three independent collectors: switching engines re-fetches that
        // engine's voice list asynchronously, and a voice id from settings is only meaningful once
        // that fetch lands — see TtsManager.setEngineAndVoice.
        viewModelScope.launch {
            combine(
                appSettingsManager.ttsEngineFlow,
                appSettingsManager.ttsVoiceFlow,
                appSettingsManager.ttsRateFlow
            ) { engine, voice, rate -> Triple(EngineId.fromStorageKey(engine), voice, rate) }
                .collect { (engine, voice, rate) ->
                    ttsManager.setSpeechRate(rate)
                    ttsManager.setEngineAndVoice(engine, voice)
                    _uiState.update { it.copy(ttsRate = rate, ttsVoice = voice) }
                }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.state.collect { ttsState ->
                _uiState.update {
                    it.copy(
                        isTtsPlaying = ttsState.isSpeaking,
                        isTtsPaused = ttsState.isPaused,
                        isTtsPreparing = ttsState.isPreparing,
                        currentSentenceIndex = ttsState.currentSentenceIndex,
                        ttsRate = ttsState.speechRate,
                        ttsEngineId = ttsState.engineId,
                        ttsErrorMessage = ttsState.errorMessage,
                        sleepTimerMinutes = ttsState.sleepTimerMinutes
                    )
                }
                if (ttsState.playbackCompletionId != lastPlaybackCompletionId) {
                    lastPlaybackCompletionId = ttsState.playbackCompletionId
                    continueWithNextChapter(ttsState.nowPlaying)
                }
            }
        }
    }

    private fun continueWithNextChapter(completed: NowPlaying?) {
        val state = _uiState.value
        val book = state.book ?: return
        if (completed?.bookId != book.id || completed.chapterIndex != state.currentChapterIndex) return

        val nextIndex = (state.currentChapterIndex + 1..book.chapters.lastIndex)
            .firstOrNull { parseSentences(book.chapters[it].content).isNotEmpty() }
            ?: return
        val chapter = book.chapters[nextIndex]
        sentences = parseSentences(chapter.content)
        _uiState.update {
            it.copy(
                currentChapterIndex = nextIndex,
                currentChapter = chapter,
                currentSentenceIndex = 0,
                textChunks = sentences.mapIndexed { index, text ->
                    TextChunk(id = "${book.id}:$nextIndex:$index", text = text)
                }
            )
        }
        publishChapterHighlights()
        saveProgress(book.id, nextIndex, 0)
        ttsManager.setNowPlaying(NowPlaying(book.id, book.title, nextIndex, chapter.title))
        ttsManager.speakSentences(sentences, 0)
    }

    /**
     * Highlights arrive for the whole book once and are re-sliced per chapter, so flipping chapters
     * doesn't re-query the database on every tap.
     */
    private fun observeHighlights(bookId: String) {
        highlightsJob?.cancel()
        highlightsJob = viewModelScope.launch {
            bookRepository.getHighlightsForBook(bookId).collect { highlights ->
                allHighlights = highlights
                publishChapterHighlights()
            }
        }
    }

    private fun publishChapterHighlights() {
        val chapterIndex = _uiState.value.currentChapterIndex
        _uiState.update { state ->
            state.copy(
                chapterHighlights = allHighlights
                    .filter { it.chapterIndex == chapterIndex }
                    .associateBy { it.sentenceIndex }
            )
        }
    }

    fun loadBook(bookId: String, bookmarkChapterIndex: Int? = null, bookmarkSentenceIndex: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val book = bookRepository.getBookById(bookId)
            if (book != null) {
                listeningTracker.setCurrentBook(bookId)
                observeHighlights(bookId)
                val chapterIndex = (bookmarkChapterIndex ?: book.currentChapterIndex)
                    .coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                val chapter = book.chapters.getOrNull(chapterIndex)
                sentences = parseSentences(chapter?.content ?: "")
                val position = (bookmarkSentenceIndex ?: book.currentPosition)
                    .coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
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
        return text
            .replace(Regex("[\\u0000-\\u001F&&[^\\n\\r\\t]]"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .flatMap(::splitLongSentence)
            .filter { it.isNotBlank() }
    }

    private fun splitLongSentence(sentence: String): List<String> {
        val normalized = sentence.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= MAX_TTS_CHARS) return listOf(normalized)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        normalized.split(" ").forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > MAX_TTS_CHARS) {
                chunks += current.toString()
                current.clear()
            }
            if (word.length > MAX_TTS_CHARS) {
                word.chunked(MAX_TTS_CHARS).forEach { oversizedPart ->
                    if (current.isNotEmpty()) {
                        chunks += current.toString()
                        current.clear()
                    }
                    chunks += oversizedPart
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    fun handleAction(action: ReaderUiAction) {
        when (action) {
            ReaderUiAction.OnPlayPauseTts -> {
                val state = _uiState.value
                if (state.isTtsPlaying) {
                    ttsManager.pause()
                } else if (state.isTtsPaused) {
                    ttsManager.resume()
                } else {
                    val book = state.book
                    val chapter = state.currentChapter
                    if (book != null && chapter != null) {
                        // Snapshot for the global mini player; speakSentences itself never touches it,
                        // so this stays put across pause/resume/skip until stop() clears it.
                        ttsManager.setNowPlaying(
                            NowPlaying(book.id, book.title, state.currentChapterIndex, chapter.title)
                        )
                    }
                    ttsManager.speakSentences(sentences, state.currentSentenceIndex)
                }
            }
            ReaderUiAction.OnStopTts -> {
                ttsManager.stop()
            }
            is ReaderUiAction.OnChangeChapter -> {
                val book = _uiState.value.book ?: return
                val newIndex = action.newIndex.coerceIn(0, book.chapters.size - 1)
                ttsManager.stop()
                val newChapter = book.chapters.getOrNull(newIndex)
                sentences = parseSentences(newChapter?.content ?: "")
                _uiState.update {
                    it.copy(
                        currentChapterIndex = newIndex,
                        currentChapter = newChapter,
                        currentSentenceIndex = 0,
                        textChunks = sentences.mapIndexed { index, text ->
                            TextChunk(id = "${book.id}:$newIndex:$index", text = text)
                        }
                    )
                }
                publishChapterHighlights()
                saveProgress(book.id, newIndex, 0)
            }
            is ReaderUiAction.OnChangeTheme -> {
                _uiState.update { it.copy(readerTheme = action.theme) }
            }
            is ReaderUiAction.OnChangeFontSize -> {
                val newSize = (_uiState.value.fontSizeSp + action.deltaSp).coerceIn(12, 32)
                _uiState.update { it.copy(fontSizeSp = newSize) }
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
                        sentenceIndex = state.currentSentenceIndex,
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
            is ReaderUiAction.OnStartMarking -> {
                val index = action.sentenceIndex.takeIf { it in sentences.indices } ?: return
                _uiState.update { it.copy(markingSentenceIndex = index) }
            }
            ReaderUiAction.OnDismissMarking -> {
                _uiState.update { it.copy(markingSentenceIndex = null) }
            }
            is ReaderUiAction.OnSaveHighlight -> {
                val state = _uiState.value
                val book = state.book ?: return
                val chapter = state.currentChapter ?: return
                val sentenceIndex = state.markingSentenceIndex ?: return
                val text = sentences.getOrNull(sentenceIndex) ?: return
                val existing = state.chapterHighlights[sentenceIndex]

                viewModelScope.launch {
                    bookRepository.addHighlight(
                        Highlight(
                            // Reusing the existing id makes recoloring or annotating an update, not a duplicate.
                            id = existing?.id ?: "hl_${System.currentTimeMillis()}",
                            bookId = book.id,
                            chapterIndex = state.currentChapterIndex,
                            sentenceIndex = sentenceIndex,
                            chapterTitle = chapter.title,
                            text = text,
                            colorIndex = action.colorIndex,
                            note = action.note.ifBlank { null }
                        )
                    )
                    _uiState.update {
                        it.copy(markingSentenceIndex = null, bookmarkAddedMessage = "Passage highlighted")
                    }
                    delay(2000)
                    _uiState.update { it.copy(bookmarkAddedMessage = null) }
                }
            }
            is ReaderUiAction.OnRemoveHighlight -> {
                val state = _uiState.value
                val book = state.book ?: return
                viewModelScope.launch {
                    bookRepository.removeHighlightAt(book.id, state.currentChapterIndex, action.sentenceIndex)
                    _uiState.update { it.copy(markingSentenceIndex = null) }
                }
            }
            ReaderUiAction.OnPreviousSentence -> {
                val newIndex = (_uiState.value.currentSentenceIndex - 1).coerceAtLeast(0)
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    ttsManager.speakSentences(sentences, newIndex)
                }
            }
            ReaderUiAction.OnNextSentence -> {
                val newIndex = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxOf(0, sentences.size - 1))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    ttsManager.speakSentences(sentences, newIndex)
                }
            }
            is ReaderUiAction.OnSeekToSentence -> {
                val newIndex = action.sentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    ttsManager.speakSentences(sentences, newIndex)
                }
            }
            ReaderUiAction.OnSkipBack -> {
                seekBySeconds(-SKIP_SECONDS, sentences)
            }
            ReaderUiAction.OnSkipForward -> {
                seekBySeconds(SKIP_SECONDS, sentences)
            }
            is ReaderUiAction.OnSleepTimer -> {
                ttsManager.setSleepTimer(action.minutes)
            }
        }
    }

    /**
     * Skips by listening time rather than by a fixed sentence count. Sentences run anywhere from three
     * to sixty words, so "back 3 sentences" moved an unpredictable distance; walking the word counts
     * at the current speech rate lands close to the ±15s a listener expects.
     */
    private fun seekBySeconds(deltaSeconds: Int, sentences: List<String>) {
        if (sentences.isEmpty()) return
        val state = _uiState.value
        val wordsPerSecond = (WORDS_PER_MINUTE * state.ttsRate) / 60f
        var budget = kotlin.math.abs(deltaSeconds) * wordsPerSecond
        val step = if (deltaSeconds < 0) -1 else 1
        var index = state.currentSentenceIndex

        while (budget > 0) {
            val next = index + step
            if (next < 0 || next > sentences.lastIndex) break
            index = next
            budget -= sentences[index].split(Regex("\\s+")).count { it.isNotBlank() }
        }

        val newIndex = index.coerceIn(0, sentences.lastIndex)
        _uiState.update { it.copy(currentSentenceIndex = newIndex) }
        state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
        if (state.isTtsPlaying && state.book != null) {
            ttsManager.speakSentences(sentences, newIndex)
        }
    }

    private fun saveProgress(bookId: String, chapterIndex: Int, sentenceIndex: Int) {
        viewModelScope.launch {
            bookRepository.updateBookProgress(bookId, chapterIndex, sentenceIndex)
        }
    }

    private companion object {
        const val MAX_TTS_CHARS = 240
        // Matches the Replay10 / Forward10 icons in the player; the control must not claim a jump it doesn't make.
        const val SKIP_SECONDS = 10
        // Conversational narration pace; the rate multiplier scales it.
        const val WORDS_PER_MINUTE = 155f
    }
}

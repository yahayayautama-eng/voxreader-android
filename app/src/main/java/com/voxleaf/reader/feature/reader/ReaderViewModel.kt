package com.voxleaf.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.repository.Bookmark
import com.voxleaf.reader.domain.repository.Highlight
import com.voxleaf.reader.domain.model.tts.TextChunk
import com.voxleaf.reader.tts.EngineId
import com.voxleaf.reader.tts.ListeningTracker
import com.voxleaf.reader.tts.NowPlaying
import com.voxleaf.reader.tts.TtsChapter
import com.voxleaf.reader.tts.TtsManager
import com.voxleaf.reader.tts.TtsTextParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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

    init {
        observeTtsState()

        viewModelScope.launch {
            combine(
                appSettingsManager.readerThemeFlow,
                appSettingsManager.readerFontFamilyFlow,
                appSettingsManager.readerFontSizeFlow,
                appSettingsManager.readerLineSpacingFlow
            ) { theme, family, size, spacing ->
                val parsedTheme = try { ReaderTheme.valueOf(theme) } catch (_: Exception) { ReaderTheme.NIGHT }
                val parsedFamily = try { ReaderFontFamily.valueOf(family) } catch (_: Exception) { ReaderFontFamily.SERIF }
                _uiState.update {
                    it.copy(
                        readerTheme = parsedTheme,
                        readerFontFamily = parsedFamily,
                        fontSizeSp = size,
                        lineSpacingMultiplier = spacing
                    )
                }
            }.collect { }
        }

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
                // TtsManager is a singleton shared across every open book, so its transport
                // flags belong to whatever book is actually playing — not necessarily this
                // screen's book. Mirroring them unconditionally made a fresh book's play button
                // control whatever was already playing instead of starting itself.
                val ownsPlayback = ttsState.nowPlaying?.bookId == _uiState.value.book?.id
                _uiState.update {
                    it.copy(
                        isTtsPlaying = ownsPlayback && ttsState.isSpeaking,
                        isTtsPaused = ownsPlayback && ttsState.isPaused,
                        isTtsPreparing = ownsPlayback && ttsState.isPreparing,
                        currentSentenceIndex = if (ownsPlayback) ttsState.currentSentenceIndex else it.currentSentenceIndex,
                        ttsRate = ttsState.speechRate,
                        ttsEngineId = ttsState.engineId,
                        ttsVoice = ttsState.selectedVoicePath,
                        availableVoices = ttsState.availableVoices,
                        ttsErrorMessage = if (ownsPlayback) ttsState.errorMessage else it.ttsErrorMessage,
                        sleepTimerMinutes = ttsState.sleepTimerMinutes
                    )
                }
                if (ownsPlayback) {
                    followPlaybackChapter(ttsState.nowPlaying)
                    // Hands-free listening never calls the manual seek/skip paths that used to be
                    // the only place progress got saved, so an autonomous sentence advance (the
                    // common case) was never persisted — resuming after a kill landed wherever the
                    // listener last tapped, not where they last heard.
                    val book = _uiState.value.book
                    if (book != null) {
                        saveProgress(book.id, ttsState.nowPlaying?.chapterIndex ?: _uiState.value.currentChapterIndex, ttsState.currentSentenceIndex)
                    }
                }
            }
        }
    }

    private fun followPlaybackChapter(playing: NowPlaying?) {
        val state = _uiState.value
        val book = state.book ?: return
        if (playing?.bookId != book.id || playing.chapterIndex == state.currentChapterIndex) return
        val chapter = book.chapters.getOrNull(playing.chapterIndex) ?: return
        sentences = TtsTextParser.sentences(chapter.content)
        _uiState.update {
            it.copy(
                currentChapterIndex = playing.chapterIndex,
                currentChapter = chapter,
                currentSentenceIndex = 0,
                textChunks = sentences.mapIndexed { index, text ->
                    TextChunk(id = "${book.id}:${playing.chapterIndex}:$index", text = text)
                }
            )
        }
        publishChapterHighlights()
        saveProgress(book.id, playing.chapterIndex, 0)
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
                sentences = TtsTextParser.sentences(chapter?.content ?: "")
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
                followPlaybackChapter(ttsManager.state.value.nowPlaying)
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Book not found") }
            }
        }
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
                    state.book?.let { book ->
                        playBook(book, state.currentChapterIndex, state.currentSentenceIndex)
                    }
                }
            }
            ReaderUiAction.OnStopTts -> {
                ttsManager.stop()
            }
            is ReaderUiAction.OnChangeChapter -> {
                val book = _uiState.value.book ?: return
                val newIndex = action.newIndex.coerceIn(0, book.chapters.size - 1)
                val wasPlaying = _uiState.value.isTtsPlaying || _uiState.value.isTtsPreparing
                val newChapter = book.chapters.getOrNull(newIndex)
                sentences = TtsTextParser.sentences(newChapter?.content ?: "")
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
                if (wasPlaying) {
                    playBook(book, newIndex, 0)
                } else {
                    ttsManager.stop()
                }
            }
            is ReaderUiAction.OnChangeTheme -> {
                _uiState.update { it.copy(readerTheme = action.theme) }
                viewModelScope.launch { appSettingsManager.setReaderTheme(action.theme.name) }
            }
            is ReaderUiAction.OnChangeFontFamily -> {
                _uiState.update { it.copy(readerFontFamily = action.fontFamily) }
                viewModelScope.launch { appSettingsManager.setReaderFontFamily(action.fontFamily.name) }
            }
            is ReaderUiAction.OnChangeLineSpacing -> {
                _uiState.update { it.copy(lineSpacingMultiplier = action.multiplier) }
                viewModelScope.launch { appSettingsManager.setReaderLineSpacing(action.multiplier) }
            }
            is ReaderUiAction.OnChangeFontSize -> {
                val newSize = (_uiState.value.fontSizeSp + action.deltaSp).coerceIn(12, 36)
                _uiState.update { it.copy(fontSizeSp = newSize) }
                viewModelScope.launch { appSettingsManager.setReaderFontSize(newSize) }
            }
            is ReaderUiAction.OnSetTtsSpeed -> {
                ttsManager.setSpeechRate(action.rate)
                viewModelScope.launch { appSettingsManager.setTtsRate(action.rate) }
            }
            is ReaderUiAction.OnSetTtsVoice -> {
                ttsManager.setVoice(action.voiceId)
                viewModelScope.launch { appSettingsManager.setTtsVoice(action.voiceId) }
            }
            is ReaderUiAction.OnSetTtsEngine -> {
                ttsManager.setEngine(action.engineId)
                viewModelScope.launch { appSettingsManager.setTtsEngine(action.engineId.storageKey) }
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
                    ttsManager.seekToSentence(newIndex)
                }
            }
            ReaderUiAction.OnNextSentence -> {
                val newIndex = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxOf(0, sentences.size - 1))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    ttsManager.seekToSentence(newIndex)
                }
            }
            is ReaderUiAction.OnSeekToSentence -> {
                val newIndex = action.sentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
                _uiState.update { it.copy(currentSentenceIndex = newIndex) }
                val state = _uiState.value
                state.book?.let { saveProgress(it.id, state.currentChapterIndex, newIndex) }
                if (state.isTtsPlaying && state.book != null) {
                    ttsManager.seekToSentence(newIndex)
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
     * Synthesis outruns speech, so a chapter is streamed sentence by sentence with the buffer
     * running ahead of the voice; there is nothing to render first and nothing to wait for.
     */
    private fun playBook(book: com.voxleaf.reader.domain.repository.Book, chapterIndex: Int, sentenceIndex: Int) {
        ttsManager.speakChapters(
            chapters = book.chapters.mapIndexed { index, chapter ->
                TtsChapter(
                    nowPlaying = NowPlaying(book.id, book.title, index, chapter.title, book.coverImagePath),
                    text = chapter.content
                )
            },
            startChapterIndex = chapterIndex,
            startSentenceIndex = sentenceIndex
        )
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
            ttsManager.seekToSentence(newIndex)
        }
    }

    private fun saveProgress(bookId: String, chapterIndex: Int, sentenceIndex: Int) {
        viewModelScope.launch {
            bookRepository.updateBookProgress(bookId, chapterIndex, sentenceIndex)
        }
    }

    private companion object {
        // Matches the Replay10 / Forward10 icons in the player; the control must not claim a jump it doesn't make.
        const val SKIP_SECONDS = 10
        // Conversational narration pace; the rate multiplier scales it.
        const val WORDS_PER_MINUTE = 155f
    }
}

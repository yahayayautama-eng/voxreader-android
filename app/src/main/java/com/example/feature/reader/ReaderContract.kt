package com.example.feature.reader

import com.example.domain.repository.Book
import com.example.domain.repository.Chapter
import com.example.domain.repository.Highlight
import com.example.tts.EngineId

enum class ReaderTheme {
    LIGHT, DARK, SEPIA, NIGHT
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val currentChapterIndex: Int = 0,
    val currentChapter: Chapter? = null,
    val readerTheme: ReaderTheme = ReaderTheme.NIGHT,
    val fontSizeSp: Int = 18,
    val isTtsPlaying: Boolean = false,
    val isTtsPaused: Boolean = false,
    val isTtsPreparing: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val ttsRate: Float = 1.0f,
    val ttsVoice: String = "default",
    val ttsEngineId: EngineId = EngineId.OFFLINE,
    val ttsPitch: Float = 1.0f,
    val isPlayerExpanded: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val textChunks: List<com.example.domain.model.tts.TextChunk> = emptyList(),
    val bookmarkAddedMessage: String? = null,
    /** Highlights in the open chapter, keyed by sentence index for O(1) lookup while painting text. */
    val chapterHighlights: Map<Int, Highlight> = emptyMap(),
    /** Sentence the highlight sheet is open for; null when no passage is being marked. */
    val markingSentenceIndex: Int? = null,
    /** Book failed to load entirely — blanks the reader in favor of a full-screen retry. */
    val errorMessage: String? = null,
    /** A single sentence failed to synthesize during playback — shown inline in the player, book stays visible. */
    val ttsErrorMessage: String? = null
)

sealed interface ReaderUiAction {
    data object OnPlayPauseTts : ReaderUiAction
    data object OnStopTts : ReaderUiAction
    data object OnPreviousSentence : ReaderUiAction
    data object OnNextSentence : ReaderUiAction
    data class OnSeekToSentence(val sentenceIndex: Int) : ReaderUiAction
    data object OnSkipBack : ReaderUiAction
    data object OnSkipForward : ReaderUiAction
    data class OnChangeChapter(val newIndex: Int) : ReaderUiAction
    data class OnChangeTheme(val theme: ReaderTheme) : ReaderUiAction
    data class OnChangeFontSize(val deltaSp: Int) : ReaderUiAction
    data class OnChangeTtsRate(val rate: Float) : ReaderUiAction
    data class OnAddBookmark(val note: String) : ReaderUiAction
    /** Long-press on a sentence: opens the marker sheet for it. */
    data class OnStartMarking(val sentenceIndex: Int) : ReaderUiAction
    data object OnDismissMarking : ReaderUiAction
    data class OnSaveHighlight(val colorIndex: Int, val note: String) : ReaderUiAction
    data class OnRemoveHighlight(val sentenceIndex: Int) : ReaderUiAction
    data object OnTogglePlayerLayout : ReaderUiAction
    data class OnSleepTimer(val minutes: Int?) : ReaderUiAction
}

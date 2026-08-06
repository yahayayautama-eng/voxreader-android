package com.example.feature.reader

import com.example.domain.repository.Book
import com.example.domain.repository.Chapter

enum class ReaderTheme {
    LIGHT, DARK, SEPIA, NIGHT
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val currentChapterIndex: Int = 0,
    val currentChapter: Chapter? = null,
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val fontSizeSp: Int = 18,
    val isTtsPlaying: Boolean = false,
    val isTtsPaused: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val ttsRate: Float = 1.0f,
    val ttsVoice: String = "default",
    val ttsPitch: Float = 1.0f,
    val isPlayerExpanded: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val textChunks: List<com.example.domain.model.tts.TextChunk> = emptyList(),
    val aiSummary: String? = null,
    val isGeneratingAiSummary: Boolean = false,
    val bookmarkAddedMessage: String? = null,
    val errorMessage: String? = null
)

sealed interface ReaderUiAction {
    data object OnPlayPauseTts : ReaderUiAction
    data object OnStopTts : ReaderUiAction
    data object OnPreviousSentence : ReaderUiAction
    data object OnNextSentence : ReaderUiAction
    data object OnSkipBack : ReaderUiAction
    data object OnSkipForward : ReaderUiAction
    data class OnChangeChapter(val newIndex: Int) : ReaderUiAction
    data class OnChangeTheme(val theme: ReaderTheme) : ReaderUiAction
    data class OnChangeFontSize(val deltaSp: Int) : ReaderUiAction
    data class OnChangeTtsRate(val rate: Float) : ReaderUiAction
    data class OnAddBookmark(val note: String) : ReaderUiAction
    data object OnGenerateAiSummary : ReaderUiAction
    data object OnDismissAiSummary : ReaderUiAction
    data object OnTogglePlayerLayout : ReaderUiAction
    data object OnVoiceSettings : ReaderUiAction
    data class OnSleepTimer(val minutes: Int?) : ReaderUiAction
}

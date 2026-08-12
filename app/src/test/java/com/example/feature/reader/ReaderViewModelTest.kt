package com.example.feature.reader

import com.example.data.local.datastore.AppSettingsManager
import com.example.data.local.dao.AudiobookDao
import com.example.data.local.entity.AudiobookGenerationEntity
import com.example.audiobook.AudiobookGenerationCoordinator
import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Chapter
import com.example.tts.ListeningTracker
import com.example.tts.NowPlaying
import com.example.tts.TtsChapter
import com.example.tts.TtsManager
import com.example.tts.TtsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var bookRepository: BookRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var appSettingsManager: AppSettingsManager
    private lateinit var listeningTracker: ListeningTracker
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var audiobookGenerationCoordinator: AudiobookGenerationCoordinator

    private val ttsStateFlow = MutableStateFlow(TtsState())
    private val ttsRateFlow = MutableStateFlow(1.0f)
    private val ttsVoiceFlow = MutableStateFlow("default")
    private val ttsEngineFlow = MutableStateFlow("offline")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        bookRepository = mockk(relaxed = true)
        ttsManager = mockk(relaxed = true)
        appSettingsManager = mockk(relaxed = true)
        listeningTracker = mockk(relaxed = true)
        audiobookDao = mockk(relaxed = true)
        audiobookGenerationCoordinator = mockk(relaxed = true)

        every { ttsManager.state } returns ttsStateFlow
        every { appSettingsManager.ttsRateFlow } returns ttsRateFlow
        every { appSettingsManager.ttsVoiceFlow } returns ttsVoiceFlow
        every { appSettingsManager.ttsEngineFlow } returns ttsEngineFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadBook updates ui state with chunks and restores progress`() = runTest(testDispatcher) {
        val chapter = Chapter(1, "Chapter 1", "This is sentence one. This is sentence two.", 1)
        val book = Book("1", "Title", "Author", "path", currentChapterIndex = 0, chapters = listOf(chapter))
        coEvery { bookRepository.getBookById("1") } returns book

        val viewModel = ReaderViewModel(bookRepository, ttsManager, appSettingsManager, listeningTracker, audiobookDao, audiobookGenerationCoordinator)
        viewModel.loadBook("1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("1", state.book?.id)
        assertEquals(2, state.textChunks.size)
        assertEquals("This is sentence one.", state.textChunks[0].text.trim())
    }

    @Test
    fun `observeTtsState updates ui state properly`() = runTest(testDispatcher) {
        val viewModel = ReaderViewModel(bookRepository, ttsManager, appSettingsManager, listeningTracker, audiobookDao, audiobookGenerationCoordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        ttsStateFlow.value = TtsState(isSpeaking = true, currentSentenceIndex = 5, speechRate = 1.5f)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isTtsPlaying)
        assertEquals(5, state.currentSentenceIndex)
        assertEquals(1.5f, state.ttsRate)
    }

    @Test
    fun `next sentence saves the reading position`() = runTest(testDispatcher) {
        val chapter = Chapter(1, "Chapter 1", "First sentence. Second sentence.", 1)
        val book = Book("1", "Title", "Author", currentPosition = 0, chapters = listOf(chapter))
        coEvery { bookRepository.getBookById("1") } returns book
        val viewModel = ReaderViewModel(bookRepository, ttsManager, appSettingsManager, listeningTracker, audiobookDao, audiobookGenerationCoordinator)

        viewModel.loadBook("1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAction(ReaderUiAction.OnNextSentence)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentSentenceIndex)
        coVerify { bookRepository.updateBookProgress("1", 0, 1) }
    }

    @Test
    fun `play action waits for converted audiobook instead of using live TTS`() = runTest(testDispatcher) {
        val chapter = Chapter(1, "Chapter 1", "First sentence. Second sentence.", 1)
        coEvery { bookRepository.getBookById("1") } returns Book("1", "Title", "Author", audiobookStatus = "CONVERTING", chapters = listOf(chapter))
        every { audiobookDao.observeGeneration("1") } returns flowOf(
            AudiobookGenerationEntity("1", "FAILED", totalChapters = 1, voiceId = "voice", modelVersion = "test")
        )
        val viewModel = ReaderViewModel(bookRepository, ttsManager, appSettingsManager, listeningTracker, audiobookDao, audiobookGenerationCoordinator)

        viewModel.loadBook("1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAction(ReaderUiAction.OnPlayPauseTts)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { ttsManager.speakChapters(any(), any(), any()) }
        verify { ttsManager.showAudiobookConversion(any(), any()) }
    }

    @Test
    fun `reader follows chapter advanced by playback queue`() = runTest(testDispatcher) {
        val chapters = listOf(
            Chapter(1, "Chapter 1", "End of chapter one."),
            Chapter(2, "Chapter 2", "Start of chapter two. Next sentence.")
        )
        coEvery { bookRepository.getBookById("1") } returns
            Book("1", "Title", "Author", chapters = chapters)
        val viewModel = ReaderViewModel(bookRepository, ttsManager, appSettingsManager, listeningTracker, audiobookDao, audiobookGenerationCoordinator)

        viewModel.loadBook("1")
        testDispatcher.scheduler.advanceUntilIdle()
        ttsStateFlow.value = TtsState(
            nowPlaying = NowPlaying("1", "Title", 1, "Chapter 2"),
            currentSentenceIndex = 0,
            totalSentences = 2,
            isPreparing = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentChapterIndex)
        assertEquals("Chapter 2", viewModel.uiState.value.currentChapter?.title)
        coVerify { bookRepository.updateBookProgress("1", 1, 0) }
    }
}

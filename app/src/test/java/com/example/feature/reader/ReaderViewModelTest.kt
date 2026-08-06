package com.example.feature.reader

import com.example.data.local.datastore.AppSettingsManager
import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Chapter
import com.example.playback.PlaybackController
import com.example.playback.PlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var playbackController: PlaybackController
    private lateinit var appSettingsManager: AppSettingsManager

    private val playbackStateFlow = MutableStateFlow(PlaybackState())
    private val ttsRateFlow = MutableStateFlow(1.0f)
    private val ttsVoiceFlow = MutableStateFlow("default")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        bookRepository = mockk(relaxed = true)
        playbackController = mockk(relaxed = true)
        appSettingsManager = mockk(relaxed = true)

        every { playbackController.playbackState } returns playbackStateFlow
        every { appSettingsManager.ttsRateFlow } returns ttsRateFlow
        every { appSettingsManager.ttsVoiceFlow } returns ttsVoiceFlow
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

        val viewModel = ReaderViewModel(bookRepository, playbackController, appSettingsManager)
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
        val viewModel = ReaderViewModel(bookRepository, playbackController, appSettingsManager)
        testDispatcher.scheduler.advanceUntilIdle()

        playbackStateFlow.value = PlaybackState(isPlaying = true, currentChunkIndex = 5, speed = 1.5f)
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
        val viewModel = ReaderViewModel(bookRepository, playbackController, appSettingsManager)

        viewModel.loadBook("1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAction(ReaderUiAction.OnNextSentence)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentSentenceIndex)
        coVerify { bookRepository.updateBookProgress("1", 0, 1) }
    }
}

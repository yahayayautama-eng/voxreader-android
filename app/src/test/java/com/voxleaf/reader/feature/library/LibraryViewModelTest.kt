package com.voxleaf.reader.feature.library

import androidx.lifecycle.SavedStateHandle
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.repository.BookRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val books = MutableStateFlow(
        listOf(
            Book("book-1", "One", "Author C", lastProgressUpdatedAt = 100L),
            Book("book-2", "Two", "Author B", lastProgressUpdatedAt = 300L),
            Book("book-3", "Three", "Author A", lastProgressUpdatedAt = 200L)
        )
    )
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        every { repository.getBooks() } returns books
        coEvery { repository.removeBook(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selection reducer selects toggles selects all and deselects all`() {
        var selected = reduceLibrarySelection(
            emptySet(),
            LibrarySelectionAction.Select("book-1")
        )
        assertEquals(setOf("book-1"), selected)

        selected = reduceLibrarySelection(
            selected,
            LibrarySelectionAction.Toggle("book-2")
        )
        assertEquals(setOf("book-1", "book-2"), selected)

        selected = reduceLibrarySelection(
            selected,
            LibrarySelectionAction.ToggleAll(setOf("book-1", "book-2", "book-3"))
        )
        assertEquals(setOf("book-1", "book-2", "book-3"), selected)

        selected = reduceLibrarySelection(
            selected,
            LibrarySelectionAction.ToggleAll(setOf("book-1", "book-2", "book-3"))
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `clear action exits selection and dismisses confirmation`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-1"))
        viewModel.handleAction(LibraryUiAction.OnRequestDelete)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as LibraryUiState.Success).showDeleteConfirmation)

        viewModel.handleAction(LibraryUiAction.OnClearSelection)
        dispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as LibraryUiState.Success
        assertFalse(state.isSelectionMode)
        assertFalse(state.showDeleteConfirmation)
    }

    @Test
    fun `cancel closes confirmation and keeps selected ids`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-1"))
        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-2"))
        viewModel.handleAction(LibraryUiAction.OnRequestDelete)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnCancelDelete)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as LibraryUiState.Success
        assertEquals(setOf("book-1", "book-2"), state.selectedBookIds)
        assertFalse(state.showDeleteConfirmation)
    }

    @Test
    fun `confirmation deletes exactly the selected stable book ids`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-3"))
        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-1"))
        viewModel.handleAction(LibraryUiAction.OnRequestDelete)
        viewModel.handleAction(LibraryUiAction.OnConfirmDelete)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.removeBook("book-1") }
        coVerify(exactly = 1) { repository.removeBook("book-3") }
        coVerify(exactly = 0) { repository.removeBook("book-2") }
        assertTrue((viewModel.uiState.value as LibraryUiState.Success).selectedBookIds.isEmpty())
    }

    @Test
    fun `partial failure removes successful id immediately and retains failed id`() = runTest(dispatcher) {
        coEvery { repository.removeBook("book-2") } throws IllegalStateException("disk busy")
        val viewModel = LibraryViewModel(repository, SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-1"))
        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-2"))
        viewModel.handleAction(LibraryUiAction.OnRequestDelete)
        viewModel.handleAction(LibraryUiAction.OnConfirmDelete)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.removeBook("book-1") }
        coVerify(exactly = 1) { repository.removeBook("book-2") }
        coVerify(exactly = 0) { repository.removeBook("book-3") }
        val state = viewModel.uiState.value as LibraryUiState.Success
        assertEquals(setOf("book-2"), state.selectedBookIds)
        assertEquals(1, state.deleteFailureCount)
    }

    @Test
    fun `selected ids restore from SavedStateHandle after view model recreation`() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        val firstViewModel = LibraryViewModel(repository, savedStateHandle)
        dispatcher.scheduler.advanceUntilIdle()
        firstViewModel.handleAction(LibraryUiAction.OnSelectBook("book-2"))
        dispatcher.scheduler.advanceUntilIdle()

        val recreatedViewModel = LibraryViewModel(repository, savedStateHandle)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            setOf("book-2"),
            (recreatedViewModel.uiState.value as LibraryUiState.Success).selectedBookIds
        )
    }

    @Test
    fun `stale restored ids are reconciled without deletion`() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "library_selected_book_ids" to arrayListOf("book-1", "missing-book")
            )
        )
        val viewModel = LibraryViewModel(repository, savedStateHandle)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            setOf("book-1"),
            (viewModel.uiState.value as LibraryUiState.Success).selectedBookIds
        )
        coVerify(exactly = 0) { repository.removeBook(any()) }
    }

    @Test
    fun `library controls and selection survive repository recomputation`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(LibraryUiAction.OnSearchQueryChange("Two"))
        viewModel.handleAction(LibraryUiAction.OnFilterSelect(LibraryFilter.ALL))
        viewModel.handleAction(LibraryUiAction.OnSortSelect(LibrarySort.TITLE))
        viewModel.handleAction(LibraryUiAction.OnSelectBook("book-2"))
        dispatcher.scheduler.advanceUntilIdle()

        books.value = books.value + Book("book-4", "Four", "Author D")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as LibraryUiState.Success
        assertEquals("Two", state.searchQuery)
        assertEquals(LibraryFilter.ALL, state.selectedFilter)
        assertEquals(LibrarySort.TITLE, state.selectedSort)
        assertEquals(setOf("book-2"), state.selectedBookIds)
        assertEquals(listOf("book-2"), state.allBooks.map { it.id })
    }
}

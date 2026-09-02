package com.voxleaf.reader.feature.bookdetails

import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.repository.Chapter
import com.voxleaf.reader.domain.usecase.RedetectChaptersUseCase
import com.voxleaf.reader.domain.usecase.SectionStructureUseCase
import com.voxleaf.reader.domain.usecase.StructurePreview
import com.voxleaf.reader.domain.usecase.StructureResult
import com.voxleaf.reader.domain.usecase.StructureSectionDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BookDetailsStructureTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var legacyRedetect: RedetectChaptersUseCase
    private lateinit var structure: SectionStructureUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        legacyRedetect = mockk(relaxed = true)
        structure = mockk(relaxed = true)
        coEvery { repository.getBookById("book") } returns Book(
            id = "book",
            title = "Document",
            author = "Author",
            chapters = listOf(Chapter(1, "One", "First sentence. Second sentence."))
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analysis is preview only and edits are applied explicitly`() = runTest(dispatcher) {
        val proposed = listOf(draft("one", "One", "First sentence. Second sentence."))
        coEvery { structure.analyze("book") } returns StructureResult.Preview(
            StructurePreview(proposed, proposed, addedCount = 0, removedCount = 0, changedCount = 0)
        )
        coEvery { structure.apply("book", any()) } returns StructureResult.Applied
        val viewModel = BookDetailsViewModel(repository, legacyRedetect, structure)
        viewModel.loadBook("book")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.handleAction(BookDetailsUiAction.OnRedetectChapters)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { structure.apply(any(), any()) }

        viewModel.handleAction(BookDetailsUiAction.OnRenameStructureSection(0, "Opening"))
        viewModel.handleAction(BookDetailsUiAction.OnSplitStructureSection(0))
        val edited = (viewModel.uiState.value as BookDetailsUiState.Success).structureDrafts.orEmpty()
        assertEquals(2, edited.size)
        assertEquals("Opening", edited.first().title)
        assertTrue(edited.all { it.isManuallyEdited })

        viewModel.handleAction(BookDetailsUiAction.OnApplyStructure)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) {
            structure.apply("book", match { it.size == 2 && it.first().title == "Opening" })
        }
    }

    @Test
    fun `cancel closes preview without applying`() = runTest(dispatcher) {
        val proposed = listOf(draft("one", "One", "First sentence."))
        coEvery { structure.analyze("book") } returns StructureResult.Preview(
            StructurePreview(proposed, proposed, 0, 0, 0)
        )
        val viewModel = BookDetailsViewModel(repository, legacyRedetect, structure)
        viewModel.loadBook("book")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAction(BookDetailsUiAction.OnRedetectChapters)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAction(BookDetailsUiAction.OnCancelStructureReview)

        assertEquals(null, (viewModel.uiState.value as BookDetailsUiState.Success).structureDrafts)
        coVerify(exactly = 0) { structure.apply(any(), any()) }
    }

    private fun draft(id: String, title: String, content: String) = StructureSectionDraft(
        id = id,
        title = title,
        content = content,
        detectionSource = "HEADING",
        detectionConfidence = 0.9f,
        detectionReason = "Heading pattern",
        startAnchor = "start",
        endAnchor = "end"
    )
}

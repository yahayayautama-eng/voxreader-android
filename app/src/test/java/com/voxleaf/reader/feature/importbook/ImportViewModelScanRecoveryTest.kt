package com.voxleaf.reader.feature.importbook

import android.graphics.Bitmap
import com.voxleaf.reader.domain.usecase.ImportScannedBookUseCase
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ImportTextBookUseCase
import com.voxleaf.reader.data.repository.ScannedPageProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportViewModelScanRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recreated ViewModel restores ordered lightweight references and latest title`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("view-model-restore")
        val firstStore = store(root)
        capture(firstStore, "one")
        capture(firstStore, "two")
        firstStore.updateTitle("Recovered title")

        val recreated = viewModel(store(root))
        runCurrent()
        awaitRealCondition { recreated.capturedPages.value.size == 2 }

        assertEquals(listOf("one", "two"), recreated.capturedPages.value.map { File(it.absolutePath).readText() })
        assertEquals("Recovered title", recreated.scanTitle.value)
    }

    @Test
    fun `retry preserves manifest pages after OCR failure`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("retry")
        val store = store(root)
        capture(store, "page")
        val viewModel = viewModel(
            store,
            scannedImporter = ImportScannedBookUseCase { _, _ -> flowOf(ImportState.Error("OCR failed")) }
        )
        runCurrent()
        awaitRealCondition { viewModel.capturedPages.value.size == 1 }

        viewModel.finishScan()
        advanceUntilIdle()
        viewModel.retryImport()

        assertTrue(viewModel.importState.value is ImportState.Idle)
        assertEquals(1, viewModel.capturedPages.value.size)
        assertEquals(1, store(root).restoreActiveSession().snapshot!!.pages.size)
    }

    @Test
    fun `single title writer persists the latest rapid update`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("title-order")
        val store = store(root)
        capture(store, "page")
        val viewModel = viewModel(store)
        runCurrent()
        awaitRealCondition { viewModel.capturedPages.value.size == 1 }

        viewModel.updateScanTitle("A")
        viewModel.updateScanTitle("AB")
        viewModel.updateScanTitle("ABC")
        awaitRealCondition { store.restoreActiveSession().snapshot?.title == "ABC" }

        assertEquals("ABC", store.restoreActiveSession().snapshot!!.title)
        assertEquals("ABC", store(root).restoreActiveSession().snapshot!!.title)
    }

    @Test
    fun `recreated ViewModel surfaces a corrupt pending capture issue`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("corrupt-pending-vm")
        val firstStore = store(root, validator = { it.readText() != "corrupt" })
        val pending = (firstStore.prepareCapture() as ScanCapturePreparation.Ready).file
        pending.writeText("corrupt")

        val recreated = viewModel(store(root, validator = { it.readText() != "corrupt" }))
        runCurrent()
        awaitRealCondition { recreated.scanIssue.value != null }

        assertEquals(ScanIssue.CORRUPT_IMAGE, recreated.scanIssue.value)
        assertTrue(recreated.capturedPages.value.isEmpty())
    }

    @Test
    fun `cancel deletes recoverable session and clears ViewModel state`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("cancel-vm")
        val store = store(root)
        capture(store, "page")
        val viewModel = viewModel(store)
        runCurrent()
        awaitRealCondition { viewModel.capturedPages.value.size == 1 }

        viewModel.cancelScan()

        assertTrue(viewModel.capturedPages.value.isEmpty())
        assertNull(store(root).restoreActiveSession().snapshot)
    }

    @Test
    fun `successful cleanup keeps success stable until navigation is acknowledged`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("success-order")
        val store = store(root)
        capture(store, "page")
        val viewModel = viewModel(
            store,
            scannedImporter = ImportScannedBookUseCase { _, _ -> flowOf(ImportState.Success("book-id")) }
        )
        runCurrent()
        awaitRealCondition { viewModel.capturedPages.value.size == 1 }
        viewModel.finishScan()
        advanceUntilIdle()

        viewModel.cleanupSuccessfulSession()

        assertEquals(ImportState.Success("book-id"), viewModel.importState.value)
        assertNull(store(root).restoreActiveSession().snapshot)

        viewModel.acknowledgeSuccessfulNavigation()
        assertTrue(viewModel.importState.value is ImportState.Idle)
    }

    @Test
    fun `reorder through ViewModel survives ViewModel recreation`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("view-model-reorder")
        val store = store(root)
        capture(store, "one")
        capture(store, "two")
        val first = viewModel(store)
        runCurrent()
        awaitRealCondition { first.capturedPages.value.size == 2 }

        first.movePage(first.capturedPages.value[1], ScanPageMove.EARLIER)
        val recreated = viewModel(store(root))
        runCurrent()
        awaitRealCondition { recreated.capturedPages.value.size == 2 }

        assertEquals(listOf("two", "one"), recreated.capturedPages.value.map { File(it.absolutePath).readText() })
    }

    @Test
    fun `unexpected page operation error preserves snapshot and surfaces recoverable issue`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("view-model-operation-error")
        var failTimestamp = false
        val store = store(root, nowProvider = {
            if (failTimestamp) error("simulated manifest timestamp failure") else 1_000L
        })
        capture(store, "keep")
        val viewModel = viewModel(store)
        runCurrent()
        awaitRealCondition { viewModel.capturedPages.value.size == 1 }
        val original = viewModel.capturedPages.value.single()

        failTimestamp = true
        viewModel.removePage(original)

        assertEquals(ScanIssue.REMOVE_FAILED, viewModel.scanIssue.value)
        assertEquals(listOf(original), viewModel.capturedPages.value)
        assertEquals("keep", File(original.absolutePath).readText())
    }

    @Test
    fun `recognized text is persisted edited and restored without repeating OCR`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("view-model-ocr-review")
        val store = store(root)
        capture(store, "image")
        var recognitions = 0
        val processor = ScannedPageProcessor(
            scanRoot = root,
            decode = { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888) },
            recognize = { recognitions++; "raw recognized text" }
        )
        val first = viewModel(store, processor = processor)
        runCurrent()
        awaitRealCondition { first.capturedPages.value.size == 1 }

        first.openOcrReview(first.capturedPages.value.single())
        first.updateOcrReviewText("corrected text")
        first.saveOcrReview()

        assertEquals("corrected text", first.ocrReview.value!!.text)
        assertEquals(1, recognitions)

        val recreated = viewModel(store(root), processor = processor)
        runCurrent()
        awaitRealCondition { recreated.capturedPages.value.singleOrNull()?.reviewedTextPath != null }
        recreated.openOcrReview(recreated.capturedPages.value.single())

        assertEquals("corrected text", recreated.ocrReview.value!!.text)
        assertEquals(1, recognitions)
    }

    private fun viewModel(
        store: ScanSessionStore,
        scannedImporter: ImportScannedBookUseCase = ImportScannedBookUseCase { _, _ -> flowOf(ImportState.Idle) },
        processor: ScannedPageProcessor = ScannedPageProcessor(
            scanRoot = temporaryFolder.root,
            decode = { null },
            recognize = { "" }
        )
    ) = ImportViewModel(
        importTextBook = ImportTextBookUseCase { flowOf(ImportState.Idle) },
        importScannedBook = scannedImporter,
        scanSessionStore = store,
        scannedPageProcessor = processor
    )

    private fun capture(store: ScanSessionStore, text: String) {
        val file = (store.prepareCapture() as ScanCapturePreparation.Ready).file
        file.writeText(text)
        store.completeCapture(true)
    }

    private fun store(
        root: File,
        validator: (File) -> Boolean = { true },
        nowProvider: () -> Long = { 1_000L }
    ) = ScanSessionStore(
        rootDirectory = root,
        usableSpace = { Long.MAX_VALUE },
        now = nowProvider,
        isReadableImage = validator
    )

    private suspend fun awaitRealCondition(condition: () -> Boolean) = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Timed out waiting for asynchronous ViewModel state", condition())
    }
}

package com.voxleaf.reader.feature.importbook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.domain.usecase.ImportScannedBookUseCase
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ImportStage
import com.voxleaf.reader.domain.usecase.ImportTextBookUseCase
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import com.voxleaf.reader.data.repository.ScannedPageProcessingResult
import com.voxleaf.reader.data.repository.ScannedPageProcessor
import com.voxleaf.reader.data.repository.ScannedPageRecognitionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importTextBook: ImportTextBookUseCase,
    private val importScannedBook: ImportScannedBookUseCase,
    private val scanSessionStore: ScanSessionStore,
    private val scannedPageProcessor: ScannedPageProcessor
) : ViewModel() {
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _capturedPages = MutableStateFlow<List<ScannedPageReference>>(emptyList())
    val capturedPages: StateFlow<List<ScannedPageReference>> = _capturedPages.asStateFlow()

    private val _scanTitle = MutableStateFlow("")
    val scanTitle: StateFlow<String> = _scanTitle.asStateFlow()

    private val _scanIssue = MutableStateFlow<ScanIssue?>(null)
    val scanIssue: StateFlow<ScanIssue?> = _scanIssue.asStateFlow()

    private val _pageOperationInProgress = MutableStateFlow(false)
    val pageOperationInProgress: StateFlow<Boolean> = _pageOperationInProgress.asStateFlow()

    private val _ocrReview = MutableStateFlow<ScanOcrReviewState?>(null)
    val ocrReview: StateFlow<ScanOcrReviewState?> = _ocrReview.asStateFlow()

    private var scanImportJob: Job? = null
    private var fileImportJob: Job? = null
    private var lastImportUri: Uri? = null
    private val titleUpdates = Channel<String>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = scanSessionStore.restoreActiveSession()
            applySnapshot(restored.snapshot)
            _scanIssue.value = restored.issue
            for (title in titleUpdates) scanSessionStore.updateTitle(title)
        }
    }

    fun importBook(uri: Uri) {
        importBooks(listOf(uri))
    }

    fun importBooks(uris: List<Uri>) {
        if (fileImportJob?.isActive == true) return
        val queue = uris.distinctBy(Uri::toString).take(MAX_SHARED_DOCUMENTS)
        if (queue.isEmpty()) return
        lastImportUri = queue.first()
        fileImportJob = viewModelScope.launch {
            val importedIds = mutableListOf<String>()
            queue.forEachIndexed { index, uri ->
                var failure: ImportState.Error? = null
                importTextBook(uri).collect { state ->
                    when (state) {
                        is ImportState.Importing -> {
                            _importState.value = ImportState.Importing(
                                progress = (index + state.progress) / queue.size,
                                stage = state.stage
                            )
                        }
                        is ImportState.Success -> importedIds += state.bookId
                        is ImportState.Error -> failure = state
                        ImportState.Idle -> Unit
                    }
                }
                if (failure != null) {
                    _importState.value = if (importedIds.isEmpty()) {
                        checkNotNull(failure)
                    } else {
                        ImportState.Error(
                            message = checkNotNull(failure).message,
                            importedCount = importedIds.size,
                            totalCount = queue.size
                        )
                    }
                    lastImportUri = uri
                    return@launch
                }
            }
            lastImportUri = null
            _importState.value = ImportState.Success(importedIds.last())
        }
    }

    suspend fun cancelFileImport() {
        fileImportJob?.cancelAndJoin()
        fileImportJob = null
        _importState.value = ImportState.Idle
    }

    suspend fun prepareCapture(): File? {
        val preparation = withContext(Dispatchers.IO) { scanSessionStore.prepareCapture() }
        return when (preparation) {
            is ScanCapturePreparation.Ready -> {
                _scanIssue.value = null
                preparation.file
            }
            is ScanCapturePreparation.Rejected -> {
                _scanIssue.value = preparation.issue
                null
            }
        }
    }

    suspend fun completeCapture(success: Boolean) {
        val completion = withContext(Dispatchers.IO) { scanSessionStore.completeCapture(success) }
        applySnapshot(completion.snapshot)
        _scanIssue.value = completion.issue
    }

    suspend fun prepareRetake(page: ScannedPageReference): File? {
        val preparation = withContext(Dispatchers.IO) { scanSessionStore.prepareReplacement(page) }
        return when (preparation) {
            is ScanCapturePreparation.Ready -> {
                _scanIssue.value = null
                preparation.file
            }
            is ScanCapturePreparation.Rejected -> {
                _scanIssue.value = preparation.issue
                null
            }
        }
    }

    suspend fun cameraLaunchFailed() {
        val completion = withContext(Dispatchers.IO) { scanSessionStore.discardPendingCapture() }
        applySnapshot(completion.snapshot)
        _scanIssue.value = completion.issue
    }

    suspend fun movePage(page: ScannedPageReference, move: ScanPageMove) {
        runPageOperation(ScanIssue.REORDER_FAILED) { scanSessionStore.movePage(page, move) }
    }

    suspend fun rotatePage(page: ScannedPageReference) {
        runPageOperation(ScanIssue.ROTATE_FAILED) { scanSessionStore.rotatePage(page) }
    }

    suspend fun removePage(page: ScannedPageReference) {
        runPageOperation(ScanIssue.REMOVE_FAILED) { scanSessionStore.removePage(page) }
    }

    suspend fun cropPage(page: ScannedPageReference, insetPercent: Int) {
        runPageOperation(ScanIssue.CROP_FAILED) { scanSessionStore.cropPage(page, insetPercent) }
    }

    suspend fun openOcrReview(page: ScannedPageReference) {
        if (_pageOperationInProgress.value) return
        val pageNumber = _capturedPages.value.indexOfFirst { it.absolutePath == page.absolutePath } + 1
        if (pageNumber <= 0) {
            _scanIssue.value = ScanIssue.PAGE_NOT_AVAILABLE
            return
        }
        _ocrReview.value = ScanOcrReviewState(page, pageNumber, "", isLoading = true)
        val result = withContext(Dispatchers.IO) { scannedPageProcessor.recognizePage(page) }
        when (result) {
            is ScannedPageRecognitionResult.Success -> {
                val saved = withContext(Dispatchers.IO) { scanSessionStore.saveReviewedText(page, result.text) }
                applySnapshot(saved.snapshot)
                _scanIssue.value = saved.issue
                val currentPage = saved.snapshot?.pages?.getOrNull(pageNumber - 1) ?: page
                _ocrReview.value = ScanOcrReviewState(currentPage, pageNumber, result.text, isLoading = false)
            }
            is ScannedPageRecognitionResult.Error -> {
                _ocrReview.value = null
                _scanIssue.value = when (result.reason) {
                    ScannedPageProcessingResult.Reason.INVALID_PATH -> ScanIssue.PAGE_NOT_AVAILABLE
                    ScannedPageProcessingResult.Reason.EMPTY_IMAGE -> ScanIssue.RETAKE_EMPTY_IMAGE
                    ScannedPageProcessingResult.Reason.CORRUPT_IMAGE -> ScanIssue.RETAKE_CORRUPT_IMAGE
                    ScannedPageProcessingResult.Reason.OCR_FAILED,
                    ScannedPageProcessingResult.Reason.NO_PAGES -> ScanIssue.OCR_REVIEW_SAVE_FAILED
                }
            }
        }
    }

    fun updateOcrReviewText(text: String) {
        _ocrReview.value = _ocrReview.value?.copy(text = text, hasUnsavedChanges = true)
    }

    suspend fun saveOcrReview() {
        val review = _ocrReview.value ?: return
        val result = withContext(Dispatchers.IO) {
            scanSessionStore.saveReviewedText(review.page, review.text)
        }
        applySnapshot(result.snapshot)
        _scanIssue.value = result.issue
        if (result.issue == null) {
            val page = result.snapshot?.pages?.getOrNull(review.pageNumber - 1) ?: review.page
            _ocrReview.value = review.copy(page = page, hasUnsavedChanges = false)
        }
    }

    fun closeOcrReview() {
        _ocrReview.value = null
    }

    fun updateScanTitle(title: String) {
        _scanTitle.value = title
        titleUpdates.trySend(title)
    }

    fun finishScan() {
        val pages = _capturedPages.value
        val title = _scanTitle.value
        if (pages.isEmpty() || scanImportJob?.isActive == true) return
        _scanIssue.value = null
        scanImportJob = viewModelScope.launch {
            importScannedBook(pages, title).collect { _importState.value = it }
        }
    }

    suspend fun cancelScan() {
        scanImportJob?.cancelAndJoin()
        withContext(NonCancellable + Dispatchers.IO) {
            scanSessionStore.cancelActiveSession()
        }
        _capturedPages.value = emptyList()
        _scanTitle.value = ""
        _scanIssue.value = null
        _importState.value = ImportState.Idle
    }

    suspend fun cleanupSuccessfulSession() {
        withContext(NonCancellable + Dispatchers.IO) {
            scanSessionStore.completeActiveSession()
        }
        _capturedPages.value = emptyList()
        _scanTitle.value = ""
        _scanIssue.value = null
    }

    fun acknowledgeSuccessfulNavigation() {
        _importState.value = ImportState.Idle
    }

    fun retryImport() {
        _importState.value = ImportState.Idle
        _scanIssue.value = null
        if (_capturedPages.value.isNotEmpty()) {
            finishScan()
        } else {
            lastImportUri?.let(::importBook)
        }
    }

    private fun applySnapshot(snapshot: ScanSessionSnapshot?) {
        _capturedPages.value = snapshot?.pages.orEmpty()
        if (snapshot != null) _scanTitle.value = snapshot.title
    }

    private suspend fun runPageOperation(fallbackIssue: ScanIssue, operation: () -> ScanOperationResult) {
        if (_pageOperationInProgress.value) return
        _pageOperationInProgress.value = true
        try {
            runCatching { withContext(Dispatchers.IO) { operation() } }
                .onSuccess { result ->
                    applySnapshot(result.snapshot)
                    _scanIssue.value = result.issue
                }
                .onFailure { _scanIssue.value = fallbackIssue }
        } finally {
            _pageOperationInProgress.value = false
        }
    }
}

private const val MAX_SHARED_DOCUMENTS = 20

data class ScanOcrReviewState(
    val page: ScannedPageReference,
    val pageNumber: Int,
    val text: String,
    val isLoading: Boolean,
    val hasUnsavedChanges: Boolean = false
)

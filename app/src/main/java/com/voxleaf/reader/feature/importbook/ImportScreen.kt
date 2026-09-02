package com.voxleaf.reader.feature.importbook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxleaf.reader.R
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ImportStage
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import com.voxleaf.reader.ui.theme.Carbon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(
    onNavigateToBookDetails: (String) -> Unit,
    incomingDocumentUris: List<String> = emptyList(),
    onIncomingDocumentsConsumed: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val scanTitle by viewModel.scanTitle.collectAsStateWithLifecycle()
    val scanIssue by viewModel.scanIssue.collectAsStateWithLifecycle()
    val pageOperationInProgress by viewModel.pageOperationInProgress.collectAsStateWithLifecycle()
    val ocrReview by viewModel.ocrReview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRemovalPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCropPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingDocumentUris) {
        if (incomingDocumentUris.isNotEmpty()) {
            viewModel.importBooks(incomingDocumentUris.map(Uri::parse))
            onIncomingDocumentsConsumed()
        }
    }

    LaunchedEffect(importState) {
        val success = importState as? ImportState.Success ?: return@LaunchedEffect
        viewModel.cleanupSuccessfulSession()
        try {
            onNavigateToBookDetails(success.bookId)
        } finally {
            viewModel.acknowledgeSuccessfulNavigation()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importBook)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        scope.launch { viewModel.completeCapture(success) }
    }

    fun launchPreparedCamera(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            scope.launch { viewModel.cameraLaunchFailed() }
        }
    }

    fun launchCamera() {
        scope.launch {
            val file = viewModel.prepareCapture() ?: return@launch
            launchPreparedCamera(file)
        }
    }

    fun launchRetake(page: ScannedPageReference) {
        scope.launch {
            val file = viewModel.prepareRetake(page) ?: return@launch
            launchPreparedCamera(file)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    stringResource(R.string.import_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_heading),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val state = importState) {
                    is ImportState.Idle, is ImportState.Success -> {
                        if (capturedPages.isEmpty()) {
                            Button(
                                onClick = {
                                    fileLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/epub+zip",
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/zip",
                                            "application/x-mobipocket-ebook",
                                            "application/vnd.amazon.ebook",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("import_file_button")
                            ) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.import_choose_document))
                            }
                            OutlinedButton(
                                onClick = ::launchCamera,
                                modifier = Modifier.fillMaxWidth().testTag("scan_document_button")
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.scan_document))
                            }
                            Text(
                                stringResource(R.string.scan_limits_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    items = capturedPages,
                                    key = { _, page -> page.absolutePath }
                                ) { index, page ->
                                    ScannedPageThumbnail(
                                        page = page,
                                        pageNumber = index + 1,
                                        pageCount = capturedPages.size,
                                        actionsEnabled = !pageOperationInProgress,
                                        onMoveEarlier = {
                                            scope.launch { viewModel.movePage(page, ScanPageMove.EARLIER) }
                                        },
                                        onMoveLater = {
                                            scope.launch { viewModel.movePage(page, ScanPageMove.LATER) }
                                        },
                                        onRotate = { scope.launch { viewModel.rotatePage(page) } },
                                        onCrop = { pendingCropPath = page.absolutePath },
                                        onRetake = { launchRetake(page) },
                                        onReviewText = { scope.launch { viewModel.openOcrReview(page) } },
                                        onRemove = { pendingRemovalPath = page.absolutePath }
                                    )
                                }
                            }
                            Text(
                                pluralStringResource(
                                    R.plurals.scan_session_summary,
                                    capturedPages.size,
                                    capturedPages.size,
                                    Formatter.formatShortFileSize(context, capturedPages.sumOf { it.byteSize })
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.scan_limits_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedTextField(
                                value = scanTitle,
                                onValueChange = viewModel::updateScanTitle,
                                label = { Text(stringResource(R.string.scan_document_title)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("scan_title_input")
                            )
                            Button(
                                onClick = ::launchCamera,
                                modifier = Modifier.fillMaxWidth().testTag("scan_add_page_button")
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.scan_add_page))
                            }
                            Button(
                                onClick = viewModel::finishScan,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("scan_finish_button")
                            ) {
                                Text(pluralStringResource(R.plurals.scan_finish, capturedPages.size, capturedPages.size))
                            }
                            TextButton(onClick = { scope.launch { viewModel.cancelScan() } }) {
                                Text(stringResource(R.string.scan_cancel))
                            }
                        }
                        scanIssue?.let { issue ->
                            Text(
                                text = scanIssueText(issue),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is ImportState.Importing -> {
                        CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(48.dp))
                        Text(
                            stringResource(R.string.import_progress, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            importStageText(state.stage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    if (capturedPages.isNotEmpty()) viewModel.cancelScan()
                                    else viewModel.cancelFileImport()
                                }
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (capturedPages.isNotEmpty()) R.string.scan_cancel
                                    else R.string.import_cancel
                                )
                            )
                        }
                    }
                    is ImportState.Error -> {
                        Text(
                            text = if (state.importedCount > 0) {
                                stringResource(
                                    R.string.import_partial_error,
                                    state.importedCount.toString(),
                                    state.totalCount.toString(),
                                    state.message
                                )
                            } else {
                                state.message
                            },
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = viewModel::retryImport,
                            modifier = Modifier.testTag("import_retry_button")
                        ) {
                            Text(stringResource(R.string.import_retry))
                        }
                        if (capturedPages.isNotEmpty()) {
                            TextButton(onClick = { scope.launch { viewModel.cancelScan() } }) {
                                Text(stringResource(R.string.scan_cancel))
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingRemoval = pendingRemovalPath?.let { path ->
        capturedPages.firstOrNull { it.absolutePath == path }
    }
    LaunchedEffect(pendingRemovalPath, pendingRemoval) {
        if (pendingRemovalPath != null && pendingRemoval == null) pendingRemovalPath = null
    }
    if (pendingRemoval != null) {
        val pageNumber = capturedPages.indexOf(pendingRemoval) + 1
        RemoveScannedPageDialog(
            pageNumber = pageNumber,
            onConfirm = {
                pendingRemovalPath = null
                scope.launch { viewModel.removePage(pendingRemoval) }
            },
            onDismiss = { pendingRemovalPath = null }
        )
    }
    val pendingCrop = pendingCropPath?.let { path ->
        capturedPages.firstOrNull { it.absolutePath == path }
    }
    LaunchedEffect(pendingCropPath, pendingCrop) {
        if (pendingCropPath != null && pendingCrop == null) pendingCropPath = null
    }
    if (pendingCrop != null) {
        val pageNumber = capturedPages.indexOf(pendingCrop) + 1
        CropScannedPageDialog(
            pageNumber = pageNumber,
            onConfirm = { inset ->
                pendingCropPath = null
                scope.launch { viewModel.cropPage(pendingCrop, inset) }
            },
            onDismiss = { pendingCropPath = null }
        )
    }
    if (ocrReview != null) {
        OcrReviewDialog(
            state = checkNotNull(ocrReview),
            onTextChanged = viewModel::updateOcrReviewText,
            onSave = { scope.launch { viewModel.saveOcrReview() } },
            onDismiss = viewModel::closeOcrReview
        )
    }
}

@Composable
internal fun ScannedPageThumbnail(
    page: ScannedPageReference,
    pageNumber: Int,
    pageCount: Int,
    actionsEnabled: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onRetake: () -> Unit,
    onReviewText: () -> Unit,
    onRemove: () -> Unit
) {
    var bitmap by remember(page.absolutePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            decodeSampledBitmap(File(page.absolutePath), maxDimension = 320)
        }
    }
    DisposableEffect(page.absolutePath, bitmap) {
        val capturedBitmap = bitmap
        onDispose { capturedBitmap?.recycle() }
    }

    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            val thumbnail = bitmap
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.scan_page_thumbnail, pageNumber),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        ScannedPageActionsMenu(
            pageNumber = pageNumber,
            canMoveEarlier = pageNumber > 1,
            canMoveLater = pageNumber < pageCount,
            actionsEnabled = actionsEnabled,
            onMoveEarlier = onMoveEarlier,
            onMoveLater = onMoveLater,
            onRotate = onRotate,
            onCrop = onCrop,
            onRetake = onRetake,
            onReviewText = onReviewText,
            onRemove = onRemove
        )
    }
}

@Composable
internal fun ScannedPageActionsMenu(
    pageNumber: Int,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    actionsEnabled: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onRetake: () -> Unit,
    onReviewText: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = actionsEnabled,
            modifier = Modifier.testTag("scan_page_actions_$pageNumber")
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.scan_page_actions, pageNumber)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_move_earlier, pageNumber)) },
                enabled = actionsEnabled && canMoveEarlier,
                onClick = { expanded = false; onMoveEarlier() },
                modifier = Modifier.testTag("scan_page_move_earlier_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_move_later, pageNumber)) },
                enabled = actionsEnabled && canMoveLater,
                onClick = { expanded = false; onMoveLater() },
                modifier = Modifier.testTag("scan_page_move_later_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_rotate, pageNumber)) },
                enabled = actionsEnabled,
                onClick = { expanded = false; onRotate() },
                modifier = Modifier.testTag("scan_page_rotate_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_crop, pageNumber)) },
                enabled = actionsEnabled,
                onClick = { expanded = false; onCrop() },
                modifier = Modifier.testTag("scan_page_crop_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_retake, pageNumber)) },
                enabled = actionsEnabled,
                onClick = { expanded = false; onRetake() },
                modifier = Modifier.testTag("scan_page_retake_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_review_text, pageNumber)) },
                enabled = actionsEnabled,
                onClick = { expanded = false; onReviewText() },
                modifier = Modifier.testTag("scan_page_review_$pageNumber")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_remove, pageNumber)) },
                enabled = actionsEnabled,
                onClick = { expanded = false; onRemove() },
                modifier = Modifier.testTag("scan_page_remove_$pageNumber")
            )
        }
    }
}

@Composable
internal fun CropScannedPageDialog(
    pageNumber: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inset by rememberSaveable { mutableStateOf(5f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_crop_title, pageNumber)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.scan_crop_message, inset.toInt()))
                Slider(
                    value = inset,
                    onValueChange = { inset = it },
                    valueRange = ScanSessionStore.MIN_CROP_INSET_PERCENT.toFloat()..
                        ScanSessionStore.MAX_CROP_INSET_PERCENT.toFloat(),
                    steps = ScanSessionStore.MAX_CROP_INSET_PERCENT -
                        ScanSessionStore.MIN_CROP_INSET_PERCENT - 1
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inset.toInt()) }) {
                Text(stringResource(R.string.scan_crop_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_crop_cancel)) }
        }
    )
}

@Composable
internal fun OcrReviewDialog(
    state: ScanOcrReviewState,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_ocr_review_title, state.pageNumber)) },
        text = {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.scan_ocr_review_loading))
                }
            } else {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChanged,
                    label = { Text(stringResource(R.string.scan_ocr_review_label)) },
                    supportingText = { Text(stringResource(R.string.scan_ocr_review_persisted)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp)
                )
            }
        },
        confirmButton = {
            if (!state.isLoading) {
                TextButton(onClick = onSave, enabled = state.hasUnsavedChanges) {
                    Text(stringResource(R.string.scan_ocr_review_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_ocr_review_close)) }
        }
    )
}

@Composable
internal fun RemoveScannedPageDialog(
    pageNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_remove_page_title, pageNumber)) },
        text = { Text(stringResource(R.string.scan_remove_page_message, pageNumber)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("scan_remove_page_confirm")
            ) {
                Text(stringResource(R.string.scan_page_remove, pageNumber))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_remove_page_cancel)) }
        }
    )
}

@Composable
private fun scanIssueText(issue: ScanIssue): String = stringResource(
    when (issue) {
        ScanIssue.PAGE_LIMIT -> R.string.scan_limit_pages
        ScanIssue.BYTE_LIMIT -> R.string.scan_limit_bytes
        ScanIssue.LOW_STORAGE -> R.string.scan_limit_storage
        ScanIssue.CAMERA_UNAVAILABLE -> R.string.scan_error_camera_unavailable
        ScanIssue.CAMERA_FAILED -> R.string.scan_error_camera_failed
        ScanIssue.EMPTY_IMAGE -> R.string.scan_error_empty_capture
        ScanIssue.CORRUPT_IMAGE -> R.string.scan_error_corrupt_capture
        ScanIssue.STORAGE_WRITE_FAILED -> R.string.scan_error_storage_write
        ScanIssue.PAGE_NOT_AVAILABLE -> R.string.scan_error_page_unavailable
        ScanIssue.MOVE_UNAVAILABLE -> R.string.scan_error_move_unavailable
        ScanIssue.REORDER_FAILED -> R.string.scan_error_reorder
        ScanIssue.REMOVE_FAILED -> R.string.scan_error_remove
        ScanIssue.ROTATE_FAILED -> R.string.scan_error_rotate
        ScanIssue.ROTATE_BYTE_LIMIT -> R.string.scan_error_rotate_bytes
        ScanIssue.ROTATE_LOW_STORAGE -> R.string.scan_error_rotate_storage
        ScanIssue.CROP_FAILED -> R.string.scan_error_crop
        ScanIssue.CROP_INVALID -> R.string.scan_error_crop_invalid
        ScanIssue.OCR_REVIEW_TOO_LARGE -> R.string.scan_error_review_too_large
        ScanIssue.OCR_REVIEW_SAVE_FAILED -> R.string.scan_error_review_save
        ScanIssue.RETAKE_CAMERA_FAILED -> R.string.scan_error_retake_camera
        ScanIssue.RETAKE_CANCELLED -> R.string.scan_error_retake_cancelled
        ScanIssue.RETAKE_EMPTY_IMAGE -> R.string.scan_error_retake_empty
        ScanIssue.RETAKE_CORRUPT_IMAGE -> R.string.scan_error_retake_corrupt
        ScanIssue.RETAKE_BYTE_LIMIT -> R.string.scan_error_retake_bytes
        ScanIssue.RETAKE_LOW_STORAGE -> R.string.scan_error_retake_storage
    }
)

private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun importStageText(stage: ImportStage): String = stringResource(
    when (stage) {
        ImportStage.PREPARING -> R.string.import_stage_preparing
        ImportStage.COPYING -> R.string.import_stage_copying
        ImportStage.READING -> R.string.import_stage_reading
        ImportStage.RECOGNIZING_TEXT -> R.string.import_stage_recognizing
        ImportStage.SAVING -> R.string.import_stage_saving
    }
)

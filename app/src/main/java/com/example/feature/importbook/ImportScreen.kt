package com.example.feature.importbook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import com.example.ui.theme.Carbon
import com.example.ui.theme.SignalOrange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.usecase.ImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ImportScreen(
    onNavigateToBookDetails: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // When import completes, navigate and reset
    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            val bookId = (importState as ImportState.Success).bookId
            onNavigateToBookDetails(bookId)
            viewModel.resetState()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = decodeSampledBitmap(file, maxDimension = 2000)
                file.delete()
                if (bitmap != null) {
                    withContext(Dispatchers.Main) { viewModel.addScannedPage(bitmap) }
                }
            }
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        val scansDir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(scansDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureFile = file
        cameraLauncher.launch(uri)
    }

    var scanTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ADD TO VOXLEAF", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bring a page\nwith you.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(8.dp))
                Text("TXT, EPUB, DOCX, text-based PDF, or a camera scan — all stay private on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (importState) {
                    is ImportState.Idle, is ImportState.Success -> {
                        if (capturedPages.isEmpty()) {
                            Button(
                                // Kindle and FictionBook files have no reliable MIME type on Android —
                                // most providers report octet-stream — so those are matched by
                                // extension after picking rather than filtered here.
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
                                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Carbon),
                                modifier = Modifier.fillMaxWidth().testTag("import_file_button")
                            ) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose a document")
                            }
                            OutlinedButton(
                                onClick = { launchCamera() },
                                modifier = Modifier.fillMaxWidth().testTag("scan_document_button")
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan document")
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(capturedPages) { page ->
                                    Image(
                                        bitmap = page.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = scanTitle,
                                onValueChange = { scanTitle = it },
                                label = { Text("Document title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("scan_title_input")
                            )
                            Button(
                                onClick = { launchCamera() },
                                modifier = Modifier.fillMaxWidth().testTag("scan_add_page_button")
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add another page")
                            }
                            Button(
                                onClick = { viewModel.finishScan(scanTitle) },
                                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Carbon),
                                modifier = Modifier.fillMaxWidth().testTag("scan_finish_button")
                            ) {
                                Text("Finish scan (${capturedPages.size} page${if (capturedPages.size == 1) "" else "s"})")
                            }
                            TextButton(onClick = { viewModel.clearScannedPages() }) {
                                Text("Cancel scan")
                            }
                        }
                    }
                    is ImportState.Importing -> {
                        val progress = (importState as ImportState.Importing).progress
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(48.dp)
                        )
                        Text("Importing... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    is ImportState.Error -> {
                        val error = (importState as ImportState.Error).message
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.testTag("import_retry_button")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (_: Exception) {
        null
    }
}

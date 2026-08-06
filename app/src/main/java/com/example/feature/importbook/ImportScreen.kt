package com.example.feature.importbook

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.usecase.ImportState

@Composable
fun ImportScreen(
    onNavigateToBookDetails: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // When import completes, navigate and reset
    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            val bookId = (importState as ImportState.Success).bookId
            onNavigateToBookDetails(bookId)
            viewModel.resetState()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Import Text Document", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Select a plain-text (.txt) file from your device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (importState) {
                    is ImportState.Idle, is ImportState.Success -> {
                        Button(
                            onClick = { launcher.launch(arrayOf("text/plain")) },
                            modifier = Modifier.testTag("import_file_button")
                        ) {
                            Text("Select text file")
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

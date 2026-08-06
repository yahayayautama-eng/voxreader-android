package com.example.feature.modelsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.LoadingState

@Composable
fun ModelSetupScreen(
    onNavigateToLibrary: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelSetupScreenContent(
        uiState = uiState,
        onAction = { action ->
            if (action == ModelSetupUiAction.OnContinue) {
                onNavigateToLibrary()
            } else {
                viewModel.handleAction(action)
            }
        }
    )
}

@Composable
fun ModelSetupScreenContent(
    uiState: ModelSetupUiState,
    onAction: (ModelSetupUiAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("On-Device Neural Model Engine", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("ONNX Runtime & Local Speech Models", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "VoxLeaf local voice models provide offline neural text-to-speech synthesis. Your books are read aloud entirely on your device. Imported content stays private and never leaves your device. Internet is only needed when you explicitly download a voice model.",
                    style = MaterialTheme.typography.bodyMedium
                )

                when (uiState) {
                    is ModelSetupUiState.Loading -> {
                        LoadingState(modifier = Modifier.height(100.dp))
                    }
                    is ModelSetupUiState.ReadyToDownload -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Size: ${uiState.modelSizeMB} MB", style = MaterialTheme.typography.labelLarge)
                        }
                        
                        Button(
                            onClick = { onAction(ModelSetupUiAction.OnDownloadStart) },
                            modifier = Modifier.fillMaxWidth().testTag("download_model_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Local Voice Model")
                        }
                    }
                    is ModelSetupUiState.Downloading -> {
                        Column {
                            Text(uiState.statusMessage, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        OutlinedButton(
                            onClick = { onAction(ModelSetupUiAction.OnDownloadPause) },
                            modifier = Modifier.fillMaxWidth().testTag("pause_download_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pause Download")
                        }
                    }
                    is ModelSetupUiState.Paused -> {
                        Column {
                            Text("Download Paused", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Button(
                            onClick = { onAction(ModelSetupUiAction.OnDownloadResume) },
                            modifier = Modifier.fillMaxWidth().testTag("resume_download_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume Download")
                        }
                    }
                    is ModelSetupUiState.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.message, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                        
                        Button(
                            onClick = { onAction(ModelSetupUiAction.OnDownloadRetry) },
                            modifier = Modifier.fillMaxWidth().testTag("retry_download_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Download")
                        }
                    }
                    is ModelSetupUiState.Offline -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.message, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                        
                        Button(
                            onClick = { onAction(ModelSetupUiAction.OnDownloadRetry) },
                            modifier = Modifier.fillMaxWidth().testTag("retry_offline_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Connection")
                        }
                    }
                    is ModelSetupUiState.InsufficientStorage -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Not enough storage. Required: ${uiState.requiredSizeMB} MB, Available: ${uiState.availableSizeMB} MB.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is ModelSetupUiState.Ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Neural Model Ready!", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50))
                        }
                        
                        Button(
                            onClick = { onAction(ModelSetupUiAction.OnContinue) },
                            modifier = Modifier.fillMaxWidth().testTag("proceed_to_library_button")
                        ) {
                            Text("Continue to Library")
                        }
                    }
                }
            }
        }
        
        // Developer tools for simulation
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Developer Simulation Controls (Fake)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onAction(ModelSetupUiAction.OnSimulateReady) }) {
                        Text("Simulate Returning User")
                    }
                    TextButton(onClick = { onAction(ModelSetupUiAction.OnSimulateOffline) }) {
                        Text("Simulate Offline")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onAction(ModelSetupUiAction.OnSimulateInsufficientStorage) }) {
                        Text("Simulate No Storage")
                    }
                    TextButton(onClick = { onAction(ModelSetupUiAction.OnSimulateFailed) }) {
                        Text("Simulate Failure")
                    }
                }
            }
        }
    }
}

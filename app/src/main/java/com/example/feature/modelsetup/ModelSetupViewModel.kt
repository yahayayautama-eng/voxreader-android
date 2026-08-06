package com.example.feature.modelsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelSetupViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ModelSetupUiState>(ModelSetupUiState.Loading)
    val uiState: StateFlow<ModelSetupUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var currentProgress = 0f

    init {
        // Initial state: ready to download for the first time
        _uiState.value = ModelSetupUiState.ReadyToDownload(modelSizeMB = 45)
    }

    fun handleAction(action: ModelSetupUiAction) {
        when (action) {
            ModelSetupUiAction.OnDownloadStart -> startFakeDownload()
            ModelSetupUiAction.OnDownloadPause -> pauseFakeDownload()
            ModelSetupUiAction.OnDownloadResume -> resumeFakeDownload()
            ModelSetupUiAction.OnDownloadRetry -> {
                currentProgress = 0f
                startFakeDownload()
            }
            ModelSetupUiAction.OnContinue -> {
                // Handled in UI layer via navigation
            }
            ModelSetupUiAction.OnSimulateOffline -> {
                downloadJob?.cancel()
                _uiState.value = ModelSetupUiState.Offline()
            }
            ModelSetupUiAction.OnSimulateInsufficientStorage -> {
                downloadJob?.cancel()
                _uiState.value = ModelSetupUiState.InsufficientStorage(requiredSizeMB = 45, availableSizeMB = 12)
            }
            ModelSetupUiAction.OnSimulateFailed -> {
                downloadJob?.cancel()
                _uiState.value = ModelSetupUiState.Failed("Connection lost during download.")
            }
            ModelSetupUiAction.OnSimulateReady -> {
                downloadJob?.cancel()
                _uiState.value = ModelSetupUiState.Ready
            }
        }
    }

    private fun startFakeDownload() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.value = ModelSetupUiState.Downloading(currentProgress, "Starting download...")
            delay(500)
            
            while (currentProgress < 1.0f) {
                currentProgress += 0.05f
                if (currentProgress >= 1.0f) {
                    currentProgress = 1.0f
                }
                
                val status = if (currentProgress < 0.9f) {
                    "Downloading neural voice weights... ${(currentProgress * 100).toInt()}%"
                } else {
                    "Optimizing local ONNX tensor graph..."
                }
                
                _uiState.value = ModelSetupUiState.Downloading(currentProgress, status)
                delay(200)
            }
            
            _uiState.value = ModelSetupUiState.Ready
        }
    }

    private fun pauseFakeDownload() {
        downloadJob?.cancel()
        _uiState.value = ModelSetupUiState.Paused(currentProgress)
    }

    private fun resumeFakeDownload() {
        startFakeDownload()
    }
}

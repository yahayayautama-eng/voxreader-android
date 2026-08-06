package com.example.feature.modelsetup

sealed interface ModelSetupUiState {
    data object Loading : ModelSetupUiState
    
    // Returning user where model is already downloaded
    data object Ready : ModelSetupUiState
    
    // Network error or offline
    data class Offline(val message: String = "No internet connection. Internet is needed only to download the voice model.") : ModelSetupUiState
    
    // Insufficient storage
    data class InsufficientStorage(val requiredSizeMB: Int, val availableSizeMB: Int) : ModelSetupUiState
    
    // Ready to download
    data class ReadyToDownload(val modelSizeMB: Int) : ModelSetupUiState
    
    // Downloading
    data class Downloading(val progress: Float, val statusMessage: String) : ModelSetupUiState
    
    // Paused download
    data class Paused(val progress: Float) : ModelSetupUiState
    
    // Download failed
    data class Failed(val message: String) : ModelSetupUiState
}

sealed interface ModelSetupUiAction {
    data object OnDownloadStart : ModelSetupUiAction
    data object OnDownloadPause : ModelSetupUiAction
    data object OnDownloadResume : ModelSetupUiAction
    data object OnDownloadRetry : ModelSetupUiAction
    data object OnContinue : ModelSetupUiAction
    data object OnSimulateOffline : ModelSetupUiAction
    data object OnSimulateInsufficientStorage : ModelSetupUiAction
    data object OnSimulateFailed : ModelSetupUiAction
    data object OnSimulateReady : ModelSetupUiAction
}

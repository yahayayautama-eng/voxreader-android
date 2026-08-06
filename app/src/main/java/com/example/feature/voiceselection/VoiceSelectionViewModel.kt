package com.example.feature.voiceselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.datastore.AppSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceSelectionUiState(
    val selectedVoiceName: String = "Puck",
    val speechRate: Float = 1.0f,
    val availableVoices: List<String> = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
)

@HiltViewModel
class VoiceSelectionViewModel @Inject constructor(
    private val appSettingsManager: AppSettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceSelectionUiState())
    val uiState: StateFlow<VoiceSelectionUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            appSettingsManager.ttsVoiceFlow.collect { voice ->
                _uiState.update { it.copy(selectedVoiceName = voice) }
            }
        }
        viewModelScope.launch {
            appSettingsManager.ttsRateFlow.collect { rate ->
                _uiState.update { it.copy(speechRate = rate) }
            }
        }
    }
    
    fun setVoice(voice: String) {
        viewModelScope.launch {
            appSettingsManager.setTtsVoice(voice)
        }
    }
    
    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            appSettingsManager.setTtsRate(rate)
        }
    }
}

package com.voxleaf.reader.feature.voiceselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.tts.EngineId
import com.voxleaf.reader.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSelectionViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val settings: AppSettingsManager
) : ViewModel() {

    /** TtsManager is the single source of truth for voices/engine now — no separate copy to drift. */
    val uiState = ttsManager.state
    val favoriteVoiceIds = settings.favoriteVoiceIdsFlow
    val recentVoiceIds = settings.recentVoiceIdsFlow

    fun setEngine(id: EngineId) = ttsManager.setEngine(id)

    fun setVoice(voicePath: String) {
        ttsManager.setVoice(voicePath)
        viewModelScope.launch { settings.markVoiceRecent(voicePath) }
    }

    fun toggleFavorite(voicePath: String) = viewModelScope.launch { settings.toggleFavoriteVoice(voicePath) }

    fun setSpeechRate(rate: Float) = ttsManager.setSpeechRate(rate)
}

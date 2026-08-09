package com.example.feature.voiceselection

import androidx.lifecycle.ViewModel
import com.example.tts.EngineId
import com.example.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VoiceSelectionViewModel @Inject constructor(
    private val ttsManager: TtsManager
) : ViewModel() {

    /** TtsManager is the single source of truth for voices/engine now — no separate copy to drift. */
    val uiState = ttsManager.state

    fun setEngine(id: EngineId) = ttsManager.setEngine(id)

    fun setVoice(voicePath: String) = ttsManager.setVoice(voicePath)

    fun setSpeechRate(rate: Float) = ttsManager.setSpeechRate(rate)
}

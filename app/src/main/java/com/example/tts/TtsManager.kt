package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class TtsState(
    val isInitialized: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val selectedVoiceName: String? = null,
    val availableVoices: List<String> = emptyList()
)

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentSentences: List<String> = emptyList()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            val voices = tts?.voices?.map { it.name } ?: emptyList()
            val currentVoice = tts?.voice?.name
            _state.update {
                it.copy(
                    isInitialized = true,
                    availableVoices = voices,
                    selectedVoiceName = currentVoice
                )
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = utteranceId?.toIntOrNull() ?: return
                    _state.update {
                        it.copy(
                            isSpeaking = true,
                            isPaused = false,
                            currentSentenceIndex = index
                        )
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val index = utteranceId?.toIntOrNull() ?: return
                    if (index >= currentSentences.size - 1) {
                        _state.update {
                            it.copy(
                                isSpeaking = false,
                                isPaused = false
                            )
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _state.update {
                        it.copy(isSpeaking = false)
                    }
                }
            })
        }
    }

    fun speakSentences(sentences: List<String>, startIndex: Int = 0) {
        if (tts == null || !_state.value.isInitialized) return
        currentSentences = sentences
        _state.update {
            it.copy(
                totalSentences = sentences.size,
                currentSentenceIndex = startIndex,
                isSpeaking = true,
                isPaused = false
            )
        }

        tts?.stop()
        for (i in startIndex until sentences.size) {
            val sentence = sentences[i]
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, i.toString())
            tts?.speak(sentence, TextToSpeech.QUEUE_ADD, params, i.toString())
        }
    }

    fun pause() {
        if (tts != null && _state.value.isSpeaking) {
            tts?.stop()
            _state.update {
                it.copy(isSpeaking = false, isPaused = true)
            }
        }
    }

    fun resume() {
        if (_state.value.isPaused && currentSentences.isNotEmpty()) {
            speakSentences(currentSentences, _state.value.currentSentenceIndex)
        }
    }

    fun stop() {
        tts?.stop()
        _state.update {
            it.copy(
                isSpeaking = false,
                isPaused = false,
                currentSentenceIndex = 0
            )
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
        _state.update { it.copy(speechRate = rate) }
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
        _state.update { it.copy(pitch = pitch) }
    }

    fun setVoice(voiceName: String) {
        val targetVoice = tts?.voices?.find { it.name == voiceName }
        if (targetVoice != null) {
            tts?.voice = targetVoice
            _state.update { it.copy(selectedVoiceName = voiceName) }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

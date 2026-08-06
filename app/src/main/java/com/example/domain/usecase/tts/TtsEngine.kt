package com.example.domain.usecase.tts

import com.example.domain.model.tts.ModelState
import com.example.domain.model.tts.SynthesisRequest
import com.example.domain.model.tts.SynthesisResult
import com.example.domain.model.tts.TtsVoice
import kotlinx.coroutines.flow.Flow

interface TtsEngine : AutoCloseable {
    val modelState: Flow<ModelState>
    suspend fun getAvailableVoices(): List<TtsVoice>
    suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult>
}

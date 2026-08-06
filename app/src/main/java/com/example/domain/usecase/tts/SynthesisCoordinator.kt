package com.example.domain.usecase.tts

import com.example.domain.model.tts.SynthesisRequest
import com.example.domain.model.tts.SynthesisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class SynthesisTask(
    val index: Int,
    val text: String,
    val generationId: Long
)

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Ready(val index: Int, val audioFile: File, val generationId: Long) : PlaybackState()
}

class SynthesisCoordinator(
    private val ttsEngine: TtsEngine,
    private val cacheManager: TtsCacheManager,
    private val coordinatorScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var currentGenerationId = 0L
    private var synthesisJob: Job? = null
    
    // Bounded queue of synthesized results ready to play
    private val readyQueue = Channel<Pair<SynthesisTask, SynthesisResult>>(capacity = 3)
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val mutex = Mutex()
    private val modelVersion = "1.0.0"

    suspend fun start(chunks: List<String>, startIndex: Int, voiceId: String, speed: Float) {
        mutex.withLock {
            currentGenerationId++
            synthesisJob?.cancel()
            
            // Clear channel
            while (readyQueue.tryReceive().isSuccess) { }
            
            val generation = currentGenerationId
            
            synthesisJob = coordinatorScope.launch {
                for (i in startIndex until chunks.size) {
                    if (currentGenerationId != generation) break
                    val text = chunks[i]
                    val task = SynthesisTask(i, text, generation)
                    
                    val key = cacheManager.generateKey(text, voiceId, speed, modelVersion)
                    val cachedFile = cacheManager.getCachedAudio(key)
                    
                    val result = if (cachedFile != null) {
                        SynthesisResult(
                            audioFilePath = cachedFile.absolutePath,
                            sampleRate = 22050,
                            audioDurationMs = 0L,
                            inferenceTimeMs = 0L,
                            realTimeFactor = 0f,
                            tokenCount = 0,
                            memoryUsedMb = 0f
                        )
                    } else {
                        val request = SynthesisRequest(text, voiceId, speed)
                        val res = ttsEngine.synthesize(request).getOrElse {
                            if (it is CancellationException) throw it
                            continue
                        }
                        
                        val tempFile = File(res.audioFilePath)
                        val cached = cacheManager.storeAudio(key, tempFile.readBytes())
                        tempFile.delete()
                        
                        if (cached != null) {
                            res.copy(audioFilePath = cached.absolutePath)
                        } else {
                            continue
                        }
                    }
                    
                    if (currentGenerationId == generation) {
                        readyQueue.send(task to result)
                    }
                }
            }
        }
    }

    suspend fun waitForNextReadyChunk(generation: Long): Pair<SynthesisTask, SynthesisResult>? {
        val next = readyQueue.receiveCatching().getOrNull()
        if (next != null && next.first.generationId == generation && next.first.generationId == currentGenerationId) {
            _playbackState.value = PlaybackState.Ready(next.first.index, File(next.second.audioFilePath), generation)
            return next
        }
        return null
    }
    
    fun getCurrentGenerationId(): Long = currentGenerationId

    fun stop() {
        synthesisJob?.cancel()
        _playbackState.value = PlaybackState.Idle
    }
}

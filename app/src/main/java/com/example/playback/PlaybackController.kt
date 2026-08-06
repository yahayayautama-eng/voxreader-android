package com.example.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentChunkIndex: Int = 0,
    val speed: Float = 1.0f
)

/**
 * Stage-0 state holder. It keeps reader controls buildable until the dedicated
 * Media3 playback milestone supplies a real controller and service.
 */
@Singleton
class PlaybackController @Inject constructor() {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(bookId: String, chapterIndex: Int, chunkIndex: Int, speed: Float, voiceId: String) {
        _playbackState.value = PlaybackState(isPlaying = true, currentChunkIndex = chunkIndex, speed = speed)
    }

    fun pause() {
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun stop() {
        _playbackState.value = PlaybackState(speed = _playbackState.value.speed)
    }

    fun setSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(speed = speed)
    }
}

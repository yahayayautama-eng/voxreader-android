package com.example.core.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.TtsManager
import com.example.ui.theme.SignalOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GlobalPlayerViewModel @Inject constructor(
    private val ttsManager: TtsManager
) : ViewModel() {
    val state = ttsManager.state

    fun playPause() = ttsManager.togglePlayback()

    fun skip(delta: Int) = ttsManager.skip(delta)
}

/**
 * Stable playback bar shown outside the Reader. Voice, engine, and speed remain in Settings;
 * this bar only exposes navigation and playback controls.
 */
@Composable
fun GlobalPlayerBar(
    onNavigateToReader: (bookId: String, chapterIndex: Int, sentenceIndex: Int) -> Unit,
    viewModel: GlobalPlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying = state.nowPlaying ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onNavigateToReader(
                    nowPlaying.bookId,
                    nowPlaying.chapterIndex,
                    state.currentSentenceIndex
                )
            }
            .testTag("global_player_bar")
    ) {
        Column {
            LinearProgressIndicator(
                progress = { (state.currentSentenceIndex + 1).toFloat() / state.totalSentences.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = SignalOrange,
                trackColor = Color.Transparent
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.bookTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            state.isPreparing -> "Preparing…"
                            state.errorMessage != null -> state.errorMessage.orEmpty()
                            else -> nowPlaying.chapterTitle
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.skip(-1) }) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous sentence")
                }
                Surface(
                    shape = CircleShape,
                    color = SignalOrange,
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { viewModel.playPause() }
                        .testTag("global_player_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isSpeaking || state.isPreparing) {
                                Icons.Outlined.Pause
                            } else {
                                Icons.Outlined.PlayArrow
                            },
                            contentDescription = if (state.isSpeaking) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.skip(+1) }) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "Next sentence")
                }
            }
        }
    }
}

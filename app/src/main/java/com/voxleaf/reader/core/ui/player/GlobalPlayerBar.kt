package com.voxleaf.reader.core.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import com.voxleaf.reader.tts.TtsManager
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
    viewModel: GlobalPlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying = state.nowPlaying ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
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
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.bookTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.skip(-1) }) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous sentence",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.playPause() }
                        .testTag("global_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (state.isSpeaking || state.isPreparing) {
                            Icons.Outlined.Pause
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = if (state.isSpeaking) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { viewModel.skip(+1) }) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = "Next sentence",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

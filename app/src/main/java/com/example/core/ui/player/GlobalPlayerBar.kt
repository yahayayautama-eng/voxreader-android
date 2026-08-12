package com.example.core.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.EngineId
import com.example.tts.TtsManager
import com.example.tts.TtsState
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

    fun stop() = ttsManager.stop()

    fun selectEngine(id: EngineId) = ttsManager.setEngine(id)

    fun selectVoice(voicePath: String) = ttsManager.setVoice(voicePath)

    fun setRate(rate: Float) = ttsManager.setSpeechRate(rate)
}

/**
 * Persistent playback bar shown on every screen except the Reader itself (which already has its own
 * full-featured player). Tapping the row returns to the book at the live TTS position; the voice
 * button opens a picker sheet in place, without leaving whatever screen the listener is on.
 */
@Composable
fun GlobalPlayerBar(
    onNavigateToReader: (bookId: String, chapterIndex: Int, sentenceIndex: Int) -> Unit,
    viewModel: GlobalPlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showVoiceSheet by remember { mutableStateOf(false) }
    val nowPlaying = state.nowPlaying

    AnimatedVisibility(
        visible = nowPlaying != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        if (nowPlaying != null) {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isConvertingAudiobook) {
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
                                        state.isConvertingAudiobook -> state.errorMessage ?: "Creating audiobook before playback starts"
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
                            IconButton(
                                onClick = { showVoiceSheet = true },
                                modifier = Modifier.testTag("global_player_voice_button")
                            ) {
                                Icon(
                                    Icons.Outlined.RecordVoiceOver,
                                    contentDescription = "Change voice",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.skip(-1) }) {
                                Icon(
                                    Icons.Outlined.SkipPrevious,
                                    contentDescription = "Previous sentence",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = SignalOrange,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable(enabled = !state.isConvertingAudiobook) { viewModel.playPause() }
                                    .testTag("global_player_play_pause")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isSpeaking || (state.isPreparing && !state.isConvertingAudiobook)) {
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
                                Icon(
                                    Icons.Outlined.SkipNext,
                                    contentDescription = "Next sentence",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVoiceSheet && nowPlaying != null) {
        VoicePickerSheet(
            state = state,
            onSelectEngine = viewModel::selectEngine,
            onSelectVoice = viewModel::selectVoice,
            onSetRate = viewModel::setRate,
            onStop = { viewModel.stop(); showVoiceSheet = false },
            onDismiss = { showVoiceSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    state: TtsState,
    onSelectEngine: (EngineId) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSetRate: (Float) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onStop, modifier = Modifier.testTag("global_player_stop")) {
                    Icon(Icons.Outlined.Close, contentDescription = "Stop playback")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(EngineId.OFFLINE to "Offline", EngineId.EDGE to "Edge TTS").forEach { (id, label) ->
                    val selected = state.engineId == id
                    Surface(
                        color = if (selected) SignalOrange.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        onClick = { onSelectEngine(id) },
                        modifier = Modifier.weight(1f).testTag("global_player_engine_${id.storageKey}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) SignalOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Speed  " + ("%.2f".format(state.speechRate).trimEnd('0').trimEnd('.')) + "×",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                    val selected = rate == state.speechRate
                    Surface(
                        color = if (selected) SignalOrange.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetRate(rate) }
                            .testTag("global_player_rate_$rate")
                    ) {
                        Text(
                            text = "${rate}×".removeSuffix(".0×") + if (rate.toString().endsWith(".0")) "×" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) SignalOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
            if (state.engineId == EngineId.EDGE) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search voices…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("global_player_voice_search")
                )
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            when {
                state.isLoadingVoices -> Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) { androidx.compose.material3.CircularProgressIndicator() }

                state.errorMessage != null && state.availableVoices.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    val filtered = remember(state.availableVoices, query) {
                        if (query.isBlank()) {
                            state.availableVoices
                        } else {
                            state.availableVoices.filter {
                                it.displayName.contains(query, ignoreCase = true) || it.locale.contains(query, ignoreCase = true)
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filtered, key = { it.id }) { voice ->
                            val isSelected = voice.id == state.selectedVoicePath
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectVoice(voice.id) }
                                    .padding(vertical = 12.dp)
                                    .testTag("global_player_voice_${voice.id}")
                            ) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = SignalOrange)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

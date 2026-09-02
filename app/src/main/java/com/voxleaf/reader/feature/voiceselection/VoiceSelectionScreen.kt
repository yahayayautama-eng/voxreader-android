package com.voxleaf.reader.feature.voiceselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxleaf.reader.tts.EngineId

@Composable
fun VoiceSelectionScreen(
    viewModel: VoiceSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteVoiceIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val recentIds by viewModel.recentVoiceIds.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var voiceFilter by remember { mutableStateOf(VoiceFilter.ALL) }
    val filtered = remember(uiState.availableVoices, query, favoriteIds, recentIds, voiceFilter) {
        uiState.availableVoices.asSequence()
            .filter {
                query.isBlank() || it.displayName.contains(query, ignoreCase = true) ||
                    it.locale.contains(query, ignoreCase = true)
            }
            .filter {
                when (voiceFilter) {
                    VoiceFilter.ALL -> true
                    VoiceFilter.FAVORITES -> it.id in favoriteIds
                    VoiceFilter.RECENT -> it.id in recentIds
                }
            }
            .sortedWith(
                compareBy<com.voxleaf.reader.tts.EngineVoice> { if (it.id in favoriteIds) 0 else 1 }
                    .thenBy { recentIds.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.displayName }
            )
            .toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "VOICE ENGINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (uiState.engineId == EngineId.OFFLINE) {
                        "Bundled neural speech synthesis — no internet, nothing leaves this device"
                    } else {
                        "Microsoft Edge's online voices — the read text is sent to Microsoft's servers"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            EngineChip(
                label = "Offline",
                selected = uiState.engineId == EngineId.OFFLINE,
                onClick = { viewModel.setEngine(EngineId.OFFLINE) },
                testTag = "engine_chip_offline",
                modifier = Modifier.weight(1f)
            )
            EngineChip(
                label = "Edge TTS (online)",
                selected = uiState.engineId == EngineId.EDGE,
                onClick = { viewModel.setEngine(EngineId.EDGE) },
                testTag = "engine_chip_edge",
                modifier = Modifier.weight(1f)
            )
        }

        // Voice Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech Rate",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${"%.2f".format(uiState.speechRate)}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rate ->
                            val selected = kotlin.math.abs(uiState.speechRate - rate) < 0.05f
                            Surface(
                                onClick = { viewModel.setSpeechRate(rate) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) com.voxleaf.reader.ui.theme.SkyPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) com.voxleaf.reader.ui.theme.SkyPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "${rate}x",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) com.voxleaf.reader.ui.theme.SkyPrimary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.engineId == EngineId.EDGE) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search 400+ voices by name or language…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("voice_search_field")
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            VoiceFilter.entries.forEach { filter ->
                EngineChip(
                    label = stringResource(
                        when (filter) {
                            VoiceFilter.ALL -> com.voxleaf.reader.R.string.voice_filter_all
                            VoiceFilter.FAVORITES -> com.voxleaf.reader.R.string.voice_filter_favorites
                            VoiceFilter.RECENT -> com.voxleaf.reader.R.string.voice_filter_recent
                        }
                    ),
                    selected = voiceFilter == filter,
                    onClick = { voiceFilter = filter },
                    testTag = "voice_filter_${filter.name.lowercase()}",
                    modifier = Modifier.width(116.dp)
                )
            }
        }

        Text(
            text = stringResource(com.voxleaf.reader.R.string.voice_available_count, filtered.size),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        when {
            uiState.isLoadingVoices -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.errorMessage != null && uiState.availableVoices.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(com.voxleaf.reader.R.string.voice_no_matches))
                    }
                } else LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { voice ->
                        val isSelected = voice.id == uiState.selectedVoicePath
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("voice_card_${voice.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setVoice(voice.id) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = voice.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(voice.locale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(voice.id) }) {
                                    Icon(
                                        if (voice.id in favoriteIds) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                        contentDescription = stringResource(
                                            if (voice.id in favoriteIds) com.voxleaf.reader.R.string.voice_remove_favorite
                                            else com.voxleaf.reader.R.string.voice_add_favorite,
                                            voice.displayName
                                        ),
                                        tint = if (voice.id in favoriteIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class VoiceFilter { ALL, FAVORITES, RECENT }

@Composable
private fun EngineChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
    }
}

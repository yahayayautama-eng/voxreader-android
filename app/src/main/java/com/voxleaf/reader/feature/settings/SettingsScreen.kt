package com.voxleaf.reader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.BuildConfig
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.ui.theme.Carbon
import com.voxleaf.reader.ui.theme.PaleGreen
import com.voxleaf.reader.ui.theme.SignalOrange
import com.voxleaf.reader.ui.theme.TextTertiary
import com.voxleaf.reader.ui.theme.VoxLeafSerif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettingsManager
) : ViewModel() {

    val autoPlay: StateFlow<Boolean> = settings.autoPlayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highlightSentences: StateFlow<Boolean> = settings.highlightSentencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val readerTheme: StateFlow<String> = settings.readerThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NIGHT")

    val readerFontFamily: StateFlow<String> = settings.readerFontFamilyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SERIF")

    val readerFontSize: StateFlow<Int> = settings.readerFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18)

    val readerLineSpacing: StateFlow<Float> = settings.readerLineSpacingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.75f)

    fun setAutoPlay(enabled: Boolean) = viewModelScope.launch { settings.setAutoPlay(enabled) }

    fun setHighlightSentences(enabled: Boolean) =
        viewModelScope.launch { settings.setHighlightSentences(enabled) }

    fun setReaderTheme(theme: String) = viewModelScope.launch { settings.setReaderTheme(theme) }

    fun setReaderFontFamily(family: String) = viewModelScope.launch { settings.setReaderFontFamily(family) }

    fun setReaderFontSize(size: Int) = viewModelScope.launch { settings.setReaderFontSize(size) }

    fun setReaderLineSpacing(spacing: Float) = viewModelScope.launch { settings.setReaderLineSpacing(spacing) }
}

@Composable
fun SettingsScreen(
    onNavigateToVoiceSelection: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
    val highlightSentences by viewModel.highlightSentences.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val readerFontFamily by viewModel.readerFontFamily.collectAsStateWithLifecycle()
    val readerFontSize by viewModel.readerFontSize.collectAsStateWithLifecycle()
    val readerLineSpacing by viewModel.readerLineSpacing.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        com.voxleaf.reader.core.ui.components.AmbientStickerDecorations(alpha = 0.20f)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Settings",
                fontFamily = VoxLeafSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))

            // The privacy promise is the product; Settings is where a sceptical user comes to check it.
            PrivacyCard()
            Spacer(modifier = Modifier.height(26.dp))

            SectionLabel("Reader Appearance")
            SettingsGroup {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Color Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val themeList = listOf(
                        "OLED" to (Color(0xFF000000) to Color(0xFFF1F5F9)),
                        "NIGHT" to (Color(0xFF121316) to Color(0xFFA0AEC0)),
                        "SEPIA" to (Color(0xFFF4ECD8) to Color(0xFF433422)),
                        "IVORY" to (Color(0xFFFAF7EE) to Color(0xFF2C2B29)),
                        "DARK" to (Color(0xFF1E1F22) to Color(0xFFE5E7EB)),
                        "LIGHT" to (Color(0xFFFFFFFF) to Color(0xFF111827))
                    )
                    val chunked = themeList.chunked(3)
                    chunked.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { (name, colors) ->
                                val selected = readerTheme.equals(name, ignoreCase = true)
                                val (bg, fg) = colors
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) SignalOrange else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setReaderTheme(name) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = fg
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Typography",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "SERIF" to ("Serif" to androidx.compose.ui.text.font.FontFamily.Serif),
                            "SANS_SERIF" to ("Sans" to androidx.compose.ui.text.font.FontFamily.SansSerif),
                            "MONOSPACE" to ("Mono" to androidx.compose.ui.text.font.FontFamily.Monospace)
                        ).forEach { (key, meta) ->
                            val (label, fam) = meta
                            val selected = readerFontFamily.equals(key, ignoreCase = true)
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.setReaderFontFamily(key) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, SignalOrange) else null
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = fam,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Text(
                        text = "Line Spacing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            1.4f to "Compact",
                            1.75f to "Normal",
                            2.1f to "Relaxed"
                        ).forEach { (spacing, label) ->
                            val selected = kotlin.math.abs(readerLineSpacing - spacing) < 0.05f
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.setReaderLineSpacing(spacing) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, SignalOrange) else null
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Text(
                        text = "Default Font Size: $readerFontSize sp",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.IconButton(onClick = { viewModel.setReaderFontSize((readerFontSize - 1).coerceAtLeast(12)) }) {
                            Icon(androidx.compose.material.icons.Icons.Outlined.TextFields, contentDescription = "Decrease Font Size")
                        }
                        androidx.compose.material3.Slider(
                            value = readerFontSize.toFloat(),
                            onValueChange = { viewModel.setReaderFontSize(it.toInt()) },
                            valueRange = 12f..36f,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.IconButton(onClick = { viewModel.setReaderFontSize((readerFontSize + 1).coerceAtMost(36)) }) {
                            Icon(androidx.compose.material.icons.Icons.Outlined.TextFields, contentDescription = "Increase Font Size", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionLabel("Voice")
            SettingsGroup {
                SettingsClickableItem(
                    title = "Voice and speed",
                    subtitle = "Offline neural voices, Edge TTS online voices, and reading speed",
                    icon = Icons.Outlined.RecordVoiceOver,
                    iconColor = com.voxleaf.reader.ui.theme.AzurePrimary,
                    onClick = onNavigateToVoiceSelection,
                    testTag = "settings_voice_item"
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionLabel("Reading")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "Start speaking on open",
                    subtitle = "Begin reading aloud as soon as you open a document",
                    checked = autoPlay,
                    onCheckedChange = viewModel::setAutoPlay,
                    testTag = "settings_autoplay_switch"
                )
                GroupDivider()
                SettingsSwitchItem(
                    title = "Highlight spoken sentence",
                    subtitle = "Tint the sentence being read so you can follow along",
                    checked = highlightSentences,
                    onCheckedChange = viewModel::setHighlightSentences,
                    testTag = "settings_highlight_switch"
                )
                GroupDivider()
                SettingsClickableItem(
                    title = "Listening stats",
                    subtitle = "Daily minutes, streaks, and time per book — all on this device",
                    icon = Icons.Outlined.Insights,
                    iconColor = Color(0xFF10B981),
                    onClick = onNavigateToStats,
                    testTag = "settings_stats_item"
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionLabel("About")
            SettingsGroup {
                SettingsClickableItem(
                    title = "About Vox Reader",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · open source licences",
                    icon = Icons.Outlined.Info,
                    iconColor = com.voxleaf.reader.ui.theme.AzureLight,
                    onClick = onNavigateToAbout,
                    testTag = "settings_about_item"
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacyCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, com.voxleaf.reader.ui.theme.AzureLight.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(com.voxleaf.reader.ui.theme.AzureLight.copy(alpha = 0.15f))
                    .border(1.dp, com.voxleaf.reader.ui.theme.AzureLight.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = com.voxleaf.reader.ui.theme.AzureLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Offline by default",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = com.voxleaf.reader.ui.theme.AzureLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Documents are always parsed on-device. Voicing them stays fully offline too, unless you opt into the Edge TTS online voice in Voice settings — that sends the text being read to Microsoft. Vox Reader has no account of its own either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.08.em,
        color = TextTertiary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = com.voxleaf.reader.ui.theme.AzurePrimary,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f))
                .border(1.dp, iconColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, so the target is the row rather than just the switch.
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Carbon,
                checkedTrackColor = SignalOrange,
                checkedBorderColor = SignalOrange,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

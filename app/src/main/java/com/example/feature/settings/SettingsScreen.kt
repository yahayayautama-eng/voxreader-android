package com.example.feature.settings

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
import com.example.BuildConfig
import com.example.data.local.datastore.AppSettingsManager
import com.example.ui.theme.Carbon
import com.example.ui.theme.PaleGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VoxLeafSerif
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

    fun setAutoPlay(enabled: Boolean) = viewModelScope.launch { settings.setAutoPlay(enabled) }

    fun setHighlightSentences(enabled: Boolean) =
        viewModelScope.launch { settings.setHighlightSentences(enabled) }
}

@Composable
fun SettingsScreen(
    onNavigateToVoiceSelection: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Previously these lived in `remember`, so every toggle reset on navigation; they persist now.
    val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
    val highlightSentences by viewModel.highlightSentences.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
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

            SectionLabel("Voice")
            SettingsGroup {
                SettingsClickableItem(
                    title = "Voice and speed",
                    subtitle = "Offline neural voices, Edge TTS online voices, and reading speed",
                    icon = Icons.Outlined.RecordVoiceOver,
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
            .border(1.dp, PaleGreen.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PaleGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = PaleGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Offline by default",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
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
        shape = RoundedCornerShape(14.dp),
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
        Icon(icon, contentDescription = null, tint = SignalOrange, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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

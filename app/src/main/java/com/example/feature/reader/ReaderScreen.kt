package com.example.feature.reader

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.ErrorState
import com.example.core.ui.components.LoadingState
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    val (bgColor, textColor) = when (uiState.readerTheme) {
        ReaderTheme.LIGHT -> Color(0xFFF8F9FA) to Color(0xFF1A202C)
        ReaderTheme.DARK -> Color(0xFF1A202C) to Color(0xFFE2E8F0)
        ReaderTheme.SEPIA -> Color(0xFFFDF6E3) to Color(0xFF433422)
        ReaderTheme.NIGHT -> Color(0xFF0A0A0C) to Color(0xFFA0AEC0)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.book?.title ?: "Reader",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            maxLines = 1
                        )
                        uiState.currentChapter?.let { chapter ->
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.handleAction(ReaderUiAction.OnGenerateAiSummary) },
                        modifier = Modifier.testTag("ai_summary_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Summary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showBookmarkDialog = true },
                        modifier = Modifier.testTag("add_bookmark_button")
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = "Add Bookmark",
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("reader_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Reader Customization",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        bottomBar = {
            ReaderTtsBottomBar(
                uiState = uiState,
                textColor = textColor,
                bgColor = bgColor,
                isExpanded = uiState.isPlayerExpanded,
                onToggleExpand = { viewModel.handleAction(ReaderUiAction.OnTogglePlayerLayout) },
                onPlayPause = { viewModel.handleAction(ReaderUiAction.OnPlayPauseTts) },
                onPrevChapter = {
                    viewModel.handleAction(ReaderUiAction.OnChangeChapter(uiState.currentChapterIndex - 1))
                },
                onNextChapter = {
                    viewModel.handleAction(ReaderUiAction.OnChangeChapter(uiState.currentChapterIndex + 1))
                },
                onPrevSentence = { viewModel.handleAction(ReaderUiAction.OnPreviousSentence) },
                onNextSentence = { viewModel.handleAction(ReaderUiAction.OnNextSentence) },
                onSkipBack = { viewModel.handleAction(ReaderUiAction.OnSkipBack) },
                onSkipForward = { viewModel.handleAction(ReaderUiAction.OnSkipForward) },
                onToggleSpeed = {
                    val nextRate = when (uiState.ttsRate) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        2.0f -> 0.75f
                        else -> 1.0f
                    }
                    viewModel.handleAction(ReaderUiAction.OnChangeTtsRate(nextRate))
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.errorMessage != null -> ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.loadBook(bookId) })
                uiState.currentChapter != null -> {
                    val scrollState = rememberScrollState()
                    val chapter = uiState.currentChapter!!
                    val sentences = remember(chapter.content) {
                        chapter.content.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        // AI Summary Banner if generated
                        AnimatedVisibility(visible = uiState.aiSummary != null || uiState.isGeneratingAiSummary) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "VoxLeaf AI Insights",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.handleAction(ReaderUiAction.OnDismissAiSummary) }) {
                                            Text("✕", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (uiState.isGeneratingAiSummary) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Generating chapter summary with VoxLeaf AI...")
                                        }
                                    } else uiState.aiSummary?.let { summary ->
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                        // Notification Toast for Bookmark
                        uiState.bookmarkAddedMessage?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        // Chapter Title
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (uiState.fontSizeSp + 4).sp,
                                fontFamily = FontFamily.Serif
                            ),
                            color = textColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        // Paragraphs / Sentences
                        sentences.forEachIndexed { index, sentence ->
                            val isCurrentActiveSentence = uiState.isTtsPlaying && index == uiState.currentSentenceIndex
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = uiState.fontSizeSp.sp,
                                    lineHeight = (uiState.fontSizeSp * 1.5f).sp,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = if (isCurrentActiveSentence) MaterialTheme.colorScheme.primary else textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isCurrentActiveSentence) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        } else Color.Transparent
                                    )
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
            // Reader Theme & Formatting Bottom Sheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Reader Appearance",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        // Theme Options
                        Text(
                            text = "Color Theme",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ReaderTheme.entries.forEach { theme ->
                                val selected = theme == uiState.readerTheme
                                val (bg, fg) = when (theme) {
                                    ReaderTheme.LIGHT -> Color(0xFFF8F9FA) to Color(0xFF1A202C)
                                    ReaderTheme.DARK -> Color(0xFF1A202C) to Color(0xFFE2E8F0)
                                    ReaderTheme.SEPIA -> Color(0xFFFDF6E3) to Color(0xFF433422)
                                    ReaderTheme.NIGHT -> Color(0xFF0A0A0C) to Color(0xFFA0AEC0)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(
                                                alpha = 0.4f
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.handleAction(ReaderUiAction.OnChangeTheme(theme))
                                        }
                                        .testTag("theme_chip_${theme.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = fg
                                    )
                                }
                            }
                        }
                        // Font Size Slider
                        Text(
                            text = "Font Size: ${uiState.fontSizeSp} sp",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = { viewModel.handleAction(ReaderUiAction.OnChangeFontSize(-1)) }) {
                                Icon(Icons.Default.FormatSize, contentDescription = "Decrease Font")
                            }
                            Slider(
                                value = uiState.fontSizeSp.toFloat(),
                                onValueChange = { newSp ->
                                    viewModel.handleAction(ReaderUiAction.OnChangeFontSize(newSp.toInt() - uiState.fontSizeSp))
                                },
                                valueRange = 12f..32f,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.handleAction(ReaderUiAction.OnChangeFontSize(1)) }) {
                                Icon(
                                    Icons.Default.FormatSize,
                                    contentDescription = "Increase Font",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { showSettingsSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
            // Bookmark Dialog
            if (showBookmarkDialog) {
                AlertDialog(
                    onDismissRequest = { showBookmarkDialog = false },
                    title = { Text("Add Bookmark") },
                    text = {
                        Column {
                            Text("Save current location with an optional note:")
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = bookmarkNote,
                                onValueChange = { bookmarkNote = it },
                                label = { Text("Note (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.handleAction(ReaderUiAction.OnAddBookmark(bookmarkNote))
                                bookmarkNote = ""
                                showBookmarkDialog = false
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBookmarkDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
@Composable
fun ReaderTtsBottomBar(
    uiState: ReaderUiState,
    textColor: Color,
    bgColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onToggleSpeed: () -> Unit
) {
    Surface(
        color = bgColor,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Audio progress / Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (uiState.isTtsPlaying) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isTtsPlaying) "Speaking sentence ${uiState.currentSentenceIndex + 1}" else "VoxLeaf Speech Audio",
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speed chip
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable(onClick = onToggleSpeed)
                            .testTag("speed_toggle_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.ttsRate}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                        contentDescription = "Toggle Expand",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevChapter,
                    enabled = uiState.currentChapterIndex > 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Chapter",
                        tint = textColor
                    )
                }
                if (isExpanded) {
                    IconButton(onClick = onSkipBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Skip Back", tint = textColor)
                    }
                    IconButton(onClick = onPrevSentence) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous Sentence", tint = textColor)
                    }
                }
                // Main Play/Pause Fab
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(52.dp)
                        .clickable(onClick = onPlayPause)
                        .testTag("tts_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isTtsPlaying) "Pause Speech" else "Play Speech",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                if (isExpanded) {
                    IconButton(onClick = onNextSentence) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Sentence", tint = textColor)
                    }
                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Skip Forward", tint = textColor)
                    }
                }
                IconButton(
                    onClick = onNextChapter,
                    enabled = uiState.book != null && uiState.currentChapterIndex < (uiState.book.chapters.size - 1)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Chapter",
                        tint = textColor
                    )
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* TODO: Sleep Timer */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Sleep Timer", tint = textColor)
                    }
                    IconButton(onClick = { /* TODO: Voice Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Voice Settings", tint = textColor)
                    }
                    IconButton(onClick = { /* TODO: Bookmark */ }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Bookmark", tint = textColor)
                    }
                }
            }
        }
    }
}

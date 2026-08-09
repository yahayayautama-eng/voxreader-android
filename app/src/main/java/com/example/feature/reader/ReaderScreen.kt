package com.example.feature.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.ErrorState
import com.example.core.ui.components.LoadingState
import com.example.domain.repository.Highlight
import com.example.ui.theme.Carbon
import com.example.ui.theme.HighlightColor
import com.example.ui.theme.Canvas
import com.example.ui.theme.Graphite
import com.example.ui.theme.NightText
import com.example.ui.theme.PaleGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SpineColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    bookmarkChapterIndex: Int? = null,
    bookmarkSentenceIndex: Int? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId, bookmarkChapterIndex, bookmarkSentenceIndex) {
        viewModel.loadBook(bookId, bookmarkChapterIndex, bookmarkSentenceIndex)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showContentsSheet by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    val (bgColor, textColor) = when (uiState.readerTheme) {
        ReaderTheme.LIGHT -> Canvas to Color(0xFF1A202C)
        ReaderTheme.DARK -> Graphite to NightText
        ReaderTheme.SEPIA -> Color(0xFFF4EEDF) to Color(0xFF382F23)
        ReaderTheme.NIGHT -> Carbon to NightText
    }
    Scaffold(
        topBar = {
          Column {
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showContentsSheet = true },
                        modifier = Modifier.testTag("reader_contents_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Toc,
                            contentDescription = "Table of contents",
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = { showBookmarkDialog = true },
                        modifier = Modifier.testTag("add_bookmark_button")
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = "Add Bookmark",
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("reader_settings_button")
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Reader Customization",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
            // The one visual thread tying the reader back to the book's shelf identity.
            uiState.book?.let { book ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(SpineColor.forKey(book.id).fill)
                )
            }
          }
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
                },
                onSleepTimer = { showSleepTimerDialog = true },
                onVoiceSettings = { showSettingsSheet = true },
                onBookmark = { showBookmarkDialog = true }
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
                uiState.errorMessage != null -> ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.loadBook(bookId, bookmarkChapterIndex, bookmarkSentenceIndex) })
                uiState.currentChapter != null -> {
                    val scrollState = rememberScrollState()
                    val chapter = uiState.currentChapter!!
                    val sentences = uiState.textChunks.map { it.text }
                    Column(
                        modifier = Modifier
                            // Prose past ~75 characters a line is hard to track back; centre the measure on tablets.
                            .align(Alignment.TopCenter)
                            .widthIn(max = 680.dp)
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
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
                        FlowingChapterText(
                            sentences = sentences,
                            currentSentenceIndex = uiState.currentSentenceIndex,
                            fontSizeSp = uiState.fontSizeSp,
                            textColor = textColor,
                            isPlaying = uiState.isTtsPlaying,
                            highlights = uiState.chapterHighlights,
                            scrollState = scrollState,
                            onSeekToSentence = { viewModel.handleAction(ReaderUiAction.OnSeekToSentence(it)) },
                            onMarkSentence = { viewModel.handleAction(ReaderUiAction.OnStartMarking(it)) }
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
            // Reader Theme & Formatting Bottom Sheet
            if (showContentsSheet) {
                ModalBottomSheet(onDismissRequest = { showContentsSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.72f)
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "Contents",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${uiState.book?.chapters?.size ?: 0} chapters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(uiState.book?.chapters.orEmpty()) { index, chapter ->
                                val isCurrentChapter = index == uiState.currentChapterIndex
                                Surface(
                                    color = if (isCurrentChapter) {
                                        SignalOrange.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            viewModel.handleAction(ReaderUiAction.OnChangeChapter(index))
                                            showContentsSheet = false
                                        }
                                        .testTag("contents_chapter_$index")
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                                        Text(
                                            text = "${index + 1}".padStart(2, '0'),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isCurrentChapter) SignalOrange else MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                                Icon(Icons.Outlined.TextFields, contentDescription = "Decrease Font")
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
                                    Icons.Outlined.TextFields,
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
            uiState.markingSentenceIndex?.let { sentenceIndex ->
                HighlightSheet(
                    sentence = uiState.textChunks.getOrNull(sentenceIndex)?.text.orEmpty(),
                    existing = uiState.chapterHighlights[sentenceIndex],
                    onSave = { colorIndex, note ->
                        viewModel.handleAction(ReaderUiAction.OnSaveHighlight(colorIndex, note))
                    },
                    onRemove = { viewModel.handleAction(ReaderUiAction.OnRemoveHighlight(sentenceIndex)) },
                    onDismiss = { viewModel.handleAction(ReaderUiAction.OnDismissMarking) }
                )
            }
            if (showSleepTimerDialog) {
                AlertDialog(
                    onDismissRequest = { showSleepTimerDialog = false },
                    title = { Text("Sleep timer") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Stop the offline voice after:")
                            listOf(5, 10, 15, 30).forEach { minutes ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.handleAction(ReaderUiAction.OnSleepTimer(minutes))
                                        showSleepTimerDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("$minutes minutes") }
                            }
                            if (uiState.sleepTimerMinutes != null) {
                                TextButton(onClick = {
                                    viewModel.handleAction(ReaderUiAction.OnSleepTimer(null))
                                    showSleepTimerDialog = false
                                }) { Text("Turn off timer") }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSleepTimerDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

/**
 * Marker sheet for one passage. Colour first, note second: picking a colour is the whole interaction
 * for most marks, and demanding a note before saving would make the common case the slow one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightSheet(
    sentence: String,
    existing: Highlight?,
    onSave: (colorIndex: Int, note: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember(existing) { mutableStateOf(existing?.colorIndex ?: 0) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "Highlight passage" else "Edit highlight",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEachIndexed { index, color ->
                    val selected = index == selectedColor
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color.fill.copy(alpha = if (selected) 1f else 0.55f))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = index }
                            .testTag("highlight_color_$index")
                    ) {
                        if (selected) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = Carbon
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null) {
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f).testTag("remove_highlight_button")
                    ) { Text("Remove") }
                }
                Button(
                    onClick = { onSave(selectedColor, note) },
                    modifier = Modifier.weight(1f).testTag("save_highlight_button")
                ) { Text("Save") }
            }
        }
    }
}

/**
 * Chapter body as continuous prose with the spoken sentence highlighted in place. Rendering one Text
 * per sentence breaks the paragraph into stacked blocks; a single annotated string keeps it reading
 * like a book while the tint tracks [currentSentenceIndex].
 */
@Composable
private fun FlowingChapterText(
    sentences: List<String>,
    currentSentenceIndex: Int,
    fontSizeSp: Int,
    textColor: Color,
    isPlaying: Boolean,
    highlights: Map<Int, Highlight>,
    scrollState: ScrollState,
    onSeekToSentence: (Int) -> Unit,
    onMarkSentence: (Int) -> Unit
) {
    // Sentence n occupies [starts[n], starts[n+1]) in the joined text; used for both tint and tap mapping.
    val starts = remember(sentences) {
        var cursor = 0
        sentences.map { sentence ->
            val start = cursor
            cursor += sentence.length + 1
            start
        }
    }
    // Dimming every sentence but one is a narration aid, not a reading one — someone reading silently
    // needs full-contrast prose, not a page that looks mostly greyed out around a single highlight.
    val annotated = remember(sentences, currentSentenceIndex, textColor, isPlaying, highlights) {
        buildAnnotatedString {
            sentences.forEachIndexed { index, sentence ->
                val current = isPlaying && index == currentSentenceIndex
                // The spoken tint is transient and the marker is permanent, so the spoken sentence
                // takes the tint back only while it is actually being read aloud.
                val marker = highlights[index]?.let { HighlightColor.at(it.colorIndex).fill.copy(alpha = 0.30f) }
                withStyle(
                    SpanStyle(
                        color = if (!isPlaying || current) textColor else textColor.copy(alpha = 0.62f),
                        background = when {
                            current -> PaleGreen.copy(alpha = 0.16f)
                            marker != null -> marker
                            else -> Color.Transparent
                        }
                    )
                ) { append(sentence) }
                if (index != sentences.lastIndex) append(" ")
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textTop by remember { mutableStateOf(0) }

    // Listening is hands-free, so the page has to follow the voice; only while actually speaking,
    // otherwise it would fight a reader who scrolled away deliberately.
    LaunchedEffect(currentSentenceIndex, isPlaying, layout) {
        if (!isPlaying) return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        val start = starts.getOrNull(currentSentenceIndex) ?: return@LaunchedEffect
        val line = result.getLineForOffset(start.coerceAtMost(result.layoutInput.text.length - 1))
        val target = textTop + result.getLineTop(line).toInt() - SPOKEN_LINE_TOP_INSET_PX
        scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.75f).sp,
            fontFamily = FontFamily.Serif
        ),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { textTop = it.positionInParent().y.toInt() }
            .pointerInput(starts) {
                fun sentenceAt(offset: androidx.compose.ui.geometry.Offset): Int? {
                    val result = layout ?: return null
                    val charIndex = result.getOffsetForPosition(offset)
                    return starts.indexOfLast { it <= charIndex }.takeIf { it >= 0 }
                }
                detectTapGestures(
                    // Tap moves the voice; long-press marks the passage — the two reading gestures
                    // people already expect, on the same run of text.
                    onTap = { offset -> sentenceAt(offset)?.let(onSeekToSentence) },
                    onLongPress = { offset -> sentenceAt(offset)?.let(onMarkSentence) }
                )
            }
    )
}

/** Keeps the spoken line off the very top edge so the reader can see what came before it. */
private const val SPOKEN_LINE_TOP_INSET_PX = 220

private val WaveformBarHeights = listOf(8, 14, 20, 12, 18, 10, 15)

@Composable
private fun WaveformProgress(progress: Float, dimColor: Color, modifier: Modifier = Modifier) {
    val filled = (progress * WaveformBarHeights.size).toInt().coerceIn(0, WaveformBarHeights.size)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        WaveformBarHeights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) SignalOrange else dimColor)
            )
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
    onToggleSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
    onVoiceSettings: () -> Unit,
    onBookmark: () -> Unit
) {
    Surface(
        color = if (bgColor == Carbon) Graphite else bgColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
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
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = if (uiState.isTtsPlaying) SignalOrange else textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            uiState.isTtsPlaying -> "Offline voice · sentence ${uiState.currentSentenceIndex + 1}"
                            uiState.isTtsPaused -> "Speech paused"
                            uiState.isTtsPreparing -> "Preparing offline voice…"
                            uiState.ttsErrorMessage != null -> uiState.ttsErrorMessage
                            else -> "Offline voice ready"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speed chip
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
                                Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                // Rate round-trips through the engine as a float; raw it prints like "0.997199x".
                                text = "%.2f".format(uiState.ttsRate).trimEnd('0').trimEnd('.') + "×",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = "Toggle Expand",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            WaveformProgress(
                progress = (uiState.currentSentenceIndex + 1).toFloat() / uiState.textChunks.size.coerceAtLeast(1),
                dimColor = textColor.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
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
                        Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = textColor
                    )
                }
                if (isExpanded) {
                    IconButton(onClick = onSkipBack) {
                        Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds", tint = textColor)
                    }
                    IconButton(onClick = onPrevSentence) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous Sentence", tint = textColor)
                    }
                }
                // Main Play/Pause — glowing orange circle, bold-contrast direction
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .background(SignalOrange.copy(alpha = 0.15f), CircleShape)
                    )
                    Surface(
                        shape = CircleShape,
                        color = SignalOrange,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable(onClick = onPlayPause)
                            .testTag("tts_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isTtsPlaying || uiState.isTtsPreparing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = if (uiState.isTtsPlaying || uiState.isTtsPreparing) "Pause Speech" else "Play Speech",
                                tint = Carbon,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                if (isExpanded) {
                    IconButton(onClick = onNextSentence) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next Sentence", tint = textColor)
                    }
                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds", tint = textColor)
                    }
                }
                IconButton(
                    onClick = onNextChapter,
                    enabled = uiState.book != null && uiState.currentChapterIndex < (uiState.book.chapters.size - 1)
                ) {
                    Icon(
                        Icons.Outlined.SkipNext,
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
                    IconButton(onClick = onSleepTimer) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = "Sleep Timer", tint = textColor)
                    }
                    IconButton(onClick = onVoiceSettings) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Voice Settings", tint = textColor)
                    }
                    IconButton(onClick = onBookmark) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Bookmark", tint = textColor)
                    }
                }
            }
        }
    }
}

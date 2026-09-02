package com.voxleaf.reader.feature.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay10
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
import androidx.compose.material3.SliderDefaults
import com.voxleaf.reader.ui.theme.SignalOrange
import com.voxleaf.reader.ui.theme.Eyebrow
import kotlin.math.roundToInt
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxleaf.reader.core.ui.components.ErrorState
import com.voxleaf.reader.core.ui.components.LoadingState
import com.voxleaf.reader.domain.repository.Highlight
import com.voxleaf.reader.tts.EngineId
import com.voxleaf.reader.ui.theme.Carbon
import com.voxleaf.reader.ui.theme.HighlightColor
import com.voxleaf.reader.ui.theme.Canvas
import com.voxleaf.reader.ui.theme.Graphite
import com.voxleaf.reader.ui.theme.NightText
import com.voxleaf.reader.ui.theme.PaleGreen
import com.voxleaf.reader.ui.theme.EditorialDisplay
import com.voxleaf.reader.ui.theme.ReaderSerif
import com.voxleaf.reader.ui.theme.SpineColor
import com.voxleaf.reader.ui.theme.UiSans
import com.voxleaf.reader.ui.theme.UtilityMono

fun fontFamilyForReader(fontFamily: ReaderFontFamily): FontFamily = when (fontFamily) {
    ReaderFontFamily.SERIF -> ReaderSerif
    ReaderFontFamily.SANS_SERIF -> UiSans
    ReaderFontFamily.MONOSPACE -> UtilityMono
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.readerChromeVisibility(visible: Boolean): Modifier = if (visible) {
    this
} else {
    this
        .alpha(0f)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
        .semantics { invisibleToUser() }
}

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
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showContentsSheet by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var contentsQuery by rememberSaveable { mutableStateOf("") }
    var bookmarkNote by remember { mutableStateOf("") }
    val (bgColor, textColor) = when (uiState.readerTheme) {
        ReaderTheme.LIGHT -> Canvas to Color(0xFF1A202C)
        ReaderTheme.DARK -> Graphite to NightText
        ReaderTheme.SEPIA -> Color(0xFFF4EEDF) to Color(0xFF382F23)
        ReaderTheme.NIGHT -> Carbon to NightText
        ReaderTheme.OLED -> Color(0xFF000000) to Color(0xFFE2E8F0)
        ReaderTheme.IVORY -> Color(0xFFFAF7EE) to Color(0xFF2C2B29)
    }
    val currentFontFamily = fontFamilyForReader(uiState.readerFontFamily)
    val showChapterInTopBar = LocalDensity.current.fontScale < 1.8f
    Scaffold(
        topBar = {
          Column(modifier = Modifier.readerChromeVisibility(chromeVisible)) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.book?.title ?: stringResource(com.voxleaf.reader.R.string.reader_title_fallback),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        uiState.currentChapter?.takeIf { showChapterInTopBar }?.let { chapter ->
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_back), tint = textColor)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showContentsSheet = true },
                        modifier = Modifier.testTag("reader_contents_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Toc,
                            contentDescription = stringResource(com.voxleaf.reader.R.string.reader_contents_description),
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = { showAppearanceSheet = true },
                        modifier = Modifier.testTag("reader_appearance_button")
                    ) {
                        Icon(
                            Icons.Outlined.TextFields,
                            contentDescription = stringResource(com.voxleaf.reader.R.string.reader_text_appearance),
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.handleAction(
                                ReaderUiAction.OnStartMarking(uiState.currentSentenceIndex)
                            )
                        },
                        modifier = Modifier.testTag("highlight_current_sentence_button")
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(com.voxleaf.reader.R.string.reader_highlight_current),
                            tint = textColor
                        )
                    }
                    IconButton(
                        onClick = { showBookmarkDialog = true },
                        modifier = Modifier.testTag("add_bookmark_button")
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = stringResource(com.voxleaf.reader.R.string.reader_add_bookmark),
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
                onPlayPause = { viewModel.handleAction(ReaderUiAction.OnPlayPauseTts) },
                onSkipBack = { viewModel.handleAction(ReaderUiAction.OnSkipBack) },
                onSkipForward = { viewModel.handleAction(ReaderUiAction.OnSkipForward) },
                onSeekToSentence = { viewModel.handleAction(ReaderUiAction.OnSeekToSentence(it)) },
                onSleepTimer = { showSleepTimerDialog = true },
                onVoiceSettings = { showVoiceSheet = true },
                onBookmark = { showBookmarkDialog = true },
                modifier = Modifier.readerChromeVisibility(chromeVisible)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(paddingValues)
        ) {
            com.voxleaf.reader.core.ui.components.AmbientStickerDecorations(alpha = 0.08f)
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
                                fontFamily = EditorialDisplay
                            ),
                            color = textColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        FlowingChapterText(
                            sentences = sentences,
                            currentSentenceIndex = uiState.currentSentenceIndex,
                            fontSizeSp = uiState.fontSizeSp,
                            lineSpacingMultiplier = uiState.lineSpacingMultiplier,
                            fontFamily = currentFontFamily,
                            textColor = textColor,
                            isPlaying = uiState.isTtsPlaying,
                            highlights = uiState.chapterHighlights,
                            scrollState = scrollState,
                            onSeekToSentence = { viewModel.handleAction(ReaderUiAction.OnSeekToSentence(it)) },
                            onMarkSentence = { viewModel.handleAction(ReaderUiAction.OnStartMarking(it)) },
                            onToggleChrome = { chromeVisible = !chromeVisible }
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
                            text = stringResource(com.voxleaf.reader.R.string.reader_contents),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = EditorialDisplay,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = pluralStringResource(
                                com.voxleaf.reader.R.plurals.reader_chapter_count,
                                uiState.book?.chapters?.size ?: 0,
                                uiState.book?.chapters?.size ?: 0
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = contentsQuery,
                            onValueChange = { contentsQuery = it },
                            label = { Text(stringResource(com.voxleaf.reader.R.string.reader_contents_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reader_contents_search")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            val chapters = uiState.book?.chapters.orEmpty().withIndex().filter { indexed ->
                                contentsQuery.isBlank() || indexed.value.title.contains(contentsQuery, ignoreCase = true)
                            }
                            items(chapters.size) { filteredIndex ->
                                val indexed = chapters[filteredIndex]
                                val index = indexed.index
                                val chapter = indexed.value
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
                                        .semantics {
                                            if (isCurrentChapter) selected = true
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                                        Text(
                                            text = "${index + 1}".padStart(2, '0'),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
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
            if (showAppearanceSheet) {
                ModalBottomSheet(onDismissRequest = { showAppearanceSheet = false }) {
                    ReaderAppearanceControls(
                        uiState = uiState,
                        onAction = viewModel::handleAction,
                        onDone = { showAppearanceSheet = false }
                    )
                }
            }
            if (showVoiceSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showVoiceSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(com.voxleaf.reader.R.string.reader_voice_and_speech),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        // Engine selection
                        Text(
                            text = stringResource(com.voxleaf.reader.R.string.reader_voice_source),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                EngineId.OFFLINE to com.voxleaf.reader.R.string.reader_voice_on_device,
                                EngineId.EDGE to com.voxleaf.reader.R.string.reader_voice_online
                            ).forEach { (engine, label) ->
                                val selected = uiState.ttsEngineId == engine
                                OutlinedButton(
                                    onClick = { viewModel.handleAction(ReaderUiAction.OnSetTtsEngine(engine)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Text(
                                        text = stringResource(label),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Speed / Rate controls
                        Text(
                            text = stringResource(com.voxleaf.reader.R.string.reader_reading_speed, uiState.ttsRate),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rate ->
                                val selected = kotlin.math.abs(uiState.ttsRate - rate) < 0.05f
                                Surface(
                                    onClick = { viewModel.handleAction(ReaderUiAction.OnSetTtsSpeed(rate)) },
                                    modifier = Modifier
                                        .width(64.dp)
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

                        // Voices
                        if (uiState.availableVoices.isNotEmpty()) {
                            Text(
                                text = stringResource(com.voxleaf.reader.R.string.reader_voice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.availableVoices.forEach { voice ->
                                    val selected = voice.id == uiState.ttsVoice
                                    Surface(
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.handleAction(ReaderUiAction.OnSetTtsVoice(voice.id)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = voice.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${voice.locale}${voice.gender?.let { " · $it" } ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (selected) {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = stringResource(com.voxleaf.reader.R.string.reader_selected),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showVoiceSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(com.voxleaf.reader.R.string.done))
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
private fun ReaderAppearanceControls(
    uiState: ReaderUiState,
    onAction: (ReaderUiAction) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(com.voxleaf.reader.R.string.reader_text_appearance),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(com.voxleaf.reader.R.string.reader_appearance_preview),
                fontFamily = fontFamilyForReader(uiState.readerFontFamily),
                fontSize = uiState.fontSizeSp.sp,
                lineHeight = (uiState.fontSizeSp * uiState.lineSpacingMultiplier).sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        Text(stringResource(com.voxleaf.reader.R.string.reader_theme), fontWeight = FontWeight.SemiBold)
        listOf(
            listOf(ReaderTheme.LIGHT, ReaderTheme.SEPIA),
            listOf(ReaderTheme.NIGHT, ReaderTheme.OLED)
        ).forEach { themes ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            themes.forEach { theme ->
                OutlinedButton(
                    onClick = { onAction(ReaderUiAction.OnChangeTheme(theme)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            when (theme) {
                                ReaderTheme.LIGHT -> com.voxleaf.reader.R.string.reader_theme_light
                                ReaderTheme.SEPIA -> com.voxleaf.reader.R.string.reader_theme_sepia
                                ReaderTheme.NIGHT -> com.voxleaf.reader.R.string.reader_theme_night
                                ReaderTheme.OLED -> com.voxleaf.reader.R.string.reader_theme_black
                                ReaderTheme.DARK -> com.voxleaf.reader.R.string.reader_theme_dark
                                ReaderTheme.IVORY -> com.voxleaf.reader.R.string.reader_theme_ivory
                            }
                        ),
                        maxLines = 1
                    )
                }
            }
          }
        }
        Text(stringResource(com.voxleaf.reader.R.string.reader_font), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReaderFontFamily.entries.forEach { family ->
                OutlinedButton(
                    onClick = { onAction(ReaderUiAction.OnChangeFontFamily(family)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (family) {
                            ReaderFontFamily.SERIF -> stringResource(com.voxleaf.reader.R.string.reader_font_book)
                            ReaderFontFamily.SANS_SERIF -> stringResource(com.voxleaf.reader.R.string.reader_font_clean)
                            ReaderFontFamily.MONOSPACE -> stringResource(com.voxleaf.reader.R.string.reader_font_mono)
                        },
                        maxLines = 1
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(com.voxleaf.reader.R.string.reader_text_size, uiState.fontSizeSp))
            val decreaseTextSize = stringResource(com.voxleaf.reader.R.string.reader_decrease_text_size)
            val increaseTextSize = stringResource(com.voxleaf.reader.R.string.reader_increase_text_size)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAction(ReaderUiAction.OnChangeFontSize(-1)) },
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = decreaseTextSize
                    }
                ) { Text(stringResource(com.voxleaf.reader.R.string.reader_text_smaller)) }
                OutlinedButton(
                    onClick = { onAction(ReaderUiAction.OnChangeFontSize(1)) },
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = increaseTextSize
                    }
                ) { Text(stringResource(com.voxleaf.reader.R.string.reader_text_larger)) }
            }
        }
        Text(stringResource(com.voxleaf.reader.R.string.reader_line_spacing))
        Slider(
            value = uiState.lineSpacingMultiplier,
            onValueChange = { onAction(ReaderUiAction.OnChangeLineSpacing(it)) },
            valueRange = 1.2f..2.2f,
            steps = 4
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.voxleaf.reader.R.string.done))
        }
    }
}

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
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ReaderSerif),
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
    lineSpacingMultiplier: Float,
    fontFamily: FontFamily,
    textColor: Color,
    isPlaying: Boolean,
    highlights: Map<Int, Highlight>,
    scrollState: ScrollState,
    onSeekToSentence: (Int) -> Unit,
    onMarkSentence: (Int) -> Unit,
    onToggleChrome: () -> Unit
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
                            current -> com.voxleaf.reader.ui.theme.SkyPrimary.copy(alpha = 0.25f)
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
            lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
            fontFamily = fontFamily
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
                    onTap = { offset ->
                        sentenceAt(offset)?.let(onSeekToSentence)
                        onToggleChrome()
                    },
                    onLongPress = { offset -> sentenceAt(offset)?.let(onMarkSentence) }
                )
            }
    )
}

/** Keeps the spoken line off the very top edge so the reader can see what came before it. */
private const val SPOKEN_LINE_TOP_INSET_PX = 220

/** Narration pace used for time estimates; the speech-rate multiplier scales it. */
private const val SCRUBBER_WORDS_PER_MINUTE = 155f

private fun formatClock(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Draggable position within the chapter, with a running time readout.
 *
 * Position is measured in sentences rather than milliseconds because that is the only unit that
 * exists while a chapter is being streamed — there is no rendered file to ask for a duration. The
 * clock is therefore an estimate derived from word count at the current speech rate, and is labelled
 * as such. Seeking is exact regardless: each stop on the slider is a real sentence boundary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterScrubber(
    uiState: ReaderUiState,
    textColor: Color,
    onSeekToSentence: (Int) -> Unit
) {
    val total = uiState.textChunks.size
    if (total == 0) return

    // Cumulative start time per sentence, recomputed only when the chapter or the rate changes.
    val (starts, duration) = remember(uiState.textChunks, uiState.ttsRate) {
        val wordsPerSecond = (SCRUBBER_WORDS_PER_MINUTE * uiState.ttsRate.coerceAtLeast(0.1f)) / 60f
        var elapsed = 0f
        val offsets = FloatArray(total)
        uiState.textChunks.forEachIndexed { index, chunk ->
            offsets[index] = elapsed
            elapsed += chunk.text.split(Regex("\\s+")).count { it.isNotBlank() } / wordsPerSecond
        }
        offsets to elapsed
    }

    // While dragging, the thumb follows the finger rather than the voice; releasing commits the seek.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val lastIndex = (total - 1).coerceAtLeast(0)
    val position = scrubPosition ?: uiState.currentSentenceIndex.coerceIn(0, lastIndex).toFloat()
    val elapsedSeconds = starts.getOrElse(position.roundToInt().coerceIn(0, lastIndex)) { 0f }
    val remainingMinutes = ((duration - elapsedSeconds).coerceAtLeast(0f) / 60f).roundToInt()
    val activeTrack = Color(0xFF2F7892)
    val thumbColor = Color(0xFF1B5871)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position,
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { onSeekToSentence(it.roundToInt().coerceIn(0, lastIndex)) }
                scrubPosition = null
            },
            valueRange = 0f..lastIndex.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrack,
                inactiveTrackColor = textColor.copy(alpha = 0.24f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(thumbColor, CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState,
                    modifier = Modifier.height(2.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = activeTrack,
                        inactiveTrackColor = textColor.copy(alpha = 0.24f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .testTag("reader_scrubber")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatClock(elapsedSeconds), style = Eyebrow, color = textColor.copy(alpha = 0.55f))
            Text("$remainingMinutes min left", style = Eyebrow, color = textColor.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun ReaderTtsBottomBar(
    uiState: ReaderUiState,
    textColor: Color,
    bgColor: Color,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeekToSentence: (Int) -> Unit,
    onSleepTimer: () -> Unit,
    onVoiceSettings: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (bgColor == Carbon) Graphite else bgColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
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
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val voiceName = uiState.availableVoices
                        .firstOrNull { it.id == uiState.ttsVoice }
                        ?.displayName
                        ?: stringResource(com.voxleaf.reader.R.string.reader_default_voice)
                    val availability = stringResource(
                        if (uiState.ttsEngineId == EngineId.EDGE) {
                            com.voxleaf.reader.R.string.reader_voice_online
                        } else {
                            com.voxleaf.reader.R.string.reader_voice_on_device
                        }
                    )
                    val voiceLabel = stringResource(
                        com.voxleaf.reader.R.string.reader_voice_status,
                        voiceName,
                        availability
                    )
                    Text(
                        text = when {
                            uiState.isTtsPlaying -> stringResource(
                                com.voxleaf.reader.R.string.reader_voice_playing,
                                voiceLabel,
                                uiState.currentSentenceIndex + 1
                            )
                            uiState.isTtsPaused -> stringResource(com.voxleaf.reader.R.string.reader_speech_paused)
                            uiState.isAudiobookConverting -> uiState.ttsErrorMessage
                                ?: stringResource(com.voxleaf.reader.R.string.reader_preparing_playback)
                            uiState.isTtsPreparing -> stringResource(
                                com.voxleaf.reader.R.string.reader_preparing_voice,
                                voiceLabel
                            )
                            uiState.ttsErrorMessage != null -> uiState.ttsErrorMessage
                            else -> voiceLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
            ChapterScrubber(
                uiState = uiState,
                textColor = textColor,
                onSeekToSentence = onSeekToSentence
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipBack) {
                    Icon(Icons.Outlined.Replay10, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_back_ten), tint = textColor)
                }
                // Main Play/Pause — Radiant Azure gradient circle with neon aura
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onPlayPause)
                            .semantics { role = Role.Button }
                            .testTag("tts_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isTtsPlaying || (uiState.isTtsPreparing && !uiState.isAudiobookConverting)) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(
                                if (uiState.isTtsPlaying || (uiState.isTtsPreparing && !uiState.isAudiobookConverting)) {
                                    com.voxleaf.reader.R.string.reader_pause_speech
                                } else {
                                    com.voxleaf.reader.R.string.reader_play_speech
                                }
                            ),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                IconButton(onClick = onSkipForward) {
                    Icon(Icons.Outlined.Forward10, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_forward_ten), tint = textColor)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSleepTimer) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_sleep_timer), tint = textColor)
                }
                IconButton(onClick = onVoiceSettings) {
                    Icon(Icons.Outlined.RecordVoiceOver, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_voice_speed), tint = textColor)
                }
                IconButton(onClick = onBookmark) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = stringResource(com.voxleaf.reader.R.string.reader_bookmark), tint = textColor)
                }
            }
        }
    }
}

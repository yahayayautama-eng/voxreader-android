package com.example.feature.bookdetails

import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.Icons


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.BookSpine
import com.example.core.ui.components.ErrorState
import com.example.core.ui.components.LoadingState
import com.example.core.ui.components.toDisplayTitle
import com.example.ui.theme.Carbon
import com.example.ui.theme.PaleGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookDetailsScreen(
    bookId: String,
    onNavigateToReader: (String, Int?) -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToVoiceSelection: () -> Unit = {},
    onBookDeleted: () -> Unit = {},
    viewModel: BookDetailsViewModel = hiltViewModel(),
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BookDetailsUiState.Loading -> LoadingState()
            is BookDetailsUiState.Error -> ErrorState(message = state.message, onRetry = { viewModel.loadBook(bookId) })
            is BookDetailsUiState.Success -> {
                val book = state.book
                LaunchedEffect(state.isDeleted) {
                    if (state.isDeleted) onBookDeleted()
                }
                val coverColor = try {
                    Color(android.graphics.Color.parseColor(book.coverColorHex))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primaryContainer
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        val progress = if (book.totalChapters > 0) {
                            (book.currentChapterIndex.toFloat() / book.totalChapters).coerceIn(0f, 1f)
                        } else 0f
                        val minutesLeft = book.chapters.drop(book.currentChapterIndex)
                            .sumOf { it.estimatedMinutes }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BookSpine(
                                title = book.title,
                                bookId = book.id,
                                coverPath = book.coverImagePath,
                                showTitle = false,
                                initialSize = 56.sp,
                                cornerRadius = 10,
                                sharedScope = sharedScope,
                                animatedScope = animatedScope,
                                modifier = Modifier.width(120.dp).height(170.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = book.title.toDisplayTitle(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("book_title_text")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = book.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "${book.totalChapters} chapters · ${formatMinutes(minutesLeft)} left · ${(progress * 100).toInt()}% complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = { onNavigateToReader(book.id, null) },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Carbon),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("read_now_button")
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (book.currentChapterIndex > 0) {
                                    "Resume · Ch ${book.currentChapterIndex + 1}"
                                } else "Start listening",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailsOutlineButton(
                                text = "Bookmark",
                                icon = Icons.Outlined.Bookmarks,
                                onClick = onNavigateToBookmarks,
                                modifier = Modifier.weight(1f).testTag("bookmarks_button")
                            )
                            DetailsOutlineButton(
                                text = "Delete",
                                icon = Icons.Outlined.DeleteOutline,
                                onClick = { showDeleteConfirmation = true },
                                modifier = Modifier.weight(1f).testTag("remove_book_button")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailsOutlineButton(
                                text = if (book.isFavorite) "In favorites" else "Favorite",
                                icon = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                tint = if (book.isFavorite) SignalOrange else null,
                                onClick = { viewModel.handleAction(BookDetailsUiAction.OnToggleFavorite) },
                                modifier = Modifier.weight(1f).testTag("details_favorite_button")
                            )
                            DetailsOutlineButton(
                                text = "Edit details",
                                icon = Icons.Outlined.Edit,
                                onClick = { viewModel.handleAction(BookDetailsUiAction.OnEditMetadata) },
                                modifier = Modifier.weight(1f).testTag("edit_metadata_button")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailsOutlineButton(
                            text = if (state.isRedetectingChapters) "Scanning…" else "Re-scan chapters",
                            icon = Icons.Outlined.AutoFixHigh,
                            enabled = !state.isRedetectingChapters,
                            onClick = { viewModel.handleAction(BookDetailsUiAction.OnRedetectChapters) },
                            modifier = Modifier.fillMaxWidth().testTag("redetect_chapters_button")
                        )
                        state.redetectMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }


                        if (book.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            SectionLabel("About this document")
                            Text(
                                text = book.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionLabel("Chapters")
                    }
                    itemsIndexed(book.chapters) { index, chapter ->
                        ChapterRow(
                            title = chapter.title,
                            minutes = chapter.estimatedMinutes,
                            state = when {
                                index < book.currentChapterIndex -> ChapterState.Played
                                index == book.currentChapterIndex -> ChapterState.Current
                                else -> ChapterState.Upcoming
                            },
                            onClick = { onNavigateToReader(book.id, index) },
                            modifier = Modifier.testTag("chapter_item_$index")
                        )
                    }
                }
                if (state.isEditingMetadata) {
                    EditMetadataDialog(
                        book = book,
                        errorMessage = state.errorMessage,
                        onDismiss = { viewModel.handleAction(BookDetailsUiAction.OnCancelMetadataEdit) },
                        onSave = { title, author, description, genre ->
                            viewModel.handleAction(
                                BookDetailsUiAction.OnSaveMetadata(title, author, description, genre)
                            )
                        }
                    )
                }
                if (showDeleteConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmation = false },
                        title = { Text("Remove book?") },
                        text = { Text("This removes the book, its reading progress, bookmarks, and imported copy from this device.") },
                        confirmButton = {
                            Button(onClick = {
                                showDeleteConfirmation = false
                                viewModel.handleAction(BookDetailsUiAction.OnDeleteBook)
                            }) { Text("Remove") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

private enum class ChapterState { Played, Current, Upcoming }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
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
private fun DetailsOutlineButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.height(48.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: LocalContentColor.current, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChapterRow(
    title: String,
    minutes: Int,
    state: ChapterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(state)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (state == ChapterState.Current) FontWeight.Bold else FontWeight.Normal,
            color = when (state) {
                ChapterState.Current -> MaterialTheme.colorScheme.onSurface
                ChapterState.Played -> MaterialTheme.colorScheme.onSurfaceVariant
                ChapterState.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (state == ChapterState.Current) {
            Text("Playing", style = MaterialTheme.typography.labelSmall, color = SignalOrange)
        } else {
            Text("$minutes min", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
}

@Composable
private fun StatusDot(state: ChapterState) {
    when (state) {
        ChapterState.Played -> Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(PaleGreen)
        )
        ChapterState.Current -> Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).border(2.dp, SignalOrange, CircleShape)
        )
        ChapterState.Upcoming -> Box(
            modifier = Modifier.size(8.dp).clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        )
    }
}

private fun formatMinutes(total: Int): String =
    if (total >= 60) "${total / 60}h ${total % 60}m" else "${total}m"

@Composable
private fun EditMetadataDialog(
    book: com.example.domain.repository.Book,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var description by remember(book.id) { mutableStateOf(book.description) }
    var genre by remember(book.id) { mutableStateOf(book.genre) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true)
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { onSave(title, author, description, genre) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

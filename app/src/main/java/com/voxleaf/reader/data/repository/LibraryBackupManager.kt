package com.voxleaf.reader.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.voxleaf.reader.data.local.AppDatabase
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.BookmarkEntity
import com.voxleaf.reader.data.local.entity.HighlightEntity
import com.voxleaf.reader.data.local.entity.ListeningDayEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val message: String) : BackupResult
    data class Error(val message: String) : BackupResult
}

data class LibraryStorageSummary(val bytes: Long, val bookCount: Int)

@Singleton
class LibraryBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    suspend fun storageSummary(): LibraryStorageSummary = withContext(Dispatchers.IO) {
        val bytes = context.filesDir.walkTopDown().filter(File::isFile).sumOf(File::length) +
            context.getDatabasePath(DATABASE_NAME).takeIf(File::isFile)?.length().orZero()
        LibraryStorageSummary(bytes, database.bookDao().getAllBookEntities().size)
    }

    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = LibraryBackupPayload(
                exportedAt = System.currentTimeMillis(),
                books = database.bookDao().getAllBookEntities().map {
                    it.copy(sourceFilePath = null, coverImagePath = null)
                },
                sections = database.bookDao().getAllSectionEntities(),
                chunks = database.bookDao().getAllTextChunkEntities(),
                progress = database.bookDao().getAllReadingProgressEntities(),
                bookmarks = database.bookmarkDao().getAllBookmarksNow(),
                highlights = database.highlightDao().getAllHighlightsNow(),
                listeningDays = database.listeningDao().getAllListeningDaysNow()
            )
            validate(payload)
            val stagingRoot = File(context.cacheDir, STAGING_DIRECTORY).apply { mkdirs() }.canonicalFile
            val staging = File(stagingRoot, "export-${System.nanoTime()}.json").canonicalFile
            require(staging.parentFile == stagingRoot)
            try {
                staging.bufferedWriter(Charsets.UTF_8).use { it.write(json.encodeToString(payload)) }
                require(staging.length() <= MAX_BACKUP_BYTES) { "The library backup is too large to export." }
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("The selected destination could not be opened.")
                output.buffered().use { destination -> staging.inputStream().buffered().use { it.copyTo(destination) } }
                BackupResult.Success("Backup saved · ${payload.books.size} documents")
            } finally {
                staging.delete()
            }
        }.getOrElse { BackupResult.Error(it.message ?: "The backup could not be saved.") }
    }

    suspend fun restoreFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("The selected backup could not be opened.")
            val bytes = input.buffered().use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BACKUP_BYTES) { "This backup exceeds the 150 MB restore limit." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val payload = json.decodeFromString<LibraryBackupPayload>(bytes.toString(Charsets.UTF_8))
            validate(payload)
            val normalizedBooks = payload.books.map { book ->
                book.copy(
                    sourceFilePath = null,
                    coverImagePath = null,
                    totalChapters = payload.sections.count { it.bookId == book.id }
                )
            }
            database.withTransaction {
                database.bookDao().insertBooks(normalizedBooks)
                database.bookDao().insertSections(payload.sections)
                database.bookDao().insertTextChunks(payload.chunks)
                database.bookDao().insertReadingProgress(payload.progress)
                database.bookmarkDao().insertBookmarks(payload.bookmarks)
                database.highlightDao().insertHighlights(payload.highlights)
                database.listeningDao().insertListeningDays(payload.listeningDays)
            }
            BackupResult.Success("Backup restored · ${payload.books.size} documents")
        }.getOrElse { BackupResult.Error(it.message ?: "The backup is invalid or could not be restored.") }
    }

    internal fun validate(payload: LibraryBackupPayload) {
        require(payload.version == BACKUP_VERSION) { "Unsupported Vox Reader backup version." }
        require(payload.books.size <= MAX_BOOKS) { "Backup contains too many documents." }
        require(payload.sections.size <= MAX_SECTIONS) { "Backup contains too many sections." }
        require(payload.chunks.size <= MAX_CHUNKS) { "Backup contains too many text passages." }
        require(payload.books.map { it.id }.distinct().size == payload.books.size) { "Backup contains duplicate document IDs." }
        require(payload.sections.map { it.id }.distinct().size == payload.sections.size) { "Backup contains duplicate section IDs." }
        require(payload.chunks.map { it.id }.distinct().size == payload.chunks.size) { "Backup contains duplicate passage IDs." }
        val bookIds = payload.books.mapTo(mutableSetOf()) { it.id }
        val sectionIds = payload.sections.mapTo(mutableSetOf()) { it.id }
        require(payload.books.all { it.id.isNotBlank() && it.title.isNotBlank() && it.title.length <= MAX_TITLE_CHARS }) {
            "Backup contains invalid document metadata."
        }
        require(payload.sections.all {
            it.bookId in bookIds && it.chapterNumber > 0 && it.title.isNotBlank() &&
                it.detectionConfidence in 0f..1f
        }) { "Backup contains an invalid section reference." }
        require(payload.chunks.all { it.sectionId in sectionIds && it.sequenceNumber >= 0 && it.text.isNotBlank() }) {
            "Backup contains an invalid text passage."
        }
        require(payload.chunks.sumOf { it.text.length.toLong() } <= MAX_TEXT_CHARS) { "Backup text is too large." }
        require(payload.progress.all { it.bookId in bookIds && it.currentChapterIndex >= 0 && it.currentPosition >= 0 }) {
            "Backup contains invalid reading progress."
        }
        require(payload.bookmarks.all { it.bookId in bookIds && it.chapterIndex >= 0 && it.sentenceIndex >= 0 }) {
            "Backup contains an invalid bookmark."
        }
        require(payload.highlights.all { it.bookId in bookIds && it.chapterIndex >= 0 && it.sentenceIndex >= 0 }) {
            "Backup contains an invalid highlight."
        }
        require(payload.listeningDays.all { it.date.matches(ISO_DATE) && it.seconds >= 0 }) {
            "Backup contains invalid listening history."
        }
    }

    private fun Long?.orZero() = this ?: 0L

    private companion object {
        const val DATABASE_NAME = "voxleaf_db"
        const val STAGING_DIRECTORY = "library-backup-staging"
        const val BACKUP_VERSION = 1
        const val MAX_BACKUP_BYTES = 150L * 1024L * 1024L
        const val MAX_BOOKS = 10_000
        const val MAX_SECTIONS = 500_000
        const val MAX_CHUNKS = 2_000_000
        const val MAX_TITLE_CHARS = 10_000
        const val MAX_TEXT_CHARS = 100_000_000L
        val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

@Serializable
internal data class LibraryBackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val books: List<BookEntity>,
    val sections: List<SectionEntity>,
    val chunks: List<TextChunkEntity>,
    val progress: List<ReadingProgressEntity>,
    val bookmarks: List<BookmarkEntity>,
    val highlights: List<HighlightEntity>,
    val listeningDays: List<ListeningDayEntity>
)

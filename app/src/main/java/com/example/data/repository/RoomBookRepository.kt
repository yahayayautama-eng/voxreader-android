package com.example.data.repository

import android.content.Context
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.BookWithDetails
import com.example.data.local.dao.HighlightDao
import com.example.data.local.dao.ListeningDao
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity
import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.repository.Chapter
import com.example.domain.repository.Highlight
import com.example.domain.repository.ListeningDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val listeningDao: ListeningDao
) : BookRepository {

    private fun BookWithDetails.toDomainModel(): Book {
        return Book(
            id = book.id,
            title = book.title,
            author = book.author,
            description = book.description,
            genre = book.genre,
            coverColorHex = book.coverColorHex,
            coverImagePath = book.coverImagePath,
            totalChapters = book.totalChapters,
            currentChapterIndex = progress?.currentChapterIndex ?: 0,
            currentPosition = progress?.currentPosition ?: 0,
            isFavorite = book.isFavorite,
            chapters = sections.sortedBy { it.section.chapterNumber }.map { sectionWithChunks ->
                Chapter(
                    chapterNumber = sectionWithChunks.section.chapterNumber,
                    title = sectionWithChunks.section.title,
                    content = sectionWithChunks.chunks.sortedBy { it.sequenceNumber }.joinToString(" ") { it.text },
                    estimatedMinutes = sectionWithChunks.section.estimatedMinutes
                )
            }
        )
    }

    override fun getBooks(): Flow<List<Book>> {
        return bookDao.getBooks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun searchBooks(query: String): Flow<List<Book>> {
        if (query.isBlank()) return getBooks()
        return bookDao.searchBooks(query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getBookById(id: String): Book? {
        return bookDao.getBookById(id)?.toDomainModel()
    }

    override suspend fun addBook(book: Book) {
        val entity = BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            description = book.description,
            genre = book.genre,
            coverColorHex = book.coverColorHex,
            coverImagePath = book.coverImagePath,
            totalChapters = book.totalChapters,
            isFavorite = book.isFavorite
        )
        bookDao.insertBook(entity)

        val progress = ReadingProgressEntity(
            bookId = book.id,
            currentChapterIndex = book.currentChapterIndex,
            currentPosition = book.currentPosition
        )
        bookDao.insertReadingProgress(progress)

        val sections = mutableListOf<SectionEntity>()
        val chunks = mutableListOf<TextChunkEntity>()

        book.chapters.forEach { chapter ->
            val sectionId = UUID.randomUUID().toString()
            sections.add(
                SectionEntity(
                    id = sectionId,
                    bookId = book.id,
                    chapterNumber = chapter.chapterNumber,
                    title = chapter.title,
                    estimatedMinutes = chapter.estimatedMinutes
                )
            )
            // Split content roughly by sentences for TextChunkEntity
            val sentences = chapter.content.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            sentences.forEachIndexed { index, sentence ->
                chunks.add(
                    TextChunkEntity(
                        id = UUID.randomUUID().toString(),
                        sectionId = sectionId,
                        sequenceNumber = index,
                        text = sentence
                    )
                )
            }
        }
        
        bookDao.insertSections(sections)
        bookDao.insertTextChunks(chunks)
    }

    override suspend fun updateBookProgress(bookId: String, chapterIndex: Int, position: Int) {
        bookDao.updateReadingProgress(bookId, chapterIndex, position)
    }

    override suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        genre: String
    ) {
        bookDao.updateBookMetadata(bookId, title, author, description, genre)
    }

    override suspend fun removeBook(bookId: String) {
        val sourceFile = bookDao.getSourceFilePath(bookId)
        bookDao.deleteBook(bookId)
        sourceFile?.let { path ->
            val file = java.io.File(path)
            if (file.parentFile?.canonicalFile == java.io.File(context.filesDir, "imports").canonicalFile) {
                file.delete()
            }
        }
    }

    override suspend fun toggleFavorite(bookId: String) {
        bookDao.toggleFavorite(bookId)
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarks().map { entities ->
            entities.map {
                Bookmark(
                    id = it.id,
                    bookId = it.bookId,
                    chapterIndex = it.chapterIndex,
                    sentenceIndex = it.sentenceIndex,
                    chapterTitle = it.chapterTitle,
                    textSnippet = it.textSnippet,
                    timestamp = it.timestamp,
                    note = it.note
                )
            }
        }
    }

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(
            BookmarkEntity(
                id = bookmark.id,
                bookId = bookmark.bookId,
                chapterIndex = bookmark.chapterIndex,
                sentenceIndex = bookmark.sentenceIndex,
                chapterTitle = bookmark.chapterTitle,
                textSnippet = bookmark.textSnippet,
                timestamp = bookmark.timestamp,
                note = bookmark.note
            )
        )
    }

    override suspend fun removeBookmark(bookmarkId: String) {
        bookmarkDao.deleteBookmark(bookmarkId)
    }

    private fun HighlightEntity.toDomainModel() = Highlight(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        sentenceIndex = sentenceIndex,
        chapterTitle = chapterTitle,
        text = text,
        colorIndex = colorIndex,
        note = note,
        timestamp = timestamp
    )

    override fun getHighlights(): Flow<List<Highlight>> =
        highlightDao.getHighlights().map { entities -> entities.map { it.toDomainModel() } }

    override fun getHighlightsForBook(bookId: String): Flow<List<Highlight>> =
        highlightDao.getHighlightsForBook(bookId).map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun addHighlight(highlight: Highlight) {
        highlightDao.insertHighlight(
            HighlightEntity(
                id = highlight.id,
                bookId = highlight.bookId,
                chapterIndex = highlight.chapterIndex,
                sentenceIndex = highlight.sentenceIndex,
                chapterTitle = highlight.chapterTitle,
                text = highlight.text,
                colorIndex = highlight.colorIndex,
                note = highlight.note,
                timestamp = highlight.timestamp
            )
        )
    }

    override suspend fun removeHighlight(highlightId: String) {
        highlightDao.deleteHighlight(highlightId)
    }

    override suspend fun removeHighlightAt(bookId: String, chapterIndex: Int, sentenceIndex: Int) {
        highlightDao.deleteHighlightAt(bookId, chapterIndex, sentenceIndex)
    }

    override suspend fun recordListening(bookId: String, seconds: Int) {
        if (seconds <= 0) return
        listeningDao.addSeconds(LocalDate.now().toString(), bookId, seconds)
    }

    override fun getListeningDays(): Flow<List<ListeningDay>> =
        listeningDao.getDailyTotals().map { totals ->
            totals.mapNotNull { total ->
                // A row written on a device whose clock or locale later changed can hold junk; drop it
                // rather than crash the stats screen on parse.
                runCatching { ListeningDay(LocalDate.parse(total.date), total.seconds) }.getOrNull()
            }
        }

    override fun getListeningByBook(): Flow<List<Pair<String, Int>>> =
        listeningDao.getBookTotals().map { totals -> totals.map { it.bookId to it.seconds } }
}

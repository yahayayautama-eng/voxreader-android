package com.example.data.repository

import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.BookWithDetails
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity
import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.repository.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao
) : BookRepository {

    private fun BookWithDetails.toDomainModel(): Book {
        return Book(
            id = book.id,
            title = book.title,
            author = book.author,
            description = book.description,
            genre = book.genre,
            coverColorHex = book.coverColorHex,
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
}

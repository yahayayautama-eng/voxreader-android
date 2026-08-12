package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

data class Chapter(
    val chapterNumber: Int,
    val title: String,
    val content: String,
    val estimatedMinutes: Int = 5
)

data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val textSnippet: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val sentenceIndex: Int = 0
)

data class Highlight(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val chapterTitle: String,
    val text: String,
    /** Index into the app's fixed highlight palette; stored as an int so the palette can be retinted. */
    val colorIndex: Int,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Seconds listened on one local calendar day, summed across books. */
data class ListeningDay(val date: java.time.LocalDate, val seconds: Int)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val genre: String = "Fiction",
    val coverColorHex: String = "#2E7D32",
    val coverImagePath: String? = null,
    val totalChapters: Int = 1,
    val currentChapterIndex: Int = 0,
    val currentPosition: Int = 0,
    val audioPositionMs: Long = 0L,
    val audiobookStatus: String = "NONE",
    val audiobookProgressPercent: Int = 0,
    val isFavorite: Boolean = false,
    val chapters: List<Chapter> = emptyList()
)

interface BookRepository {
    fun getBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(id: String): Book?
    suspend fun addBook(book: Book)
    suspend fun updateBookProgress(bookId: String, chapterIndex: Int, position: Int)
    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        genre: String
    )
    suspend fun removeBook(bookId: String)
    suspend fun toggleFavorite(bookId: String)
    fun getBookmarks(): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun removeBookmark(bookmarkId: String)

    fun getHighlights(): Flow<List<Highlight>>
    fun getHighlightsForBook(bookId: String): Flow<List<Highlight>>
    suspend fun addHighlight(highlight: Highlight)
    suspend fun removeHighlight(highlightId: String)
    /** Clears whatever is marked at this sentence, so marking it again acts as an undo. */
    suspend fun removeHighlightAt(bookId: String, chapterIndex: Int, sentenceIndex: Int)

    suspend fun recordListening(bookId: String, seconds: Int)
    fun getListeningDays(): Flow<List<ListeningDay>>
    /** Total seconds per book id, most-listened first. Ids of deleted books are kept and may not resolve. */
    fun getListeningByBook(): Flow<List<Pair<String, Int>>>
}

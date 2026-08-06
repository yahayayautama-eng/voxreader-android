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
    val note: String? = null
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val genre: String = "Fiction",
    val coverColorHex: String = "#2E7D32",
    val totalChapters: Int = 1,
    val currentChapterIndex: Int = 0,
    val currentPosition: Int = 0,
    val isFavorite: Boolean = false,
    val chapters: List<Chapter> = emptyList()
)

interface BookRepository {
    fun getBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(id: String): Book?
    suspend fun addBook(book: Book)
    suspend fun updateBookProgress(bookId: String, chapterIndex: Int, position: Int)
    suspend fun toggleFavorite(bookId: String)
    fun getBookmarks(): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun removeBookmark(bookmarkId: String)
}

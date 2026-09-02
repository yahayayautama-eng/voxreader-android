package com.voxleaf.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query("SELECT * FROM books")
    fun getBooks(): Flow<List<BookWithDetails>>

    @Transaction
    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookWithDetails>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextChunks(chunks: List<TextChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingProgress(progress: ReadingProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingProgress(progress: List<ReadingProgressEntity>)

    @Query("SELECT * FROM books")
    suspend fun getAllBookEntities(): List<BookEntity>

    @Query("SELECT * FROM sections ORDER BY bookId, chapterNumber")
    suspend fun getAllSectionEntities(): List<SectionEntity>

    @Query("SELECT * FROM text_chunks ORDER BY sectionId, sequenceNumber")
    suspend fun getAllTextChunkEntities(): List<TextChunkEntity>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllReadingProgressEntities(): List<ReadingProgressEntity>

    @Query("UPDATE books SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :bookId")
    suspend fun toggleFavorite(bookId: String)
    
    @Query("""
        SELECT s.chapterNumber AS chapterIndex, s.title AS chapterTitle, t.sequenceNumber AS chunkIndex, t.text AS snippet 
        FROM text_chunks t 
        INNER JOIN sections s ON t.sectionId = s.id 
        WHERE s.bookId = :bookId AND t.text LIKE '%' || :query || '%' 
        ORDER BY s.chapterNumber ASC, t.sequenceNumber ASC
    """)
    suspend fun searchInBook(bookId: String, query: String): List<com.voxleaf.reader.data.local.dao.SearchResultSnippet>
    
    @Query("UPDATE reading_progress SET currentChapterIndex = :chapterIndex, currentPosition = :position, lastUpdatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateReadingProgress(bookId: String, chapterIndex: Int, position: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET title = :title, author = :author, description = :description, genre = :genre WHERE id = :bookId")
    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        genre: String
    )

    @Query("SELECT sourceFilePath FROM books WHERE id = :bookId")
    suspend fun getSourceFilePath(bookId: String): String?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getReadingProgress(bookId: String): ReadingProgressEntity?

    @Query("DELETE FROM sections WHERE bookId = :bookId")
    suspend fun deleteSectionsForBook(bookId: String)

    @Query("UPDATE books SET totalChapters = :count WHERE id = :bookId")
    suspend fun updateTotalChapters(bookId: String, count: Int)

    @androidx.room.Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)
}

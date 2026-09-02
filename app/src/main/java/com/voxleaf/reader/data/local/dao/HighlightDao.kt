package com.voxleaf.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voxleaf.reader.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights ORDER BY timestamp DESC")
    fun getHighlights(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapterIndex, sentenceIndex")
    fun getHighlightsForBook(bookId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :highlightId")
    suspend fun deleteHighlight(highlightId: String)

    /** Re-marking an already-marked sentence toggles it off, so the same gesture undoes itself. */
    @Query("DELETE FROM highlights WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND sentenceIndex = :sentenceIndex")
    suspend fun deleteHighlightAt(bookId: String, chapterIndex: Int, sentenceIndex: Int)

    @Query("SELECT * FROM highlights WHERE bookId = :bookId")
    suspend fun getHighlightsForBookNow(bookId: String): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlights(highlights: List<HighlightEntity>)

    @Query("SELECT * FROM highlights")
    suspend fun getAllHighlightsNow(): List<HighlightEntity>
}

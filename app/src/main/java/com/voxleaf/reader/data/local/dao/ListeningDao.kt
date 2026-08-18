package com.voxleaf.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DayTotal(val date: String, val seconds: Int)

data class BookTotal(val bookId: String, val seconds: Int)

@Dao
interface ListeningDao {

    /**
     * Accumulates into today's row. An upsert rather than read-modify-write so concurrent ticks from
     * the playback service can't lose each other's seconds.
     */
    @Query(
        """
        INSERT INTO listening_days (date, bookId, seconds) VALUES (:date, :bookId, :seconds)
        ON CONFLICT(date, bookId) DO UPDATE SET seconds = seconds + :seconds
        """
    )
    suspend fun addSeconds(date: String, bookId: String, seconds: Int)

    @Query("SELECT date, SUM(seconds) AS seconds FROM listening_days GROUP BY date ORDER BY date")
    fun getDailyTotals(): Flow<List<DayTotal>>

    @Query("SELECT bookId, SUM(seconds) AS seconds FROM listening_days GROUP BY bookId ORDER BY seconds DESC")
    fun getBookTotals(): Flow<List<BookTotal>>
}

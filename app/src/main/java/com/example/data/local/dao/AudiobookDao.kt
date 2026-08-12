package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AudioCueEntity
import com.example.data.local.entity.AudiobookGenerationEntity
import com.example.data.local.entity.ChapterAudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobook_generations WHERE bookId = :bookId")
    fun observeGeneration(bookId: String): Flow<AudiobookGenerationEntity?>

    @Query("SELECT * FROM audiobook_generations WHERE bookId = :bookId")
    suspend fun getGeneration(bookId: String): AudiobookGenerationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGeneration(generation: AudiobookGenerationEntity)

    @Query("UPDATE audiobook_generations SET status = :status, errorCode = :errorCode, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateGenerationStatus(
        bookId: String,
        status: String,
        errorCode: String? = null,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE audiobook_generations SET completedChapters = :completedChapters, progressPercent = :progressPercent, generatedBytes = generatedBytes + :generatedBytes, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateGenerationProgress(
        bookId: String,
        completedChapters: Int,
        progressPercent: Int,
        generatedBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM chapter_audio WHERE bookId = :bookId ORDER BY chapterIndex")
    fun observeChapterAudio(bookId: String): Flow<List<ChapterAudioEntity>>

    @Query("SELECT * FROM chapter_audio WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapterAudio(bookId: String): List<ChapterAudioEntity>

    @Query("SELECT * FROM chapter_audio WHERE bookId = :bookId AND status != 'READY' ORDER BY chapterIndex")
    suspend fun getIncompleteChapterAudio(bookId: String): List<ChapterAudioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterAudio(chapter: ChapterAudioEntity)

    @Query("UPDATE chapter_audio SET status = :status, filePath = :filePath, durationMs = :durationMs, fileSizeBytes = :fileSizeBytes, checksum = :checksum, segmentCount = :segmentCount, updatedAt = :updatedAt WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun updateChapterAudio(
        bookId: String,
        chapterIndex: Int,
        status: String,
        filePath: String?,
        durationMs: Long,
        fileSizeBytes: Long,
        checksum: String?,
        segmentCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM audio_cues WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY sentenceIndex")
    suspend fun getCues(bookId: String, chapterIndex: Int): List<AudioCueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCues(cues: List<AudioCueEntity>)

    @Query("DELETE FROM audio_cues WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun deleteCues(bookId: String, chapterIndex: Int)

    @Query("DELETE FROM audiobook_generations WHERE bookId = :bookId")
    suspend fun deleteGeneration(bookId: String)

    @Query("DELETE FROM chapter_audio WHERE bookId = :bookId")
    suspend fun deleteChapterAudio(bookId: String)
}

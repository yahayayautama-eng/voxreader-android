package com.example.audiobook

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.example.data.local.dao.AudiobookDao
import com.example.data.local.entity.AudioCueEntity
import com.example.data.local.entity.AudiobookGenerationEntity
import com.example.data.local.entity.ChapterAudioEntity
import com.example.tts.KokoroNativeEngine
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Singleton
class AudiobookGenerationCoordinator @Inject constructor(
    private val workManager: WorkManager,
    private val audiobookDao: AudiobookDao,
    private val audioFileStore: AudioFileStore
) {
    suspend fun enqueue(
        bookId: String,
        totalChapters: Int,
        voiceId: String = KokoroNativeEngine.DEFAULT_VOICE,
        estimatedBytes: Long = 0L
    ) {
        audiobookDao.upsertGeneration(
            AudiobookGenerationEntity(
                bookId = bookId,
                status = "QUEUED",
                totalChapters = totalChapters,
                voiceId = voiceId,
                modelVersion = KokoroNativeEngine.MODEL_VERSION,
                estimatedBytes = estimatedBytes
            )
        )
        val request = OneTimeWorkRequestBuilder<GenerateAudiobookWorker>()
            .setInputData(Data.Builder().putString(GenerateAudiobookWorker.BOOK_ID, bookId).build())
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("audiobook-generation-$bookId", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(bookId: String) {
        workManager.cancelUniqueWork("audiobook-generation-$bookId").await()
        audiobookDao.updateGenerationStatus(bookId, "CANCELLED")
    }

    suspend fun retry(bookId: String) {
        audiobookDao.updateGenerationStatus(bookId, "QUEUED", errorCode = null, errorMessage = null)
        enqueueWork(bookId)
    }

    suspend fun regenerate(bookId: String, totalChapters: Int) {
        cancel(bookId)
        audiobookDao.deleteChapterAudio(bookId)
        audiobookDao.deleteGeneration(bookId)
        audioFileStore.deleteBook(bookId)
        enqueue(bookId, totalChapters)
    }

    private fun enqueueWork(bookId: String) {
        workManager.enqueueUniqueWork(
            "audiobook-generation-$bookId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GenerateAudiobookWorker>()
                .setInputData(Data.Builder().putString(GenerateAudiobookWorker.BOOK_ID, bookId).build())
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }
}

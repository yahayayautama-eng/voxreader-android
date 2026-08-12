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
import com.example.data.local.datastore.AppSettingsManager
import com.example.tts.KokoroNativeEngine
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@Singleton
class AudiobookGenerationCoordinator @Inject constructor(
    private val workManager: WorkManager,
    private val audiobookDao: AudiobookDao,
    private val audioFileStore: AudioFileStore,
    private val appSettingsManager: AppSettingsManager
) {
    suspend fun enqueue(
        bookId: String,
        totalChapters: Int,
        voiceId: String? = null,
        generationSpeed: Float? = null,
        estimatedBytes: Long = 0L
    ) {
        val selectedVoice = appSettingsManager.ttsVoiceFlow.first()
            .takeIf { it.startsWith("voices/kitten/") }
            ?: KokoroNativeEngine.DEFAULT_VOICE
        val selectedSpeed = generationSpeed ?: appSettingsManager.ttsRateFlow.first()
        audiobookDao.upsertGeneration(
            AudiobookGenerationEntity(
                bookId = bookId,
                status = "QUEUED",
                totalChapters = totalChapters,
                voiceId = voiceId ?: selectedVoice,
                modelVersion = KokoroNativeEngine.MODEL_VERSION,
                generationSpeed = selectedSpeed.coerceIn(0.5f, 2f),
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

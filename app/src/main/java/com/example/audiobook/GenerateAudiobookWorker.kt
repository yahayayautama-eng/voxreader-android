package com.example.audiobook

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AudiobookDao
import com.example.data.local.entity.AudioCueEntity
import com.example.data.local.entity.ChapterAudioEntity
import com.example.data.local.datastore.AppSettingsManager
import com.example.tts.KokoroNativeEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class GenerateAudiobookWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val audiobookDao: AudiobookDao,
    private val audioFileStore: AudioFileStore,
    private val generator: KokoroNativeEngine,
    private val appSettingsManager: AppSettingsManager
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString(BOOK_ID) ?: return@withContext Result.failure()
        setForeground(getForegroundInfo())
        val book = database.bookDao().getBookById(bookId) ?: return@withContext Result.failure()
        val storedGeneration = audiobookDao.getGeneration(bookId) ?: return@withContext Result.failure()
        val generation = if (storedGeneration.status == "QUEUED" && storedGeneration.completedChapters == 0) {
            val voice = appSettingsManager.ttsVoiceFlow.first()
                .takeIf { it.startsWith("voices/kitten/") }
                ?: KokoroNativeEngine.DEFAULT_VOICE
            storedGeneration.copy(
                voiceId = voice,
                generationSpeed = appSettingsManager.ttsRateFlow.first().coerceIn(0.5f, 2f)
            ).also { audiobookDao.upsertGeneration(it) }
        } else storedGeneration
        audiobookDao.updateGenerationStatus(bookId, "CONVERTING")
        try {
            book.sections.sortedBy { it.section.chapterNumber }.forEachIndexed { chapterIndex, section ->
                if (isStopped) return@withContext Result.failure()
                val existing = audiobookDao.getChapterAudio(bookId).firstOrNull { it.chapterIndex == chapterIndex }
                if (existing?.status == READY && existing.filePath?.let { path -> path.endsWith(".m4a", ignoreCase = true) && File(path).isFile } == true) return@forEachIndexed
                audiobookDao.upsertChapterAudio(ChapterAudioEntity(bookId, chapterIndex, GENERATING))
                val segments = section.chunks.sortedBy { it.sequenceNumber }.mapNotNull { chunk ->
                    generator.synthesize(chunk.text, generation.generationSpeed, generation.voiceId)
                }
                if (segments.isEmpty()) return@withContext Result.retry()
                val wav = audioFileStore.tempWavChapterFile(bookId, chapterIndex)
                val encoded = audioFileStore.tempChapterFile(bookId, chapterIndex)
                val cues = WavChapterAssembler.assemble(segments, wav)
                AacChapterEncoder.encodeWav(wav, encoded)
                val final = audioFileStore.chapterFile(bookId, chapterIndex)
                audioFileStore.commit(encoded, final)
                wav.delete()
                segments.forEach(File::delete)
                audiobookDao.updateChapterAudio(bookId, chapterIndex, READY, final.absolutePath, cues.last().endMs, final.length(), audioFileStore.checksum(final), cues.size)
                val completed = chapterIndex + 1
                val percent = (completed * 100 / book.sections.size.coerceAtLeast(1)).coerceIn(0, 100)
                audiobookDao.updateGenerationProgress(bookId, completed, percent, final.length())
                audiobookDao.deleteCues(bookId, chapterIndex)
                audiobookDao.insertCues(cues.mapIndexed { index, cue -> AudioCueEntity(bookId, chapterIndex, index, cue.startMs, cue.endMs) })
            }
            audiobookDao.updateGenerationStatus(bookId, "READY")
            Result.success()
        } catch (error: Exception) {
            audiobookDao.updateGenerationStatus(bookId, "FAILED", "GENERATION_FAILED", error.message)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, GenerationNotification.create(applicationContext))

    companion object {
        const val BOOK_ID = "bookId"
        private const val READY = "READY"
        private const val GENERATING = "GENERATING"
        private const val NOTIFICATION_ID = 2001
    }
}

package com.example.audiobook

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
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
import com.example.tts.TtsTextParser
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
        val bookId = inputData.getString(BOOK_ID) ?: run {
            Log.e(TAG, "Audiobook job missing book id")
            return@withContext Result.failure()
        }
        val book = database.bookDao().getBookById(bookId) ?: run {
            Log.e(TAG, "Audiobook job book not found: $bookId")
            return@withContext Result.failure()
        }
        val storedGeneration = audiobookDao.getGeneration(bookId) ?: run {
            Log.e(TAG, "Audiobook job generation row not found: $bookId")
            return@withContext Result.failure()
        }
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
        // Leftover WAVs from a killed/crashed prior attempt would never otherwise be removed:
        // audiobook-runtime output is deliberately not swept during live playback (see
        // KokoroNativeEngine), so a crash between synth and chapter-commit leaks forever.
        generator.cleanupAudiobookRuntime()
        try {
            // Mark the job as started before foreground promotion. If HyperOS rejects the
            // foreground notification, the queue must become FAILED rather than stay QUEUED forever.
            setForeground(getForegroundInfo())
            book.sections.sortedBy { it.section.chapterNumber }.forEachIndexed { chapterIndex, section ->
                if (isStopped) {
                    // A stop (cancel, constraints no longer met, app killed) is not a failure — leaving
                    // the row on CONVERTING here is exactly what previously required a separate
                    // "repair stale jobs" pass on every screen open. Put it back in the queue instead.
                    audiobookDao.updateGenerationStatus(bookId, "QUEUED")
                    return@withContext Result.retry()
                }
                val existing = audiobookDao.getChapterAudio(bookId).firstOrNull { it.chapterIndex == chapterIndex }
                if (existing?.status == READY && existing.filePath?.let { path -> path.endsWith(".m4a", ignoreCase = true) && File(path).isFile } == true) return@forEachIndexed
                if (existing?.status == SKIPPED) return@forEachIndexed
                audiobookDao.upsertChapterAudio(ChapterAudioEntity(bookId, chapterIndex, GENERATING))
                // Imported database chunks can contain whole paragraphs. Keep native inference
                // within the native-safe character limit used by foreground playback.
                val sentences = section.chunks.sortedBy { it.sequenceNumber }
                    .flatMap { TtsTextParser.sentences(it.text) }
                if (sentences.isEmpty()) {
                    // A blank/whitespace-only section (common from OCR or malformed source
                    // markup) is not a generation failure — it has nothing to synthesize. Failing
                    // the whole book over one empty chapter blocked every other chapter from ever
                    // reaching READY. Playback already only requires non-blank chapters (see
                    // ReaderViewModel/PlaybackService completeness checks), so skipping here is safe.
                    audiobookDao.upsertChapterAudio(ChapterAudioEntity(bookId, chapterIndex, SKIPPED))
                    val completed = chapterIndex + 1
                    val percent = (completed * 100 / book.sections.size.coerceAtLeast(1)).coerceIn(0, 100)
                    audiobookDao.updateGenerationProgress(bookId, completed, percent, 0L)
                    return@forEachIndexed
                }
                val synthesized = sentences.map { sentence ->
                    synthesizeWithRetry(sentence, generation.generationSpeed, generation.voiceId)
                }
                val segments = synthesized.filterNotNull()
                if (synthesized.any { it == null } && segments.isNotEmpty()) {
                    segments.forEach(File::delete)
                    val message = "The offline voice missed ${synthesized.count { it == null }} sentence(s) in chapter ${chapterIndex + 1}."
                    Log.e(TAG, "$message attempt=${runAttemptCount + 1} voice=${generation.voiceId}")
                    if (runAttemptCount >= MAX_EMPTY_OUTPUT_RETRIES) {
                        audiobookDao.updateChapterAudio(bookId, chapterIndex, FAILED, null, 0L, 0L, null, 0)
                        audiobookDao.updateGenerationStatus(bookId, "FAILED", "INCOMPLETE_AUDIO", message)
                        return@withContext Result.failure()
                    }
                    audiobookDao.updateGenerationStatus(bookId, "QUEUED", "INCOMPLETE_AUDIO", message)
                    return@withContext Result.retry()
                }
                if (segments.isEmpty()) {
                    val message = "The offline voice produced no audio for chapter ${chapterIndex + 1}."
                    Log.e(TAG, "$message attempt=${runAttemptCount + 1} voice=${generation.voiceId}")
                    if (runAttemptCount >= MAX_EMPTY_OUTPUT_RETRIES) {
                        audiobookDao.updateChapterAudio(bookId, chapterIndex, FAILED, null, 0L, 0L, null, 0)
                        audiobookDao.updateGenerationStatus(bookId, "FAILED", "NO_AUDIO_OUTPUT", message)
                        return@withContext Result.failure()
                    }
                    audiobookDao.updateGenerationStatus(bookId, "QUEUED", "NO_AUDIO_OUTPUT", message)
                    return@withContext Result.retry()
                }
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
            Log.e(TAG, "Audiobook generation failed for $bookId", error)
            audiobookDao.updateGenerationStatus(bookId, "FAILED", "GENERATION_FAILED", error.message)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            GenerationNotification.create(applicationContext),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

    private suspend fun synthesizeWithRetry(text: String, speed: Float, voiceId: String): File? {
        repeat(SYNTHESIS_RETRIES) { attempt ->
            generator.synthesizeForAudiobook(text, speed, voiceId)?.let { return it }
            Log.w(TAG, "Retrying empty sentence output attempt=${attempt + 1}/$SYNTHESIS_RETRIES")
        }
        return null
    }

    companion object {
        const val BOOK_ID = "bookId"
        private const val READY = "READY"
        private const val GENERATING = "GENERATING"
        private const val FAILED = "FAILED"
        private const val SKIPPED = "SKIPPED"
        private const val MAX_EMPTY_OUTPUT_RETRIES = 2
        private const val SYNTHESIS_RETRIES = 2
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "GenerateAudiobookWorker"
    }
}

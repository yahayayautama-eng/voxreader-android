package com.example.audiobook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.AppDatabase
import com.example.data.local.datastore.AppSettingsManager
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class GenerateAudiobookWorkerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var audioFileStore: AudioFileStore
    private lateinit var appSettingsManager: AppSettingsManager
    private lateinit var synthesisDir: File
    private lateinit var fakeSynthesizer: FakeAudiobookSynthesizer

    private val bookId = "book-1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        audioFileStore = AudioFileStore(context)
        appSettingsManager = AppSettingsManager(context)
        synthesisDir = File(context.filesDir, "fake-synth-output")
        fakeSynthesizer = FakeAudiobookSynthesizer(synthesisDir)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildWorker(): GenerateAudiobookWorker {
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = GenerateAudiobookWorker(
                appContext,
                workerParameters,
                db,
                db.audiobookDao(),
                audioFileStore,
                fakeSynthesizer,
                appSettingsManager
            )
        }
        return TestListenableWorkerBuilder<GenerateAudiobookWorker>(context)
            .setInputData(androidx.work.workDataOf(GenerateAudiobookWorker.BOOK_ID to bookId))
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun seedBook(sectionTexts: List<String?>) {
        db.bookDao().insertBook(
            BookEntity(bookId, "Title", "Author", "", "", "#000000", sectionTexts.size, false)
        )
        sectionTexts.forEachIndexed { index, text ->
            val sectionId = "section-$index"
            db.bookDao().insertSections(
                listOf(SectionEntity(sectionId, bookId, index, "Chapter $index", 1))
            )
            if (text != null) {
                db.bookDao().insertTextChunks(
                    listOf(TextChunkEntity("chunk-$index", sectionId, 0, text))
                )
            }
        }
        db.audiobookDao().upsertGeneration(
            com.example.data.local.entity.AudiobookGenerationEntity(
                bookId = bookId,
                status = "QUEUED",
                totalChapters = sectionTexts.size,
                voiceId = "voices/kitten/en-US-bella.bin",
                modelVersion = "test"
            )
        )
    }

    @Test
    fun blankSection_isSkipped_andBookStillCompletes() = runTest {
        seedBook(listOf("First chapter text.", "   ", "Third chapter text."))
        buildWorker().doWork()

        val chapters = db.audiobookDao().getChapterAudio(bookId).sortedBy { it.chapterIndex }
        assertEquals("READY", chapters[0].status)
        assertEquals("SKIPPED", chapters[1].status)
        assertEquals("READY", chapters[2].status)
        assertEquals("READY", db.audiobookDao().getGeneration(bookId)?.status)
    }

    private fun buildWorkerWithAttempt(attempt: Int): GenerateAudiobookWorker {
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = GenerateAudiobookWorker(
                appContext, workerParameters, db, db.audiobookDao(), audioFileStore, fakeSynthesizer, appSettingsManager
            )
        }
        return TestListenableWorkerBuilder<GenerateAudiobookWorker>(context)
            .setInputData(androidx.work.workDataOf(GenerateAudiobookWorker.BOOK_ID to bookId))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun permanentlyFailingSentence_neverYieldsReadyAudio() = runTest {
        seedBook(listOf("This sentence will never synthesize."))
        fakeSynthesizer.alwaysFail("This sentence will never synthesize.")

        // MAX_EMPTY_OUTPUT_RETRIES gates on runAttemptCount, which WorkManager itself would advance
        // on each redelivery of the same job; a fresh worker per attempt mirrors that here.
        var lastResult: ListenableWorker.Result? = null
        for (attempt in 0..2) {
            lastResult = buildWorkerWithAttempt(attempt).doWork()
        }

        assertTrue(lastResult is ListenableWorker.Result.Failure)
        assertEquals("FAILED", db.audiobookDao().getGeneration(bookId)?.status)
        val chapter = db.audiobookDao().getChapterAudio(bookId).first()
        assertTrue(chapter.status != "READY")
    }

    @Test
    fun resumeAfterInterruption_convergesAndDoesNotRedoCompletedChapters() = runTest {
        seedBook(listOf("Chapter zero sentence.", "Chapter one sentence."))

        // Simulate a process kill right after chapter 0 finishes synthesizing, before chapter 1
        // is even attempted, by asking the worker to stop as soon as its lone sentence lands.
        val firstWorker = buildWorker()
        fakeSynthesizer.afterCall = { _, text ->
            if (text == "Chapter zero sentence.") firstWorker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
        }
        val firstResult = firstWorker.doWork()
        assertTrue(firstResult is ListenableWorker.Result.Retry)
        assertEquals("READY", db.audiobookDao().getChapterAudio(bookId).first { it.chapterIndex == 0 }.status)
        assertEquals("QUEUED", db.audiobookDao().getGeneration(bookId)?.status)
        val callsAfterFirstRun = fakeSynthesizer.callsByText["Chapter zero sentence."] ?: 0
        assertTrue(callsAfterFirstRun > 0)

        // Fresh worker instance on the same DB, simulating a process restart.
        fakeSynthesizer.afterCall = null
        buildWorker().doWork()

        val callsAfterSecondRun = fakeSynthesizer.callsByText["Chapter zero sentence."] ?: 0
        assertEquals(callsAfterFirstRun, callsAfterSecondRun)

        val chapters = db.audiobookDao().getChapterAudio(bookId).sortedBy { it.chapterIndex }
        assertTrue(chapters.all { it.status == "READY" })
        assertEquals("READY", db.audiobookDao().getGeneration(bookId)?.status)
    }

    @Test
    fun rerunningFinishedJob_isNoOp() = runTest {
        seedBook(listOf("Only chapter sentence."))

        buildWorker().doWork()
        val callCountAfterFirstRun = fakeSynthesizer.callCount

        buildWorker().doWork()
        assertEquals(callCountAfterFirstRun, fakeSynthesizer.callCount)
        assertEquals("READY", db.audiobookDao().getGeneration(bookId)?.status)
    }
}

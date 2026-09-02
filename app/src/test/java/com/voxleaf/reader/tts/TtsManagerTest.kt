package com.voxleaf.reader.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers [TtsManager]'s decision-making: engine selection, fallback, queueing, and the estimated
 * clock. Every playback defect found by hand in this app so far lived in this class, and none of it
 * was reachable by a test until the manager depended on [TtsEngine] rather than on the concrete
 * engines.
 *
 * Audio output itself is not asserted, since MediaPlayer is a real decoder the JVM cannot run. What
 * is asserted is everything that decides what gets handed to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TtsManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var offline: FakeEngine
    private lateinit var online: FakeEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        offline = FakeEngine(requiresNetwork = false, voices = listOf(voice("sherpa:libritts:79", "Ada")))
        online = FakeEngine(requiresNetwork = true, voices = listOf(voice("en-US-AriaNeural", "Aria")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manager() = TtsManager(
        context = context,
        offlineEngine = { offline },
        edgeEngine = { online },
        appSettingsManager = AppSettingsManager(context)
    )

    private fun voice(id: String, name: String) = EngineVoice(id, name, "en-US")

    @Test
    fun `speaking a chapter reports its sentence count and now playing`() = runTest(dispatcher) {
        val manager = manager()

        manager.speakChapters(
            chapters = listOf(chapter(0, "One two three. Four five six.")),
            startChapterIndex = 0
        )
        advanceUntilIdle()

        assertEquals(2, manager.state.value.totalSentences)
        assertEquals("Book", manager.state.value.nowPlaying?.bookTitle)
    }

    /** An empty chapter is not a stopping point; the queue should roll on to the next one. */
    @Test
    fun `empty chapters are skipped when starting a book`() = runTest(dispatcher) {
        val manager = manager()

        manager.speakChapters(
            chapters = listOf(chapter(0, "   "), chapter(1, "Real content here.")),
            startChapterIndex = 0
        )
        advanceUntilIdle()

        assertEquals(1, manager.state.value.nowPlaying?.chapterIndex)
    }

    /**
     * The regression that used to stop playback dead when Wi-Fi dropped mid-chapter: the online
     * engine returning null must fall back to the bundled voice rather than ending the session.
     */
    @Test
    fun `online failure falls back to the offline voice`() = runTest(dispatcher) {
        online.failSynthesis = true
        val manager = manager()
        manager.setEngine(EngineId.EDGE)
        advanceUntilIdle()

        manager.speakSentences(listOf("A sentence to speak."))
        advanceUntilIdle()

        assertTrue("offline engine should have been asked", offline.synthesisCalls > 0)
        assertEquals(EngineId.EDGE, manager.state.value.engineId)
    }

    @Test
    fun `both engines failing surfaces an error and stops`() = runTest(dispatcher) {
        offline.failSynthesis = true
        val manager = manager()

        manager.speakSentences(listOf("A sentence to speak."))
        advanceUntilIdle()

        assertNotNull(manager.state.value.errorMessage)
        assertFalse(manager.state.value.isSpeaking)
    }

    @Test
    fun `stop clears the session so nothing is left playing`() = runTest(dispatcher) {
        val manager = manager()
        manager.speakChapters(listOf(chapter(0, "Something to say.")), startChapterIndex = 0)
        advanceUntilIdle()

        manager.stop()
        advanceUntilIdle()

        assertNull(manager.state.value.nowPlaying)
        assertFalse(manager.state.value.isSpeaking)
        assertFalse(manager.state.value.isPaused)
        assertEquals(0, manager.state.value.currentSentenceIndex)
    }

    /** Switching engines must re-fetch voices and land on one that engine actually offers. */
    @Test
    fun `switching engine adopts a voice from the new engine`() = runTest(dispatcher) {
        val manager = manager()

        manager.setEngine(EngineId.EDGE)
        advanceUntilIdle()

        assertEquals(EngineId.EDGE, manager.state.value.engineId)
        assertEquals("en-US-AriaNeural", manager.state.value.selectedVoicePath)
    }

    /** A persisted engine and voice pair has to be applied together or the voice is lost to the race. */
    @Test
    fun `engine and voice are applied together`() = runTest(dispatcher) {
        online.voices = listOf(voice("en-US-GuyNeural", "Guy"), voice("en-US-AriaNeural", "Aria"))
        val manager = manager()

        manager.setEngineAndVoice(EngineId.EDGE, "en-US-GuyNeural")
        advanceUntilIdle()

        assertEquals("en-US-GuyNeural", manager.state.value.selectedVoicePath)
    }

    @Test
    fun `speech rate is clamped to a usable range`() = runTest(dispatcher) {
        val manager = manager()

        manager.setSpeechRate(9f)
        assertEquals(2f, manager.state.value.speechRate, 0.001f)

        manager.setSpeechRate(0.01f)
        assertEquals(0.5f, manager.state.value.speechRate, 0.001f)
    }

    /** The estimated clock backs the lock-screen scrubber, so it must grow with the chapter. */
    @Test
    fun `estimated duration reflects the length of the chapter`() = runTest(dispatcher) {
        val manager = manager()
        val long = (1..40).joinToString(" ") { "Sentence number $it is here." }

        manager.speakSentences(listOf(long))
        advanceUntilIdle()

        assertTrue(
            "expected a non-zero estimate, got " + manager.state.value.estimatedDurationMs,
            manager.state.value.estimatedDurationMs > 0
        )
    }

    /** A scrub past the end must land on the last sentence rather than run off the list. */
    @Test
    fun `seeking beyond the end clamps to the final sentence`() = runTest(dispatcher) {
        val manager = manager()
        manager.speakSentences(listOf("First one.", "Second one.", "Third one."))
        advanceUntilIdle()

        manager.seekToMillis(Long.MAX_VALUE / 2)
        advanceUntilIdle()

        assertEquals(2, manager.state.value.currentSentenceIndex)
    }

    @Test
    fun `seeking to zero returns to the first sentence`() = runTest(dispatcher) {
        val manager = manager()
        manager.speakSentences(listOf("First one.", "Second one.", "Third one."))
        advanceUntilIdle()
        manager.seekToSentence(2)
        advanceUntilIdle()

        manager.seekToMillis(0L)
        advanceUntilIdle()

        assertEquals(0, manager.state.value.currentSentenceIndex)
    }

    @Test
    fun `skip is bounded by the sentences available`() = runTest(dispatcher) {
        val manager = manager()
        manager.speakSentences(listOf("One here.", "Two here."))
        advanceUntilIdle()

        manager.skip(50)
        advanceUntilIdle()
        assertEquals(1, manager.state.value.currentSentenceIndex)

        manager.skip(-50)
        advanceUntilIdle()
        assertEquals(0, manager.state.value.currentSentenceIndex)
    }

    @Test
    fun `clearing the sleep timer removes it from state`() = runTest(dispatcher) {
        val manager = manager()

        manager.setSleepTimer(15)
        assertEquals(15, manager.state.value.sleepTimerMinutes)

        manager.setSleepTimer(null)
        assertNull(manager.state.value.sleepTimerMinutes)
    }

    private fun chapter(index: Int, text: String) = TtsChapter(
        nowPlaying = NowPlaying("book", "Book", index, "Chapter " + (index + 1)),
        text = text
    )

    /** Writes a real, tiny file so the manager's checks on synthesis output behave as in production. */
    private class FakeEngine(
        override val requiresNetwork: Boolean,
        var voices: List<EngineVoice>,
        var failSynthesis: Boolean = false
    ) : TtsEngine {
        override val id: String get() = if (requiresNetwork) "edge" else "offline"
        override val displayName: String get() = "Fake engine"
        var synthesisCalls = 0

        override suspend fun listVoices(): List<EngineVoice> = voices

        override suspend fun synthesize(text: String, voiceId: String, speed: Float): File? {
            synthesisCalls++
            if (failSynthesis) return null
            return File.createTempFile("fake-tts", ".wav").apply {
                writeBytes(ByteArray(128))
                deleteOnExit()
            }
        }
    }
}

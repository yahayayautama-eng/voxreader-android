package com.example.domain.usecase.tts

import com.example.domain.model.tts.SynthesisRequest
import com.example.domain.model.tts.SynthesisResult
import com.example.domain.model.tts.ModelState
import com.example.domain.model.tts.TtsVoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@OptIn(ExperimentalCoroutinesApi::class)
class SynthesisCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheManager: TtsCacheManager
    private lateinit var ttsEngine: FakeTtsEngineForTesting
    private lateinit var coordinator: SynthesisCoordinator
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        cacheManager = TtsCacheManager()
        cacheManager.init(tempFolder.newFolder("context"))
        
        ttsEngine = FakeTtsEngineForTesting()
        coordinator = SynthesisCoordinator(ttsEngine, cacheManager, CoroutineScope(testDispatcher))
    }
    
    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOrderingAndCache() = runTest(testDispatcher) {
        val chunks = listOf("Chunk 1", "Chunk 2", "Chunk 3")
        coordinator.start(chunks, 0, "voice_1", 1.0f)
        
        val gen = coordinator.getCurrentGenerationId()
        
        val first = coordinator.waitForNextReadyChunk(gen)
        assertNotNull(first)
        assertEquals(0, first!!.first.index)
        assertEquals("Chunk 1", first.first.text)
        
        val second = coordinator.waitForNextReadyChunk(gen)
        assertNotNull(second)
        assertEquals(1, second!!.first.index)
        assertEquals("Chunk 2", second.first.text)
        
        val key = cacheManager.generateKey("Chunk 1", "voice_1", 1.0f, "1.0.0")
        assertNotNull(cacheManager.getCachedAudio(key))
    }

    @Test
    fun testRapidSeekCancelsOldGeneration() = runTest(testDispatcher) {
        val chunks = listOf("Chunk 1", "Chunk 2", "Chunk 3", "Chunk 4")
        coordinator.start(chunks, 0, "voice_1", 1.0f)
        
        val oldGen = coordinator.getCurrentGenerationId()
        
        coordinator.start(chunks, 2, "voice_1", 1.0f)
        val newGen = coordinator.getCurrentGenerationId()
        
        assertNotEquals(oldGen, newGen)
        
        val next = coordinator.waitForNextReadyChunk(newGen)
        assertNotNull(next)
        assertEquals(2, next!!.first.index)
        assertEquals("Chunk 3", next.first.text)
    }
    
    @Test
    fun testEvictionWorks() = runTest(testDispatcher) {
        for (i in 0..100) {
            val largeData = ByteArray(1024 * 1024)
            cacheManager.storeAudio("dummy_$i", largeData)
        }
        
        val cacheDir = File(tempFolder.root, "context/tts_cache")
        val files = cacheDir.listFiles()?.size ?: 0
        assertTrue("Cache size is $files", files <= 52)
    }
}

class FakeTtsEngineForTesting : TtsEngine {
    override val modelState: Flow<ModelState> = flowOf(ModelState.Installed("path", "1.0", 100f))
    
    override suspend fun getAvailableVoices(): List<TtsVoice> = emptyList()

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
        val file = File.createTempFile("fake_audio", ".wav")
        file.writeBytes("fake wav content".toByteArray())
        return Result.success(
            SynthesisResult(
                audioFilePath = file.absolutePath,
                sampleRate = 22050,
                audioDurationMs = 1000,
                inferenceTimeMs = 100,
                realTimeFactor = 0.1f,
                tokenCount = request.text.length,
                memoryUsedMb = 10f
            )
        )
    }

    override fun close() {}
}

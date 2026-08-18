package com.voxleaf.reader.tts.onnx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.domain.model.tts.SynthesisRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FakeTtsEngineTest {

    private lateinit var context: Context
    private lateinit var engine: FakeTtsEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = FakeTtsEngine(context)
    }

    @Test
    fun testSynthesizeCreatesValidWav() = runTest {
        val request = SynthesisRequest(
            text = "Welcome to the offline reader.",
            voiceId = "voice_1"
        )

        val result = engine.synthesize(request)
        assertTrue(result.isSuccess)
        
        val synthesisResult = result.getOrNull()!!
        val file = File(synthesisResult.audioFilePath)
        
        assertTrue(file.exists())
        assertTrue(file.length() > 44) // larger than WAV header

        // Basic WAV header validation
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        
        val riff = ByteArray(4).apply { buffer.get(this) }
        assertTrue("RIFF".toByteArray().contentEquals(riff))
        
        buffer.position(8)
        val wave = ByteArray(4).apply { buffer.get(this) }
        assertTrue("WAVE".toByteArray().contentEquals(wave))
    }
}

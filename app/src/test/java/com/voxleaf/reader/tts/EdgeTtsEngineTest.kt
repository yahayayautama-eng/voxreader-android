package com.voxleaf.reader.tts

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Covers the pure, deterministic pieces of the Edge TTS protocol: message framing and the anti-abuse
 * token shape. The WebSocket round trip itself isn't unit-testable without a live connection or a
 * fake server, and is instead verified against the real endpoint on-device.
 */
class EdgeTtsEngineTest {

    private fun engine(): EdgeTtsEngine {
        val context = mockk<Context> {
            every { cacheDir } returns Files.createTempDirectory("edge-tts-test").toFile()
        }
        return EdgeTtsEngine(context = context)
    }

    // ---- Binary frame parsing ----------------------------------------------------------------

    @Test
    fun `extracts audio bytes after a length-prefixed header`() {
        val header = "X-RequestId:abc\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)
        val audioBytes = byteArrayOf(1, 2, 3, 4)
        val lengthPrefix = byteArrayOf((headerBytes.size ushr 8).toByte(), headerBytes.size.toByte())
        val frame = (lengthPrefix + headerBytes + audioBytes).toByteString()

        val result = engine().extractAudioPayload(frame)

        assertTrue(result?.contentEquals(audioBytes) == true)
    }

    @Test
    fun `ignores a non-audio frame such as metadata`() {
        val header = "Path:audio.metadata\r\n\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)
        val lengthPrefix = byteArrayOf((headerBytes.size ushr 8).toByte(), headerBytes.size.toByte())
        val frame = (lengthPrefix + headerBytes).toByteString()

        assertNull(engine().extractAudioPayload(frame))
    }

    @Test
    fun `returns null instead of throwing on a truncated frame`() {
        assertNull(engine().extractAudioPayload("x".encodeUtf8()))
        assertNull(engine().extractAudioPayload(byteArrayOf(0, 100).toByteString()))
    }

    // ---- SSML message construction -----------------------------------------------------------

    @Test
    fun `ssml embeds the voice id and text`() {
        val message = engine().ssmlMessage("Hello world.", "en-US-AriaNeural", speed = 1.0f)
        assertTrue(message.contains("Path:ssml"))
        assertTrue(message.contains("voice name='en-US-AriaNeural'"))
        assertTrue(message.contains("Hello world."))
        assertTrue(message.contains("rate='+0%'"))
    }

    @Test
    fun `speed above 1 becomes a positive rate percentage`() {
        val message = engine().ssmlMessage("Text.", "en-US-AriaNeural", speed = 1.25f)
        assertTrue(message.contains("rate='+25%'"))
    }

    @Test
    fun `speed below 1 becomes a negative rate percentage`() {
        val message = engine().ssmlMessage("Text.", "en-US-AriaNeural", speed = 0.75f)
        assertTrue(message.contains("rate='-25%'"))
    }

    @Test
    fun `xml special characters in the text are escaped`() {
        val message = engine().ssmlMessage("Tom & Jerry <said> \"hi\"", "en-US-AriaNeural", speed = 1.0f)
        assertTrue(message.contains("Tom &amp; Jerry &lt;said&gt; &quot;hi&quot;"))
        assertTrue(!message.contains("<said>"))
    }

    // ---- Anti-abuse token ----------------------------------------------------------------------

    @Test
    fun `sec-ms-gec is 64 uppercase hex characters`() {
        val token = engine().secMsGec()
        assertEquals(64, token.length)
        assertTrue(token.all { it in "0123456789ABCDEF" })
    }

    @Test
    fun `sec-ms-gec is stable within the same time window`() {
        val engine = engine()
        assertEquals(engine.secMsGec(), engine.secMsGec())
    }
}

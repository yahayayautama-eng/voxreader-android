package com.example.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online voices via the same WebSocket endpoint Microsoft Edge's "Read Aloud" feature uses.
 *
 * There is no public API for this — it is a reverse-engineered protocol, documented and reimplemented
 * by many open-source projects (e.g. `rany2/edge-tts`, MIT licensed); this is an independent Kotlin
 * implementation of that publicly-known protocol shape, not a port of any one of them. It requires no
 * account or key: [TRUSTED_CLIENT_TOKEN] is the same fixed public token every such client uses, and
 * [secMsGec] recreates the anti-abuse header Microsoft's own web client computes client-side.
 *
 * One WebSocket connection is opened per sentence, matching how [KokoroNativeEngine] is called from
 * [TtsManager]'s buffering loop.
 * ponytail: multiplexing many sentences over one persistent connection would save a handshake per
 * sentence; add it if network latency between sentences becomes the bottleneck.
 */
@Singleton
class EdgeTtsEngine @Inject constructor(
    @ApplicationContext context: Context
) : TtsEngine {

    override val id: String get() = EngineId.EDGE.storageKey
    override val displayName: String get() = "Edge TTS (online)"
    override val requiresNetwork: Boolean get() = true

    private val cacheDir = File(context.cacheDir, "edge-tts").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedVoices: List<EngineVoice>? = null

    override suspend fun listVoices(): List<EngineVoice> {
        cachedVoices?.let { return it }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$VOICE_LIST_URL?trustedclienttoken=$TRUSTED_CLIENT_TOKEN")
                .header("User-Agent", USER_AGENT)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val voices = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string() ?: return@use emptyList()
                    json.decodeFromString<List<EdgeVoiceDto>>(body)
                        .map { EngineVoice(id = it.shortName, displayName = friendlyName(it), locale = it.locale, gender = it.gender) }
                        .sortedBy { it.displayName }
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Could not fetch Edge TTS voice list", exception)
                emptyList()
            }
            if (voices.isNotEmpty()) cachedVoices = voices
            voices
        }
    }

    /** "Microsoft Server Speech Text to Speech Voice (en-US, AriaNeural)" -> "Aria (English, US)". */
    private fun friendlyName(dto: EdgeVoiceDto): String {
        val shortSuffix = dto.shortName.substringAfterLast('-').removeSuffix("Neural")
        val language = Locale.forLanguageTag(dto.locale).displayName.takeIf { it.isNotBlank() } ?: dto.locale
        return "$shortSuffix ($language)"
    }

    override suspend fun synthesize(text: String, voiceId: String, speed: Float): File? {
        if (text.isBlank()) return null
        return withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runSynthesis(text, voiceId, speed) }
        }
    }

    private suspend fun runSynthesis(text: String, voiceId: String, speed: Float): File? {
        val output = File(cacheDir, "edge-${System.nanoTime()}.mp3")
        val outputStream = FileOutputStream(output)
        val result = CompletableDeferred<Boolean>()
        var receivedAnyAudio = false

        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val gec = secMsGec()
        val url = "$SYNTHESIS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=$gec&Sec-MS-GEC-Version=$GEC_VERSION&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(url)
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(speechConfigMessage())
                webSocket.send(ssmlMessage(text, voiceId, speed))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    if (!result.isCompleted) result.complete(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audio = extractAudioPayload(bytes) ?: return
                if (audio.isNotEmpty()) {
                    outputStream.write(audio)
                    receivedAnyAudio = true
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Edge TTS connection failed", t)
                if (!result.isCompleted) result.complete(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) result.complete(receivedAnyAudio)
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        val succeeded = try {
            result.await()
        } finally {
            runCatching { outputStream.close() }
        }

        return if (succeeded && receivedAnyAudio && output.length() > 0) {
            output
        } else {
            webSocket.cancel()
            output.delete()
            null
        }
    }

    /** Binary frame layout: 2-byte big-endian header length, header text, then raw audio bytes. */
    internal fun extractAudioPayload(frame: ByteString): ByteArray? {
        if (frame.size < 2) return null
        val headerLength = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
        val audioStart = 2 + headerLength
        if (audioStart > frame.size) return null
        val header = frame.substring(2, audioStart).utf8()
        // Exact line match: "Path:audio.metadata" (word-boundary timings, not audio) also starts with
        // "Path:audio" and would otherwise slip through a loose `contains` check.
        if (!header.contains("Path:audio\r\n")) return null
        return frame.substring(audioStart).toByteArray()
    }

    private fun speechConfigMessage(): String =
        "X-Timestamp:${timestamp()}\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""

    internal fun ssmlMessage(text: String, voiceId: String, speed: Float): String {
        val ratePercent = ((speed - 1f) * 100).toInt()
        val rateAttr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
            "<voice name='${voiceId.xmlEscape()}'>" +
            "<prosody rate='$rateAttr'>${text.xmlEscape()}</prosody>" +
            "</voice></speak>"
        return "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp()}\r\n" +
            "Path:ssml\r\n\r\n$ssml"
    }

    private fun timestamp(): String =
        java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

    /**
     * Reproduces the `Sec-MS-GEC` anti-abuse token Microsoft's own web client derives locally: a
     * SHA-256 of the current time — as Windows "ticks" (100ns units since 1601-01-01), rounded down to
     * a 5-minute window — concatenated with [TRUSTED_CLIENT_TOKEN].
     */
    internal fun secMsGec(): String {
        val windowsEpochOffsetSeconds = 11_644_473_600L
        val nowSeconds = System.currentTimeMillis() / 1000L
        val roundedSeconds = (nowSeconds + windowsEpochOffsetSeconds).let { it - (it % 300) }
        val ticks = roundedSeconds * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    internal fun String.xmlEscape(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    @Serializable
    private data class EdgeVoiceDto(
        @SerialName("ShortName") val shortName: String,
        @SerialName("Locale") val locale: String,
        @SerialName("Gender") val gender: String? = null
    )

    private companion object {
        const val TAG = "EdgeTtsEngine"
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val VOICE_LIST_URL = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
        const val SYNTHESIS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        const val GEC_VERSION = "1-143.0.3650.75"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        const val SYNTHESIS_TIMEOUT_MS = 15_000L
    }
}

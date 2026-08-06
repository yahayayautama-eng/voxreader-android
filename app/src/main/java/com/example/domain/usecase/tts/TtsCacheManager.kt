package com.example.domain.usecase.tts

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsCacheManager @Inject constructor() {
    private var cacheDir: File? = null
    private val maxCacheSizeBytes = 50L * 1024 * 1024 // 50MB

    fun init(contextDir: File) {
        cacheDir = File(contextDir, "tts_cache")
        if (cacheDir?.exists() == false) {
            cacheDir?.mkdirs()
        }
    }

    fun getCachedAudio(key: String): File? {
        val dir = cacheDir ?: return null
        val file = File(dir, "$key.wav")
        return if (file.exists()) file else null
    }

    fun generateKey(text: String, voiceId: String, speed: Float, modelVersion: String): String {
        val input = "$text|$voiceId|$speed|$modelVersion"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun storeAudio(key: String, audioData: ByteArray): File? {
        val dir = cacheDir ?: return null
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$key.wav")
        file.writeBytes(audioData)
        enforceCacheLimit()
        return file
    }

    fun enforceCacheLimit() {
        val dir = cacheDir ?: return
        if (!dir.exists()) return
        val files = dir.listFiles()?.filter { it.extension == "wav" }?.sortedBy { it.lastModified() } ?: return
        var currentSize = files.sumOf { it.length() }
        var i = 0
        while (currentSize > maxCacheSizeBytes && i < files.size) {
            val file = files[i]
            val fileLen = file.length()
            if (file.delete()) {
                currentSize -= fileLen
            }
            i++
        }
    }
    
    fun clearCache() {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }
}

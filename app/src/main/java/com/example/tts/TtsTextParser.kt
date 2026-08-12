package com.example.tts

object TtsTextParser {
    fun sentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text
            .replace(Regex("[\\u0000-\\u001F&&[^\\n\\r\\t]]"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .flatMap(::splitLongSentence)
            .filter(String::isNotBlank)
    }

    private fun splitLongSentence(sentence: String): List<String> {
        val normalized = sentence.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= MAX_TTS_CHARS) return listOf(normalized)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        normalized.split(' ').forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > MAX_TTS_CHARS) {
                chunks += current.toString()
                current.clear()
            }
            if (word.length > MAX_TTS_CHARS) {
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current.clear()
                }
                chunks += word.chunked(MAX_TTS_CHARS)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private const val MAX_TTS_CHARS = 240
}

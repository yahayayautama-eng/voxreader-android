package com.voxleaf.reader.tts

import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.kokoroText
import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.kokoroVoiceId
import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.route
import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.spokenVoiceId
import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.voiceId
import com.voxleaf.reader.tts.SherpaTtsEngine.Route
import com.voxleaf.reader.tts.SherpaTtsEngine.VoiceModel.KOKORO
import com.voxleaf.reader.tts.SherpaTtsEngine.VoiceModel.PIPER
import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaTtsEngineRouteTest {
    @Test
    fun `english text with a piper voice stays on piper`() {
        assertEquals(Route(PIPER, 109), route("Chapter one.", voiceId(109)))
    }

    @Test
    fun `chinese text always goes to kokoro, keeping a chosen chinese speaker`() {
        assertEquals(Route(KOKORO, 3), route("今天下雨了。", voiceId(109)))
        assertEquals(Route(KOKORO, 3), route("今天下雨了。", kokoroVoiceId(1)))
        assertEquals(Route(KOKORO, 58), route("今天下雨了。", kokoroVoiceId(58)))
    }

    @Test
    fun `english text with a kokoro voice stays on kokoro in an english speaker`() {
        assertEquals(Route(KOKORO, 2), route("Chapter one.", kokoroVoiceId(2)))
        assertEquals(Route(KOKORO, 0), route("Chapter one.", kokoroVoiceId(58)))
    }

    @Test
    fun `unknown voice ids fall back to the default piper narrator`() {
        assertEquals(Route(PIPER, 79), route("Chapter one.", "old-engine-voice"))
    }

    @Test
    fun `spoken voice names the kokoro speaker that reads chinese, not the chosen narrator`() {
        assertEquals(kokoroVoiceId(3), spokenVoiceId("今天下雨了。", voiceId(79)))
        assertEquals(voiceId(79), spokenVoiceId("Chapter one.", voiceId(79)))
    }

    @Test
    fun `full-width chinese punctuation becomes ascii kokoro can tokenize`() {
        assertEquals("他说:“你好,世界!”(完)", kokoroText("他说：「你好，世界！」（完）"))
        assertEquals("Plain text.", kokoroText("Plain text."))
    }
}

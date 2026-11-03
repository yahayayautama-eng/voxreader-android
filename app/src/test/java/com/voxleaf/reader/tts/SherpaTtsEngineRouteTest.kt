package com.voxleaf.reader.tts

import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.kokoroVoiceId
import com.voxleaf.reader.tts.SherpaTtsEngine.Companion.route
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
}

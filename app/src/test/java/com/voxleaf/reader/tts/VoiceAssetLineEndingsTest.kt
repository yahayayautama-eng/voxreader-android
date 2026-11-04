package com.voxleaf.reader.tts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class VoiceAssetLineEndingsTest {

    /** sherpa-onnx exits the whole process when a CRLF tokens file yields duplicate tokens. */
    @Test
    fun `voice text assets have no carriage returns`() {
        val root = File("../offlinevoices/src/main/assets/sherpa")
        assumeTrue("voice assets not present", root.isDirectory)
        root.walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .forEach { file -> assertFalse("$file has CRLF line endings", file.readBytes().contains('\r'.code.toByte())) }
    }
}

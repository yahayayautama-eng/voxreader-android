package com.voxleaf.reader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubParserTest {

    private fun createZipFile(entries: Map<String, String>): File {
        val file = File.createTempFile("test_epub", ".epub")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `parse valid epub extracts title and text`() {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()

        val contentOpf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test EPUB</dc:title>
                    <dc:creator>EPUB Author</dc:creator>
                </metadata>
                <manifest>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                    <itemref idref="chapter1"/>
                </spine>
            </package>
        """.trimIndent()

        val chapter1Xhtml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter 1 Title</h1>
                <p>Hello EPUB world.</p>
                <ul>
                    <li>List item EPUB</li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val file = createZipFile(mapOf(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to contentOpf,
            "OEBPS/chapter1.xhtml" to chapter1Xhtml
        ))

        val result = EpubParser.parse(file)
        
        assertEquals("Test EPUB", result.title)
        assertEquals("EPUB Author", result.author)
        assertEquals(3, result.paragraphs.size)
        
        assertEquals("Chapter 1 Title", result.paragraphs[0].text)
        assertTrue(result.paragraphs[0].isHeading)
        
        assertEquals("Hello EPUB world.", result.paragraphs[1].text)
        assertTrue(!result.paragraphs[1].isHeading)
        
        assertEquals("- List item EPUB", result.paragraphs[2].text)
    }

    @Test
    fun `parse epub with missing container throws exception`() {
        val file = createZipFile(mapOf("OEBPS/content.opf" to "<package></package>"))
        assertThrows(EpubParser.ParseException::class.java) {
            EpubParser.parse(file)
        }
    }
}

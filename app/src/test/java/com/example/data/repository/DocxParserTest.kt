package com.example.data.repository

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
class DocxParserTest {

    private fun createZipFile(entries: Map<String, String>): File {
        val file = File.createTempFile("test_doc", ".docx")
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
    fun `parse valid docx extracts title and text`() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                        <w:r><w:t>Chapter 1</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r><w:t>Hello </w:t></w:r>
                        <w:r><w:t>World!</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                        <w:r><w:t>List item</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val coreXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Document</dc:title>
                <dc:creator>Test Author</dc:creator>
            </cp:coreProperties>
        """.trimIndent()

        val file = createZipFile(mapOf(
            "word/document.xml" to documentXml,
            "docProps/core.xml" to coreXml
        ))

        val result = DocxParser.parse(file)
        
        assertEquals("Test Document", result.title)
        assertEquals("Test Author", result.author)
        assertEquals(3, result.paragraphs.size)
        
        assertEquals("Chapter 1", result.paragraphs[0].text)
        assertTrue(result.paragraphs[0].isHeading)
        
        assertEquals("Hello World!", result.paragraphs[1].text)
        assertTrue(!result.paragraphs[1].isHeading)
        
        assertEquals("- List item", result.paragraphs[2].text)
    }

    @Test
    fun `parse docx with missing text throws exception`() {
        val file = createZipFile(mapOf("word/document.xml" to "<w:document></w:document>"))
        assertThrows(DocxParser.ParseException::class.java) {
            DocxParser.parse(file)
        }
    }
    
    @Test
    fun `parse zip bomb throws exception`() {
        val file = File.createTempFile("bomb", ".docx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            // Write a lot of spaces to trigger size limit
            val chunk = ByteArray(1024 * 1024) { ' '.code.toByte() }
            for (i in 0..55) { // > 50MB
                zos.write(chunk)
            }
            zos.closeEntry()
        }
        
        assertThrows(DocxParser.ParseException::class.java) {
            DocxParser.parse(file)
        }
    }
}

package com.example.data.repository

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DocxParser {
    
    // Limits to prevent zip bombs and OOM
    private const val MAX_ENTRIES = 10000
    private const val MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024L // 50 MB
    
    class ParseException(message: String) : Exception(message)
    
    data class Paragraph(val text: String, val isHeading: Boolean)
    
    data class DocxResult(
        val title: String?,
        val author: String?,
        val paragraphs: List<Paragraph>
    )

    fun parse(file: File): DocxResult {
        var title: String? = null
        var author: String? = null
        val paragraphs = mutableListOf<Paragraph>()

        var entriesCount = 0
        var totalUncompressedSize = 0L

        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entriesCount++
                if (entriesCount > MAX_ENTRIES) {
                    throw ParseException("Too many entries in DOCX archive.")
                }

                if (!entry.isDirectory) {
                    if (entry.name == "word/document.xml") {
                        val text = parseDocumentXml(zis, { size -> 
                            totalUncompressedSize += size
                            if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                                throw ParseException("Uncompressed data too large.")
                            }
                        })
                        paragraphs.addAll(text)
                    } else if (entry.name == "docProps/core.xml") {
                        val (t, a) = parseCoreProps(zis, { size -> 
                            totalUncompressedSize += size
                            if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                                throw ParseException("Uncompressed data too large.")
                            }
                        })
                        if (t != null) title = t
                        if (a != null) author = a
                    } else {
                        // Skip other files, but consume safely
                        var bytesRead = 0
                        val buffer = ByteArray(8192)
                        var read = zis.read(buffer)
                        while (read != -1) {
                            bytesRead += read
                            totalUncompressedSize += read
                            if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                                throw ParseException("Uncompressed data too large.")
                            }
                            read = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        
        if (paragraphs.isEmpty()) {
            throw ParseException("No text found in DOCX file.")
        }
        
        return DocxResult(title, author, paragraphs)
    }
    
    private fun parseDocumentXml(inputStream: InputStream, onBytesRead: (Int) -> Unit): List<Paragraph> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        val wrappedStream = object : InputStream() {
            override fun read(): Int {
                val b = inputStream.read()
                if (b != -1) onBytesRead(1)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = inputStream.read(b, off, len)
                if (read != -1) onBytesRead(read)
                return read
            }
        }
        
        parser.setInput(wrappedStream, null)
        
        val paragraphs = mutableListOf<Paragraph>()
        var currentParagraph = java.lang.StringBuilder()
        var inParagraph = false
        var inText = false
        var isHeading = false
        var isList = false
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "w:p") {
                        inParagraph = true
                        currentParagraph.clear()
                        isHeading = false
                        isList = false
                    } else if (name == "w:t") {
                        inText = true
                    } else if (name == "w:pStyle") {
                        val style = parser.getAttributeValue(null, "w:val")
                        if (style != null && style.startsWith("Heading")) {
                            isHeading = true
                        }
                    } else if (name == "w:numPr") {
                        isList = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentParagraph.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == "w:p") {
                        inParagraph = false
                        val text = currentParagraph.toString().trim()
                        if (text.isNotEmpty()) {
                            val formattedText = if (isList) "- $text" else text
                            paragraphs.add(Paragraph(formattedText, isHeading))
                        }
                    } else if (name == "w:t") {
                        inText = false
                    }
                }
            }
            eventType = parser.next()
        }
        return paragraphs
    }
    
    private fun parseCoreProps(inputStream: InputStream, onBytesRead: (Int) -> Unit): Pair<String?, String?> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        val wrappedStream = object : InputStream() {
            override fun read(): Int {
                val b = inputStream.read()
                if (b != -1) onBytesRead(1)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = inputStream.read(b, off, len)
                if (read != -1) onBytesRead(read)
                return read
            }
        }
        parser.setInput(wrappedStream, null)
        
        var title: String? = null
        var creator: String? = null
        var inTitle = false
        var inCreator = false
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "dc:title" || name == "title") inTitle = true
                    if (name == "dc:creator" || name == "creator") inCreator = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title = parser.text
                    if (inCreator) creator = parser.text
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == "dc:title" || name == "title") inTitle = false
                    if (name == "dc:creator" || name == "creator") inCreator = false
                }
            }
            eventType = parser.next()
        }
        return Pair(title, creator)
    }
}

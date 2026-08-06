package com.example.data.repository

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object EpubParser {
    
    // Limits to prevent zip bombs and OOM
    private const val MAX_ENTRIES = 10000
    private const val MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024L // 50 MB
    
    class ParseException(message: String) : Exception(message)
    
    data class Paragraph(val text: String, val isHeading: Boolean)
    
    data class EpubResult(
        val title: String?,
        val author: String?,
        val paragraphs: List<Paragraph>
    )

    fun parse(file: File): EpubResult {
        var totalUncompressedSize = 0L
        
        // Step 1: Read all relevant entries into memory or temporary storage
        // For simplicity and speed, we'll read small text entries into memory.
        val entries = mutableMapOf<String, ByteArray>()
        var entriesCount = 0
        
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entriesCount++
                if (entriesCount > MAX_ENTRIES) {
                    throw ParseException("Too many entries in EPUB archive.")
                }
                
                if (!entry.isDirectory) {
                    // Only read xml, opf, html, xhtml, ncx
                    val name = entry.name.lowercase()
                    if (name.endsWith(".xml") || name.endsWith(".opf") || 
                        name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm") || name.endsWith(".ncx")) {
                        
                        val baos = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read = zis.read(buffer)
                        var entrySize = 0L
                        while (read != -1) {
                            baos.write(buffer, 0, read)
                            totalUncompressedSize += read
                            entrySize += read
                            if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE || entrySize > 10 * 1024 * 1024) {
                                throw ParseException("Uncompressed data too large.")
                            }
                            read = zis.read(buffer)
                        }
                        entries[entry.name] = baos.toByteArray()
                    } else {
                        // Skip, but consume bytes
                        var read = zis.read(ByteArray(8192))
                        while (read != -1) {
                            totalUncompressedSize += read
                            if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                                throw ParseException("Uncompressed data too large.")
                            }
                            read = zis.read(ByteArray(8192))
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        
        // Step 2: Parse container.xml to find OPF
        val containerBytes = entries["META-INF/container.xml"] ?: throw ParseException("Missing META-INF/container.xml")
        val opfPath = parseContainerXml(containerBytes.inputStream()) ?: throw ParseException("No OPF file defined in container.xml")
        
        // Step 3: Parse OPF for metadata, manifest, and spine
        val opfBytes = entries[opfPath] ?: throw ParseException("OPF file not found in archive: $opfPath")
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        
        val (title, author, manifest, spine) = parseOpf(opfBytes.inputStream())
        
        // Step 4: Parse each chapter in spine order
        val paragraphs = mutableListOf<Paragraph>()
        for (idref in spine) {
            val href = manifest[idref] ?: continue
            val chapterPath = opfDir + href.substringBefore("#") // Ignore anchors
            
            val chapterBytes = entries[chapterPath]
            if (chapterBytes != null) {
                val chapterParagraphs = parseXhtml(chapterBytes.inputStream())
                paragraphs.addAll(chapterParagraphs)
            }
        }
        
        if (paragraphs.isEmpty()) {
            throw ParseException("No text found in EPUB file.")
        }
        
        return EpubResult(title, author, paragraphs)
    }
    
    private fun parseContainerXml(inputStream: InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        
        var opfPath: String? = null
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val mediaType = parser.getAttributeValue(null, "media-type")
                if (mediaType == "application/oebps-package+xml") {
                    opfPath = parser.getAttributeValue(null, "full-path")
                    break
                }
            }
            eventType = parser.next()
        }
        return opfPath
    }
    
    private data class OpfData(
        val title: String?,
        val author: String?,
        val manifest: Map<String, String>, // id -> href
        val spine: List<String> // idrefs
    )
    
    private fun parseOpf(inputStream: InputStream): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        
        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        
        var inTitle = false
        var inAuthor = false
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "dc:title" || name == "title") inTitle = true
                    if (name == "dc:creator" || name == "creator") inAuthor = true
                    if (name == "item") {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = href
                        }
                    }
                    if (name == "itemref") {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) {
                            spine.add(idref)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title = parser.text
                    if (inAuthor) author = parser.text
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == "dc:title" || name == "title") inTitle = false
                    if (name == "dc:creator" || name == "creator") inAuthor = false
                }
            }
            eventType = parser.next()
        }
        return OpfData(title, author, manifest, spine)
    }
    
    private fun parseXhtml(inputStream: InputStream): List<Paragraph> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        
        val paragraphs = mutableListOf<Paragraph>()
        var currentText = java.lang.StringBuilder()
        var isHeading = false
        var isList = false
        var inBody = false
        
        var currentBlockElement: String? = null
        
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "body") inBody = true
                        if (inBody) {
                            if (name == "p" || name.startsWith("h") && name.length == 2 || name == "div" || name == "li") {
                                currentBlockElement = name
                                currentText.clear()
                                isHeading = name.startsWith("h") && name.length == 2
                                isList = name == "li"
                            } else if (name == "br") {
                                currentText.append(" ")
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inBody && currentBlockElement != null) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "body") inBody = false
                        if (name == currentBlockElement) {
                            val text = currentText.toString().replace(Regex("\\s+"), " ").trim()
                            if (text.isNotEmpty()) {
                                val formattedText = if (isList) "- $text" else text
                                paragraphs.add(Paragraph(formattedText, isHeading))
                            }
                            currentBlockElement = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Ignore malformed XML within chapters
        }
        
        return paragraphs
    }
}

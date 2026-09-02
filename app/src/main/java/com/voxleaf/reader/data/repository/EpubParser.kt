package com.voxleaf.reader.data.repository

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
    private const val MAX_ENTRIES = 20000
    private const val MAX_UNCOMPRESSED_SIZE = 300 * 1024 * 1024L // 300 MB
    private const val MAX_COVER_SIZE = 20 * 1024 * 1024L // 20 MB
    
    class ParseException(message: String) : Exception(message)
    
    data class Paragraph(val text: String, val isHeading: Boolean)

    data class NavTarget(val title: String, val normalizedHref: String, val fragment: String?)

    data class ParagraphWithId(val paragraph: Paragraph, val elementId: String?)

    /**
     * One spine document. EPUBs are authored one file per chapter, so this is the book's real
     * structure — far more reliable than hunting for h1-h6 tags, which plenty of books never use.
     */
    data class Chapter(val title: String?, val paragraphs: List<Paragraph>)

    data class EpubResult(
        val title: String?,
        val author: String?,
        val chapters: List<Chapter>,
        val paragraphs: List<Paragraph>,
        val coverBytes: ByteArray?
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
        
        // Step 3: Parse OPF for metadata, manifest, spine, and navigation
        val opfBytes = entries[opfPath] ?: throw ParseException("OPF file not found in archive: $opfPath")
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        
        val (title, author, manifest, spine, coverHref, navHref) = parseOpf(opfBytes.inputStream())
        
        // Step 4: Parse TOC (NCX and EPUB 3 Navigation Document)
        val ncxTargets = entries.entries
            .firstOrNull { it.key.lowercase().endsWith(".ncx") }
            ?.let { (path, bytes) ->
                val ncxDir = if (path.contains("/")) path.substringBeforeLast("/") + "/" else ""
                parseNcx(bytes.inputStream(), ncxDir)
            }
            ?: emptyList()

        val navTargets = navHref?.let { href ->
            val rawNavPath = opfDir + href.substringBefore("#")
            val navPath = normalizePath(rawNavPath)
            (entries[rawNavPath] ?: entries[navPath])?.let { bytes ->
                val navDir = if (navPath.contains("/")) navPath.substringBeforeLast("/") + "/" else ""
                parseNavDocument(bytes.inputStream(), navDir)
            }
        } ?: emptyList()

        val ncxTargetsByFile = ncxTargets.groupBy { it.normalizedHref }
        val navTargetsByFile = navTargets.groupBy { it.normalizedHref }
        val allTargetsByFile = ncxTargetsByFile + navTargetsByFile

        val ncxTitles = ncxTargets.groupBy { it.normalizedHref }.mapValues { it.value.first().title }
        val navTitles = navTargets.groupBy { it.normalizedHref }.mapValues { it.value.first().title }
        val tocTitles = ncxTitles + navTitles

        // Step 5: Parse each chapter in spine order
        val chapters = mutableListOf<Chapter>()
        for (idref in spine) {
            val href = manifest[idref] ?: continue
            val rawChapterPath = opfDir + href.substringBefore("#")
            val chapterPath = normalizePath(rawChapterPath)

            val chapterBytes = entries[rawChapterPath] ?: entries[chapterPath] ?: continue
            val fileTargets = allTargetsByFile[chapterPath] ?: emptyList()

            if (fileTargets.isNotEmpty()) {
                val fileChapters = splitXhtmlByAnchors(chapterBytes.inputStream(), fileTargets)
                chapters.addAll(fileChapters)
            } else {
                val chapterParagraphs = parseXhtml(chapterBytes.inputStream())
                if (chapterParagraphs.isNotEmpty()) {
                    chapters += Chapter(
                        title = tocTitles[chapterPath]
                            ?: chapterParagraphs.firstOrNull { it.isHeading }?.text,
                        paragraphs = chapterParagraphs
                    )
                }
            }
        }

        // Merge orphan spine items (files without TOC entries and without headings)
        val mergedChapters = mutableListOf<Chapter>()
        for (chapter in chapters) {
            if (chapter.title == null && chapter.paragraphs.none { it.isHeading } && mergedChapters.isNotEmpty()) {
                // Merge into previous chapter
                // removeAt, not removeLast(): List.removeLast() is API 35+, and below that
                // it throws NoSuchMethodError at runtime rather than failing to compile.
                val prev = mergedChapters.removeAt(mergedChapters.lastIndex)
                mergedChapters += Chapter(prev.title, prev.paragraphs + chapter.paragraphs)
            } else {
                mergedChapters += chapter
            }
        }

        val paragraphs = mergedChapters.flatMap { it.paragraphs }

        if (paragraphs.isEmpty()) {
            throw ParseException("No text found in EPUB file.")
        }

        val coverBytes = coverHref?.let { href ->
            readSingleEntry(file, opfDir + href.substringBefore("#"))
        }

        return EpubResult(title, author, mergedChapters, paragraphs, coverBytes)
    }

    /** "OEBPS/./text/../ch1.xhtml" -> "OEBPS/ch1.xhtml", so NCX hrefs and spine hrefs compare equal. */
    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.split("/").forEach { segment ->
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> parts.removeLastOrNull()
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    /** navMap entries from toc.ncx: returns list of NavTarget. */
    private fun parseNcx(inputStream: InputStream, ncxDir: String): List<NavTarget> {
        val targets = mutableListOf<NavTarget>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var inNavLabelText = false
            var pendingTitle: String? = null
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "navpoint" -> pendingTitle = null
                        "text" -> inNavLabelText = true
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src")
                            val label = pendingTitle?.trim()
                            if (src != null && !label.isNullOrEmpty()) {
                                val fullPath = ncxDir + src.substringBefore("#")
                                val fragment = if ("#" in src) src.substringAfter("#") else null
                                targets += NavTarget(label, normalizePath(fullPath), fragment)
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (inNavLabelText) {
                        pendingTitle = (pendingTitle.orEmpty() + parser.text)
                    }
                    XmlPullParser.END_TAG -> if (parser.name.lowercase() == "text") {
                        inNavLabelText = false
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // A malformed TOC just means we fall back to heading-derived titles.
        }
        return targets
    }

    /**
     * EPUB 3 Navigation Document: parses <nav epub:type="toc"> for chapter titles and hrefs.
     * Returns a list of NavTarget for anchor splitting and chapter titles.
     */
    private fun parseNavDocument(inputStream: InputStream, navDir: String): List<NavTarget> {
        val targets = mutableListOf<NavTarget>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            
            var inNav = false
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "nav") {
                            var isToc = false
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i).lowercase()
                                val attrVal = parser.getAttributeValue(i).lowercase()
                                if ((attrName == "epub:type" || attrName == "type") && attrVal.contains("toc")) {
                                    isToc = true
                                    break
                                }
                            }
                            if (isToc) {
                                inNav = true
                            }
                        }
                        if (inNav && name == "a") {
                            val href = parser.getAttributeValue(null, "href")
                            val title = if (parser.isEmptyElementTag) "" else readElementText(parser).trim()
                            if (href != null && title.isNotEmpty()) {
                                val fullPath = navDir + href.substringBefore("#")
                                val fragment = if ("#" in href) href.substringAfter("#") else null
                                targets += NavTarget(title, normalizePath(fullPath), fragment)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.lowercase() == "nav" && inNav) {
                            inNav = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return targets
    }

    /** Reads all text content until the matching end tag. */
    private fun readElementText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private fun splitXhtmlByAnchors(
        inputStream: InputStream,
        targets: List<NavTarget>
    ): List<Chapter> {
        if (targets.size < 2 || targets.all { it.fragment == null }) {
            // Single chapter, no splitting needed
            val paragraphs = parseXhtml(inputStream)
            if (paragraphs.isEmpty()) return emptyList()
            val title = targets.firstOrNull()?.title
                ?: paragraphs.firstOrNull { it.isHeading }?.text
            return listOf(Chapter(title, paragraphs))
        }
        
        // Parse all paragraphs with their element IDs tracked
        val allParagraphs = parseXhtmlWithIds(inputStream)
        if (allParagraphs.isEmpty()) return emptyList()
        val chapters = mutableListOf<Chapter>()
        
        val fragmentToTitle = targets.filter { it.fragment != null }.associate { it.fragment!! to it.title }
        
        var currentTitle = targets.first().title
        var currentParagraphs = mutableListOf<Paragraph>()
        
        for ((paragraph, elementId) in allParagraphs) {
            if (elementId != null && fragmentToTitle.containsKey(elementId)) {
                // Flush current chapter
                if (currentParagraphs.isNotEmpty()) {
                    chapters += Chapter(currentTitle, currentParagraphs.toList())
                    currentParagraphs = mutableListOf()
                }
                currentTitle = fragmentToTitle[elementId] ?: currentTitle
            }
            currentParagraphs += paragraph
        }
        if (currentParagraphs.isNotEmpty()) {
            chapters += Chapter(currentTitle, currentParagraphs.toList())
        }
        
        return chapters
    }

    private fun readSingleEntry(file: File, targetPath: String): ByteArray? {
        return try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == targetPath) {
                        val baos = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read = zis.read(buffer)
                        var size = 0L
                        while (read != -1) {
                            baos.write(buffer, 0, read)
                            size += read
                            if (size > MAX_COVER_SIZE) return null
                            read = zis.read(buffer)
                        }
                        return baos.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            null
        } catch (_: Exception) {
            null
        }
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
        val spine: List<String>, // idrefs
        val coverHref: String?,
        val navHref: String?
    )

    private fun parseOpf(inputStream: InputStream): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        var coverId: String? = null
        var coverHrefFromProperties: String? = null
        var navHref: String? = null

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
                        val properties = parser.getAttributeValue(null, "properties")
                        if (id != null && href != null) {
                            manifest[id] = href
                            if (properties?.contains("cover-image") == true) {
                                coverHrefFromProperties = href
                            }
                            if (properties?.contains("nav") == true) {
                                navHref = href
                            }
                        }
                    }
                    if (name == "itemref") {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) {
                            spine.add(idref)
                        }
                    }
                    if (name == "meta") {
                        if (parser.getAttributeValue(null, "name") == "cover") {
                            coverId = parser.getAttributeValue(null, "content")
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
        val coverHref = coverHrefFromProperties ?: coverId?.let { manifest[it] }
        return OpfData(title, author, manifest, spine, coverHref, navHref)
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
        } catch (_: Exception) {
            // Ignore malformed XML within chapters
        }
        
        return paragraphs
    }

    private fun parseXhtmlWithIds(inputStream: InputStream): List<ParagraphWithId> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        val paragraphs = mutableListOf<ParagraphWithId>()
        var currentText = java.lang.StringBuilder()
        var isHeading = false
        var isList = false
        var inBody = false

        var currentBlockElement: String? = null
        var currentBlockId: String? = null
        var pendingId: String? = null

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "body") inBody = true
                        if (inBody) {
                            val elemId = parser.getAttributeValue(null, "id")
                                ?: (if (name == "a") parser.getAttributeValue(null, "name") else null)
                            if (elemId != null) {
                                if (currentBlockElement != null) {
                                    currentBlockId = elemId
                                } else {
                                    pendingId = elemId
                                }
                            }

                            if (name == "p" || name.startsWith("h") && name.length == 2 || name == "div" || name == "li") {
                                currentBlockElement = name
                                currentBlockId = elemId ?: pendingId
                                pendingId = null
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
                                paragraphs.add(ParagraphWithId(Paragraph(formattedText, isHeading), currentBlockId))
                                currentBlockId = null
                            } else if (currentBlockId != null) {
                                pendingId = currentBlockId
                                currentBlockId = null
                            }
                            currentBlockElement = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Ignore malformed XML within chapters
        }

        return paragraphs
    }
}

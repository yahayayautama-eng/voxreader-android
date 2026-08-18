package com.voxleaf.reader.data.repository

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * FictionBook 2 reader, including the zipped `.fbz` variant.
 *
 * FB2 is the easy one of the Kindle-adjacent formats: it is well-formed XML whose `<section>` and
 * `<title>` elements are the author's own chapter structure, so no heading detection is needed.
 */
object Fb2Parser {

    class ParseException(message: String) : Exception(message)

    data class Fb2Chapter(val title: String?, val paragraphs: List<String>)

    data class Fb2Result(
        val title: String?,
        val author: String?,
        val chapters: List<Fb2Chapter>
    )

    private const val MAX_UNCOMPRESSED_SIZE = 300 * 1024 * 1024L

    fun parse(file: File): Fb2Result {
        val stream = if (file.name.endsWith(".fbz", ignoreCase = true) || isZip(file)) {
            unzipFirstFb2(file)
        } else {
            FileInputStream(file)
        }
        return stream.use(::parseStream)
    }

    private fun isZip(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
    } catch (_: Exception) {
        false
    }

    private fun unzipFirstFb2(file: File): InputStream {
        val zis = ZipInputStream(FileInputStream(file))
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                // Bounded so a zip bomb can't take the process down; the caller closes the stream.
                return BoundedInputStream(zis, MAX_UNCOMPRESSED_SIZE)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        throw ParseException("No FictionBook file inside this archive.")
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val limit: Long
    ) : InputStream() {
        private var read = 0L

        override fun read(): Int = delegate.read().also { if (it != -1) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            delegate.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        override fun close() = delegate.close()

        private fun count(bytes: Long) {
            read += bytes
            if (read > limit) throw ParseException("FictionBook file is too large.")
        }
    }

    private fun parseStream(input: InputStream): Fb2Result {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var bookTitle: String? = null
        var firstName: String? = null
        var lastName: String? = null

        val chapters = mutableListOf<Fb2Chapter>()
        var inBody = false
        var inBinary = false
        // `<title>` appears in both the metadata block and inside sections, so which one we are
        // reading depends entirely on whether <body> has started.
        var inTitle = false
        var currentTitle: String? = null
        var titleText = StringBuilder()
        var paragraphs = mutableListOf<String>()
        var currentText: StringBuilder? = null
        var pendingElement: String? = null

        fun finishChapter() {
            if (paragraphs.isNotEmpty() || currentTitle != null) {
                if (paragraphs.isNotEmpty()) {
                    chapters += Fb2Chapter(currentTitle, paragraphs.toList())
                }
                paragraphs = mutableListOf()
                currentTitle = null
            }
        }

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (val name = parser.name.lowercase()) {
                        "body" -> inBody = true
                        // Cover images and fonts live in <binary>; never speakable text.
                        "binary" -> inBinary = true
                        "section" -> if (inBody) finishChapter()
                        "title" -> {
                            inTitle = true
                            titleText = StringBuilder()
                        }
                        "book-title" -> if (!inBody) {
                            pendingElement = name
                            currentText = StringBuilder()
                        }
                        "first-name", "last-name" -> if (!inBody && firstName == null || !inBody && lastName == null) {
                            pendingElement = name
                            currentText = StringBuilder()
                        }
                        "p", "v", "subtitle" -> if (inBody && !inBinary) {
                            pendingElement = name
                            currentText = StringBuilder()
                        }
                    }

                    XmlPullParser.TEXT -> if (!inBinary) {
                        if (inTitle && inBody) titleText.append(parser.text)
                        currentText?.append(parser.text)
                    }

                    XmlPullParser.END_TAG -> when (val name = parser.name.lowercase()) {
                        "body" -> {
                            finishChapter()
                            inBody = false
                        }
                        "binary" -> inBinary = false
                        "title" -> {
                            inTitle = false
                            if (inBody) {
                                currentTitle = titleText.toString().normalizeSpace().takeIf { it.isNotEmpty() }
                            }
                        }
                        "book-title" -> {
                            if (pendingElement == name) bookTitle = currentText?.toString()?.normalizeSpace()
                            currentText = null
                            pendingElement = null
                        }
                        "first-name" -> {
                            if (pendingElement == name) firstName = currentText?.toString()?.normalizeSpace()
                            currentText = null
                            pendingElement = null
                        }
                        "last-name" -> {
                            if (pendingElement == name) lastName = currentText?.toString()?.normalizeSpace()
                            currentText = null
                            pendingElement = null
                        }
                        "p", "v", "subtitle" -> {
                            if (pendingElement == name) {
                                currentText?.toString()?.normalizeSpace()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { paragraphs += it }
                            }
                            currentText = null
                            pendingElement = null
                        }
                    }
                }
                event = parser.next()
            }
        } catch (exception: ParseException) {
            throw exception
        } catch (_: Exception) {
            // Truncated or malformed XML: keep whatever chapters were already complete.
        }
        finishChapter()

        if (chapters.isEmpty()) throw ParseException("No readable text found in this FictionBook file.")

        return Fb2Result(
            title = bookTitle?.takeIf { it.isNotEmpty() },
            author = listOfNotNull(firstName, lastName)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .takeIf { it.isNotEmpty() },
            chapters = chapters
        )
    }

    private fun String.normalizeSpace(): String = replace(Regex("\\s+"), " ").trim()
}

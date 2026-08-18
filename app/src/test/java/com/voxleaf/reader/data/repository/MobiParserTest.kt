package com.voxleaf.reader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MobiParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // ---- PalmDOC LZ77 -----------------------------------------------------------------------

    @Test
    fun `palmdoc passes literals through`() {
        val input = byteArrayOf(*"Hello".map { it.code.toByte() }.toByteArray())
        assertEquals("Hello", String(MobiParser.decompressPalmDoc(input)))
    }

    @Test
    fun `palmdoc expands a length-distance back-reference`() {
        // Literals "abc", then a pair: top bits 10, distance 3, and a length field of (3 - 3).
        val distance = 3
        val length = 3
        val pair = 0x8000 or (distance shl 3) or (length - 3)
        val input = byteArrayOf(
            'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
            (pair ushr 8).toByte(), (pair and 0xFF).toByte()
        )
        assertEquals("abcabc", String(MobiParser.decompressPalmDoc(input)))
    }

    @Test
    fun `palmdoc expands the space-plus-character shorthand`() {
        // High bit set means: emit a space, then the byte with that bit cleared.
        val input = byteArrayOf(('t'.code or 0x80).toByte())
        assertEquals(" t", String(MobiParser.decompressPalmDoc(input)))
    }

    @Test
    fun `palmdoc copies a run of literal bytes`() {
        val input = byteArrayOf(3, 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte())
        assertEquals("xyz", String(MobiParser.decompressPalmDoc(input)))
    }

    @Test
    fun `palmdoc stops cleanly on a truncated back-reference`() {
        // A damaged file must not hang or throw; whatever decoded so far is kept.
        val input = byteArrayOf('a'.code.toByte(), 0x80.toByte())
        assertEquals("a", String(MobiParser.decompressPalmDoc(input)))
    }

    @Test
    fun `palmdoc rejects decompression beyond its configured limit`() {
        val error = runCatching {
            MobiParser.decompressPalmDoc("too much text".toByteArray(), maxOutputBytes = 8)
        }.exceptionOrNull()

        assertTrue(error is MobiParser.ParseException)
    }

    // ---- Trailing entries -------------------------------------------------------------------

    @Test
    fun `no trailing flags leaves the record untouched`() {
        val record = byteArrayOf(1, 2, 3, 4)
        assertEquals(4, MobiParser.removeTrailingEntries(record, 0).size)
    }

    @Test
    fun `a trailing entry is stripped using its end-encoded length`() {
        // Two payload bytes plus a 3-byte trailing entry whose final byte encodes total length 3.
        val record = byteArrayOf(9, 9, 7, 7, (0x80 or 3).toByte())
        assertEquals(2, MobiParser.removeTrailingEntries(record, 0b10).size)
    }

    @Test
    fun `the multibyte overlap byte is stripped`() {
        val record = byteArrayOf(9, 9, 9, 0b01)
        // Low 2 bits + 1 = 2 bytes removed.
        assertEquals(2, MobiParser.removeTrailingEntries(record, 0b1).size)
    }

    // ---- Section splitting ------------------------------------------------------------------

    @Test
    fun `each KF8 html document becomes a chapter`() {
        val text = """
            <html><head><title>Book</title></head><body><h1>One</h1><p>First body.</p></body></html>
            <html><head><title>Book</title></head><body><h1>Two</h1><p>Second body.</p></body></html>
        """.trimIndent()

        val sections = MobiParser.splitSections(text)

        assertEquals(listOf("One", "Two"), sections.map { it.title })
        assertTrue(sections[0].paragraphs.any { it.text == "First body." })
        assertTrue(sections[1].paragraphs.any { it.text == "Second body." })
    }

    @Test
    fun `MOBI6 splits on page breaks instead`() {
        val text = "<html><body><h2>Alpha</h2><p>One.</p>" +
            "<mbp:pagebreak/><h2>Beta</h2><p>Two.</p></body></html>"

        val sections = MobiParser.splitSections(text)

        assertEquals(listOf("Alpha", "Beta"), sections.map { it.title })
    }

    @Test
    fun `an explicit chapter label beats a caption styled as a heading`() {
        // Found on a real Gutenberg AZW3: the illustration caption is an <h*> and precedes the title.
        val text = "<html><body><h4>He rode a black horse.</h4><h2>CHAPTER II.</h2>" +
            "<p>Mr. Bennet was among the earliest.</p></body></html>"

        assertEquals(listOf("CHAPTER II."), MobiParser.splitSections(text).map { it.title })
    }

    @Test
    fun `a document with no breaks stays one chapter for the importer to split`() {
        val sections = MobiParser.splitSections("<html><body><p>Just prose.</p></body></html>")
        assertEquals(1, sections.size)
    }

    @Test
    fun `falls back to the title tag when a section has no heading`() {
        val text = "<html><head><title>Preface</title></head><body><p>Words.</p></body></html>" +
            "<html><head><title>Chapter</title></head><body><p>More.</p></body></html>"
        assertEquals(listOf("Preface", "Chapter"), MobiParser.splitSections(text).map { it.title })
    }

    // ---- End to end -------------------------------------------------------------------------

    @Test
    fun `parses a minimal uncompressed MOBI file`() {
        val html = "<html><head><title>Ignored</title></head><body><h1>Chapter One</h1>" +
            "<p>The first paragraph.</p></body></html>" +
            "<html><head><title>Ignored</title></head><body><h1>Chapter Two</h1>" +
            "<p>The second paragraph.</p></body></html>"
        val file = writeMobi(temporaryFolder.newFile("book.mobi"), html)

        val result = MobiParser.parse(file)

        assertEquals("Test Kindle Book", result.title)
        assertEquals("Jane Author", result.author)
        assertEquals(listOf("Chapter One", "Chapter Two"), result.chapters.map { it.title })
        assertTrue(result.chapters[0].paragraphs.any { it.text == "The first paragraph." })
    }

    @Test
    fun `rejects a file that is not a Kindle book`() {
        val file = temporaryFolder.newFile("not-a-book.mobi")
        file.writeBytes(ByteArray(200) { 0 })

        val error = runCatching { MobiParser.parse(file) }.exceptionOrNull()
        assertTrue(error is MobiParser.ParseException)
    }

    @Test
    fun `reports DRM rather than failing obscurely`() {
        val file = writeMobi(temporaryFolder.newFile("drm.mobi"), "<html><body><p>x</p></body></html>", encryption = 1)

        val error = runCatching { MobiParser.parse(file) }.exceptionOrNull()
        assertTrue(error is MobiParser.ParseException)
        assertTrue(error!!.message!!.contains("DRM"))
    }

    /**
     * Builds the smallest file the parser will accept: PalmDB header, record table, a record 0
     * holding the PalmDOC/MOBI/EXTH headers, and one uncompressed text record.
     */
    private fun writeMobi(file: File, html: String, encryption: Int = 0): File {
        val title = "Test Kindle Book".toByteArray()
        val author = "Jane Author".toByteArray()

        val mobiHeaderLength = 232
        val exthOffset = mobiHeaderLength + 16
        val exthRecordLength = 8 + author.size
        val exthLength = 12 + exthRecordLength
        val titleOffset = exthOffset + exthLength
        val record0 = ByteArray(titleOffset + title.size)

        fun putU16(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value ushr 8).toByte()
            target[offset + 1] = value.toByte()
        }

        fun putU32(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value ushr 24).toByte()
            target[offset + 1] = (value ushr 16).toByte()
            target[offset + 2] = (value ushr 8).toByte()
            target[offset + 3] = value.toByte()
        }

        fun putString(target: ByteArray, offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).copyInto(target, offset)
        }

        putU16(record0, 0, 1) // compression: none
        putU16(record0, 8, 1) // one text record
        putU16(record0, 10, 4096)
        putU16(record0, 12, encryption)
        putString(record0, 16, "MOBI")
        putU32(record0, 20, mobiHeaderLength)
        putU32(record0, 28, 65001) // UTF-8
        putU32(record0, 36, 6) // MOBI6
        putU32(record0, 84, titleOffset)
        putU32(record0, 88, title.size)
        putU32(record0, 108, 100) // resourceStart, unused here
        putU32(record0, 128, 0x40) // EXTH present
        putU32(record0, 240, 0) // no trailing entries
        putString(record0, exthOffset, "EXTH")
        putU32(record0, exthOffset + 4, exthLength)
        putU32(record0, exthOffset + 8, 1) // one EXTH record
        putU32(record0, exthOffset + 12, 100) // type: creator
        putU32(record0, exthOffset + 16, exthRecordLength)
        author.copyInto(record0, exthOffset + 20)
        title.copyInto(record0, titleOffset)

        val textRecord = html.toByteArray()
        val headerSize = 78 + 2 * 8
        val header = ByteArray(headerSize)
        putString(header, 0, "VoxLeafTest")
        putString(header, 60, "BOOK")
        putString(header, 64, "MOBI")
        putU16(header, 76, 2)
        putU32(header, 78, headerSize)
        putU32(header, 86, headerSize + record0.size)

        file.writeBytes(header + record0 + textRecord)
        return file
    }
}

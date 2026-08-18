package com.voxleaf.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfBookParser {
    private const val MAX_PAGES = 5_000

    class ParseException(message: String) : Exception(message)

    /** A top-level bookmark: the document's own idea of where a chapter starts. */
    data class OutlineEntry(val title: String, val startPage: Int)

    data class PdfResult(
        val title: String?,
        val author: String?,
        /** One entry per document page, blanks included, so [outline] page indices stay aligned. */
        val pages: List<String>,
        val outline: List<OutlineEntry>,
        val coverBitmap: Bitmap?
    )

    suspend fun parse(
        context: Context,
        file: File,
        ocrScanner: OcrScanner? = null,
        onProgress: ((Float) -> Unit)? = null
    ): PdfResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        return try {
            PDDocument.load(file).use { document ->
                if (document.isEncrypted) {
                    throw ParseException("Password-protected PDFs are not supported.")
                }
                val totalPages = document.numberOfPages
                if (totalPages > MAX_PAGES) {
                    throw ParseException("PDF has too many pages. Maximum supported size is $MAX_PAGES pages.")
                }
                val stripper = PDFTextStripper()
                val extractedPages = mutableListOf<String>()
                for (pageNumber in 1..totalPages) {
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    val text = stripper.getText(document)
                        .replace(Regex("\r\n?"), "\n")
                        .replace(Regex("[ \t]+"), " ")
                        .lines()
                        .joinToString("\n") { it.trim() }
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                    extractedPages += text
                }

                val hasDigitalText = extractedPages.any { it.isNotBlank() }
                val finalPages: List<String> = if (!hasDigitalText && ocrScanner != null) {
                    // Scanned / Image-only PDF: Perform on-device OCR page by page
                    val renderer = PDFRenderer(document)
                    val ocrPages = mutableListOf<String>()
                    for (pageIndex in 0 until totalPages) {
                        onProgress?.invoke(pageIndex.toFloat() / totalPages.toFloat())
                        val pageBitmap = try {
                            renderer.renderImage(pageIndex, 1.5f)
                        } catch (_: Exception) {
                            null
                        }
                        val recognizedText = if (pageBitmap != null) {
                            try {
                                ocrScanner.recognize(pageBitmap).trim()
                            } catch (_: Exception) {
                                ""
                            }
                        } else {
                            ""
                        }
                        ocrPages += recognizedText
                    }
                    onProgress?.invoke(1.0f)
                    ocrPages
                } else {
                    extractedPages
                }

                if (finalPages.none { it.isNotBlank() }) {
                    throw ParseException("No selectable text found in this PDF.")
                }
                val coverBitmap = try {
                    PDFRenderer(document).renderImage(0)
                } catch (_: Exception) {
                    null
                }
                PdfResult(
                    title = document.documentInformation?.title?.trim(),
                    author = document.documentInformation?.author?.trim(),
                    pages = finalPages,
                    outline = readOutline(document),
                    coverBitmap = coverBitmap
                )
            }
        } catch (exception: ParseException) {
            throw exception
        } catch (_: Exception) {
            throw ParseException("Could not read this PDF. It may be damaged or unsupported.")
        }
    }

    private fun readOutline(document: PDDocument): List<OutlineEntry> {
        return try {
            val outline = document.documentCatalog.documentOutline
            if (outline == null) {
                emptyList()
            } else {
                val entries = mutableListOf<OutlineEntry>()
                fun traverse(item: PDOutlineItem?) {
                    var current = item
                    while (current != null) {
                        val title = current.title?.trim().orEmpty()
                        val page = try {
                            current.findDestinationPage(document)
                        } catch (_: Exception) {
                            null
                        }
                        val index = page?.let { document.pages.indexOf(it) } ?: -1
                        if (title.isNotEmpty() && index >= 0) {
                            entries += OutlineEntry(title, index)
                        }
                        // Recurse into children
                        current.firstChild?.let { traverse(it) }
                        current = current.nextSibling
                    }
                }
                outline.firstChild?.let { traverse(it) }

                // Smart level selection: if top-level has only 1 entry, use its children instead
                val topLevel = mutableListOf<OutlineEntry>()
                val topLevelItems = outline.children().toList()
                if (topLevelItems.size == 1) {
                    // Single root node, use its children
                    val root = topLevelItems.first()
                    var child = root.firstChild
                    while (child != null) {
                        val title = child.title?.trim().orEmpty()
                        val page = try { child.findDestinationPage(document) } catch (_: Exception) { null }
                        val index = page?.let { document.pages.indexOf(it) } ?: -1
                        if (title.isNotEmpty() && index >= 0) {
                            topLevel += OutlineEntry(title, index)
                        }
                        child = child.nextSibling
                    }
                    if (topLevel.size >= 2) {
                        return topLevel.sortedBy { it.startPage }.distinctBy { it.startPage }
                    }
                }

                // Otherwise use all top-level entries
                entries
                    .sortedBy { it.startPage }
                    .distinctBy { it.startPage }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Strips running headers, footers, and standalone page numbers from extracted PDF pages.
     * Running headers are detected by comparing the first/last lines across consecutive pages.
     */
    fun stripRunningHeaders(pages: List<String>): List<String> {
        if (pages.size < 3) return pages

        // Detect repeated first lines (running headers)
        val firstLines = pages.map { it.lines().firstOrNull()?.trim().orEmpty() }
        val lastLines = pages.map { it.lines().lastOrNull()?.trim().orEmpty() }

        // A line is a running header if it appears as the first line on >= 30% of non-blank pages
        val firstLineCounts = firstLines.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val threshold = (pages.count { it.isNotBlank() } * 0.30).toInt().coerceAtLeast(3)
        val headerLines = firstLineCounts.filter { it.value >= threshold }.keys

        // Same for last lines (running footers)
        val lastLineCounts = lastLines.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val footerLines = lastLineCounts.filter { it.value >= threshold }.keys

        // Standalone page number regex (top or bottom of page)
        val pageNumberRegex = Regex("""^\s*(?:page\s*)?[0-9]{1,4}\s*$""", RegexOption.IGNORE_CASE)

        return pages.map { pageText ->
            val lines = pageText.lines().toMutableList()
            // Strip header
            if (lines.isNotEmpty() && (headerLines.contains(lines.first().trim()) || pageNumberRegex.matches(lines.first().trim()))) {
                lines.removeFirst()
            }
            // Strip footer
            if (lines.isNotEmpty() && (footerLines.contains(lines.last().trim()) || pageNumberRegex.matches(lines.last().trim()))) {
                lines.removeLast()
            }
            lines.joinToString("\n").trim()
        }
    }

    /**
     * Slices [pages] at the [outline] boundaries. Pure so it can be tested without a PDF; returns
     * null when the outline is too thin to be a useful table of contents.
     */
    fun sectionsFromOutline(
        pages: List<String>,
        outline: List<OutlineEntry>
    ): List<ChapterDetector.Section>? {
        if (outline.size < 2) return null
        return outline.mapIndexedNotNull { index, entry ->
            val start = entry.startPage.coerceIn(0, pages.size)
            val end = (outline.getOrNull(index + 1)?.startPage ?: pages.size).coerceIn(start, pages.size)
            val content = pages.subList(start, end).filter { it.isNotBlank() }.joinToString("\n\n")
            if (content.isBlank()) null else ChapterDetector.Section(entry.title, content)
        }.takeIf { it.size >= 2 }
    }
}

package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfBookParser {
    private const val MAX_PAGES = 1_000

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

    fun parse(context: Context, file: File): PdfResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        return try {
            PDDocument.load(file).use { document ->
                if (document.isEncrypted) {
                    throw ParseException("Password-protected PDFs are not supported.")
                }
                if (document.numberOfPages > MAX_PAGES) {
                    throw ParseException("PDF has too many pages. Maximum supported size is $MAX_PAGES pages.")
                }
                val stripper = PDFTextStripper()
                val pages = (1..document.numberOfPages).map { pageNumber ->
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    // Collapse only horizontal whitespace here; line breaks are what let ChapterDetector
                    // recognize an isolated heading line further down the pipeline. Flattening them earlier
                    // (as this used to) made every page one giant line and headings undetectable.
                    stripper.getText(document)
                        .replace(Regex("\r\n?"), "\n")
                        .replace(Regex("[ \t]+"), " ")
                        .lines()
                        .joinToString("\n") { it.trim() }
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                }
                if (pages.none { it.isNotBlank() }) {
                    throw ParseException("No selectable text found. This may be a scanned PDF and needs offline OCR.")
                }
                val coverBitmap = try {
                    PDFRenderer(document).renderImage(0)
                } catch (_: Exception) {
                    null
                }
                PdfResult(
                    title = document.documentInformation.title?.trim(),
                    author = document.documentInformation.author?.trim(),
                    pages = pages,
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

    /**
     * The document's own bookmarks — the only chapter list that is authored rather than guessed.
     * Only the top level is read: nested items are sub-sections, and flattening them would turn a
     * table of contents back into the long flat list this is meant to replace.
     */
    private fun readOutline(document: PDDocument): List<OutlineEntry> = try {
        val outline = document.documentCatalog.documentOutline
        if (outline == null) {
            emptyList()
        } else {
            outline.children().mapNotNull { item ->
                val title = item.title?.trim().orEmpty()
                val page = try {
                    item.findDestinationPage(document)
                } catch (_: Exception) {
                    null
                }
                val index = page?.let { document.pages.indexOf(it) } ?: -1
                if (title.isEmpty() || index < 0) null else OutlineEntry(title, index)
            }
                .sortedBy { it.startPage }
                // Two bookmarks on one page would give the second an empty body; keep the first.
                .distinctBy { it.startPage }
        }
    } catch (_: Exception) {
        emptyList()
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

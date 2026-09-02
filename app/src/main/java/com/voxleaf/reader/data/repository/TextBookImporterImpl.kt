package com.voxleaf.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.voxleaf.reader.data.local.AppDatabase
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import com.voxleaf.reader.domain.usecase.ImportScannedBookUseCase
import com.voxleaf.reader.domain.usecase.ImportStage
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ImportTextBookUseCase
import com.voxleaf.reader.domain.usecase.RedetectChaptersUseCase
import com.voxleaf.reader.domain.usecase.RedetectResult
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import com.voxleaf.reader.domain.usecase.SectionStructureUseCase
import com.voxleaf.reader.domain.usecase.StructurePreview
import com.voxleaf.reader.domain.usecase.StructureResult
import com.voxleaf.reader.domain.usecase.StructureSectionDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class TextBookImporterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val ocrScanner: OcrScanner,
    private val scannedPageProcessor: ScannedPageProcessor
) : ImportTextBookUseCase, ImportScannedBookUseCase, RedetectChaptersUseCase, SectionStructureUseCase {

    /**
     * Re-parses the book's source file with the current [ChapterDetector] and replaces its sections.
     * Only reachable for books imported from a file that's still on disk — the sentence-joined chunks
     * saved at import time lose the line structure the detector needs, so there's no way to upgrade an
     * old page-per-section import in place.
     */
    override suspend fun invoke(bookId: String): RedetectResult = when (val result = analyze(bookId)) {
        is StructureResult.Preview -> {
            if (result.value.current.any { it.isManuallyEdited }) {
                RedetectResult.Error("This document has manual structure edits. Review the preview before applying changes.")
            } else when (val applied = apply(bookId, result.value.proposed)) {
                StructureResult.Applied -> RedetectResult.Success
                is StructureResult.Error -> RedetectResult.Error(applied.message)
                is StructureResult.Preview -> RedetectResult.Error("Could not apply the structure preview.")
            }
        }
        is StructureResult.Error -> RedetectResult.Error(result.message)
        StructureResult.Applied -> RedetectResult.Error("Could not build a structure preview.")
    }

    override suspend fun analyze(bookId: String): StructureResult = withContext(Dispatchers.IO) {
        try {
            val stored = database.bookDao().getBookById(bookId)
                ?: return@withContext StructureResult.Error("Document not found.")
            val path = stored.book.sourceFilePath
                ?: return@withContext StructureResult.Error("Original file isn't available for this document.")
            val file = File(path)
            if (!file.isFile) {
                return@withContext StructureResult.Error("Original file is no longer on this device.")
            }
            val proposed = parseByExtension(file, file.name).sections
                .take(MAX_STRUCTURE_SECTIONS)
                .mapIndexed { index, section -> section.toDraft(bookId, index, manuallyEdited = false) }
            if (proposed.isEmpty()) {
                return@withContext StructureResult.Error("No readable sections were found.")
            }
            val current = stored.sections.sortedBy { it.section.chapterNumber }.map { value ->
                val content = value.chunks.sortedBy { it.sequenceNumber }.joinToString(" ") { it.text }
                StructureSectionDraft(
                    id = value.section.id,
                    title = value.section.title,
                    content = content,
                    detectionSource = value.section.detectionSource,
                    detectionConfidence = value.section.detectionConfidence,
                    detectionReason = value.section.detectionReason,
                    startAnchor = value.section.startAnchor.ifBlank { sectionAnchor(content, true) },
                    endAnchor = value.section.endAnchor.ifBlank { sectionAnchor(content, false) },
                    isManuallyEdited = value.section.isManuallyEdited
                )
            }
            val currentByAnchor = current.associateBy { it.startAnchor to it.endAnchor }
            val proposedByAnchor = proposed.associateBy { it.startAnchor to it.endAnchor }
            val changed = proposed.count { candidate ->
                currentByAnchor[candidate.startAnchor to candidate.endAnchor]?.title != candidate.title
            }
            StructureResult.Preview(
                StructurePreview(
                    current = current,
                    proposed = proposed,
                    addedCount = proposedByAnchor.keys.count { it !in currentByAnchor },
                    removedCount = currentByAnchor.keys.count { it !in proposedByAnchor },
                    changedCount = changed
                )
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            StructureResult.Error("Could not analyze this document's structure.")
        }
    }

    override suspend fun apply(
        bookId: String,
        sections: List<StructureSectionDraft>
    ): StructureResult = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizeDrafts(sections)
            if (normalized.isEmpty()) return@withContext StructureResult.Error("Keep at least one readable section.")
            if (normalized.size > MAX_STRUCTURE_SECTIONS) {
                return@withContext StructureResult.Error("This document exceeds the 500-section review limit.")
            }
            replaceSectionsPreservingAnchors(bookId, normalized)
            StructureResult.Applied
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            StructureResult.Error("The structure could not be applied. Your existing document was kept.")
        }
    }

    override fun invoke(uri: Uri): Flow<ImportState> = flow {
        var destination: File? = null
        emit(ImportState.Importing(0f, ImportStage.PREPARING))

        try {
            if (uri.scheme != "content") {
                emit(ImportState.Error("Please select a TXT, EPUB, DOCX, or PDF document from the system picker."))
                return@flow
            }

            val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let(cursor::getString)
                } else {
                    null
                }
            } ?: "Imported text"

            val mimeType = context.contentResolver.getType(uri)
            val isEpub = displayName.endsWith(".epub", ignoreCase = true) || mimeType == EPUB_MIME_TYPE
            val isPdf = displayName.endsWith(".pdf", ignoreCase = true) || mimeType == PDF_MIME_TYPE
            val isDocx = displayName.endsWith(".docx", ignoreCase = true) || mimeType == DOCX_MIME_TYPE
            // Kindle and FictionBook files are matched on extension: the system picker reports them
            // as octet-stream about as often as it reports a real type.
            val mobiExtension = MOBI_EXTENSIONS.firstOrNull { displayName.endsWith(it, ignoreCase = true) }
            val fb2Extension = FB2_EXTENSIONS.firstOrNull { displayName.endsWith(it, ignoreCase = true) }
            val isText = displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"
            if (!isText && !isEpub && !isPdf && !isDocx && mobiExtension == null && fb2Extension == null) {
                emit(
                    ImportState.Error(
                        "Invalid file type. Select a TXT, EPUB, DOCX, MOBI, AZW3, FB2, or text-based PDF document."
                    )
                )
                return@flow
            }

            val importDirectory = File(context.filesDir, "imports").apply { mkdirs() }
            val extension = when {
                isEpub -> "epub"
                isPdf -> "pdf"
                isDocx -> "docx"
                mobiExtension != null -> mobiExtension.removePrefix(".")
                fb2Extension != null -> fb2Extension.removePrefix(".")
                else -> "txt"
            }
            val destinationFile = File(importDirectory, "${UUID.randomUUID()}.$extension")
            destination = destinationFile
            emit(ImportState.Importing(0.1f, ImportStage.COPYING))

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    TextImportStreams.copyBounded(input, output)
                }
            } ?: run {
                emit(ImportState.Error("Could not read the selected file."))
                return@flow
            }

            if (destinationFile.length() == 0L) {
                destinationFile.delete()
                emit(ImportState.Error("File is empty."))
                return@flow
            }

            emit(ImportState.Importing(0.3f, ImportStage.READING))
            // Dispatch on the saved file's extension, not the picker's guess, so import and re-scan
            // always choose the same parser for the same book.
            val importedBook = parseByExtension(destinationFile, displayName) { progress ->
                // Map OCR progress between 0.3 and 0.75
                // Flow emission handled before persistBook
            }

            emit(ImportState.Importing(0.8f, ImportStage.SAVING))
            val result = persistBook(importedBook, destinationFile.absolutePath, "File contains no readable text.")
            if (result is ImportState.Error) {
                destinationFile.delete()
            }
            emit(result)
        } catch (_: TextImportTooLargeException) {
            destination?.delete()
            emit(ImportState.Error("File is too large. Maximum supported size is 300MB."))
        } catch (exception: EncryptedDocumentException) {
            destination?.delete()
            emit(
                ImportState.Error(
                    context.getString(com.voxleaf.reader.R.string.import_error_encrypted, exception.format)
                )
            )
        } catch (exception: EpubParser.ParseException) {
            destination?.delete()
            emit(ImportState.Error(documentParseMessage("EPUB", exception.message)))
        } catch (exception: PdfBookParser.ParseException) {
            destination?.delete()
            emit(ImportState.Error(exception.message ?: "Could not read this PDF file."))
        } catch (exception: DocxParser.ParseException) {
            destination?.delete()
            emit(ImportState.Error(documentParseMessage("DOCX", exception.message)))
        } catch (exception: MobiParser.ParseException) {
            destination?.delete()
            emit(ImportState.Error(exception.message ?: "Could not read this Kindle book."))
        } catch (exception: Fb2Parser.ParseException) {
            destination?.delete()
            emit(ImportState.Error(exception.message ?: "Could not read this FictionBook file."))
        } catch (exception: CancellationException) {
            destination?.delete()
            throw exception
        } catch (_: Exception) {
            destination?.delete()
            emit(ImportState.Error("Could not import this document. Check that it is a valid TXT, EPUB, DOCX, or PDF file."))
        }
    }.flowOn(Dispatchers.IO)

    override fun invoke(pages: List<ScannedPageReference>, title: String): Flow<ImportState> = flow {
        emit(ImportState.Importing(0f, ImportStage.PREPARING))
        try {
            val processing = scannedPageProcessor.process(pages) { completed, total ->
                emit(
                    ImportState.Importing(
                        0.1f + 0.6f * (completed.toFloat() / total),
                        ImportStage.RECOGNIZING_TEXT
                    )
                )
            }
            if (processing is ScannedPageProcessingResult.Error) {
                emit(ImportState.Error(scanProcessingError(processing)))
                return@flow
            }
            processing as ScannedPageProcessingResult.Success
            val sections = processing.pages.map { page ->
                ImportedSection("Page ${page.pageNumber}", page.text)
            }
            val importedBook = ImportedBook(
                title = title.ifBlank { "Scanned document" },
                author = "Unknown Author",
                description = "Scanned with the camera and recognized on-device",
                sections = sections,
                coverBitmap = processing.coverBitmap
            )
            try {
                if (sections.none { it.content.isNotBlank() }) {
                    emit(ImportState.Error(context.getString(com.voxleaf.reader.R.string.scan_error_no_readable_text)))
                    return@flow
                }
                emit(ImportState.Importing(0.8f, ImportStage.SAVING))
                emit(
                    persistBook(
                        importedBook,
                        sourceFilePath = null,
                        context.getString(com.voxleaf.reader.R.string.scan_error_no_readable_text)
                    )
                )
            } finally {
                processing.coverBitmap.recycle()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            emit(ImportState.Error(context.getString(com.voxleaf.reader.R.string.scan_error_processing)))
        }
    }.flowOn(Dispatchers.IO)

    private fun scanProcessingError(error: ScannedPageProcessingResult.Error): String {
        val page = error.pageNumber ?: 1
        return when (error.reason) {
            ScannedPageProcessingResult.Reason.NO_PAGES ->
                context.getString(com.voxleaf.reader.R.string.scan_error_no_pages)
            ScannedPageProcessingResult.Reason.INVALID_PATH ->
                context.getString(com.voxleaf.reader.R.string.scan_error_invalid_page, page.toString())
            ScannedPageProcessingResult.Reason.EMPTY_IMAGE ->
                context.getString(com.voxleaf.reader.R.string.scan_error_empty_page, page.toString())
            ScannedPageProcessingResult.Reason.CORRUPT_IMAGE ->
                context.getString(com.voxleaf.reader.R.string.scan_error_corrupt_page, page.toString())
            ScannedPageProcessingResult.Reason.OCR_FAILED ->
                context.getString(com.voxleaf.reader.R.string.scan_error_ocr_page, page.toString())
        }
    }

    private suspend fun persistBook(
        importedBook: ImportedBook,
        sourceFilePath: String?,
        emptyTextMessage: String
    ): ImportState {
        val bookId = UUID.randomUUID().toString()
        val sections = mutableListOf<SectionEntity>()
        val chunks = mutableListOf<TextChunkEntity>()

        importedBook.sections.forEachIndexed { index, importedSection ->
            val startAnchor = sectionAnchor(importedSection.content, fromStart = true)
            val endAnchor = sectionAnchor(importedSection.content, fromStart = false)
            val sectionId = stableSectionId(bookId, startAnchor, endAnchor, index)
            sections += SectionEntity(
                id = sectionId,
                bookId = bookId,
                chapterNumber = index + 1,
                title = importedSection.title,
                estimatedMinutes = max(1, importedSection.content.length / 1000),
                detectionSource = importedSection.detectionSource,
                detectionConfidence = importedSection.detectionConfidence,
                detectionReason = importedSection.detectionReason,
                startAnchor = startAnchor,
                endAnchor = endAnchor
            )
            importedSection.content.split(Regex("(?<=[.!?])\\s+"))
                .filter { it.isNotBlank() }
                .forEachIndexed { sentenceIndex, sentence ->
                    chunks += TextChunkEntity(
                        id = UUID.randomUUID().toString(),
                        sectionId = sectionId,
                        sequenceNumber = sentenceIndex,
                        text = sentence
                    )
                }
        }

        if (chunks.isEmpty()) {
            return ImportState.Error(emptyTextMessage)
        }

        val coverImagePath = importedBook.coverBitmap?.let { saveCoverBitmap(bookId, it) }

        database.withTransaction {
            database.bookDao().insertBook(
                BookEntity(
                    id = bookId,
                    title = importedBook.title,
                    author = importedBook.author,
                    description = importedBook.description,
                    genre = "General",
                    coverColorHex = coverColorForTitle(importedBook.title),
                    totalChapters = sections.size,
                    isFavorite = false,
                    sourceFilePath = sourceFilePath,
                    coverImagePath = coverImagePath
                )
            )
            database.bookDao().insertReadingProgress(ReadingProgressEntity(bookId, 0, 0))
            database.bookDao().insertSections(sections)
            database.bookDao().insertTextChunks(chunks)
        }

        return ImportState.Success(bookId)
    }

    private fun saveCoverBitmap(bookId: String, bitmap: Bitmap): String? {
        return try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val maxDimension = 480
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val coverFile = File(coversDir, "$bookId.png")
            FileOutputStream(coverFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, output)
            }
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun readWithEncodingFallback(file: File): Pair<String, Charset> {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return "" to StandardCharsets.UTF_8
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8).replace("\r\n", "\n") to StandardCharsets.UTF_8
        }
        return try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().replace("\r\n", "\n") to StandardCharsets.UTF_8
        } catch (_: Exception) {
            String(bytes, Charset.forName("ISO-8859-1")).replace("\r\n", "\n") to Charset.forName("ISO-8859-1")
        }
    }

    private fun extractTitle(content: String, filename: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.length < 100 }
            ?: filename.removeSuffix(".txt").takeIf { it.isNotBlank() }
            ?: "Imported text"
    }

    private fun parseText(file: File, filename: String): ImportedBook {
        val content = readWithEncodingFallback(file).first
        if (content.isBlank()) throw IllegalArgumentException("Text file contains no readable content.")
        val sections = ChapterDetector.split(content)?.map { ImportedSection(it.title, it.content) }
            // Scene break fallback: split on ***, ---, etc. before falling back to paragraph grouping
            ?: ChapterDetector.splitBySceneBreaks(content)?.map { ImportedSection(it.title, it.content) }
            // No headings or scene breaks: group paragraphs into readable stretches.
            ?: groupParagraphs(content).mapIndexed { index, text ->
                ImportedSection("Part ${index + 1}", text)
            }
        return ImportedBook(
            title = extractTitle(content, filename),
            author = "Unknown Author",
            description = "Imported plain-text document",
            sections = sections
        )
    }

    /** Roughly a chapter's worth of reading per group, so a plain .txt still gets navigable parts. */
    private fun groupParagraphs(content: String, targetChars: Int = 3000): List<String> {
        val paragraphs = content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return listOf(content.trim())
        val groups = mutableListOf<String>()
        val current = StringBuilder()
        paragraphs.forEach { paragraph ->
            if (current.isNotEmpty() && current.length + paragraph.length > targetChars) {
                groups += current.toString().trim()
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotEmpty()) groups += current.toString().trim()
        return groups
    }

    private fun parseEpub(file: File, filename: String): ImportedBook {
        ensureZipNotEncrypted(file, "EPUB")
        val epub = EpubParser.parse(file)
        // One spine document per chapter is how EPUBs are authored, and the NCX supplies the titles.
        // Splitting on h1-h6 instead (as this used to) merged or shredded books depending on styling.
        val spineSections = epub.chapters.mapIndexedNotNull { index, chapter ->
            val body = chapter.paragraphs
                .let { if (it.firstOrNull()?.isHeading == true) it.drop(1) else it }
                .joinToString("\n\n") { it.text }
                .trim()
            if (body.isBlank()) {
                null
            } else {
                ImportedSection(chapter.title?.trim().orEmpty().ifBlank { "Chapter ${index + 1}" }, body)
            }
        }
        val sections = ensureSections(spineSections, epub.paragraphs.joinToString("\n") { it.text })
        if (sections.isEmpty()) throw EpubParser.ParseException("No readable chapters found in EPUB file.")
        val coverBitmap = epub.coverBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        return ImportedBook(
            title = epub.title?.trim().takeUnless { it.isNullOrBlank() }
                ?: filename.removeSuffix(".epub").ifBlank { "Imported EPUB" },
            author = epub.author?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown Author",
            description = "Imported EPUB document",
            sections = sections,
            coverBitmap = coverBitmap
        )
    }

    /**
     * Single place that maps a file to a parser. Import and re-scan both route through here, so a new
     * format can never be supported on one path and silently missing on the other.
     */
    private suspend fun parseByExtension(
        file: File,
        displayName: String,
        onProgress: ((Float) -> Unit)? = null
    ): ImportedBook {
        // Dispatch on the stored file's own extension — it was chosen from the MIME type when the
        // picker gave a name without one — while titles still fall back to the name the user saw.
        val stored = file.name
        return when {
            stored.endsWith(".epub", ignoreCase = true) -> parseEpub(file, displayName)
            stored.endsWith(".pdf", ignoreCase = true) -> parsePdf(file, displayName, onProgress)
            stored.endsWith(".docx", ignoreCase = true) -> parseDocx(file, displayName)
            MOBI_EXTENSIONS.any { stored.endsWith(it, ignoreCase = true) } -> parseMobi(file, displayName)
            FB2_EXTENSIONS.any { stored.endsWith(it, ignoreCase = true) } -> parseFb2(file, displayName)
            else -> parseText(file, displayName)
        }
    }

    private fun parseMobi(file: File, filename: String): ImportedBook {
        val mobi = MobiParser.parse(file)
        val sections = mobi.chapters.mapIndexedNotNull { index, chapter ->
            val body = chapter.paragraphs
                .let { if (it.firstOrNull()?.isHeading == true) it.drop(1) else it }
                .joinToString("\n\n") { it.text }
                .trim()
            if (body.isBlank()) {
                null
            } else {
                ImportedSection(chapter.title?.trim().orEmpty().ifBlank { "Chapter ${index + 1}" }, body)
            }
        }
        val fullText = mobi.chapters.flatMap { it.paragraphs }.joinToString("\n") { it.text }
        val coverBitmap = mobi.coverBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        return ImportedBook(
            title = mobi.title?.trim().takeUnless { it.isNullOrBlank() }
                ?: filename.substringBeforeLast('.').ifBlank { "Imported Kindle book" },
            author = mobi.author?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown Author",
            description = "Imported Kindle document",
            sections = ensureSections(sections, fullText),
            coverBitmap = coverBitmap
        )
    }

    private fun parseFb2(file: File, filename: String): ImportedBook {
        val fb2 = Fb2Parser.parse(file)
        val sections = fb2.chapters.mapIndexedNotNull { index, chapter ->
            val body = chapter.paragraphs.joinToString("\n\n").trim()
            if (body.isBlank()) {
                null
            } else {
                ImportedSection(chapter.title?.trim().orEmpty().ifBlank { "Chapter ${index + 1}" }, body)
            }
        }
        return ImportedBook(
            title = fb2.title?.trim().takeUnless { it.isNullOrBlank() }
                ?: filename.substringBeforeLast('.').ifBlank { "Imported FictionBook" },
            author = fb2.author?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown Author",
            description = "Imported FictionBook document",
            sections = ensureSections(sections, fb2.chapters.flatMap { it.paragraphs }.joinToString("\n"))
        )
    }

    private fun parseDocx(file: File, filename: String): ImportedBook {
        ensureZipNotEncrypted(file, "DOCX")
        val docx = DocxParser.parse(file)
        val sections = mutableListOf<ImportedSection>()
        var currentTitle: String? = null
        val currentContent = mutableListOf<String>()
        fun finishSection() {
            if (currentContent.isNotEmpty()) {
                sections += ImportedSection(
                    title = currentTitle ?: "Section ${sections.size + 1}",
                    content = currentContent.joinToString("\n\n")
                )
                currentContent.clear()
            }
        }
        docx.paragraphs.forEach { paragraph ->
            if (paragraph.isHeading) {
                finishSection()
                currentTitle = paragraph.text
            } else {
                currentContent += paragraph.text
            }
        }
        finishSection()
        if (sections.isEmpty()) throw DocxParser.ParseException("No readable content found in DOCX file.")
        return ImportedBook(
            title = docx.title?.trim().takeUnless { it.isNullOrBlank() }
                ?: filename.removeSuffix(".docx").ifBlank { "Imported DOCX" },
            author = docx.author?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown Author",
            description = "Imported DOCX document",
            // Heading styles first; a document that never used them still gets detected chapters.
            sections = ensureSections(sections, docx.paragraphs.joinToString("\n") { it.text })
        )
    }

    private suspend fun parsePdf(
        file: File,
        filename: String,
        onProgress: ((Float) -> Unit)? = null
    ): ImportedBook {
        val pdf = PdfBookParser.parse(context, file, ocrScanner, onProgress)
        // Strip running headers/footers before joining pages
        val cleanedPages = PdfBookParser.stripRunningHeaders(pdf.pages)
        val fullText = cleanedPages.filter { it.isNotBlank() }.joinToString("\n\n")
        // Prefer the PDF's own bookmarks — an authored table of contents beats any guess. Failing that,
        // detect headings, then scene breaks, then group into readable stretches. One section per page
        // is the last resort: a page break is a print artifact, not a chapter boundary.
        // Font size before words: an authored outline is still the best answer, but where there is
        // none, the type sizes the document was set in say where headings are far more reliably than
        // matching heading-shaped strings — which is what promoted running headers to chapters.
        val structured = PdfBookParser.sectionsFromOutline(cleanedPages, pdf.outline)
            ?: PdfBookParser.sectionsFromFontSize(pdf.lines)
            ?: ChapterDetector.split(fullText)
            ?: ChapterDetector.splitBySceneBreaks(fullText)
        val grouped = structured?.map { ImportedSection(it.title, it.content) }
            ?: groupParagraphs(fullText).takeIf { it.size > 1 }
                ?.mapIndexed { index, text -> ImportedSection("Part ${index + 1}", text) }
        return ImportedBook(
            title = pdf.title?.takeUnless { it.isBlank() }
                ?: filename.removeSuffix(".pdf").ifBlank { "Imported PDF" },
            author = pdf.author?.takeUnless { it.isBlank() } ?: "Unknown Author",
            description = "Imported text-based PDF document",
            sections = grouped ?: cleanedPages.mapIndexedNotNull { index, pageText ->
                pageText.takeIf { it.isNotBlank() }?.let { ImportedSection("Page ${index + 1}", it) }
            },
            coverBitmap = pdf.coverBitmap
        )
    }

    /**
     * A document whose own structure produced a single blob isn't chapterless — it's just unstyled.
     * Fall back to heading detection, then to readable stretches, so every format lands on the same
     * chapter logic instead of each importer inventing its own.
     */
    private fun ensureSections(sections: List<ImportedSection>, fullText: String): List<ImportedSection> {
        if (sections.size > 1) {
            // If any individual section is an omnibus containing multiple internal chapters, subdivide it.
            // Lowered threshold from 5000 to 3000 to catch more multi-chapter files, but with a safeguard:
            // only sub-split if each resulting section averages >= 500 chars (prevents over-fragmentation).
            val expanded = mutableListOf<ImportedSection>()
            for (section in sections) {
                if (section.content.length > 3000) {
                    val subSplit = ChapterDetector.split(section.content)
                    if (subSplit != null && subSplit.size > 1) {
                        val avgLen = subSplit.sumOf { it.content.length } / subSplit.size
                        if (avgLen >= 500) {
                            expanded += subSplit.map { ImportedSection(it.title, it.content) }
                            continue
                        }
                    }
                }
                expanded += section
            }
            return expanded
        }
        return ChapterDetector.split(fullText)?.map { ImportedSection(it.title, it.content) }
            ?: ChapterDetector.splitBySceneBreaks(fullText)?.map { ImportedSection(it.title, it.content) }
            ?: groupParagraphs(fullText).takeIf { it.size > 1 }
                ?.mapIndexed { index, text -> ImportedSection("Part ${index + 1}", text) }
            ?: sections
    }

    private fun ImportedSection.toDraft(
        bookId: String,
        index: Int,
        manuallyEdited: Boolean
    ): StructureSectionDraft {
        val start = sectionAnchor(content, fromStart = true)
        val end = sectionAnchor(content, fromStart = false)
        return StructureSectionDraft(
            id = stableSectionId(bookId, start, end, index),
            title = title.trim().ifBlank { "Section ${index + 1}" },
            content = content.trim(),
            detectionSource = detectionSource,
            detectionConfidence = detectionConfidence.coerceIn(0f, 1f),
            detectionReason = detectionReason,
            startAnchor = start,
            endAnchor = end,
            isManuallyEdited = manuallyEdited
        )
    }

    private fun normalizeDrafts(input: List<StructureSectionDraft>): List<StructureSectionDraft> {
        val result = mutableListOf<StructureSectionDraft>()
        var leadingIgnored = ""
        input.take(MAX_STRUCTURE_SECTIONS + 1).forEach { draft ->
            val readable = draft.content.trim()
            if (readable.isBlank()) return@forEach
            if (draft.isIgnored) {
                if (result.isEmpty()) {
                    leadingIgnored = listOf(leadingIgnored, readable).filter { it.isNotBlank() }.joinToString(" ")
                } else {
                    val previous = result.removeAt(result.lastIndex)
                    result += previous.copy(
                        content = previous.content.trim() + " " + readable,
                        endAnchor = sectionAnchor(readable, fromStart = false),
                        isManuallyEdited = true
                    )
                }
            } else {
                val combined = listOf(leadingIgnored, readable).filter { it.isNotBlank() }.joinToString(" ")
                leadingIgnored = ""
                result += draft.copy(
                    title = draft.title.trim().ifBlank { "Section ${result.size + 1}" },
                    content = combined,
                    startAnchor = sectionAnchor(combined, fromStart = true),
                    endAnchor = sectionAnchor(combined, fromStart = false)
                )
            }
        }
        if (leadingIgnored.isNotBlank() && result.isNotEmpty()) {
            val previous = result.removeAt(result.lastIndex)
            result += previous.copy(
                content = previous.content.trim() + " " + leadingIgnored,
                endAnchor = sectionAnchor(leadingIgnored, fromStart = false),
                isManuallyEdited = true
            )
        }
        return result
    }

    private suspend fun replaceSectionsPreservingAnchors(
        bookId: String,
        drafts: List<StructureSectionDraft>
    ) {
        val oldBook = database.bookDao().getBookById(bookId)
            ?: error("Document not found")
        val oldOrdered = oldBook.sections.sortedBy { it.section.chapterNumber }
        val progress = oldBook.progress
        val progressText = progress?.let { saved ->
            oldOrdered.getOrNull(saved.currentChapterIndex)?.chunks
                ?.sortedBy { it.sequenceNumber }
                ?.getOrNull(saved.currentPosition)
                ?.text
        }
        val bookmarks = database.bookmarkDao().getBookmarksForBookNow(bookId)
        val highlights = database.highlightDao().getHighlightsForBookNow(bookId)

        val sectionEntities = mutableListOf<SectionEntity>()
        val chunkEntities = mutableListOf<TextChunkEntity>()
        val locations = mutableListOf<StructureLocation>()
        val usedIds = mutableSetOf<String>()
        drafts.forEachIndexed { chapterIndex, draft ->
            val start = sectionAnchor(draft.content, fromStart = true)
            val end = sectionAnchor(draft.content, fromStart = false)
            val preferredId = draft.id.takeIf { it.isNotBlank() && usedIds.add(it) }
            val sectionId = preferredId ?: stableSectionId(bookId, start, end, chapterIndex).also(usedIds::add)
            sectionEntities += SectionEntity(
                id = sectionId,
                bookId = bookId,
                chapterNumber = chapterIndex + 1,
                title = draft.title.trim(),
                estimatedMinutes = max(1, draft.content.length / 1000),
                detectionSource = if (draft.isManuallyEdited) "MANUAL" else draft.detectionSource,
                detectionConfidence = draft.detectionConfidence.coerceIn(0f, 1f),
                detectionReason = draft.detectionReason,
                startAnchor = start,
                endAnchor = end,
                isManuallyEdited = draft.isManuallyEdited
            )
            splitSentences(draft.content).forEachIndexed { sentenceIndex, sentence ->
                chunkEntities += TextChunkEntity(
                    id = UUID.randomUUID().toString(),
                    sectionId = sectionId,
                    sequenceNumber = sentenceIndex,
                    text = sentence
                )
                locations += StructureLocation(chapterIndex, sentenceIndex, draft.title.trim(), sentence)
            }
        }
        check(chunkEntities.isNotEmpty())

        fun remap(text: String, oldChapter: Int, oldSentence: Int): StructureLocation {
            val needle = normalizeAnchorText(text)
            val exact = locations.filter { normalizeAnchorText(it.text) == needle }
            if (exact.isNotEmpty()) return exact.minBy { kotlin.math.abs(it.chapterIndex - oldChapter) }
            val prefix = needle.take(80)
            val partial = if (prefix.length >= 24) locations.filter { normalizeAnchorText(it.text).contains(prefix) } else emptyList()
            if (partial.isNotEmpty()) return partial.minBy { kotlin.math.abs(it.chapterIndex - oldChapter) }
            val chapter = oldChapter.coerceIn(0, sectionEntities.lastIndex)
            val inChapter = locations.filter { it.chapterIndex == chapter }
            return inChapter.getOrNull(oldSentence.coerceIn(0, (inChapter.size - 1).coerceAtLeast(0)))
                ?: locations.first()
        }

        val remappedProgress = progress?.let {
            remap(progressText.orEmpty(), it.currentChapterIndex, it.currentPosition)
        }
        val remappedBookmarks = bookmarks.map { saved ->
            val target = remap(saved.textSnippet, saved.chapterIndex, saved.sentenceIndex)
            saved.copy(
                chapterIndex = target.chapterIndex,
                sentenceIndex = target.sentenceIndex,
                chapterTitle = target.chapterTitle
            )
        }
        val remappedHighlights = highlights.map { saved ->
            val target = remap(saved.text, saved.chapterIndex, saved.sentenceIndex)
            saved.copy(
                chapterIndex = target.chapterIndex,
                sentenceIndex = target.sentenceIndex,
                chapterTitle = target.chapterTitle
            )
        }

        database.withTransaction {
            database.bookDao().deleteSectionsForBook(bookId)
            database.bookDao().insertSections(sectionEntities)
            database.bookDao().insertTextChunks(chunkEntities)
            database.bookDao().updateTotalChapters(bookId, sectionEntities.size)
            remappedProgress?.let {
                database.bookDao().updateReadingProgress(bookId, it.chapterIndex, it.sentenceIndex)
            }
            if (remappedBookmarks.isNotEmpty()) database.bookmarkDao().insertBookmarks(remappedBookmarks)
            if (remappedHighlights.isNotEmpty()) database.highlightDao().insertHighlights(remappedHighlights)
        }
    }

    private fun splitSentences(content: String): List<String> =
        content.split(Regex("(?<=[.!?])\\s+")).map(String::trim).filter(String::isNotBlank)

    private fun sectionAnchor(content: String, fromStart: Boolean): String {
        val normalized = normalizeAnchorText(content)
        val sample = if (fromStart) normalized.take(240) else normalized.takeLast(240)
        return sha256(sample)
    }

    private fun normalizeAnchorText(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun stableSectionId(bookId: String, start: String, end: String, index: Int): String =
        UUID.nameUUIDFromBytes("$bookId|$start|$end|$index".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class StructureLocation(
        val chapterIndex: Int,
        val sentenceIndex: Int,
        val chapterTitle: String,
        val text: String
    )

    private data class ImportedBook(
        val title: String,
        val author: String,
        val description: String,
        val sections: List<ImportedSection>,
        val coverBitmap: Bitmap? = null
    )

    private data class ImportedSection(
        val title: String,
        val content: String,
        val detectionSource: String = "DETECTED",
        val detectionConfidence: Float = 0.75f,
        val detectionReason: String = "Detected from document structure and heading patterns"
    )

    private fun documentParseMessage(format: String, detail: String?): String {
        val normalized = detail.orEmpty().lowercase()
        val resource = when {
            "too large" in normalized || "too many" in normalized ->
                com.voxleaf.reader.R.string.import_error_archive_limits
            "no text" in normalized || "no readable" in normalized ->
                com.voxleaf.reader.R.string.import_error_no_readable_text
            else -> com.voxleaf.reader.R.string.import_error_malformed
        }
        return context.getString(resource, format)
    }

    private fun ensureZipNotEncrypted(file: File, format: String) {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header) }
        if (read >= 8 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte()) {
            val flags = (header[6].toInt() and 0xff) or ((header[7].toInt() and 0xff) shl 8)
            if (flags and 0x1 != 0) throw EncryptedDocumentException(format)
        }
    }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
        const val PDF_MIME_TYPE = "application/pdf"
        const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val MAX_STRUCTURE_SECTIONS = 500

        // .azw is the DRM-free Kindle wrapper around MOBI; .azw3 is KF8. Both are PalmDB underneath.
        val MOBI_EXTENSIONS = listOf(".mobi", ".azw3", ".azw", ".prc")
        val FB2_EXTENSIONS = listOf(".fb2", ".fbz")

        // Brand-adjacent palette so generated covers stay on-theme instead of one repeated blue.
        val COVER_PALETTE = listOf("#2B6CB0", "#16624A", "#B0562B", "#7C3AED", "#B02B5C", "#2B8FB0", "#8A6D1F")

        fun coverColorForTitle(title: String): String =
            COVER_PALETTE[(title.hashCode().mod(COVER_PALETTE.size))]
    }
}

private class EncryptedDocumentException(val format: String) : Exception()

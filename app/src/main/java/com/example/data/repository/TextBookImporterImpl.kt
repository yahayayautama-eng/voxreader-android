package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity
import com.example.domain.usecase.ImportState
import com.example.domain.usecase.ImportTextBookUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class TextBookImporterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) : ImportTextBookUseCase {
    override fun invoke(uri: Uri): Flow<ImportState> = flow {
        var destination: File? = null
        emit(ImportState.Importing(0f))

        try {
            if (uri.scheme != "content") {
                emit(ImportState.Error("Please select a text document from the system picker."))
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
            if (mimeType != "text/plain" && !displayName.endsWith(".txt", ignoreCase = true)) {
                emit(ImportState.Error("Invalid file type. Only plain-text (.txt) files are supported."))
                return@flow
            }

            val importDirectory = File(context.filesDir, "imports").apply { mkdirs() }
            val destinationFile = File(importDirectory, "${UUID.randomUUID()}.txt")
            destination = destinationFile
            emit(ImportState.Importing(0.1f))

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

            emit(ImportState.Importing(0.3f))
            val content = readWithEncodingFallback(destinationFile).first
            if (content.isBlank()) {
                destinationFile.delete()
                emit(ImportState.Error("File contains no readable text."))
                return@flow
            }

            val bookId = UUID.randomUUID().toString()
            val rawSections = content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            val sections = mutableListOf<SectionEntity>()
            val chunks = mutableListOf<TextChunkEntity>()

            rawSections.forEachIndexed { index, sectionText ->
                val sectionId = UUID.randomUUID().toString()
                sections += SectionEntity(
                    id = sectionId,
                    bookId = bookId,
                    chapterNumber = index + 1,
                    title = "Section ${index + 1}",
                    estimatedMinutes = max(1, sectionText.length / 1000)
                )
                sectionText.split(Regex("(?<=[.!?])\\s+"))
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
                destinationFile.delete()
                emit(ImportState.Error("File contains no readable text."))
                return@flow
            }

            emit(ImportState.Importing(0.8f))
            database.withTransaction {
                database.bookDao().insertBook(
                    BookEntity(
                        id = bookId,
                        title = extractTitle(content, displayName),
                        author = "Unknown Author",
                        description = "Imported plain-text document",
                        genre = "General",
                        coverColorHex = "#2B6CB0",
                        totalChapters = sections.size,
                        isFavorite = false,
                        sourceFilePath = destinationFile.absolutePath
                    )
                )
                database.bookDao().insertReadingProgress(ReadingProgressEntity(bookId, 0, 0))
                database.bookDao().insertSections(sections)
                database.bookDao().insertTextChunks(chunks)
            }

            emit(ImportState.Success(bookId))
        } catch (_: TextImportTooLargeException) {
            destination?.delete()
            emit(ImportState.Error("File is too large. Maximum supported size is 20MB."))
        } catch (exception: CancellationException) {
            destination?.delete()
            throw exception
        } catch (_: Exception) {
            destination?.delete()
            emit(ImportState.Error("Could not import the selected text file."))
        }
    }.flowOn(Dispatchers.IO)

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
}

package com.voxleaf.reader.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed interface ImportState {
    data object Idle : ImportState
    data class Importing(
        val progress: Float,
        val stage: ImportStage = ImportStage.PREPARING
    ) : ImportState
    data class Success(val bookId: String) : ImportState
    data class Error(
        val message: String,
        val importedCount: Int = 0,
        val totalCount: Int = 0
    ) : ImportState
}

fun interface ImportTextBookUseCase {
    operator fun invoke(uri: Uri): Flow<ImportState>
}

fun interface ImportScannedBookUseCase {
    operator fun invoke(pages: List<ScannedPageReference>, title: String): Flow<ImportState>
}

enum class ImportStage {
    PREPARING,
    COPYING,
    READING,
    RECOGNIZING_TEXT,
    SAVING
}

data class ScannedPageReference(
    val absolutePath: String,
    val byteSize: Long,
    val reviewedTextPath: String? = null,
    val reviewedTextByteSize: Long = 0L
)

sealed interface RedetectResult {
    data object Success : RedetectResult
    data class Error(val message: String) : RedetectResult
}

fun interface RedetectChaptersUseCase {
    suspend operator fun invoke(bookId: String): RedetectResult
}

data class StructureSectionDraft(
    val id: String,
    val title: String,
    val content: String,
    val detectionSource: String,
    val detectionConfidence: Float,
    val detectionReason: String,
    val startAnchor: String,
    val endAnchor: String,
    val isManuallyEdited: Boolean = false,
    val isIgnored: Boolean = false
)

data class StructurePreview(
    val current: List<StructureSectionDraft>,
    val proposed: List<StructureSectionDraft>,
    val addedCount: Int,
    val removedCount: Int,
    val changedCount: Int
)

sealed interface StructureResult {
    data class Preview(val value: StructurePreview) : StructureResult
    data object Applied : StructureResult
    data class Error(val message: String) : StructureResult
}

interface SectionStructureUseCase {
    suspend fun analyze(bookId: String): StructureResult
    suspend fun apply(bookId: String, sections: List<StructureSectionDraft>): StructureResult
}

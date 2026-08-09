package com.example.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed interface ImportState {
    data object Idle : ImportState
    data class Importing(val progress: Float) : ImportState
    data class Success(val bookId: String) : ImportState
    data class Error(val message: String) : ImportState
}

fun interface ImportTextBookUseCase {
    operator fun invoke(uri: Uri): Flow<ImportState>
}

fun interface ImportScannedBookUseCase {
    operator fun invoke(pages: List<Bitmap>, title: String): Flow<ImportState>
}

sealed interface RedetectResult {
    data object Success : RedetectResult
    data class Error(val message: String) : RedetectResult
}

fun interface RedetectChaptersUseCase {
    suspend operator fun invoke(bookId: String): RedetectResult
}

package com.example.domain.usecase

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

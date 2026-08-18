package com.voxleaf.reader.feature.importbook

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxleaf.reader.domain.usecase.ImportScannedBookUseCase
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ImportTextBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importTextBook: ImportTextBookUseCase,
    private val importScannedBook: ImportScannedBookUseCase
) : ViewModel() {
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _capturedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedPages: StateFlow<List<Bitmap>> = _capturedPages.asStateFlow()

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            importTextBook(uri).collect { _importState.value = it }
        }
    }

    fun addScannedPage(bitmap: Bitmap) {
        if (_capturedPages.value.size >= MAX_SCANNED_PAGES) {
            bitmap.recycle()
            return
        }
        _capturedPages.value = _capturedPages.value + bitmap
    }

    fun clearScannedPages() {
        _capturedPages.value = emptyList()
    }

    fun finishScan(title: String) {
        val pages = _capturedPages.value
        viewModelScope.launch {
            importScannedBook(pages, title).collect { _importState.value = it }
        }
    }

    fun resetState() {
        _importState.value = ImportState.Idle
        clearScannedPages()
    }

    override fun onCleared() {
        clearScannedPages()
        super.onCleared()
    }

    companion object {
        const val MAX_SCANNED_PAGES = 8
    }
}

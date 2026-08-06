package com.example.feature.importbook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.ImportState
import com.example.domain.usecase.ImportTextBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importTextBook: ImportTextBookUseCase
) : ViewModel() {
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            importTextBook(uri).collect { _importState.value = it }
        }
    }

    fun resetState() {
        _importState.value = ImportState.Idle
    }
}

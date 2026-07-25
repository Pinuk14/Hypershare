package com.hypershare.ui.filebrowser

import androidx.lifecycle.ViewModel
import com.hypershare.model.TransferJob
import com.hypershare.model.TransferPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FileBrowserUiState(
    val queuedJobs: List<TransferJob> = emptyList(),
    val isViewOnlyPermission: Boolean = false
)

class FileBrowserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    fun togglePermission(isViewOnly: Boolean) {
        _uiState.value = _uiState.value.copy(isViewOnlyPermission = isViewOnly)
    }
}

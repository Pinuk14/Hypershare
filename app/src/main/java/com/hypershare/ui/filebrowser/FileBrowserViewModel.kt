package com.hypershare.ui.filebrowser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.application.IncomingTransferPrompt
import com.hypershare.application.TransferCompletedEvent
import com.hypershare.application.TransferManager
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.TransferJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectedFileInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
)

data class FileBrowserUiState(
    val selectedFile: SelectedFileInfo? = null,
    val isViewOnlyPermission: Boolean = false,
    val selectedPeerId: String? = null,
    val incomingPrompt: IncomingTransferPrompt? = null,
    val latestCompletedEvent: TransferCompletedEvent? = null
)

class FileBrowserViewModel : ViewModel() {

    private val transferManager = TransferManager.getInstance()

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    val transferJobs: StateFlow<List<TransferJob>> = transferManager.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Collect incoming transfer prompts (Nearby Share incoming sheet)
        viewModelScope.launch {
            transferManager.incomingPrompts.collect { prompt ->
                _uiState.value = _uiState.value.copy(incomingPrompt = prompt)
            }
        }

        // Collect transfer completion events for celebratory summary
        viewModelScope.launch {
            transferManager.completionEvents.collect { event ->
                _uiState.value = _uiState.value.copy(latestCompletedEvent = event)
            }
        }
    }

    fun selectFile(context: Context, uri: Uri) {
        var name = "file"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "*/*"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) name = cursor.getString(nameIdx)
                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
        }

        _uiState.value = _uiState.value.copy(
            selectedFile = SelectedFileInfo(uri, name, size, mime)
        )
    }

    fun clearSelectedFile() {
        _uiState.value = _uiState.value.copy(selectedFile = null)
    }

    fun togglePermission(isViewOnly: Boolean) {
        _uiState.value = _uiState.value.copy(isViewOnlyPermission = isViewOnly)
    }

    fun selectPeer(peerId: String) {
        _uiState.value = _uiState.value.copy(selectedPeerId = peerId)
    }

    fun sendSelectedFileToPeer(targetPeerId: String, targetIp: String = "") {
        val file = _uiState.value.selectedFile ?: return
        transferManager.startSendTransfer(
            fileUri = file.uri,
            targetPeerId = targetPeerId,
            targetIp = targetIp,
            isViewOnly = _uiState.value.isViewOnlyPermission
        )
        // Keep or clear selected file after initiation
        _uiState.value = _uiState.value.copy(selectedFile = null, selectedPeerId = null)
    }

    fun acceptIncomingTransfer(transferId: String) {
        transferManager.acceptTransfer(transferId)
        _uiState.value = _uiState.value.copy(incomingPrompt = null)
    }

    fun rejectIncomingTransfer(transferId: String) {
        transferManager.rejectTransfer(transferId)
        _uiState.value = _uiState.value.copy(incomingPrompt = null)
    }

    fun dismissIncomingPrompt() {
        _uiState.value = _uiState.value.copy(incomingPrompt = null)
    }

    fun dismissCompletionDialog() {
        _uiState.value = _uiState.value.copy(latestCompletedEvent = null)
    }

    fun cancelTransfer(transferId: String) {
        transferManager.cancelTransfer(transferId)
    }
}

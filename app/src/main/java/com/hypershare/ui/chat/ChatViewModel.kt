package com.hypershare.ui.chat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessageItem(
    val id: String,
    val senderPeerId: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isRelayed: Boolean = false
)

data class ChatUiState(
    val peerId: String = "",
    val peerName: String = "Peer",
    val hopCount: Int = 1,
    val messages: List<ChatMessageItem> = emptyList(),
    val inputText: String = ""
)

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val newMessage = ChatMessageItem(
            id = System.currentTimeMillis().toString(),
            senderPeerId = "local",
            text = text,
            isOutgoing = true
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + newMessage,
            inputText = ""
        )
    }
}

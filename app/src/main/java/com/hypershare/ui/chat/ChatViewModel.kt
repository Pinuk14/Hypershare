package com.hypershare.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.db.MessageRepository
import com.hypershare.service.LanSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MessageStatus {
    SENT,        // Single grey tick (In-flight / sent over socket)
    DELIVERED,   // Double grey tick (Received by recipient device)
    READ         // Double green tick (Seen by user in chatroom)
}

data class ChatMessageItem(
    val id: String,
    val senderId: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isRelayed: Boolean = false
)

data class ChatUiState(
    val peerId: String = "peer-default",
    val peerName: String = "Peer",
    val peerIpAddress: String = "",
    val hopCount: Int = 1,
    val messages: List<ChatMessageItem> = emptyList(),
    val inputText: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val repository = MessageRepository(app.applicationContext)
    private var socketManager: LanSocketManager? = null
    private var myPeerId: String = "local"

    fun loadPeerMessages(
        peerId: String,
        targetIp: String = "",
        myId: String = "local",
        msgRepo: MessageRepository? = null,
        lanSocketMgr: LanSocketManager? = null
    ) {
        socketManager = lanSocketMgr
        myPeerId = myId

        _uiState.value = _uiState.value.copy(
            peerId = peerId,
            peerName = peerId.replace("HyperShare_", "").replace("_", " "),
            peerIpAddress = targetIp
        )

        // Load persisted history from SQLite on IO thread
        viewModelScope.launch {
            val historicalMessages = withContext(Dispatchers.IO) {
                repository.getMessagesForPeer(peerId)
            }
            // Merge existing in-memory (from live session) with DB history, sorted by timestamp
            val merged = (_uiState.value.messages + historicalMessages)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
            _uiState.value = _uiState.value.copy(messages = merged)

            // Trigger READ_ACK for any unread incoming messages now that DB load is complete
            markMessagesAsRead()
        }

        // Observe incoming socket messages
        lanSocketMgr?.let { mgr ->
            viewModelScope.launch {
                mgr.incomingMessagesFlow.collect { message ->
                    receiveIncomingMessage(message)
                }
            }

            // Observe ACK events (DELIVERED / READ status updates)
            viewModelScope.launch {
                mgr.ackEventsFlow.collect { ack ->
                    handleAckEvent(ack.msgId, ack.status)
                }
            }
        }
    }

    fun markMessagesAsRead() {
        val peerId = _uiState.value.peerId
        val targetIp = _uiState.value.peerIpAddress
        val currentMessages = _uiState.value.messages

        val unread = currentMessages.filter { !it.isOutgoing && it.status == MessageStatus.DELIVERED }
        if (unread.isEmpty()) return

        // 1. Update local UI state to MessageStatus.READ
        val updatedList = currentMessages.map { msg ->
            if (!msg.isOutgoing && msg.status == MessageStatus.DELIVERED) {
                msg.copy(status = MessageStatus.READ)
            } else {
                msg
            }
        }
        _uiState.value = _uiState.value.copy(messages = updatedList)

        // 2. Update local SQLite DB & dispatch READ_ACK packets over socket
        unread.forEach { msg ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateMessageStatus(msg.id, MessageStatus.READ)
            }
            socketManager?.sendAckPacket(
                targetPeerId = peerId,
                targetIp = targetIp,
                msgId = msg.id,
                ackType = com.hypershare.model.PacketType.READ_ACK,
                senderPeerId = myPeerId
            )
        }
    }

    private fun handleAckEvent(msgId: String, status: MessageStatus) {
        val currentMessages = _uiState.value.messages
        val updated = currentMessages.map { msg ->
            if (msg.id == msgId) msg.copy(status = status) else msg
        }
        _uiState.value = _uiState.value.copy(messages = updated)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMessageStatus(msgId, status)
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val msgId = java.util.UUID.randomUUID().toString()
        val newMessage = ChatMessageItem(
            id = msgId,
            senderId = myPeerId,
            text = text,
            isOutgoing = true,
            status = MessageStatus.SENT
        )

        val updatedList = (_uiState.value.messages + newMessage).distinctBy { it.id }
        _uiState.value = _uiState.value.copy(
            messages = updatedList,
            inputText = ""
        )

        // Persist to SQLite
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMessage(_uiState.value.peerId, newMessage)
        }

        // Transmit over TCP socket to target peer IP / peerId
        val targetIp = _uiState.value.peerIpAddress
        val targetPeerId = _uiState.value.peerId
        if (targetIp.isNotEmpty() || targetPeerId.isNotEmpty()) {
            socketManager?.sendMessageToPeer(
                targetIp = targetIp,
                targetPeerId = targetPeerId,
                text = text,
                senderPeerId = myPeerId,
                msgId = msgId
            )
        }
    }

    fun receiveIncomingMessage(message: ChatMessageItem) {
        // Only process messages that belong to the currently open chat peer.
        // incomingMessagesFlow is global — messages from OTHER peers are ignored here
        // and will be loaded from DB when that peer's chat is opened.
        val currentPeerId = _uiState.value.peerId
        if (!message.isOutgoing && message.senderId != currentPeerId) return

        if (_uiState.value.messages.any { it.id == message.id }) return
        val updatedList = (_uiState.value.messages + message)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
        _uiState.value = _uiState.value.copy(messages = updatedList)
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMessage(currentPeerId, message)
        }
        // READ_ACK is sent by LanSocketManager directly when activeChatPeerId matches,
        // so we do NOT send it again here to avoid double-ACKing.
    }

    fun clearChatHistory() {
        val peerId = _uiState.value.peerId
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteChatHistory(peerId)
        }
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
}

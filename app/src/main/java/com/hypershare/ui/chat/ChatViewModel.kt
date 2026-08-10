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
    val isPeerTrusted: Boolean = false,
    val hasPeerAcceptedUs: Boolean = false,
    val untrustedOutgoingCount: Int = 0,
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
        peerName: String = "",
        targetIp: String = "",
        myId: String = "local",
        msgRepo: MessageRepository? = null,
        lanSocketMgr: LanSocketManager? = null
    ) {
        socketManager = lanSocketMgr
        myPeerId = myId

        val initialName = if (peerName.isNotEmpty() && peerName != peerId) {
            peerName
        } else {
            peerId.take(12)
        }

        _uiState.value = _uiState.value.copy(
            peerId = peerId,
            peerName = initialName,
            peerIpAddress = targetIp
        )

        // Load persisted history, display name, trust status, peer acceptance, and outgoing count from SQLite on IO thread
        viewModelScope.launch {
            val (historicalMessages, dbDisplayName, isTrusted, hasPeerAccepted, outgoingCount) = withContext(Dispatchers.IO) {
                Quintuple(
                    repository.getMessagesForPeer(peerId),
                    repository.getPeerDisplayName(peerId),
                    repository.isPeerTrusted(peerId),
                    repository.hasPeerAcceptedUs(peerId),
                    repository.getOutgoingMessageCountForPeer(peerId)
                )
            }

            val finalName = if (!dbDisplayName.isNullOrEmpty()) {
                dbDisplayName
            } else if (peerName.isNotEmpty() && peerName != peerId) {
                peerName
            } else {
                initialName
            }

            // Save peer in DB if valid name provided
            if (peerName.isNotEmpty() && peerName != peerId) {
                withContext(Dispatchers.IO) {
                    repository.savePeer(peerId, peerName)
                }
            }

            // Merge existing in-memory (from live session) with DB history, sorted by timestamp
            val merged = (_uiState.value.messages + historicalMessages)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }

            _uiState.value = _uiState.value.copy(
                peerName = finalName,
                isPeerTrusted = isTrusted,
                hasPeerAcceptedUs = hasPeerAccepted,
                untrustedOutgoingCount = outgoingCount,
                messages = merged
            )

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

            // Observe CONTACT_ACCEPT events from peer
            viewModelScope.launch {
                mgr.contactAcceptEventsFlow.collect { event ->
                    if (event.peerId == peerId) {
                        _uiState.value = _uiState.value.copy(hasPeerAcceptedUs = true)
                    }
                }
            }
        }
    }

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    fun markMessagesAsRead() {
        val peerId = _uiState.value.peerId
        val targetIp = _uiState.value.peerIpAddress
        val unreadIncoming = _uiState.value.messages.filter { !it.isOutgoing && it.status != MessageStatus.READ }
        if (unreadIncoming.isEmpty()) return

        val updated = _uiState.value.messages.map { msg ->
            if (!msg.isOutgoing && msg.status != MessageStatus.READ) msg.copy(status = MessageStatus.READ) else msg
        }
        _uiState.value = _uiState.value.copy(messages = updated)

        viewModelScope.launch(Dispatchers.IO) {
            for (msg in unreadIncoming) {
                repository.updateMessageStatus(msg.id, MessageStatus.READ)
            }
            socketManager?.sendAckPacket(
                targetPeerId = peerId,
                targetIp = targetIp,
                msgId = unreadIncoming.last().id,
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
        val isMutualTrust = _uiState.value.isPeerTrusted && _uiState.value.hasPeerAcceptedUs
        val capped = if (!isMutualTrust && text.length > 300) {
            text.take(300)
        } else {
            text
        }
        _uiState.value = _uiState.value.copy(inputText = capped)
    }

    fun sendMessage() {
        val rawText = _uiState.value.inputText.trim()
        if (rawText.isEmpty()) return

        val isMutualTrust = _uiState.value.isPeerTrusted && _uiState.value.hasPeerAcceptedUs
        val currentOutgoingCount = _uiState.value.untrustedOutgoingCount

        // Enforce 2-message cap until BOTH users have accepted contact
        if (!isMutualTrust && currentOutgoingCount >= 2) {
            return
        }

        // Enforce 300 character max limit until mutual trust is established
        val text = if (!isMutualTrust && rawText.length > 300) rawText.take(300) else rawText

        val msgId = java.util.UUID.randomUUID().toString()
        val newMessage = ChatMessageItem(
            id = msgId,
            senderId = myPeerId,
            text = text,
            isOutgoing = true,
            status = MessageStatus.SENT
        )

        val updatedList = (_uiState.value.messages + newMessage).distinctBy { it.id }
        val nextOutgoingCount = if (!isMutualTrust) currentOutgoingCount + 1 else currentOutgoingCount

        _uiState.value = _uiState.value.copy(
            messages = updatedList,
            untrustedOutgoingCount = nextOutgoingCount,
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

    fun acceptContactTrust() {
        val currentPeerId = _uiState.value.peerId
        val targetIp = _uiState.value.peerIpAddress
        if (currentPeerId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.markPeerAsTrusted(currentPeerId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isPeerTrusted = true)
            }
            // Send CONTACT_ACCEPT packet to peer over TCP
            socketManager?.sendContactAcceptPacket(
                targetPeerId = currentPeerId,
                targetIp = targetIp,
                senderPeerId = myPeerId
            )
        }
    }
}

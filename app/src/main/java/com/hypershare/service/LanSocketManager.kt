package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hypershare.model.Packet
import com.hypershare.model.PacketHeader
import com.hypershare.model.PacketType
import com.hypershare.protocol.PacketBuilder
import com.hypershare.protocol.PacketParser
import com.hypershare.application.UserIdentityManager
import com.hypershare.ui.chat.ChatMessageItem
import com.hypershare.ui.chat.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class AckEvent(val msgId: String, val status: MessageStatus)
data class PeerHandshakeEvent(val peerId: String, val ipAddress: String)

class LanSocketManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequenceCounter = AtomicInteger(1)

    private fun configureSocketPerformance(socket: Socket) {
        try {
            socket.keepAlive = true
            socket.tcpNoDelay = true // Disable Nagle algorithm to prevent 40-200ms ACK latency spikes
            socket.sendBufferSize = 1024 * 1024      // 1 MB send buffer
            socket.receiveBufferSize = 1024 * 1024   // 1 MB receive buffer
        } catch (_: Exception) {}
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var appContext: Context? = null

    // Map peerId / IP -> active Socket connection
    private val activeSockets = ConcurrentHashMap<String, Socket>()
    // Cache output streams per socket key to avoid creating multiple streams on same socket
    private val activeOutputStreams = ConcurrentHashMap<String, DataOutputStream>()

    // replay=0: DB is the source of truth. Replaying stale messages on re-subscribe causes ordering chaos.
    private val _incomingMessagesFlow = MutableSharedFlow<ChatMessageItem>(replay = 0, extraBufferCapacity = 64)
    val incomingMessagesFlow: SharedFlow<ChatMessageItem> = _incomingMessagesFlow.asSharedFlow()

    data class AckEvent(val msgId: String, val status: MessageStatus)

    private val _ackEventsFlow = MutableSharedFlow<AckEvent>(replay = 0, extraBufferCapacity = 64)
    val ackEventsFlow: SharedFlow<AckEvent> = _ackEventsFlow.asSharedFlow()

    data class ContactAcceptEvent(val peerId: String)

    private val _contactAcceptEventsFlow = MutableSharedFlow<ContactAcceptEvent>(replay = 0, extraBufferCapacity = 64)
    val contactAcceptEventsFlow: SharedFlow<ContactAcceptEvent> = _contactAcceptEventsFlow.asSharedFlow()

    private val _peerHandshakeEventsFlow = MutableSharedFlow<PeerHandshakeEvent>(replay = 0, extraBufferCapacity = 16)
    val peerHandshakeEventsFlow: SharedFlow<PeerHandshakeEvent> = _peerHandshakeEventsFlow.asSharedFlow()

    // Transfer Protocol Events Flow (Week 9)
    private val _incomingTransferPacketsFlow = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 256)
    val incomingTransferPacketsFlow: SharedFlow<Packet> = _incomingTransferPacketsFlow.asSharedFlow()

    fun nextSequenceNumber(): Int = sequenceCounter.getAndIncrement()

    fun getPeerIp(peerId: String): String {
        return activeSockets[peerId]?.inetAddress?.hostAddress ?: ""
    }
    data class PendingOutboundMessage(
        val msgId: String,
        val targetPeerId: String,
        val text: String,
        val senderPeerId: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val pendingOutboundMessages = java.util.concurrent.ConcurrentLinkedQueue<PendingOutboundMessage>()
    private val helloResponsesSent = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile var activeChatPeerId: String? = null
    @Volatile var activeGroupChatId: String? = null

    companion object {
        const val DEFAULT_PORT = 47200
        private const val TAG = "LanSocketManager"
        private const val NOTIFICATION_CHANNEL_ID = "hypershare_chat_messages"
        private const val GROUP_NOTIFICATION_CHANNEL_ID = "hypershare_group_messages"

        @Volatile private var INSTANCE: LanSocketManager? = null

        fun getInstance(): LanSocketManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanSocketManager().also { INSTANCE = it }
            }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val context = appContext ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "HyperShare Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming peer chat messages"
                enableVibration(true)
            }
            manager?.createNotificationChannel(channel)

            val groupChannel = NotificationChannel(
                GROUP_NOTIFICATION_CHANNEL_ID,
                "HyperShare Group Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming group chat messages"
                enableVibration(true)
            }
            manager?.createNotificationChannel(groupChannel)
        }
    }

    /**
     * Posts a status bar notification for an incoming group message.
     */
    fun postGroupMessageNotification(groupId: String, groupName: String, senderDisplayName: String, text: String) {
        if (activeGroupChatId == groupId) return
        val context = appContext ?: return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(context, com.hypershare.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_GROUP_ID", groupId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, groupId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, GROUP_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(groupName)
                .setContentText("$senderDisplayName: $text")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(groupId.hashCode(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post group notification: ${e.message}", e)
        }
    }

    /**
     * Posts a status bar notification for a broadcast group invite when app is in background.
     */
    fun postBroadcastInviteNotification(groupId: String, groupName: String, adminUserId: String, adminDisplayName: String) {
        val context = appContext ?: return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(context, com.hypershare.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_GROUP_ID", groupId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, ("invite_$groupId").hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, GROUP_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Group Invite: $groupName")
                .setContentText("$adminDisplayName invited you to join")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(("invite_$groupId").hashCode(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post broadcast invite notification: ${e.message}", e)
        }
    }

    private fun showIncomingNotification(senderPeerId: String, text: String) {
        // Suppress notification if user is currently active in the chatroom with sender
        if (activeChatPeerId == senderPeerId) {
            Log.d(TAG, "User currently in chat with $senderPeerId — notification suppressed")
            return
        }

        val context = appContext ?: return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(context, com.hypershare.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_PEER_ID", senderPeerId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                senderPeerId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val repository = com.hypershare.db.MessageRepository(context)
            val dbDisplayName = repository.getPeerDisplayName(senderPeerId)
            val cleanSenderName = if (!dbDisplayName.isNullOrEmpty()) {
                dbDisplayName
            } else if (senderPeerId.length > 16) {
                senderPeerId.take(12)
            } else {
                senderPeerId.replace("HyperShare_", "").replace("_", " ")
            }

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Message from $cleanSenderName")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(senderPeerId.hashCode(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.message}", e)
        }
    }

    fun startServer(port: Int = DEFAULT_PORT) {
        if (isRunning) {
            Log.d(TAG, "Server already running, skipping duplicate startServer()")
            return
        }
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true   // SO_REUSEADDR: survive quick restarts
                    bind(java.net.InetSocketAddress(port))
                }
                Log.d(TAG, "TCP server listening on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    configureSocketPerformance(socket)
                    Log.d(TAG, "Accepted connection from ${socket.inetAddress.hostAddress}")
                    handleIncomingSocket(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}", e)
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            configureSocketPerformance(socket)
            val senderIp = try { socket.inetAddress?.hostAddress ?: "unknown" } catch (_: Exception) { "unknown" }
            try {
                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 256 * 1024))
                while (isRunning && !socket.isClosed) {
                    val length = try {
                        input.readInt()
                    } catch (e: java.io.EOFException) {
                        Log.d(TAG, "Socket stream EOF from $senderIp")
                        break
                    } catch (e: java.net.SocketException) {
                        Log.d(TAG, "Socket connection closed/reset by $senderIp: ${e.message}")
                        break
                    }

                    if (length <= 0 || length > 1024 * 1024) {
                        Log.d(TAG, "Frame end or invalid length $length from $senderIp — closing socket")
                        break
                    }

                    val packetBytes = ByteArray(length)
                    input.readFully(packetBytes)

                    val packet = PacketParser.parsePacket(packetBytes)
                    val sourcePeerId = packet.header.sourcePeerId

                    // Register socket under sourcePeerId and IP for ANY incoming packet type
                    if (sourcePeerId.isNotEmpty()) {
                        if (activeSockets[sourcePeerId] !== socket) {
                            activeOutputStreams.remove(sourcePeerId)
                        }
                        activeSockets[sourcePeerId] = socket
                    }
                    if (senderIp != "unknown") {
                        if (activeSockets[senderIp] !== socket) {
                            activeOutputStreams.remove(senderIp)
                        }
                        activeSockets[senderIp] = socket
                    }

                    when (packet.header.type) {
                        PacketType.HELLO -> {
                            Log.d(TAG, "HELLO from peerId=$sourcePeerId ip=$senderIp")
                            if (sourcePeerId.isNotEmpty()) {
                                _peerHandshakeEventsFlow.emit(PeerHandshakeEvent(sourcePeerId, senderIp))
                                sendHelloResponseIfNeeded(socket, sourcePeerId)
                                flushPendingMessagesForPeer(sourcePeerId)
                            }
                        }
                        PacketType.MSG -> {
                            val payloadText = String(packet.payload, StandardCharsets.UTF_8)
                            val parts = payloadText.split("::", limit = 2)
                            val (msgId, text) = if (parts.size == 2) parts[0] to parts[1] else java.util.UUID.randomUUID().toString() to payloadText

                            val isChatActive = (activeChatPeerId == sourcePeerId)
                            val initialStatus = if (isChatActive) MessageStatus.READ else MessageStatus.DELIVERED

                            Log.d(TAG, "MSG received from $sourcePeerId [msgId=$msgId, isActive=$isChatActive]: $text")
                            val messageItem = ChatMessageItem(
                                id = msgId,
                                senderId = sourcePeerId,
                                text = text,
                                isOutgoing = false,
                                timestamp = System.currentTimeMillis(),
                                status = initialStatus
                            )

                            // Persist incoming message to SQLite database immediately regardless of UI state
                            appContext?.let { ctx ->
                                val repository = com.hypershare.db.MessageRepository(ctx)
                                scope.launch(Dispatchers.IO) {
                                    repository.insertMessage(sourcePeerId, messageItem)
                                }
                            }

                            _incomingMessagesFlow.emit(messageItem)

                            // Post notification for incoming chat only if chat is not active
                            showIncomingNotification(sourcePeerId, text)

                            // Send ACK back to sender (READ_ACK if chatroom is open, DELIVERED ACK otherwise)
                            val ackTypeToSend = if (isChatActive) PacketType.READ_ACK else PacketType.ACK
                            sendAckPacket(
                                targetPeerId = sourcePeerId,
                                targetIp = senderIp,
                                msgId = msgId,
                                ackType = ackTypeToSend,
                                senderPeerId = packet.header.destinationPeerId
                            )
                        }
                        PacketType.ACK -> {
                            val ackedMsgId = String(packet.payload, StandardCharsets.UTF_8)
                            Log.d(TAG, "DELIVERED ACK received for msgId=$ackedMsgId")
                            appContext?.let { ctx ->
                                val repository = com.hypershare.db.MessageRepository(ctx)
                                scope.launch(Dispatchers.IO) {
                                    repository.updateMessageStatus(ackedMsgId, MessageStatus.DELIVERED)
                                }
                            }
                            _ackEventsFlow.emit(AckEvent(ackedMsgId, MessageStatus.DELIVERED))
                        }
                        PacketType.READ_ACK -> {
                            val readMsgId = String(packet.payload, StandardCharsets.UTF_8)
                            Log.d(TAG, "READ ACK received for msgId=$readMsgId")
                            appContext?.let { ctx ->
                                val repository = com.hypershare.db.MessageRepository(ctx)
                                scope.launch(Dispatchers.IO) {
                                    repository.updateMessageStatus(readMsgId, MessageStatus.READ)
                                }
                            }
                            _ackEventsFlow.emit(AckEvent(readMsgId, MessageStatus.READ))
                        }
                        PacketType.CONTACT_ACCEPT -> {
                            Log.d(TAG, "CONTACT_ACCEPT received from peerId=$sourcePeerId")
                            appContext?.let { ctx ->
                                val repository = com.hypershare.db.MessageRepository(ctx)
                                scope.launch(Dispatchers.IO) {
                                    val wasAlreadyAccepted = repository.hasPeerAcceptedUs(sourcePeerId)
                                    repository.markPeerAcceptanceReceived(sourcePeerId)
                                    // Only send reciprocal accept if this is a new transition and we also trust the peer
                                    if (!wasAlreadyAccepted && repository.isPeerTrusted(sourcePeerId)) {
                                        val myPeerId = UserIdentityManager.getInstance(ctx).getPeerId()
                                        sendContactAcceptPacket(
                                            targetPeerId = sourcePeerId,
                                            targetIp = senderIp,
                                            senderPeerId = myPeerId
                                        )
                                    }
                                    // Only notify once on initial acceptance transition
                                    if (!wasAlreadyAccepted) {
                                        _contactAcceptEventsFlow.emit(ContactAcceptEvent(sourcePeerId))
                                        val senderName = repository.getPeerDisplayName(sourcePeerId) ?: sourcePeerId.take(12)
                                        showIncomingNotification(sourcePeerId, "$senderName accepted & saved your contact! Mutual pairing complete.")
                                    }
                                }
                            }
                        }
                        PacketType.TRANSFER_INIT,
                        PacketType.TRANSFER_COMPLETE,
                        PacketType.TRANSFER_ACCEPT,
                        PacketType.TRANSFER_REJECT,
                        PacketType.DATA,
                        PacketType.TRANSFER_CHUNK_ACK,
                        PacketType.TRANSFER_CANCEL -> {
                            _incomingTransferPacketsFlow.emit(packet)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Incoming socket loop ended for $senderIp: ${e.message}")
            } finally {
                // Remove ALL keys pointing to this disconnected socket (both peerId and IP keys)
                val deadKeys = activeSockets.entries.filter { it.value === socket }.map { it.key }
                deadKeys.forEach { key ->
                    activeSockets.remove(key)
                    activeOutputStreams.remove(key)
                }
                helloResponsesSent.remove(socket)
                try { socket.close() } catch (_: Exception) { }
            }
        }
    }

    /** Opens a TCP socket and completes only the existing HELLO handshake. */
    fun connectToPeer(targetIp: String, port: Int = DEFAULT_PORT, senderPeerId: String) {
        if (targetIp.isEmpty()) return
        scope.launch {
            val existingSocket = activeSockets[targetIp]
            if (existingSocket != null && !existingSocket.isClosed) return@launch

            var socket: Socket? = null
            var attempts = 0
            val maxAttempts = 6
            while (socket == null && attempts < maxAttempts && isRunning) {
                try {
                    attempts++
                    val s = Socket()
                    configureSocketPerformance(s)
                    s.connect(java.net.InetSocketAddress(targetIp, port), 2000)
                    socket = s
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt $attempts/$maxAttempts: Handshake connect to $targetIp:$port failed (${e.message}), retrying in 400ms...")
                    if (attempts < maxAttempts) {
                        kotlinx.coroutines.delay(400)
                    }
                }
            }

            if (socket == null) {
                Log.e(TAG, "Failed to open TCP handshake connection to $targetIp:$port after $maxAttempts attempts")
                activeSockets.remove(targetIp)
                activeOutputStreams.remove(targetIp)
                return@launch
            }

            try {
                activeSockets[targetIp] = socket
                activeOutputStreams.remove(targetIp)
                sendHello(socket, targetIp, senderPeerId)
                helloResponsesSent.add(socket)
                handleIncomingSocket(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete initial TCP handshake to $targetIp:$port", e)
                activeSockets.remove(targetIp)
                activeOutputStreams.remove(targetIp)
            }
        }
    }

    private fun sendHelloResponseIfNeeded(socket: Socket, targetPeerId: String) {
        if (!helloResponsesSent.add(socket)) return
        val context = appContext ?: return
        val localPeerId = UserIdentityManager.getInstance(context).getPeerId()
        sendHello(socket, targetPeerId, localPeerId)
    }

    private fun sendHello(socket: Socket, targetPeerId: String, senderPeerId: String) {
        val helloPacket = PacketBuilder.buildHelloPacket(
            sourcePeerId = senderPeerId,
            destPeerId = targetPeerId,
            seqNum = sequenceCounter.getAndIncrement(),
            publicKeyBytes = senderPeerId.toByteArray(StandardCharsets.UTF_8)
        )
        val helloBytes = PacketBuilder.serializePacket(helloPacket)
        val output = activeOutputStreams.getOrPut(targetPeerId) {
            DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 256 * 1024))
        }
        val frame = ByteArray(4 + helloBytes.size)
        frame[0] = (helloBytes.size ushr 24).toByte()
        frame[1] = (helloBytes.size ushr 16).toByte()
        frame[2] = (helloBytes.size ushr 8).toByte()
        frame[3] = helloBytes.size.toByte()
        System.arraycopy(helloBytes, 0, frame, 4, helloBytes.size)
        synchronized(output) {
            output.write(frame)
            output.flush()
        }
    }

    fun queuePendingMessage(msg: PendingOutboundMessage) {
        pendingOutboundMessages.add(msg)
        Log.d(TAG, "Queued pending emergency message for ${msg.targetPeerId}: ${msg.text}")
    }

    fun flushPendingMessagesForPeer(peerId: String) {
        val matching = pendingOutboundMessages.filter { it.targetPeerId == peerId }
        if (matching.isEmpty()) return
        Log.d(TAG, "Flushing ${matching.size} queued emergency messages for peerId=$peerId")
        matching.forEach { pending ->
            pendingOutboundMessages.remove(pending)
            sendMessageToPeer(
                targetIp = "",
                text = pending.text,
                senderPeerId = pending.senderPeerId,
                msgId = pending.msgId,
                targetPeerId = pending.targetPeerId
            )
        }
    }

    fun sendMessageToPeer(
        targetIp: String,
        port: Int = DEFAULT_PORT,
        text: String,
        senderPeerId: String,
        msgId: String = java.util.UUID.randomUUID().toString(),
        targetPeerId: String = ""
    ) {
        scope.launch {
            try {
                val key = if (targetPeerId.isNotEmpty() && activeSockets.containsKey(targetPeerId)) targetPeerId
                          else if (targetIp.isNotEmpty() && activeSockets.containsKey(targetIp)) targetIp
                          else ""
                var socket = if (key.isNotEmpty()) activeSockets[key] else null

                if (socket == null || socket.isClosed) {
                    if (targetIp.isEmpty()) {
                        Log.w(TAG, "Cannot send message to $targetPeerId immediately — socket not yet open. Enqueuing for Wi-Fi Direct delivery.")
                        if (targetPeerId.isNotEmpty()) {
                            queuePendingMessage(PendingOutboundMessage(msgId, targetPeerId, text, senderPeerId))
                        }
                        return@launch
                    }
                    Log.d(TAG, "Opening new TCP connection to $targetIp:$port")
                    socket = Socket(targetIp, port)
                    socket.keepAlive = true
                    if (targetPeerId.isNotEmpty()) activeSockets[targetPeerId] = socket
                    activeSockets[targetIp] = socket
                    if (targetPeerId.isNotEmpty()) activeOutputStreams.remove(targetPeerId)
                    activeOutputStreams.remove(targetIp)

                    // Send HELLO packet first
                    val helloPacket = PacketBuilder.buildHelloPacket(
                        sourcePeerId = senderPeerId,
                        destPeerId = if (targetPeerId.isNotEmpty()) targetPeerId else targetIp,
                        seqNum = sequenceCounter.getAndIncrement(),
                        publicKeyBytes = senderPeerId.toByteArray(StandardCharsets.UTF_8)
                    )
                    val helloBytes = PacketBuilder.serializePacket(helloPacket)
                    val out = DataOutputStream(socket.getOutputStream())
                    val streamKey = if (targetPeerId.isNotEmpty()) targetPeerId else targetIp
                    activeOutputStreams[streamKey] = out
                    synchronized(out) {
                        out.writeInt(helloBytes.size)
                        out.write(helloBytes)
                        out.flush()
                    }
                    Log.d(TAG, "HELLO sent to $targetIp ($targetPeerId)")

                    // Start listening loop on this outbound client socket!
                    handleIncomingSocket(socket)
                }

                val streamKey = when {
                    targetPeerId.isNotEmpty() && activeOutputStreams.containsKey(targetPeerId) -> targetPeerId
                    targetIp.isNotEmpty() && activeOutputStreams.containsKey(targetIp) -> targetIp
                    targetPeerId.isNotEmpty() -> targetPeerId
                    else -> targetIp
                }
                val out = activeOutputStreams.getOrPut(streamKey) {
                    DataOutputStream(socket.getOutputStream())
                }

                // Format payload with msgId prefix
                val payloadString = "$msgId::$text"

                // Send MSG packet
                val msgPacket = PacketBuilder.buildMessagePacket(
                    sourcePeerId = senderPeerId,
                    destPeerId = if (targetPeerId.isNotEmpty()) targetPeerId else targetIp,
                    seqNum = sequenceCounter.getAndIncrement(),
                    text = payloadString
                )
                val msgBytes = PacketBuilder.serializePacket(msgPacket)
                synchronized(out) {
                    out.writeInt(msgBytes.size)
                    out.write(msgBytes)
                    out.flush()
                }
                Log.d(TAG, "MSG sent to $targetIp ($targetPeerId) [msgId=$msgId]: $text")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message to $targetIp ($targetPeerId): ${e.message}", e)
                // Clean up broken connection so next send retries fresh
                if (targetPeerId.isNotEmpty()) activeSockets.remove(targetPeerId)
                if (targetIp.isNotEmpty()) activeSockets.remove(targetIp)
                if (targetPeerId.isNotEmpty()) activeOutputStreams.remove(targetPeerId)
                if (targetIp.isNotEmpty()) activeOutputStreams.remove(targetIp)
            }
        }
    }

    fun sendAckPacket(
        targetPeerId: String,
        targetIp: String = "",
        msgId: String,
        ackType: PacketType,
        senderPeerId: String,
        port: Int = DEFAULT_PORT
    ) {
        scope.launch {
            try {
                // Prefer peerId key; fall back to IP key
                val key = if (activeSockets.containsKey(targetPeerId)) targetPeerId
                          else if (targetIp.isNotEmpty() && activeSockets.containsKey(targetIp)) targetIp
                          else ""
                var socket = if (key.isNotEmpty()) activeSockets[key] else null

                // Purge stale/closed socket before deciding to reconnect
                if (socket != null && socket.isClosed) {
                    activeSockets.remove(targetPeerId)
                    activeSockets.remove(targetIp)
                    activeOutputStreams.remove(targetPeerId)
                    activeOutputStreams.remove(targetIp)
                    socket = null
                }

                // Do NOT open a new outbound connection just to send an ACK — ACKs must travel
                // back over the EXISTING connection that the MSG arrived on. If that connection
                // is gone, silently drop the ACK (the sender will treat the msg as DELIVERED).
                if (socket == null) {
                    Log.w(TAG, "Cannot send ${ackType.name} — no live socket for $targetPeerId / $targetIp (peer may have disconnected)")
                    return@launch
                }

                val streamKey = when {
                    activeOutputStreams.containsKey(targetPeerId) -> targetPeerId
                    targetIp.isNotEmpty() && activeOutputStreams.containsKey(targetIp) -> targetIp
                    else -> targetPeerId
                }
                val out = activeOutputStreams.getOrPut(streamKey) {
                    DataOutputStream(socket.getOutputStream())
                }

                val ackPacket = Packet(
                    header = PacketHeader(
                        type = ackType,
                        sequenceNumber = sequenceCounter.getAndIncrement(),
                        sourcePeerId = senderPeerId,
                        destinationPeerId = targetPeerId
                    ),
                    payload = msgId.toByteArray(StandardCharsets.UTF_8)
                )
                val bytes = PacketBuilder.serializePacket(ackPacket)
                synchronized(out) {
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
                Log.d(TAG, "${ackType.name} sent to $targetPeerId ($targetIp) for msgId=$msgId")
            } catch (e: java.net.SocketException) {
                // Broken pipe / connection reset = peer disconnected. Purge stale entries.
                Log.w(TAG, "Socket broken sending ${ackType.name} to $targetPeerId — purging stale connection")
                activeSockets.remove(targetPeerId)
                if (targetIp.isNotEmpty()) activeSockets.remove(targetIp)
                activeOutputStreams.remove(targetPeerId)
                if (targetIp.isNotEmpty()) activeOutputStreams.remove(targetIp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send ${ackType.name} to $targetPeerId: ${e.message}", e)
            }
        }
    }

    fun sendContactAcceptPacket(
        targetPeerId: String,
        targetIp: String,
        senderPeerId: String
    ) {
        scope.launch {
            try {
                val streamKey = when {
                    activeOutputStreams.containsKey(targetPeerId) -> targetPeerId
                    targetIp.isNotEmpty() && activeOutputStreams.containsKey(targetIp) -> targetIp
                    else -> targetPeerId
                }
                var socket = activeSockets[streamKey]
                if (socket == null && targetIp.isNotEmpty()) {
                    try {
                        val newSocket = Socket(targetIp, DEFAULT_PORT)
                        newSocket.keepAlive = true
                        activeSockets[targetPeerId] = newSocket
                        activeSockets[targetIp] = newSocket
                        scope.launch(Dispatchers.IO) {
                            handleIncomingSocket(newSocket)
                        }
                        socket = newSocket
                    } catch (_: Exception) { null }
                }

                if (socket == null) return@launch
                val out = activeOutputStreams.getOrPut(streamKey) {
                    DataOutputStream(socket.getOutputStream())
                }

                val acceptPacket = Packet(
                    header = PacketHeader(
                        type = PacketType.CONTACT_ACCEPT,
                        sequenceNumber = sequenceCounter.getAndIncrement(),
                        sourcePeerId = senderPeerId,
                        destinationPeerId = targetPeerId
                    ),
                    payload = "CONTACT_ACCEPT".toByteArray(StandardCharsets.UTF_8)
                )
                val bytes = PacketBuilder.serializePacket(acceptPacket)
                synchronized(out) {
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
                Log.d(TAG, "Sent CONTACT_ACCEPT to peerId=$targetPeerId ip=$targetIp")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send CONTACT_ACCEPT to $targetPeerId: ${e.message}")
            }
        }
    }

    fun sendPacket(packet: Packet, targetPeerId: String, targetIp: String = "") {
        scope.launch(Dispatchers.IO) {
            sendPacketDirect(packet, targetPeerId, targetIp)
        }
    }

    fun sendPacketDirect(packet: Packet, targetPeerId: String, targetIp: String = "") {
        try {
            val key = if (targetPeerId.isNotEmpty() && activeSockets.containsKey(targetPeerId)) targetPeerId
                      else if (targetIp.isNotEmpty() && activeSockets.containsKey(targetIp)) targetIp
                      else ""
            var socket = if (key.isNotEmpty()) activeSockets[key] else null

            if (socket == null || socket.isClosed) {
                if (targetIp.isEmpty()) {
                    Log.w(TAG, "Cannot send packet ${packet.header.type} — no live socket for $targetPeerId")
                    return
                }
                val newSocket = Socket()
                configureSocketPerformance(newSocket)
                newSocket.connect(java.net.InetSocketAddress(targetIp, DEFAULT_PORT), 3000)
                if (targetPeerId.isNotEmpty()) activeSockets[targetPeerId] = newSocket
                activeSockets[targetIp] = newSocket
                if (targetPeerId.isNotEmpty()) activeOutputStreams.remove(targetPeerId)
                activeOutputStreams.remove(targetIp)
                scope.launch(Dispatchers.IO) { handleIncomingSocket(newSocket) }
                socket = newSocket
            }

            val streamKey = when {
                targetPeerId.isNotEmpty() && activeOutputStreams.containsKey(targetPeerId) -> targetPeerId
                targetIp.isNotEmpty() && activeOutputStreams.containsKey(targetIp) -> targetIp
                targetPeerId.isNotEmpty() -> targetPeerId
                else -> targetIp
            }
            val out = activeOutputStreams.getOrPut(streamKey) {
                DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 256 * 1024))
            }

            val bytes = PacketBuilder.serializePacket(packet)
            val frame = ByteArray(4 + bytes.size)
            frame[0] = (bytes.size ushr 24).toByte()
            frame[1] = (bytes.size ushr 16).toByte()
            frame[2] = (bytes.size ushr 8).toByte()
            frame[3] = bytes.size.toByte()
            System.arraycopy(bytes, 0, frame, 4, bytes.size)

            synchronized(out) {
                out.write(frame)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet ${packet.header.type} to $targetPeerId: ${e.message}", e)
            if (targetPeerId.isNotEmpty()) activeSockets.remove(targetPeerId)
            if (targetIp.isNotEmpty()) activeSockets.remove(targetIp)
            if (targetPeerId.isNotEmpty()) activeOutputStreams.remove(targetPeerId)
            if (targetIp.isNotEmpty()) activeOutputStreams.remove(targetIp)
        }
    }

    fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) { }
        closeActiveConnections()
    }

    fun closeActiveConnections() {
        for (socket in activeSockets.values) {
            try { socket.close() } catch (e: Exception) { }
        }
        activeSockets.clear()
        activeOutputStreams.clear()
        helloResponsesSent.clear()
    }

}


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
import com.hypershare.ui.chat.ChatMessageItem
import com.hypershare.ui.chat.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class AckEvent(val msgId: String, val status: MessageStatus)

class LanSocketManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequenceCounter = AtomicInteger(1)

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

    @Volatile var activeChatPeerId: String? = null

    companion object {
        const val DEFAULT_PORT = 47200
        private const val TAG = "LanSocketManager"
        private const val NOTIFICATION_CHANNEL_ID = "hypershare_chat_messages"

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
                    socket.keepAlive = true
                    Log.d(TAG, "Accepted connection from ${socket.inetAddress.hostAddress}")
                    handleIncomingSocket(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}", e)
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        scope.launch {
            val senderIp = try { socket.inetAddress?.hostAddress ?: "unknown" } catch (_: Exception) { "unknown" }
            try {
                val input = DataInputStream(socket.getInputStream())
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
                                    repository.markPeerAcceptanceReceived(sourcePeerId)
                                }
                            }
                            _contactAcceptEventsFlow.emit(ContactAcceptEvent(sourcePeerId))
                            val senderName = appContext?.let { ctx ->
                                com.hypershare.db.MessageRepository(ctx).getPeerDisplayName(sourcePeerId)
                            } ?: sourcePeerId.take(12)
                            showIncomingNotification(sourcePeerId, "$senderName accepted & saved your contact! Tap to pair.")
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
                try { socket.close() } catch (_: Exception) { }
            }
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
                        Log.w(TAG, "Cannot send message to $targetPeerId — targetIp is empty")
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
                var socket = activeSockets[streamKey] ?: if (targetIp.isNotEmpty()) {
                    try { Socket(targetIp, DEFAULT_PORT) } catch (_: Exception) { null }
                } else null

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

    fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) { }
        for (socket in activeSockets.values) {
            try { socket.close() } catch (e: Exception) { }
        }
        activeSockets.clear()
        activeOutputStreams.clear()
    }

}


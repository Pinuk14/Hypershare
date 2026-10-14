package com.hypershare.db

import android.content.ContentValues
import android.content.Context
import com.hypershare.ui.chat.ChatMessageItem
import com.hypershare.ui.chat.MessageStatus

class MessageRepository(context: Context) {

    private val dbHelper = ChatDatabaseHelper(context)

    init {
        cleanupGhostAndDemoData()
    }

    fun getMessagesForPeer(peerId: String): List<ChatMessageItem> {
        val messages = mutableListOf<ChatMessageItem>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            "messages",
            arrayOf("id", "peer_id", "text", "timestamp", "is_outgoing", "delivery_status"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            "timestamp ASC"
        )

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val peerIdIndex = it.getColumnIndexOrThrow("peer_id")
            val textIndex = it.getColumnIndexOrThrow("text")
            val isOutgoingIndex = it.getColumnIndexOrThrow("is_outgoing")
            val timestampIndex = it.getColumnIndexOrThrow("timestamp")
            val statusIndex = it.getColumnIndexOrThrow("delivery_status")

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val peer = it.getString(peerIdIndex)
                val text = it.getString(textIndex)
                val isOutgoing = it.getInt(isOutgoingIndex) == 1
                val timestamp = it.getLong(timestampIndex)
                val statusStr = it.getString(statusIndex)
                val status = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { MessageStatus.SENT }

                messages.add(
                    ChatMessageItem(
                        id = id,
                        senderId = if (isOutgoing) "local" else peer,
                        text = text,
                        isOutgoing = isOutgoing,
                        timestamp = timestamp,
                        status = status
                    )
                )
            }
        }
        return messages
    }

    fun insertMessage(peerId: String, message: ChatMessageItem) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", message.id)
            put("peer_id", peerId)
            put("text", message.text)
            put("timestamp", message.timestamp)
            put("is_outgoing", if (message.isOutgoing) 1 else 0)
            put("delivery_status", message.status.name)
        }

        db.insertWithOnConflict("messages", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("delivery_status", status.name)
        }
        db.update("messages", values, "id = ?", arrayOf(messageId))
    }

    fun deleteChatHistory(peerId: String) {
        val db = dbHelper.writableDatabase
        db.delete("messages", "peer_id = ?", arrayOf(peerId))
    }

    fun getOutgoingMessageCountForPeer(peerId: String): Int {
        if (peerId.isEmpty()) return 0
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE peer_id = ? AND is_outgoing = 1",
            arrayOf(peerId)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun getUnreadMessageCountForPeer(peerId: String): Int {
        if (peerId.isEmpty()) return 0
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE peer_id = ? AND is_outgoing = 0 AND delivery_status != 'READ'",
            arrayOf(peerId)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun getTotalUnreadMessageCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE is_outgoing = 0 AND delivery_status != 'READ'",
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun getLastMessageForPeer(peerId: String): ChatMessageItem? {
        if (peerId.isEmpty()) return null
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            arrayOf("id", "peer_id", "text", "timestamp", "is_outgoing", "delivery_status"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            "timestamp DESC",
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val text = it.getString(it.getColumnIndexOrThrow("text"))
                val timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                val isOutgoing = it.getInt(it.getColumnIndexOrThrow("is_outgoing")) == 1
                val statusStr = it.getString(it.getColumnIndexOrThrow("delivery_status"))
                val status = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { MessageStatus.SENT }
                return ChatMessageItem(
                    id = id,
                    senderId = if (isOutgoing) "local" else peerId,
                    text = text,
                    isOutgoing = isOutgoing,
                    timestamp = timestamp,
                    status = status
                )
            }
        }
        return null
    }

    fun savePeer(peerId: String, displayName: String, isTrusted: Boolean = false, hasPeerAccepted: Boolean = false, publicKey: String? = null) {
        if (peerId.isEmpty() || displayName.isEmpty()) return
        val db = dbHelper.writableDatabase

        // Check existing trust state so discovery doesn't overwrite established pairing
        val currentlyTrusted = isPeerTrusted(peerId)
        val currentlyAccepted = hasPeerAcceptedUs(peerId)
        val finalTrust = if (currentlyTrusted) 1 else (if (isTrusted) 1 else 0)
        val finalAccepted = if (currentlyAccepted) 1 else (if (hasPeerAccepted) 1 else 0)

        val values = ContentValues().apply {
            put("peer_id", peerId)
            put("display_name", displayName)
            put("last_seen", System.currentTimeMillis())
            put("is_trusted", finalTrust)
            put("has_peer_accepted", finalAccepted)
            if (!publicKey.isNullOrEmpty()) {
                put("public_key", publicKey)
            }
        }
        db.insertWithOnConflict("peers", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markPeerAsTrusted(peerId: String, publicKey: String? = null) {
        if (peerId.isEmpty()) return
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_trusted", 1)
            if (!publicKey.isNullOrEmpty()) {
                put("public_key", publicKey)
            }
        }
        db.update("peers", values, "peer_id = ?", arrayOf(peerId))
    }

    fun markPeerAcceptanceReceived(peerId: String) {
        if (peerId.isEmpty()) return
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("has_peer_accepted", 1)
        }
        db.update("peers", values, "peer_id = ?", arrayOf(peerId))
    }

    fun isPeerTrusted(peerId: String): Boolean {
        if (peerId.isEmpty()) return false
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "peers",
            arrayOf("is_trusted"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex("is_trusted")
                if (index != -1) return it.getInt(index) == 1
            }
        }
        return false
    }

    fun hasPeerAcceptedUs(peerId: String): Boolean {
        if (peerId.isEmpty()) return false
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "peers",
            arrayOf("has_peer_accepted"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex("has_peer_accepted")
                if (index != -1) return it.getInt(index) == 1
            }
        }
        return false
    }

    fun getPeerDisplayName(peerId: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "peers",
            arrayOf("display_name"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex("display_name")
                if (index != -1) return it.getString(index)
            }
        }
        return null
    }

    fun cleanupDemoData() = cleanupGhostAndDemoData()

    fun cleanupGhostAndDemoData(myPeerId: String? = null, myUsername: String? = null) {
        try {
            val db = dbHelper.writableDatabase
            // Delete demo peers
            db.delete("messages", "peer_id IN ('peer-1', 'peer-2', 'demo-peer-1', 'demo-peer-2')", null)
            db.delete("peers", "peer_id IN ('peer-1', 'peer-2', 'demo-peer-1', 'demo-peer-2')", null)

            // Delete self if it got inserted into peers table
            if (!myPeerId.isNullOrBlank()) {
                db.delete("peers", "peer_id = ? OR peer_id = 'local'", arrayOf(myPeerId))
            }
            if (!myUsername.isNullOrBlank()) {
                val cleanUser = myUsername.lowercase().replace(Regex("[^a-z0-9]"), "")
                db.delete(
                    "peers",
                    "REPLACE(REPLACE(REPLACE(LOWER(display_name), '_', ''), ' ', ''), '-', '') = ?",
                    arrayOf(cleanUser)
                )
            }
            if (!myPeerId.isNullOrBlank()) {
                db.delete("messages", "peer_id = ? OR peer_id = 'local'", arrayOf(myPeerId))
            }

            // Delete mDNS ghost peers that have parentheses or spaces in peer_id or display_name and no messages
            db.delete(
                "peers",
                "peer_id NOT IN (SELECT DISTINCT peer_id FROM messages) AND (peer_id LIKE '%(%' OR display_name LIKE '%(%' OR peer_id LIKE 'HyperShare_%' OR peer_id LIKE '% %')",
                null
            )

            // Delete temporary/ghost Wi-Fi Direct and Windows Miracast peers from previous tests
            db.delete(
                "peers",
                "peer_id LIKE 'Android_%' OR display_name LIKE 'Android_%' OR " +
                "peer_id LIKE 'DESKTOP-%' OR display_name LIKE 'DESKTOP-%' OR " +
                "peer_id LIKE 'LAPTOP-%' OR display_name LIKE 'LAPTOP-%' OR " +
                "peer_id GLOB '[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:*' OR " +
                "display_name GLOB '[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:*'",
                null
            )
            db.delete(
                "messages",
                "peer_id LIKE 'Android_%' OR " +
                "peer_id LIKE 'DESKTOP-%' OR " +
                "peer_id LIKE 'LAPTOP-%' OR " +
                "peer_id GLOB '[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:*'",
                null
            )

            // Migrate messages from legacy short mDNS peer_ids (e.g. Vivo_I2302) to genuine cryptographic peer ID if both exist
            db.execSQL("""
                UPDATE messages 
                SET peer_id = (
                    SELECT p2.peer_id FROM peers p2 
                    WHERE (LENGTH(p2.peer_id) >= 16 OR p2.peer_id LIKE 'peer-%')
                      AND REPLACE(REPLACE(LOWER(p2.display_name), '_', ''), ' ', '') = REPLACE(REPLACE(LOWER(messages.peer_id), '_', ''), ' ', '')
                    LIMIT 1
                )
                WHERE LENGTH(peer_id) < 16
                  AND NOT peer_id LIKE 'peer-%'
                  AND EXISTS (
                      SELECT 1 FROM peers p2 
                      WHERE (LENGTH(p2.peer_id) >= 16 OR p2.peer_id LIKE 'peer-%')
                        AND REPLACE(REPLACE(LOWER(p2.display_name), '_', ''), ' ', '') = REPLACE(REPLACE(LOWER(messages.peer_id), '_', ''), ' ', '')
                  )
            """.trimIndent())

            // Delete legacy short mDNS peer_ids when a genuine cryptographic peer ID exists for the same device
            db.execSQL("""
                DELETE FROM peers 
                WHERE LENGTH(peer_id) < 16
                  AND NOT peer_id LIKE 'peer-%'
                  AND EXISTS (
                      SELECT 1 FROM peers p2 
                      WHERE (LENGTH(p2.peer_id) >= 16 OR p2.peer_id LIKE 'peer-%')
                        AND REPLACE(REPLACE(LOWER(p2.display_name), '_', ''), ' ', '') = REPLACE(REPLACE(LOWER(peers.display_name), '_', ''), ' ', '')
                  )
            """.trimIndent())
        } catch (_: Exception) {}
    }

    fun getAllConversations(myPeerId: String = ""): List<PeerConversation> {
        val conversations = mutableListOf<PeerConversation>()
        val db = dbHelper.readableDatabase

        // Ensure any peer with recorded messages is also indexed in the peers table (excluding self and demo)
        val syncQuery = """
            INSERT OR IGNORE INTO peers (peer_id, display_name, last_seen, is_trusted, has_peer_accepted)
            SELECT DISTINCT m.peer_id, m.peer_id, MAX(m.timestamp), 0, 0
            FROM messages m
            WHERE m.peer_id NOT IN (SELECT peer_id FROM peers)
              AND m.peer_id NOT IN ('peer-1', 'peer-2', 'demo-peer-1', 'demo-peer-2', 'local', ?)
            GROUP BY m.peer_id
        """.trimIndent()
        try {
            dbHelper.writableDatabase.execSQL(syncQuery, arrayOf(myPeerId))
        } catch (_: Exception) {}

        val query = """
            SELECT 
                p.peer_id, 
                p.display_name, 
                p.last_seen, 
                p.is_trusted, 
                p.has_peer_accepted,
                MAX(COALESCE(m.timestamp, p.last_seen)) AS last_activity,
                (SELECT m2.text FROM messages m2 WHERE m2.peer_id = p.peer_id ORDER BY m2.timestamp DESC LIMIT 1) AS last_message,
                COUNT(CASE WHEN m.is_outgoing = 0 AND m.delivery_status != 'READ' THEN 1 END) AS unread_count
            FROM peers p
            LEFT JOIN messages m ON p.peer_id = m.peer_id
            WHERE p.peer_id NOT IN ('peer-1', 'peer-2', 'demo-peer-1', 'demo-peer-2', 'local', ?)
              AND p.peer_id NOT LIKE '%(%'
              AND p.display_name NOT LIKE '%(%'
            GROUP BY p.peer_id
            ORDER BY last_activity DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(myPeerId))
        cursor.use {
            val peerIdIdx = it.getColumnIndexOrThrow("peer_id")
            val nameIdx = it.getColumnIndexOrThrow("display_name")
            val lastSeenIdx = it.getColumnIndexOrThrow("last_seen")
            val trustedIdx = it.getColumnIndexOrThrow("is_trusted")
            val acceptedIdx = it.getColumnIndexOrThrow("has_peer_accepted")
            val lastActIdx = it.getColumnIndexOrThrow("last_activity")
            val lastMsgIdx = it.getColumnIndexOrThrow("last_message")
            val unreadIdx = it.getColumnIndexOrThrow("unread_count")

            while (it.moveToNext()) {
                val peerId = it.getString(peerIdIdx)
                val rawName = it.getString(nameIdx)
                val displayName = if (rawName.isNotBlank() && rawName != peerId) rawName else peerId.take(12)
                val lastSeen = it.getLong(lastSeenIdx)
                val isTrusted = it.getInt(trustedIdx) == 1
                val hasPeerAccepted = it.getInt(acceptedIdx) == 1
                val lastActivity = it.getLong(lastActIdx)
                val lastMessage = if (!it.isNull(lastMsgIdx)) it.getString(lastMsgIdx) else null
                val unreadCount = it.getInt(unreadIdx)

                conversations.add(
                    PeerConversation(
                        peerId = peerId,
                        displayName = displayName,
                        lastSeen = lastSeen,
                        isTrusted = isTrusted,
                        hasPeerAccepted = hasPeerAccepted,
                        lastActivity = if (lastActivity > 0) lastActivity else lastSeen,
                        lastMessageSnippet = lastMessage,
                        unreadCount = unreadCount
                    )
                )
            }
        }
        return conversations
    }
}

data class PeerConversation(
    val peerId: String,
    val displayName: String,
    val lastSeen: Long,
    val isTrusted: Boolean,
    val hasPeerAccepted: Boolean,
    val lastActivity: Long,
    val lastMessageSnippet: String?,
    val unreadCount: Int
)

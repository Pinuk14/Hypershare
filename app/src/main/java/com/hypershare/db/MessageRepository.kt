package com.hypershare.db

import android.content.ContentValues
import android.content.Context
import com.hypershare.ui.chat.ChatMessageItem
import com.hypershare.ui.chat.MessageStatus

class MessageRepository(context: Context) {

    private val dbHelper = ChatDatabaseHelper(context)

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
}

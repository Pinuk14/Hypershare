package com.hypershare.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.hypershare.model.Group
import com.hypershare.model.GroupMember
import com.hypershare.model.GroupMemberRole
import com.hypershare.model.GroupMessage
import com.hypershare.model.GroupType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.hypershare.model.JoinMethod

class GroupRepository(context: Context) {

    private val dbHelper = ChatDatabaseHelper(context)

    private val _groupsFlow = MutableStateFlow<List<Group>>(emptyList())
    val groupsFlow: StateFlow<List<Group>> = _groupsFlow.asStateFlow()

    init {
        refreshGroups()
    }

    fun refreshGroups() {
        _groupsFlow.value = getAllGroups()
    }

    fun createGroup(group: Group, members: List<GroupMember>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val groupValues = ContentValues().apply {
                put("group_id", group.groupId)
                put("group_name", group.groupName)
                put("admin_user_id", group.adminUserId)
                put("group_type", group.groupType.name)
                put("is_active", if (group.isActive) 1 else 0)
                put("created_at", group.createdAt)
                put("join_method", group.joinMethod.name)
            }
            db.insertWithOnConflict("groups", null, groupValues, SQLiteDatabase.CONFLICT_REPLACE)

            for (member in members) {
                val memberValues = ContentValues().apply {
                    put("group_id", group.groupId)
                    put("user_id", member.userId)
                    put("display_name", member.displayName)
                    put("public_key", member.publicKeyHex)
                    put("role", member.role.name)
                    put("joined_at", member.joinedAt)
                }
                db.insertWithOnConflict("group_members", null, memberValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshGroups()
    }

    fun findByGroupId(groupId: String): Group? = getGroup(groupId)

    fun getGroup(groupId: String): Group? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "groups",
            arrayOf("group_id", "group_name", "admin_user_id", "group_type", "is_active", "created_at", "join_method"),
            "group_id = ?",
            arrayOf(groupId),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val gId = it.getString(it.getColumnIndexOrThrow("group_id"))
                val name = it.getString(it.getColumnIndexOrThrow("group_name"))
                val admin = it.getString(it.getColumnIndexOrThrow("admin_user_id"))
                val typeStr = it.getString(it.getColumnIndexOrThrow("group_type"))
                val isActive = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                val joinMethodStr = try { it.getString(it.getColumnIndexOrThrow("join_method")) } catch (_: Exception) { "DIRECT" }

                val type = try { GroupType.valueOf(typeStr) } catch (_: Exception) { GroupType.PERMANENT }
                val joinMethod = try { JoinMethod.valueOf(joinMethodStr) } catch (_: Exception) { JoinMethod.DIRECT }
                return Group(
                    groupId = gId,
                    groupName = name,
                    adminUserId = admin,
                    groupType = type,
                    isActive = isActive,
                    createdAt = createdAt,
                    joinMethod = joinMethod
                )
            }
        }
        return null
    }

    fun getAllGroups(): List<Group> {
        val groups = mutableListOf<Group>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "groups",
            arrayOf("group_id", "group_name", "admin_user_id", "group_type", "is_active", "created_at", "join_method"),
            null, null, null, null, "created_at DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val gId = it.getString(it.getColumnIndexOrThrow("group_id"))
                val name = it.getString(it.getColumnIndexOrThrow("group_name"))
                val admin = it.getString(it.getColumnIndexOrThrow("admin_user_id"))
                val typeStr = it.getString(it.getColumnIndexOrThrow("group_type"))
                val isActive = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                val joinMethodStr = try { it.getString(it.getColumnIndexOrThrow("join_method")) } catch (_: Exception) { "DIRECT" }

                val type = try { GroupType.valueOf(typeStr) } catch (_: Exception) { GroupType.PERMANENT }
                val joinMethod = try { JoinMethod.valueOf(joinMethodStr) } catch (_: Exception) { JoinMethod.DIRECT }
                groups.add(
                    Group(
                        groupId = gId,
                        groupName = name,
                        adminUserId = admin,
                        groupType = type,
                        isActive = isActive,
                        createdAt = createdAt,
                        joinMethod = joinMethod
                    )
                )
            }
        }
        return groups
    }

    fun getGroupMembers(groupId: String): List<GroupMember> {
        val members = mutableListOf<GroupMember>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "group_members",
            arrayOf("group_id", "user_id", "display_name", "public_key", "role", "joined_at"),
            "group_id = ?",
            arrayOf(groupId),
            null, null, "joined_at ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val gId = it.getString(it.getColumnIndexOrThrow("group_id"))
                val userId = it.getString(it.getColumnIndexOrThrow("user_id"))
                val displayName = it.getString(it.getColumnIndexOrThrow("display_name"))
                val pubKey = it.getString(it.getColumnIndexOrThrow("public_key")) ?: ""
                val roleStr = it.getString(it.getColumnIndexOrThrow("role"))
                val joinedAt = it.getLong(it.getColumnIndexOrThrow("joined_at"))

                val role = try { GroupMemberRole.valueOf(roleStr) } catch (_: Exception) { GroupMemberRole.MEMBER }
                members.add(
                    GroupMember(
                        groupId = gId,
                        userId = userId,
                        displayName = displayName,
                        publicKeyHex = pubKey,
                        role = role,
                        joinedAt = joinedAt
                    )
                )
            }
        }
        return members
    }

    fun addMember(groupId: String, member: GroupMember) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("group_id", groupId)
            put("user_id", member.userId)
            put("display_name", member.displayName)
            put("public_key", member.publicKeyHex)
            put("role", member.role.name)
            put("joined_at", member.joinedAt)
        }
        db.insertWithOnConflict("group_members", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeMember(groupId: String, userId: String) {
        val db = dbHelper.writableDatabase
        db.delete("group_members", "group_id = ? AND user_id = ?", arrayOf(groupId, userId))
    }

    fun updateGroupActiveStatus(groupId: String, isActive: Boolean) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_active", if (isActive) 1 else 0)
        }
        db.update("groups", values, "group_id = ?", arrayOf(groupId))
        refreshGroups()
    }

    fun deleteGroup(groupId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("groups", "group_id = ?", arrayOf(groupId))
            db.delete("group_members", "group_id = ?", arrayOf(groupId))
            db.delete("group_messages", "group_id = ?", arrayOf(groupId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshGroups()
    }

    fun insertGroupMessage(message: GroupMessage) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", message.messageId)
            put("group_id", message.groupId)
            put("sender_id", message.senderId)
            put("sender_display_name", message.senderDisplayName)
            put("text", message.text)
            put("timestamp", message.timestamp)
            put("delivery_status", message.deliveryStatus)
        }
        db.insertWithOnConflict("group_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getGroupMessages(groupId: String): List<GroupMessage> {
        val messages = mutableListOf<GroupMessage>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "group_messages",
            arrayOf("id", "group_id", "sender_id", "sender_display_name", "text", "timestamp", "delivery_status"),
            "group_id = ?",
            arrayOf(groupId),
            null, null, "timestamp ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val gId = it.getString(it.getColumnIndexOrThrow("group_id"))
                val senderId = it.getString(it.getColumnIndexOrThrow("sender_id"))
                val senderName = it.getString(it.getColumnIndexOrThrow("sender_display_name"))
                val text = it.getString(it.getColumnIndexOrThrow("text"))
                val ts = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                val status = it.getString(it.getColumnIndexOrThrow("delivery_status"))

                messages.add(
                    GroupMessage(
                        messageId = id,
                        groupId = gId,
                        senderId = senderId,
                        senderDisplayName = senderName,
                        text = text,
                        timestamp = ts,
                        deliveryStatus = status
                    )
                )
            }
        }
        return messages
    }

    fun recordMessageReceipt(messageId: String, userId: String, status: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("user_id", userId)
            put("status", status)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("group_message_receipts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun checkAndUpdateGroupMessageStatus(groupId: String, messageId: String, totalOtherMembersCount: Int): String {
        if (totalOtherMembersCount <= 0) return "READ_ALL"
        val db = dbHelper.writableDatabase

        var readCount = 0
        var deliveredCount = 0

        val cursor = db.query(
            "group_message_receipts",
            arrayOf("user_id", "status"),
            "message_id = ?",
            arrayOf(messageId),
            null, null, null
        )

        cursor.use {
            while (it.moveToNext()) {
                val st = it.getString(it.getColumnIndexOrThrow("status"))
                if (st == "READ") {
                    readCount++
                    deliveredCount++
                } else if (st == "DELIVERED") {
                    deliveredCount++
                }
            }
        }

        val newStatus = when {
            readCount >= totalOtherMembersCount -> "READ_ALL"
            deliveredCount >= totalOtherMembersCount -> "DELIVERED_ALL"
            else -> "SENT"
        }

        val values = ContentValues().apply {
            put("delivery_status", newStatus)
        }
        db.update("group_messages", values, "id = ?", arrayOf(messageId))

        return newStatus
    }

    fun getUnreadCountForGroup(groupId: String, localUserId: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM group_messages WHERE group_id = ? AND sender_id != ? AND delivery_status != 'READ'",
            arrayOf(groupId, localUserId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun markGroupMessagesAsRead(groupId: String, localUserId: String): List<GroupMessage> {
        val unreadMessages = mutableListOf<GroupMessage>()
        val db = dbHelper.writableDatabase

        val cursor = db.query(
            "group_messages",
            arrayOf("id", "group_id", "sender_id", "sender_display_name", "text", "timestamp", "delivery_status"),
            "group_id = ? AND sender_id != ? AND delivery_status != 'READ'",
            arrayOf(groupId, localUserId),
            null, null, null
        )

        cursor.use {
            while (it.moveToNext()) {
                unreadMessages.add(
                    GroupMessage(
                        messageId = it.getString(it.getColumnIndexOrThrow("id")),
                        groupId = it.getString(it.getColumnIndexOrThrow("group_id")),
                        senderId = it.getString(it.getColumnIndexOrThrow("sender_id")),
                        senderDisplayName = it.getString(it.getColumnIndexOrThrow("sender_display_name")),
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        deliveryStatus = "READ"
                    )
                )
            }
        }

        val values = ContentValues().apply {
            put("delivery_status", "READ")
        }
        db.update("group_messages", values, "group_id = ? AND sender_id != ?", arrayOf(groupId, localUserId))

        return unreadMessages
    }
}

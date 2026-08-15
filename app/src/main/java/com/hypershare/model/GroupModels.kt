package com.hypershare.model

import kotlinx.serialization.Serializable

enum class GroupType {
    PERMANENT,
    TEMPORARY
}

enum class GroupMemberRole {
    ADMIN,
    MEMBER
}

enum class JoinMethod {
    QR,
    BROADCAST,
    DIRECT
}

data class Group(
    val groupId: String,
    val groupName: String,
    val adminUserId: String,
    val groupType: GroupType,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val joinMethod: JoinMethod = JoinMethod.DIRECT
)

data class GroupMember(
    val groupId: String,
    val userId: String,
    val displayName: String,
    val publicKeyHex: String,
    val role: GroupMemberRole = GroupMemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false
)

data class GroupMessage(
    val messageId: String,
    val groupId: String,
    val senderId: String,
    val senderDisplayName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "SENT"
)

// --- Serializable DTOs for Binary Packet Payloads ---

@Serializable
data class MemberDto(
    val userId: String,
    val displayName: String,
    val publicKeyHex: String,
    val role: String = "MEMBER"
)

@Serializable
data class GroupCreatePayload(
    val groupId: String,
    val groupName: String,
    val adminUserId: String,
    val groupType: String,
    val members: List<MemberDto>,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class GroupJoinAckPayload(
    val groupId: String,
    val joiningUserId: String
)

@Serializable
data class GroupMsgPayload(
    val groupId: String,
    val messageId: String,
    val senderId: String,
    val senderDisplayName: String,
    val timestamp: Long,
    val text: String
)

@Serializable
data class GroupMsgAckPayload(
    val groupId: String,
    val messageId: String,
    val ackUserId: String
)

@Serializable
data class GroupMemberAddPayload(
    val groupId: String,
    val newMember: MemberDto,
    val adminUserId: String
)

@Serializable
data class GroupMemberRemovePayload(
    val groupId: String,
    val removedUserId: String,
    val adminUserId: String
)

@Serializable
data class GroupKickedPayload(
    val groupId: String,
    val reason: String
)

@Serializable
data class GroupDissolvePayload(
    val groupId: String,
    val dissolveTimestamp: Long
)

@Serializable
data class GroupRestorePayload(
    val groupId: String,
    val activeMemberIds: List<String>
)

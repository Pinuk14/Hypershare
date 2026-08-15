package com.hypershare.application

import android.content.Context
import com.hypershare.db.GroupRepository
import com.hypershare.identity.IdentityManager
import com.hypershare.model.Group
import com.hypershare.model.GroupCreatePayload
import com.hypershare.model.GroupDissolvePayload
import com.hypershare.model.GroupJoinAckPayload
import com.hypershare.model.GroupKickedPayload
import com.hypershare.model.GroupMember
import com.hypershare.model.GroupMemberAddPayload
import com.hypershare.model.GroupMemberRemovePayload
import com.hypershare.model.GroupMemberRole
import com.hypershare.model.GroupMessage
import com.hypershare.model.GroupMsgAckPayload
import com.hypershare.model.GroupMsgPayload
import com.hypershare.model.GroupRestorePayload
import com.hypershare.model.GroupType
import com.hypershare.model.JoinMethod
import com.hypershare.model.MemberDto
import com.hypershare.model.Packet
import com.hypershare.model.PacketType
import com.hypershare.protocol.PacketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed class GroupEvent {
    data class GroupCreated(val group: Group) : GroupEvent()
    data class GroupJoined(val group: Group) : GroupEvent()
    data class GroupMessageReceived(val message: GroupMessage) : GroupEvent()
    data class MemberAdded(val groupId: String, val member: GroupMember) : GroupEvent()
    data class MemberRemoved(val groupId: String, val userId: String) : GroupEvent()
    data class GroupDissolved(val groupId: String) : GroupEvent()
    data class JoinRequestReceived(val groupId: String, val groupName: String, val joiningUserId: String, val joiningDisplayName: String) : GroupEvent()
    data class MessageStatusChanged(val groupId: String, val messageId: String, val status: String) : GroupEvent()
}

class GroupManager(
    private val context: Context,
    private val groupRepository: GroupRepository,
    private val identityManager: IdentityManager,
    private val sendPacketLambda: (destPeerId: String, packet: Packet) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val seqNumCounter = AtomicInteger(1000)

    private val _groupEvents = MutableSharedFlow<GroupEvent>()
    val groupEvents: SharedFlow<GroupEvent> = _groupEvents.asSharedFlow()

    // Active connected count for temporary groups: groupId -> ConcurrentHashMap of online member userIds
    private val activeConnectedMembers = ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>>()

    /**
     * Admin Action: Create a new Permanent or Temporary Group and distribute GROUP_CREATE packets to initial members.
     */
    fun createGroup(groupName: String, groupType: GroupType, initialMembers: List<GroupMember>): Group {
        val groupId = UUID.randomUUID().toString()
        val localUserId = identityManager.getUserId()
        val localPubKey = identityManager.getPublicKeyHex()
        val localDisplayName = com.hypershare.application.UserIdentityManager.getInstance(context).getUsername().ifBlank { "User" }

        val adminMember = GroupMember(
            groupId = groupId,
            userId = localUserId,
            displayName = localDisplayName,
            publicKeyHex = localPubKey,
            role = GroupMemberRole.ADMIN,
            isOnline = true
        )

        val fullMemberList = mutableListOf(adminMember)
        initialMembers.forEach {
            if (it.userId != localUserId) {
                fullMemberList.add(it.copy(groupId = groupId, role = GroupMemberRole.MEMBER))
            }
        }

        val group = Group(
            groupId = groupId,
            groupName = groupName,
            adminUserId = localUserId,
            groupType = groupType,
            isActive = true
        )

        // Save to local DB
        groupRepository.createGroup(group, fullMemberList)

        // Track active members for temporary groups
        val activeSet = ConcurrentHashMap.newKeySet<String>()
        activeSet.add(localUserId)
        initialMembers.forEach { activeSet.add(it.userId) }
        activeConnectedMembers[groupId] = activeSet

        // Build and send GROUP_CREATE packets to each initial member
        val payloadDto = GroupCreatePayload(
            groupId = groupId,
            groupName = groupName,
            adminUserId = localUserId,
            groupType = groupType.name,
            members = fullMemberList.map {
                MemberDto(it.userId, it.displayName, it.publicKeyHex, it.role.name)
            }
        )
        val payloadJson = json.encodeToString(payloadDto)

        for (member in fullMemberList) {
            if (member.userId != localUserId) {
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = member.userId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_CREATE,
                    jsonPayload = payloadJson
                )
                sendPacketLambda(member.userId, packet)
            }
        }

        scope.launch { _groupEvents.emit(GroupEvent.GroupCreated(group)) }
        return group
    }

    /**
     * Broadcasts a GROUP_CREATE (0x20) invite packet to target peers.
     */
    fun broadcastGroupInvite(groupId: String, targetPeerIds: List<String>) {
        val group = groupRepository.getGroup(groupId) ?: return
        val members = groupRepository.getGroupMembers(groupId)
        val localUserId = identityManager.getUserId()

        val payloadDto = GroupCreatePayload(
            groupId = groupId,
            groupName = group.groupName,
            adminUserId = localUserId,
            groupType = group.groupType.name,
            members = members.map { MemberDto(it.userId, it.displayName, it.publicKeyHex, it.role.name) }
        )
        val payloadJson = json.encodeToString(payloadDto)

        for (peerId in targetPeerIds) {
            if (peerId != localUserId) {
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = peerId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_CREATE,
                    jsonPayload = payloadJson
                )
                sendPacketLambda(peerId, packet)
            }
        }
    }

    /**
     * Send a text message to a group via O(N) direct sends to all active memberIds.
     */
    fun sendGroupMessage(groupId: String, text: String): GroupMessage? {
        val localUserId = identityManager.getUserId()
        val localDisplayName = com.hypershare.application.UserIdentityManager.getInstance(context).getUsername().ifBlank { "User" }
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val groupMsg = GroupMessage(
            messageId = messageId,
            groupId = groupId,
            senderId = localUserId,
            senderDisplayName = localDisplayName,
            text = text,
            timestamp = now,
            deliveryStatus = "SENT"
        )

        // Save locally
        groupRepository.insertGroupMessage(groupMsg)

        val members = groupRepository.getGroupMembers(groupId)
        val payloadDto = GroupMsgPayload(
            groupId = groupId,
            messageId = messageId,
            senderId = localUserId,
            senderDisplayName = localDisplayName,
            timestamp = now,
            text = text
        )
        val payloadJson = json.encodeToString(payloadDto)

        for (member in members) {
            if (member.userId != localUserId) {
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = member.userId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_MSG,
                    jsonPayload = payloadJson
                )
                sendPacketLambda(member.userId, packet)
            }
        }

        return groupMsg
    }

    /**
     * Admin Action: Add a member to an existing group.
     */
    fun addMember(groupId: String, newMember: GroupMember) {
        val localUserId = identityManager.getUserId()
        val group = groupRepository.getGroup(groupId) ?: return
        if (group.adminUserId != localUserId) return // Only admin can add

        groupRepository.addMember(groupId, newMember)

        val payloadDto = GroupMemberAddPayload(
            groupId = groupId,
            newMember = MemberDto(newMember.userId, newMember.displayName, newMember.publicKeyHex, newMember.role.name),
            adminUserId = localUserId
        )
        val payloadJson = json.encodeToString(payloadDto)

        val members = groupRepository.getGroupMembers(groupId)
        for (member in members) {
            if (member.userId != localUserId) {
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = member.userId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_MEMBER_ADD,
                    jsonPayload = payloadJson
                )
                sendPacketLambda(member.userId, packet)
            }
        }
        scope.launch { _groupEvents.emit(GroupEvent.MemberAdded(groupId, newMember)) }
    }

    /**
     * Admin Action: Remove a member from an existing group.
     */
    fun removeMember(groupId: String, removedUserId: String) {
        val localUserId = identityManager.getUserId()
        val group = groupRepository.getGroup(groupId) ?: return
        if (group.adminUserId != localUserId) return // Only admin can remove

        val members = groupRepository.getGroupMembers(groupId)
        groupRepository.removeMember(groupId, removedUserId)

        val payloadDto = GroupMemberRemovePayload(
            groupId = groupId,
            removedUserId = removedUserId,
            adminUserId = localUserId
        )
        val payloadJson = json.encodeToString(payloadDto)

        for (member in members) {
            if (member.userId != localUserId) {
                val packetType = if (member.userId == removedUserId) PacketType.GROUP_KICKED else PacketType.GROUP_MEMBER_REMOVE
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = member.userId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = packetType,
                    jsonPayload = if (packetType == PacketType.GROUP_KICKED) {
                        json.encodeToString(GroupKickedPayload(groupId, "Removed by admin"))
                    } else payloadJson
                )
                sendPacketLambda(member.userId, packet)
            }
        }
        scope.launch { _groupEvents.emit(GroupEvent.MemberRemoved(groupId, removedUserId)) }
    }

    /**
     * Admin Action: Dissolve group.
     */
    fun dissolveGroup(groupId: String) {
        val localUserId = identityManager.getUserId()
        val group = groupRepository.getGroup(groupId) ?: return
        if (group.adminUserId != localUserId) return

        groupRepository.updateGroupActiveStatus(groupId, false)

        val payloadDto = GroupDissolvePayload(groupId = groupId, dissolveTimestamp = System.currentTimeMillis())
        val payloadJson = json.encodeToString(payloadDto)

        val members = groupRepository.getGroupMembers(groupId)
        for (member in members) {
            if (member.userId != localUserId) {
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = member.userId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_DISSOLVE,
                    jsonPayload = payloadJson
                )
                sendPacketLambda(member.userId, packet)
            }
        }
        scope.launch { _groupEvents.emit(GroupEvent.GroupDissolved(groupId)) }
    }

    private val broadcastInvitesSeen = ConcurrentHashMap.newKeySet<String>()

    /**
     * Handle incoming group network packets dispatch.
     */
    fun handleGroupPacket(packet: Packet, sessionManager: SessionManager? = null, isAppInForeground: Boolean = false) {
        val payloadStr = String(packet.payload, StandardCharsets.UTF_8)
        val localUserId = identityManager.getUserId()

        try {
            when (packet.header.type) {
                PacketType.GROUP_CREATE -> {
                    val createPayload = json.decodeFromString<GroupCreatePayload>(payloadStr)
                    val dedupKey = "${packet.header.sourcePeerId}_${createPayload.groupId}"

                    if (!broadcastInvitesSeen.add(dedupKey)) {
                        return // Already seen
                    }

                    if (packet.header.sourcePeerId == localUserId) return

                    val existingGroup = groupRepository.getGroup(createPayload.groupId)
                    if (existingGroup != null) {
                        val members = groupRepository.getGroupMembers(createPayload.groupId)
                        if (members.any { it.userId == localUserId }) {
                            return // Already a member
                        }
                    }

                    val adminName = createPayload.members.find { it.userId == createPayload.adminUserId }?.displayName ?: "Admin"

                    if (isAppInForeground && sessionManager != null) {
                        sessionManager.emitInviteBanner(
                            InviteBannerState(
                                groupId = createPayload.groupId,
                                groupName = createPayload.groupName,
                                adminUserId = createPayload.adminUserId,
                                adminDisplayName = adminName,
                                joinMethod = JoinMethod.BROADCAST
                            )
                        )
                    } else {
                        com.hypershare.service.LanSocketManager.getInstance().postBroadcastInviteNotification(
                            groupId = createPayload.groupId,
                            groupName = createPayload.groupName,
                            adminUserId = createPayload.adminUserId,
                            adminDisplayName = adminName
                        )
                    }
                }

                PacketType.GROUP_JOIN_ACK -> {
                    val ackPayload = json.decodeFromString<GroupJoinAckPayload>(payloadStr)
                    activeConnectedMembers.getOrPut(ackPayload.groupId) { ConcurrentHashMap.newKeySet() }.add(ackPayload.joiningUserId)

                    val group = groupRepository.getGroup(ackPayload.groupId)
                    if (group != null && group.adminUserId == localUserId) {
                        val members = groupRepository.getGroupMembers(ackPayload.groupId)
                        val isExistingMember = members.any { it.userId == ackPayload.joiningUserId }
                        if (!isExistingMember) {
                            val joiningName = ackPayload.joiningUserId.take(8)
                            scope.launch {
                                _groupEvents.emit(
                                    GroupEvent.JoinRequestReceived(
                                        groupId = ackPayload.groupId,
                                        groupName = group.groupName,
                                        joiningUserId = ackPayload.joiningUserId,
                                        joiningDisplayName = joiningName
                                    )
                                )
                            }
                        }
                    }
                }

                PacketType.GROUP_MSG -> {
                    val msgPayload = json.decodeFromString<GroupMsgPayload>(payloadStr)
                    val activeGroupChat = com.hypershare.service.LanSocketManager.getInstance().activeGroupChatId
                    val isViewingActiveGroup = activeGroupChat == msgPayload.groupId

                    val initialStatus = if (isViewingActiveGroup) "READ" else "RECEIVED"

                    val groupMsg = GroupMessage(
                        messageId = msgPayload.messageId,
                        groupId = msgPayload.groupId,
                        senderId = msgPayload.senderId,
                        senderDisplayName = msgPayload.senderDisplayName,
                        text = msgPayload.text,
                        timestamp = msgPayload.timestamp,
                        deliveryStatus = initialStatus
                    )

                    groupRepository.insertGroupMessage(groupMsg)

                    // 1. Send GROUP_MSG_ACK back to sender (delivered receipt)
                    val ackPayload = json.encodeToString(GroupMsgAckPayload(msgPayload.groupId, msgPayload.messageId, localUserId))
                    val ackPacket = PacketBuilder.buildGroupPacket(
                        sourcePeerId = localUserId,
                        destPeerId = msgPayload.senderId,
                        seqNum = seqNumCounter.incrementAndGet(),
                        type = PacketType.GROUP_MSG_ACK,
                        jsonPayload = ackPayload
                    )
                    sendPacketLambda(msgPayload.senderId, ackPacket)

                    // 2. If user is currently viewing this group chat, send GROUP_MSG_READ back immediately
                    if (isViewingActiveGroup) {
                        val readPayload = json.encodeToString(GroupMsgAckPayload(msgPayload.groupId, msgPayload.messageId, localUserId))
                        val readPacket = PacketBuilder.buildGroupPacket(
                            sourcePeerId = localUserId,
                            destPeerId = msgPayload.senderId,
                            seqNum = seqNumCounter.incrementAndGet(),
                            type = PacketType.GROUP_MSG_READ,
                            jsonPayload = readPayload
                        )
                        sendPacketLambda(msgPayload.senderId, readPacket)
                    } else {
                        // 3. Post system status bar notification if not viewing active group
                        val groupObj = groupRepository.getGroup(msgPayload.groupId)
                        val groupNameStr = groupObj?.groupName ?: "Group Chat"
                        com.hypershare.service.LanSocketManager.getInstance().postGroupMessageNotification(
                            groupId = msgPayload.groupId,
                            groupName = groupNameStr,
                            senderDisplayName = msgPayload.senderDisplayName,
                            text = msgPayload.text
                        )
                    }

                    scope.launch { _groupEvents.emit(GroupEvent.GroupMessageReceived(groupMsg)) }
                }

                PacketType.GROUP_MSG_ACK -> {
                    val ackPayload = json.decodeFromString<GroupMsgAckPayload>(payloadStr)
                    groupRepository.recordMessageReceipt(ackPayload.messageId, ackPayload.ackUserId, "DELIVERED")

                    val members = groupRepository.getGroupMembers(ackPayload.groupId)
                    val otherMembersCount = (members.size - 1).coerceAtLeast(1)
                    val newStatus = groupRepository.checkAndUpdateGroupMessageStatus(ackPayload.groupId, ackPayload.messageId, otherMembersCount)

                    scope.launch { _groupEvents.emit(GroupEvent.MessageStatusChanged(ackPayload.groupId, ackPayload.messageId, newStatus)) }
                }

                PacketType.GROUP_MSG_READ -> {
                    val readPayload = json.decodeFromString<GroupMsgAckPayload>(payloadStr)
                    groupRepository.recordMessageReceipt(readPayload.messageId, readPayload.ackUserId, "READ")

                    val members = groupRepository.getGroupMembers(readPayload.groupId)
                    val otherMembersCount = (members.size - 1).coerceAtLeast(1)
                    val newStatus = groupRepository.checkAndUpdateGroupMessageStatus(readPayload.groupId, readPayload.messageId, otherMembersCount)

                    scope.launch { _groupEvents.emit(GroupEvent.MessageStatusChanged(readPayload.groupId, readPayload.messageId, newStatus)) }
                }

                PacketType.GROUP_MEMBER_ADD -> {
                    val addPayload = json.decodeFromString<GroupMemberAddPayload>(payloadStr)
                    val newMember = GroupMember(
                        groupId = addPayload.groupId,
                        userId = addPayload.newMember.userId,
                        displayName = addPayload.newMember.displayName,
                        publicKeyHex = addPayload.newMember.publicKeyHex,
                        role = try { GroupMemberRole.valueOf(addPayload.newMember.role) } catch (_: Exception) { GroupMemberRole.MEMBER }
                    )

                    val existingGroup = groupRepository.getGroup(addPayload.groupId)
                    if (existingGroup == null) {
                        val newGroup = Group(
                            groupId = addPayload.groupId,
                            groupName = "Group Chat",
                            adminUserId = packet.header.sourcePeerId,
                            groupType = GroupType.PERMANENT,
                            isActive = true,
                            joinMethod = JoinMethod.DIRECT
                        )
                        val localMember = GroupMember(
                            groupId = addPayload.groupId,
                            userId = localUserId,
                            displayName = com.hypershare.application.UserIdentityManager.getInstance(context).getUsername().ifBlank { "User" },
                            publicKeyHex = identityManager.getPublicKeyHex(),
                            role = GroupMemberRole.MEMBER
                        )
                        groupRepository.createGroup(newGroup, listOf(localMember, newMember))
                    } else {
                        groupRepository.addMember(addPayload.groupId, newMember)
                    }
                    scope.launch { _groupEvents.emit(GroupEvent.MemberAdded(addPayload.groupId, newMember)) }
                }

                PacketType.GROUP_MEMBER_REMOVE -> {
                    val remPayload = json.decodeFromString<GroupMemberRemovePayload>(payloadStr)
                    groupRepository.removeMember(remPayload.groupId, remPayload.removedUserId)
                    scope.launch { _groupEvents.emit(GroupEvent.MemberRemoved(remPayload.groupId, remPayload.removedUserId)) }
                }

                PacketType.GROUP_KICKED -> {
                    val kickedPayload = json.decodeFromString<GroupKickedPayload>(payloadStr)
                    groupRepository.updateGroupActiveStatus(kickedPayload.groupId, false)
                    scope.launch { _groupEvents.emit(GroupEvent.GroupDissolved(kickedPayload.groupId)) }
                }

                PacketType.GROUP_DISSOLVE -> {
                    val dissolvePayload = json.decodeFromString<GroupDissolvePayload>(payloadStr)
                    groupRepository.updateGroupActiveStatus(dissolvePayload.groupId, false)
                    activeConnectedMembers.remove(dissolvePayload.groupId)
                    scope.launch { _groupEvents.emit(GroupEvent.GroupDissolved(dissolvePayload.groupId)) }
                }

                PacketType.GROUP_RESTORE -> {
                    val restorePayload = json.decodeFromString<GroupRestorePayload>(payloadStr)
                    val set = activeConnectedMembers.getOrPut(restorePayload.groupId) { ConcurrentHashMap.newKeySet() }
                    set.addAll(restorePayload.activeMemberIds)
                    groupRepository.updateGroupActiveStatus(restorePayload.groupId, true)
                }

                else -> {}
            }
        } catch (_: Exception) {
            // Invalid packet formatting ignored gracefully
        }
    }

    /**
     * Called when a peer reconnects on the local network to trigger GROUP_RESTORE state synchronization.
     */
    fun onPeerReconnected(peerId: String) {
        val localUserId = identityManager.getUserId()
        val groups = groupRepository.getAllGroups()

        for (group in groups) {
            val members = groupRepository.getGroupMembers(group.groupId)
            if (members.any { it.userId == peerId }) {
                val activeSet = activeConnectedMembers.getOrPut(group.groupId) { ConcurrentHashMap.newKeySet() }
                activeSet.add(peerId)
                activeSet.add(localUserId)

                val restorePayload = json.encodeToString(GroupRestorePayload(group.groupId, activeSet.toList()))
                val packet = PacketBuilder.buildGroupPacket(
                    sourcePeerId = localUserId,
                    destPeerId = peerId,
                    seqNum = seqNumCounter.incrementAndGet(),
                    type = PacketType.GROUP_RESTORE,
                    jsonPayload = restorePayload
                )
                sendPacketLambda(peerId, packet)
            }
        }
    }

    /**
     * Called when a peer disconnects to handle temporary group lifecycle (isActive = false when all members disconnect).
     */
    fun onPeerDisconnected(peerId: String) {
        for ((groupId, activeSet) in activeConnectedMembers) {
            activeSet.remove(peerId)
            val group = groupRepository.getGroup(groupId)
            if (group != null && group.groupType == GroupType.TEMPORARY) {
                // If only 1 local user remains or empty, dissolve temporary group
                if (activeSet.size <= 1) {
                    groupRepository.updateGroupActiveStatus(groupId, false)
                    scope.launch { _groupEvents.emit(GroupEvent.GroupDissolved(groupId)) }
                }
            }
        }
    }

    /**
     * Marks unread group messages as read and transmits GROUP_MSG_READ packets to senders.
     */
    fun markGroupAsReadAndSendReadAcks(groupId: String) {
        val localUserId = identityManager.getUserId()
        val unreadList = groupRepository.markGroupMessagesAsRead(groupId, localUserId)
        for (msg in unreadList) {
            val readPayload = json.encodeToString(GroupMsgAckPayload(msg.groupId, msg.messageId, localUserId))
            val readPacket = PacketBuilder.buildGroupPacket(
                sourcePeerId = localUserId,
                destPeerId = msg.senderId,
                seqNum = seqNumCounter.incrementAndGet(),
                type = PacketType.GROUP_MSG_READ,
                jsonPayload = readPayload
            )
            sendPacketLambda(msg.senderId, readPacket)
        }
    }
}

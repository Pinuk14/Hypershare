package com.hypershare.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.application.GroupEvent
import com.hypershare.application.GroupManager
import com.hypershare.application.UserIdentityManager
import com.hypershare.db.GroupRepository
import com.hypershare.identity.IdentityManager
import com.hypershare.model.Group
import com.hypershare.model.GroupMember
import com.hypershare.model.GroupMessage
import com.hypershare.model.GroupType
import com.hypershare.service.LanSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GroupChatUiState(
    val groupId: String = "",
    val groupName: String = "Group",
    val groupType: GroupType = GroupType.PERMANENT,
    val isAdmin: Boolean = false,
    val isActive: Boolean = true,
    val members: List<GroupMember> = emptyList(),
    val messages: List<GroupMessage> = emptyList(),
    val inputText: String = ""
)

class GroupChatViewModel(app: Application) : AndroidViewModel(app) {

    private val groupRepository = GroupRepository(app.applicationContext)
    private val identityManager = IdentityManager.getInstance(app.applicationContext)
    private val userIdentityManager = UserIdentityManager.getInstance(app.applicationContext)

    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    private var groupManager: GroupManager? = null

    fun setGroupManager(manager: GroupManager) {
        groupManager = manager
        viewModelScope.launch {
            manager.groupEvents.collect { event ->
                val currentGroupId = _uiState.value.groupId
                when (event) {
                    is GroupEvent.GroupMessageReceived -> {
                        if (event.message.groupId == currentGroupId) {
                            refreshMessages()
                            // Send read ack since user is viewing this group
                            manager.markGroupAsReadAndSendReadAcks(currentGroupId)
                        }
                    }
                    is GroupEvent.MemberAdded -> {
                        if (event.groupId == currentGroupId) refreshMembers()
                    }
                    is GroupEvent.MemberRemoved -> {
                        if (event.groupId == currentGroupId) refreshMembers()
                    }
                    is GroupEvent.GroupDissolved -> {
                        if (event.groupId == currentGroupId) {
                            _uiState.value = _uiState.value.copy(isActive = false)
                        }
                    }
                    is GroupEvent.MessageStatusChanged -> {
                        if (event.groupId == currentGroupId) refreshMessages()
                    }
                    else -> {}
                }
            }
        }
    }

    fun loadGroup(groupId: String) {
        val localUserId = identityManager.getUserId()

        // Register active group chat for notification suppression
        LanSocketManager.getInstance().activeGroupChatId = groupId

        viewModelScope.launch {
            val group = withContext(Dispatchers.IO) { groupRepository.getGroup(groupId) }
            val members = withContext(Dispatchers.IO) { groupRepository.getGroupMembers(groupId) }
            val messages = withContext(Dispatchers.IO) { groupRepository.getGroupMessages(groupId) }

            _uiState.value = GroupChatUiState(
                groupId = groupId,
                groupName = group?.groupName ?: "Group",
                groupType = group?.groupType ?: GroupType.PERMANENT,
                isAdmin = group?.adminUserId == localUserId,
                isActive = group?.isActive != false,
                members = members,
                messages = messages,
                inputText = ""
            )

            // Mark messages as read on open
            withContext(Dispatchers.IO) {
                groupManager?.markGroupAsReadAndSendReadAcks(groupId)
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val groupId = _uiState.value.groupId

        _uiState.value = _uiState.value.copy(inputText = "")

        viewModelScope.launch(Dispatchers.IO) {
            groupManager?.sendGroupMessage(groupId, text)
            refreshMessages()
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager?.removeMember(_uiState.value.groupId, userId)
            refreshMembers()
        }
    }

    fun broadcastInvite(targetPeerIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager?.broadcastGroupInvite(_uiState.value.groupId, targetPeerIds)
        }
    }

    fun invitePeerDirectly(peer: GroupMember) {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager?.addMember(_uiState.value.groupId, peer)
            refreshMembers()
        }
    }

    fun dissolveGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            groupManager?.dissolveGroup(_uiState.value.groupId)
        }
    }

    fun onDispose() {
        if (LanSocketManager.getInstance().activeGroupChatId == _uiState.value.groupId) {
            LanSocketManager.getInstance().activeGroupChatId = null
        }
    }

    private fun refreshMessages() {
        viewModelScope.launch {
            val msgs = withContext(Dispatchers.IO) {
                groupRepository.getGroupMessages(_uiState.value.groupId)
            }
            _uiState.value = _uiState.value.copy(messages = msgs)
        }
    }

    private fun refreshMembers() {
        viewModelScope.launch {
            val members = withContext(Dispatchers.IO) {
                groupRepository.getGroupMembers(_uiState.value.groupId)
            }
            _uiState.value = _uiState.value.copy(members = members)
        }
    }

    fun getLocalUserId(): String = identityManager.getUserId()
    fun getLocalDisplayName(): String = userIdentityManager.getUsername().ifBlank { "User" }
}

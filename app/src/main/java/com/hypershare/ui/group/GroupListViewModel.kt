package com.hypershare.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.application.GroupEvent
import com.hypershare.application.GroupManager
import com.hypershare.db.GroupRepository
import com.hypershare.identity.IdentityManager
import com.hypershare.model.Group
import com.hypershare.model.GroupMember
import com.hypershare.model.GroupMemberRole
import com.hypershare.model.GroupType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GroupListUiState(
    val groups: List<Group> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap()
)

class GroupListViewModel(app: Application) : AndroidViewModel(app) {

    private val groupRepository = GroupRepository(app.applicationContext)
    private val identityManager = IdentityManager.getInstance(app.applicationContext)

    private val _uiState = MutableStateFlow(GroupListUiState())
    val uiState: StateFlow<GroupListUiState> = _uiState.asStateFlow()

    private var groupManager: GroupManager? = null

    init {
        loadGroups()
    }

    fun setGroupManager(manager: GroupManager) {
        groupManager = manager
        viewModelScope.launch {
            manager.groupEvents.collect { event ->
                when (event) {
                    is GroupEvent.GroupCreated,
                    is GroupEvent.GroupJoined,
                    is GroupEvent.GroupDissolved,
                    is GroupEvent.MemberAdded,
                    is GroupEvent.MemberRemoved -> loadGroups()
                    else -> {}
                }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            val groups = withContext(Dispatchers.IO) {
                groupRepository.getAllGroups()
            }
            val counts = withContext(Dispatchers.IO) {
                groups.associate { it.groupId to groupRepository.getGroupMembers(it.groupId).size }
            }
            _uiState.value = GroupListUiState(groups = groups, memberCounts = counts)
        }
    }

    fun createGroup(
        groupName: String,
        groupType: GroupType,
        initialMembers: List<GroupMember> = emptyList()
    ) {
        val manager = groupManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            manager.createGroup(groupName, groupType, initialMembers)
            loadGroups()
        }
    }

    fun getLocalUserId(): String = identityManager.getUserId()
}

package com.hypershare.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hypershare.application.UserIdentityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountSettingsUiState(
    val username: String = "",
    val peerId: String = "",
    val publicKeyFingerprint: String = "",
    val isIdentityVerified: Boolean = true
)

class AccountSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val identityManager = UserIdentityManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        AccountSettingsUiState(
            username = identityManager.getUsername(),
            peerId = identityManager.getPeerId(),
            publicKeyFingerprint = identityManager.getKeyFingerprint()
        )
    )
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    fun updateUsername(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        identityManager.setUsername(trimmed)
        _uiState.value = _uiState.value.copy(username = trimmed)
    }
}

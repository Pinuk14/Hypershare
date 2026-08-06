package com.hypershare.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountSettingsUiState(
    val username: String = "USER_NAME",
    val peerId: String = "HYPERSHARE-NODE-8X92",
    val publicKeyFingerprint: String = "EC:3B:82:F6:14:B8:A6:22:C5:5E:F5:9E:0B",
    val isIdentityVerified: Boolean = true
)

class AccountSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    fun updateUsername(newName: String) {
        _uiState.value = _uiState.value.copy(username = newName)
    }
}

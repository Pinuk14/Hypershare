package com.hypershare.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hypershare.application.UserIdentityManager
import com.hypershare.identity.ContactCard
import com.hypershare.identity.IdentityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountSettingsUiState(
    val username: String = "",
    val peerId: String = "",
    val publicKeyFingerprint: String = "",
    val isIdentityVerified: Boolean = true,
    val signedCardJson: String = ""
)

class AccountSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val userIdentityManager = UserIdentityManager.getInstance(application)
    private val cryptoIdentityManager = IdentityManager.getInstance(application)

    private fun buildSignedCardJson(name: String): String {
        return try {
            val card = ContactCard.createSignedCard(cryptoIdentityManager, name)
            card.toJson()
        } catch (e: Exception) {
            ""
        }
    }

    private val initialUsername = userIdentityManager.getUsername()
    private val _uiState = MutableStateFlow(
        AccountSettingsUiState(
            username = initialUsername,
            peerId = userIdentityManager.getPeerId(),
            publicKeyFingerprint = userIdentityManager.getKeyFingerprint(),
            signedCardJson = buildSignedCardJson(initialUsername)
        )
    )
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    fun updateUsername(newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        userIdentityManager.setUsername(trimmed)
        val newSignedJson = buildSignedCardJson(trimmed)
        _uiState.value = _uiState.value.copy(
            username = trimmed,
            signedCardJson = newSignedJson
        )
        return true
    }

    fun refreshState() {
        val currentUsername = userIdentityManager.getUsername()
        if (currentUsername != _uiState.value.username || _uiState.value.signedCardJson.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                username = currentUsername,
                signedCardJson = buildSignedCardJson(currentUsername)
            )
        }
    }
}


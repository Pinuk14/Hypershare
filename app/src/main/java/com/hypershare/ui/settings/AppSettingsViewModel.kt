package com.hypershare.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettingsUiState(
    val isDarkMode: Boolean = true,
    val networkPort: Int = 47200,
    val autoSwitchEmergencyMode: Boolean = true
)

class AppSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    fun toggleDarkMode(isDark: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = isDark)
    }

    fun toggleAutoSwitch(autoSwitch: Boolean) {
        _uiState.value = _uiState.value.copy(autoSwitchEmergencyMode = autoSwitch)
    }
}

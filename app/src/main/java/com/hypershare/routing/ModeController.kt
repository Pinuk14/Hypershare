package com.hypershare.routing

import com.hypershare.model.PeerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModeController {
    private val _currentMode = MutableStateFlow(PeerMode.MODE_1_WIFI)
    val currentMode: StateFlow<PeerMode> = _currentMode.asStateFlow()

    private var isManualOverride: Boolean = false

    fun onNetworkStateChanged(isLanAvailable: Boolean) {
        if (isManualOverride) return

        if (!isLanAvailable && _currentMode.value == PeerMode.MODE_1_WIFI) {
            // Auto switch to Disaster Mesh Mode 2
            _currentMode.value = PeerMode.MODE_2_MESH
        } else if (isLanAvailable && _currentMode.value == PeerMode.MODE_2_MESH) {
            // Auto restore Mode 1
            _currentMode.value = PeerMode.MODE_1_WIFI
        }
    }

    fun setManualMode(mode: PeerMode) {
        isManualOverride = true
        _currentMode.value = mode
    }

    fun clearManualOverride() {
        isManualOverride = false
    }
}

package com.hypershare.ui.modetoggle

import androidx.lifecycle.ViewModel
import com.hypershare.model.PeerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModeToggleViewModel : ViewModel() {
    private val _selectedMode = MutableStateFlow(PeerMode.MODE_1_WIFI)
    val selectedMode: StateFlow<PeerMode> = _selectedMode.asStateFlow()

    fun selectMode(mode: PeerMode) {
        _selectedMode.value = mode
    }
}

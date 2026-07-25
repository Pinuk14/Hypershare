package com.hypershare.ui.routingdebug

import androidx.lifecycle.ViewModel
import com.hypershare.model.RouteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoutingDebugUiState(
    val routes: List<RouteEntry> = emptyList()
)

class RoutingDebugViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RoutingDebugUiState())
    val uiState: StateFlow<RoutingDebugUiState> = _uiState.asStateFlow()
}

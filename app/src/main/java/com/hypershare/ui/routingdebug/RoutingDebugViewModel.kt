package com.hypershare.ui.routingdebug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.model.RouteEntry
import com.hypershare.routing.RoutingTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoutingDebugUiState(
    val routes: List<RouteEntry> = emptyList()
)

class RoutingDebugViewModel(
    private val routingTable: RoutingTable = RoutingTable.getInstance()
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutingDebugUiState())
    val uiState: StateFlow<RoutingDebugUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            routingTable.routesFlow.collect { list ->
                _uiState.value = _uiState.value.copy(routes = list)
            }
        }
    }
}

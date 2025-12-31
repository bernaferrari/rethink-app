package com.celzero.bravedns.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeScreenViewModel(
    private val persistentState: PersistentState,
    private val appConfig: AppConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    init {
        observeVpnState()
        // Add other observers here (Firewall, DNS, etc.)
        // For now, I'll set some defaults or fetch initial values
        refreshData()
    }

    private fun observeVpnState() {
        // In a real migration, we'd convert LiveData to Flow or use observeAsState in Compose
        // For this hybrid approach, we can manually observe if we are in a ViewModel/Fragment boundary,
        // but since VpnController is a singleton and not injected as a repository flow, it's tricky.
        // Assuming we can poll or hook into callbacks.
        
        // Mocking behavior for validation
        _uiState.update { 
            it.copy(
                isVpnActive = VpnController.hasTunnel(),
                firewallUniversalRules = persistentState.getUniversalRulesCount()
            )
        }
    }

    fun toggleVpn() {
        // Toggle logic
    }
    
    fun refreshData() {
         _uiState.update {
            it.copy(
                isVpnActive = VpnController.hasTunnel(),
                firewallUniversalRules = persistentState.getUniversalRulesCount(),
                dnsConnectedName = "Testing...",
                appsTotal = 0
            )
        }
    }
}

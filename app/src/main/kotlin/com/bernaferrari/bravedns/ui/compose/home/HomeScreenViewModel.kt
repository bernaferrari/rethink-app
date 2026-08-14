package com.bernaferrari.bravedns.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class HomeScreenViewModel(
    private val persistentState: PersistentState,
    private val appConfig: AppConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    init {
        observeVpnState()
        observeLogsCount()
    }

    private fun observeVpnState() {
        persistentState.vpnEnabled.onEach { enabled ->
            _uiState.update { it.copy(isVpnActive = enabled) }
            syncDnsStatus()
        }.launchIn(viewModelScope)

        VpnController.connectionStatus.onEach {
            if (VpnController.isAppPaused()) return@onEach
            syncDnsStatus()
        }.launchIn(viewModelScope)
    }

    private fun observeLogsCount() {
        appConfig.dnsLogsCount.onEach { count ->
            _uiState.update { it.copy(dnsLogsCount = count) }
        }.launchIn(viewModelScope)

        appConfig.networkLogsCount.onEach { count ->
            _uiState.update { it.copy(networkLogsCount = count) }
        }.launchIn(viewModelScope)
    }

    fun syncDnsStatus() {
        viewModelScope.launch {
            val vpnState = VpnController.state()
            val isEch = vpnState.isEch
            var isFailing = false
            var status = HomeProtectionStatus.Protected

            if (vpnState.on) {
                when (vpnState.connectionState) {
                    BraveVPNService.State.APP_ERROR,
                    BraveVPNService.State.DNS_ERROR,
                    BraveVPNService.State.DNS_SERVER_DOWN,
                    BraveVPNService.State.NO_INTERNET -> {
                        isFailing = true
                        status = HomeProtectionStatus.Failing
                    }
                    BraveVPNService.State.WORKING,
                    BraveVPNService.State.NEW -> {
                        status = if (isEch) {
                            HomeProtectionStatus.UltraSecure
                        } else {
                            HomeProtectionStatus.Protected
                        }
                    }
                    else -> {
                        isFailing = true
                        status = HomeProtectionStatus.Failing
                    }
                }
            } else if (persistentState.getVpnEnabled()) {
                isFailing = true
                status = HomeProtectionStatus.Waiting
            } else {
                isFailing = true
                status = HomeProtectionStatus.Exposed
            }

            if (VpnController.isUnderlyingVpnNetworkEmpty()) {
                isFailing = true
                status = HomeProtectionStatus.NoNetwork
            }

            _uiState.update {
                it.copy(protectionStatus = status, isProtectionFailing = isFailing)
            }
        }
    }
}

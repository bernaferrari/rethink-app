package com.bernaferrari.bravedns.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private val appConfig: AppConfig,
    private val connectionTrackerRepository: ConnectionTrackerRepository,
    private val dnsLogRepository: DnsLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    init {
        observeVpnState()
        observeLogsCount()
        observeLastDay()
    }

    /** The last 24 hours, sliding: the window start moves every few minutes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLastDay() {
        val since = flow {
            while (true) {
                emit(System.currentTimeMillis() - DAY_MILLIS)
                delay(WINDOW_STEP_MILLIS)
            }
        }
        since.flatMapLatest { start ->
            combine(
                connectionTrackerRepository.observeVerdictCounts(start),
                dnsLogRepository.observeBlockedSince(start),
                connectionTrackerRepository.observeTopBlockedApps(start),
            ) { verdicts, dnsBlocked, top -> Triple(verdicts, dnsBlocked, top) }
        }.conflate().onEach { (verdicts, dnsBlocked, top) ->
            _uiState.update {
                it.copy(
                    allowedToday = verdicts.allowed,
                    blockedToday = verdicts.blocked,
                    dnsBlockedToday = dnsBlocked,
                    topBlocked = top,
                    dnsName = persistentState.connectedDnsName,
                )
            }
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val WINDOW_STEP_MILLIS = 5 * 60 * 1000L
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

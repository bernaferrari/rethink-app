package com.bernaferrari.bravedns.ui.compose.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.scheduler.WorkScheduler
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.BlockFreeDnsMode
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.firestack.backend.Backend
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.util.Utilities.isAtleastR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.bernaferrari.bravedns.database.DnsLogRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

data class DnsSettingsUiState(
    val connectedDnsName: String = "--",
    val connectedDnsType: String = "--",
    val dnsLatency: String = "",
    val dnsType: AppConfig.DnsType = AppConfig.DnsType.DOH,
    val isSmartDnsEnabled: Boolean = false,
    val isSystemDnsEnabled: Boolean = false,
    val isRethinkDnsConnected: Boolean = false,
    val fetchFavIcon: Boolean = false,
    val preventDnsLeaks: Boolean = false,
    val enableDnsAlg: Boolean = false,
    val periodicallyCheckBlocklistUpdate: Boolean = false,
    val useCustomDownloadManager: Boolean = false,
    val enableDnsCache: Boolean = false,
    val proxyDns: Boolean = false,
    val useSystemDnsForUndelegatedDomains: Boolean = false,
    val useFallbackDnsToBypass: Boolean = false,
    val blockFreeDnsMode: BlockFreeDnsMode = BlockFreeDnsMode.AUTO,
    val blocklistEnabled: Boolean = false,
    val numberOfLocalBlocklists: Int = 0,
    val bypassBlockInDns: Boolean = false,
    val splitDns: Boolean = false,
    val dnsRecordTypesAutoMode: Boolean = false,
    val allowedDnsRecordTypesSize: Int = 0,
    val isShowSplitDns: Boolean = false,
    val isShowBypassDnsBlock: Boolean = false,
    val isRefreshing: Boolean = false,
    val recentLatencies: List<Long> = emptyList(),
    /** The resolver in use isn't answering (the tunnel is up and lookups fail or time out). */
    val isDnsFailing: Boolean = false,
    /** Rethink DNS runs on its own resolver ("Max") rather than Cloudflare's edge ("Sky"). */
    val isRethinkMax: Boolean = false,
    /** Name of the fallback DNS chosen in DNS › Advanced (formerly Network). */
    val fallbackDnsName: String = "",
    /** The on-device blocklists were downloaded at least once. */
    val localBlocklistsDownloaded: Boolean = false,
)

@KoinViewModel
class DnsSettingsViewModel(
    private val persistentState: PersistentState,
    private val appConfig: AppConfig,
    private val workScheduler: WorkScheduler,
    private val dnsLogRepository: DnsLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsSettingsUiState())
    val uiState: StateFlow<DnsSettingsUiState> = _uiState.asStateFlow()

    init {
        // The hero's latency bars follow the DNS log live.
        dnsLogRepository.observeRecentLatencies()
            .onEach { latencies -> _uiState.update { it.copy(recentLatencies = latencies) } }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
        updateUiState()
        // We might want to observe appConfig.connectedDnsFlow()
        // but for now we'll just pull it.
    }

    fun updateUiState() {
        viewModelScope.launch {
            val type = appConfig.getDnsType()
            val isSmart = appConfig.isSmartDnsEnabled()
            val isSystem = appConfig.isSystemDns()
            val isRethink = appConfig.isRethinkDnsConnected()
            val connectedName = persistentState.connectedDnsName

            _uiState.update {
                it.copy(
                    dnsType = type,
                    isSmartDnsEnabled = isSmart,
                    isSystemDnsEnabled = isSystem,
                    isRethinkDnsConnected = isRethink,
                    connectedDnsName = connectedName,
                    fetchFavIcon = persistentState.fetchFavIcon,
                    preventDnsLeaks = persistentState.preventDnsLeaks,
                    enableDnsAlg = persistentState.enableDnsAlg,
                    periodicallyCheckBlocklistUpdate = persistentState.periodicallyCheckBlocklistUpdate,
                    useCustomDownloadManager = persistentState.useCustomDownloadManager,
                    enableDnsCache = persistentState.enableDnsCache,
                    proxyDns = persistentState.proxyDns,
                    useSystemDnsForUndelegatedDomains = persistentState.useSystemDnsForUndelegatedDomains,
                    useFallbackDnsToBypass = persistentState.useFallbackDnsToBypass,
                    blockFreeDnsMode = BlockFreeDnsMode.fromMode(persistentState.blockFreeDnsMode),
                    blocklistEnabled = persistentState.blocklistEnabled,
                    numberOfLocalBlocklists = persistentState.numberOfLocalBlocklists,
                    bypassBlockInDns = persistentState.bypassBlockInDns,
                    splitDns = persistentState.splitDns,
                    dnsRecordTypesAutoMode = persistentState.dnsRecordTypesAutoMode,
                    allowedDnsRecordTypesSize = persistentState.getAllowedDnsRecordTypes().size,
                    isShowSplitDns = isAtleastR() || persistentState.enableDnsAlg,
                    isShowBypassDnsBlock = persistentState.enableDnsAlg,
                    fallbackDnsName = fallbackDnsName(),
                    localBlocklistsDownloaded = persistentState.localBlocklistTimestamp != INIT_TIME_MS,
                )
            }
            updateLatency()
            updateRethinkNetwork()
            refreshDnsStatus()
        }
    }

    private fun fallbackDnsName(): String =
        (Constants.DEFAULT_DNS_LIST.firstOrNull { it.url == persistentState.defaultDnsUrl }
            ?: Constants.DEFAULT_DNS_LIST.firstOrNull())?.name.orEmpty()

    private fun updateRethinkNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            // Same probe the Rethink DNS list used for its Sky/Max toggle.
            val isMax = appConfig.getRethinkPlusEndpoint()?.url?.contains(Constants.MAX_ENDPOINT) == true
            _uiState.update { it.copy(isRethinkMax = isMax) }
        }
    }

    /**
     * Whether the resolver in use is answering. Only a running tunnel can tell; a stopped one is
     * not "failing". Starting, answering and "no internet" (not the resolver's fault) are fine.
     */
    fun refreshDnsStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val failing = if (!VpnController.hasTunnel()) {
                false
            } else {
                val backend = if (appConfig.isSmartDnsEnabled()) Backend.Plus else Backend.Preferred
                when (VpnController.getDnsStatus(backend)?.let { Transaction.Status.fromId(it) }) {
                    null -> false
                    Transaction.Status.START,
                    Transaction.Status.COMPLETE,
                    Transaction.Status.SEND_FAIL,
                    Transaction.Status.BAD_QUERY -> false
                    else -> true
                }
            }
            _uiState.update { it.copy(isDnsFailing = failing) }
        }
    }

    /** Rethink DNS network: the same switch the Rethink DNS list's Sky/Max toggle made. */
    fun setRethinkMax(max: Boolean) {
        _uiState.update { it.copy(isRethinkMax = max) }
        viewModelScope.launch(Dispatchers.IO) {
            if (max) appConfig.switchRethinkDnsToMax() else appConfig.switchRethinkDnsToSky()
            withContext(Dispatchers.Main) { updateUiState() }
        }
    }

    /** Fallback DNS, the same preference the Network screen's "Choose fallback DNS" wrote. */
    fun setDefaultDnsUrl(url: String) {
        persistentState.defaultDnsUrl = url
        updateUiState()
    }

    private fun updateLatency() {
        viewModelScope.launch(Dispatchers.IO) {
            val p50 = VpnController.p50("preferred") // simplified
            if (p50 > 0) {
                _uiState.update { it.copy(dnsLatency = "($p50 ms)") }
            } else {
                _uiState.update { it.copy(dnsLatency = "") }
            }
        }
    }

    fun refreshDns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            VpnController.refresh()
            delay(2000)
            _uiState.update { it.copy(isRefreshing = false) }
            updateUiState()
        }
    }

    fun setFavIconEnabled(enabled: Boolean) {
        persistentState.fetchFavIcon = enabled
        updateUiState()
    }

    fun setPreventDnsLeaksEnabled(enabled: Boolean) {
        persistentState.preventDnsLeaks = enabled
        updateUiState()
    }

    fun setDnsAlgEnabled(enabled: Boolean) {
        persistentState.enableDnsAlg = enabled
        updateUiState()
    }

    fun setPeriodicallyCheckBlocklistUpdate(enabled: Boolean) {
        persistentState.periodicallyCheckBlocklistUpdate = enabled
        if (enabled) {
            workScheduler.scheduleBlocklistUpdateCheckJob()
        } else {
            workScheduler.cancelBlocklistUpdateCheckJob()
        }
        updateUiState()
    }

    fun setUseCustomDownloadManager(enabled: Boolean) {
        persistentState.useCustomDownloadManager = enabled
        updateUiState()
    }

    fun setEnableDnsCache(enabled: Boolean) {
        persistentState.enableDnsCache = enabled
        updateUiState()
    }

    fun setProxyDns(enabled: Boolean) {
        persistentState.proxyDns = enabled
        updateUiState()
    }

    fun setUseSystemDnsForUndelegatedDomains(enabled: Boolean) {
        persistentState.useSystemDnsForUndelegatedDomains = enabled
        updateUiState()
    }

    fun setUseFallbackDnsToBypass(enabled: Boolean) {
        persistentState.useFallbackDnsToBypass = enabled
        updateUiState()
    }

    fun setBlockFreeDnsMode(mode: BlockFreeDnsMode) {
        persistentState.blockFreeDnsMode = mode.mode
        updateUiState()
    }

    fun setBypassBlockInDns(enabled: Boolean) {
        persistentState.bypassBlockInDns = enabled
        updateUiState()
    }

    fun setSplitDns(enabled: Boolean) {
        persistentState.splitDns = enabled
        updateUiState()
    }

    fun enableSystemDns() {
        viewModelScope.launch(Dispatchers.IO) {
            appConfig.enableSystemDns()
            withContext(Dispatchers.Main) { updateUiState() }
        }
    }

    fun enableSmartDns() {
        viewModelScope.launch(Dispatchers.IO) {
            appConfig.enableSmartDns()
            withContext(Dispatchers.Main) { updateUiState() }
        }
    }
}

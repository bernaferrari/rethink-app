package com.bernaferrari.bravedns.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.data.AppConfig
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.PauseTimer
import com.celzero.firestack.backend.Backend
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

    /** Device facts the Activity reads on start and resume (always-on, Private DNS, notifications). */
    private val environment = MutableStateFlow(HomeEnvironment())

    /** Set by Welcome's "Turn on protection"; the success card shows once protection is on. */
    private val protectedCardArmed = MutableStateFlow(false)

    /** The preferred resolver has failed [DNS_FAILURES_BEFORE_BANNER] checks in a row. */
    private val dnsFailing = MutableStateFlow(false)

    init {
        observeVpnState()
        observeLogsCount()
        observeLastDay()
        observePause()
        observeHealth()
        pollDnsHealth()
    }

    fun updateEnvironment(value: HomeEnvironment) {
        environment.value = value
    }

    fun armProtectedCard() {
        protectedCardArmed.value = true
    }

    fun dismissProtectedCard() {
        protectedCardArmed.value = false
    }

    private fun observeHealth() {
        combine(environment, protectedCardArmed, dnsFailing, _uiState) { env, armed, failing, state ->
            val protectedOn = state.isVpnActive && !state.isPaused
            homeHealthIssue(env, state.isVpnActive, state.isPaused, failing) to
                homeCard(env, protectedOn, armed)
        }.distinctUntilChanged().onEach { (health, card) ->
            _uiState.update { it.copy(health = health, card = card) }
        }.launchIn(viewModelScope)
    }

    /** The pause countdown while paused; 0 while paused means "until I resume". */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePause() {
        VpnController.connectionStatus
            .map { it == BraveVPNService.State.PAUSED }
            .distinctUntilChanged()
            .flatMapLatest { paused ->
                if (paused) PauseTimer.pauseCountDownFlow() else flowOf(0L)
            }
            .onEach { remaining -> _uiState.update { it.copy(pauseRemainingMillis = remaining) } }
            .launchIn(viewModelScope)
    }

    /**
     * Asks the tunnel whether the preferred resolver answers, as the DNS screen does, while
     * protection is on. System DNS has no preferred resolver to fail, and a pinned state (the
     * store-capture build) has no tunnel to ask.
     */
    private fun pollDnsHealth() {
        viewModelScope.launch {
            var failures = 0
            while (isActive) {
                val state = _uiState.value
                val check = state.isVpnActive && !state.isPaused && VpnController.pinnedState == null
                val failed = check && withContext(Dispatchers.IO) {
                    if (appConfig.getDnsType().isSystemDns()) return@withContext false
                    val id = if (appConfig.isSmartDnsEnabled()) Backend.Plus else Backend.Preferred
                    runCatching { isDnsFailing(VpnController.getDnsStatus(id)) }.getOrDefault(false)
                }
                failures = if (failed) failures + 1 else 0
                dnsFailing.value = failures >= DNS_FAILURES_BEFORE_BANNER
                delay(DNS_POLL_MILLIS)
            }
        }
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
                )
            }
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val WINDOW_STEP_MILLIS = 5 * 60 * 1000L
        const val DNS_POLL_MILLIS = 5_000L
        val DNS_KEYS = setOf("connected_dns_name", "dns_type")
        // Two misses in a row: a resolver still starting up doesn't flash a banner.
        const val DNS_FAILURES_BEFORE_BANNER = 2
    }

    private fun observeVpnState() {
        persistentState.vpnEnabled.onEach { syncDnsStatus() }.launchIn(viewModelScope)
        VpnController.sessionActive.onEach { syncDnsStatus() }.launchIn(viewModelScope)

        // Paused is a state of its own now ([HomeProtectionStatus.Paused]), so it syncs too.
        VpnController.connectionStatus.onEach { syncDnsStatus() }.launchIn(viewModelScope)
        // The hero's DNS line follows a resolver change made anywhere.
        persistentState.preferenceKeyChanges()
            .onEach { key -> if (key in DNS_KEYS) syncDnsStatus() }
            .launchIn(viewModelScope)
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
            val presentation = homeVpnPresentation(
                VpnController.state(),
                VpnController.sessionActive.value,
                VpnController.isUnderlyingVpnNetworkEmpty(),
            )
            val (dnsName, keepoutDns) = withContext(Dispatchers.IO) {
                persistentState.connectedDnsName to appConfig.isRethinkDnsConnected()
            }
            _uiState.update {
                it.copy(
                    isVpnActive = presentation.active,
                    protectionStatus = presentation.status,
                    isProtectionFailing = presentation.failing,
                    isPaused = presentation.status == HomeProtectionStatus.Paused,
                    dnsName = dnsName,
                    dnsIsKeepout = keepoutDns,
                )
            }
        }
    }
}

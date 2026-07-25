package com.celzero.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.AppInfoDAO
import com.celzero.bravedns.ui.compose.firewall.RethinkFirewallFilter
import com.celzero.bravedns.ui.compose.firewall.RethinkFirewallFilters
import com.celzero.bravedns.ui.compose.firewall.RethinkFirewallTopLevelFilter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

/** Shared filtering and bulk-rule state for the app firewall list. */
@OptIn(FlowPreview::class)
@KoinViewModel
class AppInfoViewModel(
    @Provided private val appInfoDAO: AppInfoDAO,
    @Provided private val firewallRules: AppFirewallRuleMutator,
) : ViewModel() {

    private val defaultFilters = RethinkFirewallFilters(topLevel = RethinkFirewallTopLevelFilter.Installed)
    private val baseFilters = MutableStateFlow(defaultFilters.copy(query = ""))
    private val searchInput = MutableStateFlow(defaultFilters.query)
    private val bulkUpdateMutex = Mutex()

    private val effectiveFilters: StateFlow<RethinkFirewallFilters> =
        combine(baseFilters, searchInput.debounce(300).distinctUntilChanged()) { base, debouncedSearch ->
            base.copy(query = debouncedSearch.trim())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultFilters)

    val appInfo: StateFlow<List<AppInfo>> =
        combine(appInfoDAO.getAllAppDetailsFlow(), effectiveFilters) { apps, filters ->
            filterAndSortApps(apps, filters)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filters: RethinkFirewallFilters) {
        baseFilters.value = filters.copy(query = "")
        searchInput.value = filters.query
    }

    suspend fun updateUnmeteredStatus(blocked: Boolean) = updateFilteredApps { uid ->
        val connection = firewallRules.connectionStatus(uid)
        wifiState(blocked, connection)
    }

    suspend fun updateMeteredStatus(blocked: Boolean) = updateFilteredApps { uid ->
        val connection = firewallRules.connectionStatus(uid)
        mobileState(blocked, connection)
    }

    suspend fun updateBypassStatus(bypass: Boolean) =
        updateFilteredApps {
            AppRuleState(
                if (bypass) AppFirewallStatus.BypassUniversal else AppFirewallStatus.None,
                AppConnectionStatus.Allow,
            )
        }

    suspend fun updateBypassDnsFirewall(bypass: Boolean) =
        updateFilteredApps {
            AppRuleState(
                if (bypass) AppFirewallStatus.BypassDnsFirewall else AppFirewallStatus.None,
                AppConnectionStatus.Allow,
            )
        }

    suspend fun updateExcludeStatus(exclude: Boolean) =
        updateFilteredApps {
            AppRuleState(
                if (exclude) AppFirewallStatus.Exclude else AppFirewallStatus.None,
                AppConnectionStatus.Allow,
            )
        }

    suspend fun updateLockdownStatus(lockdown: Boolean) =
        updateFilteredApps {
            AppRuleState(
                if (lockdown) AppFirewallStatus.Isolate else AppFirewallStatus.None,
                AppConnectionStatus.Allow,
            )
        }

    suspend fun getAppCount(): Int = appInfoDAO.getAppCount()

    private fun filterAndSortApps(
        apps: List<AppInfo>,
        filters: RethinkFirewallFilters,
    ): List<AppInfo> =
        apps.asSequence()
            .filter { matchesTopLevelFilter(it, filters.topLevel) }
            .filter { filters.categories.isEmpty() || it.appCategory in filters.categories }
            .filter { matchesFirewallFilter(it, filters.status) }
            .filter { matchesSearch(it, filters.query) }
            .sortedWith(
                compareBy<AppInfo>(
                    { it.appName.ifBlank { it.packageName }.lowercase() },
                    { it.packageName.lowercase() },
                    { it.uid },
                )
            )
            .toList()

    private fun matchesTopLevelFilter(app: AppInfo, filter: RethinkFirewallTopLevelFilter): Boolean =
        when (filter) {
            RethinkFirewallTopLevelFilter.All -> true
            RethinkFirewallTopLevelFilter.Installed -> !app.isSystemApp
            RethinkFirewallTopLevelFilter.System -> app.isSystemApp
        }

    private fun matchesFirewallFilter(app: AppInfo, filter: RethinkFirewallFilter): Boolean {
        val status = app.firewallStatus
        val connection = app.connectionStatus
        val regularMatch =
            when (filter) {
                RethinkFirewallFilter.All -> true
                RethinkFirewallFilter.Allowed ->
                    status == AppFirewallStatus.None.id && connection == AppConnectionStatus.Allow.id
                RethinkFirewallFilter.Blocked ->
                    status == AppFirewallStatus.None.id && connection in BLOCKED_CONNECTION_IDS
                RethinkFirewallFilter.BlockedWifi ->
                    status == AppFirewallStatus.None.id && connection == AppConnectionStatus.Unmetered.id
                RethinkFirewallFilter.BlockedMobile ->
                    status == AppFirewallStatus.None.id && connection == AppConnectionStatus.Metered.id
                RethinkFirewallFilter.Bypass -> status in BYPASS_STATUS_IDS
                RethinkFirewallFilter.Excluded -> status == AppFirewallStatus.Exclude.id
                RethinkFirewallFilter.Lockdown -> status == AppFirewallStatus.Isolate.id
            }
        return regularMatch || (filter == RethinkFirewallFilter.Bypass && app.isProxyExcluded)
    }

    private fun matchesSearch(app: AppInfo, query: String): Boolean =
        query.isBlank() || app.appName.contains(query.trim(), ignoreCase = true)

    private suspend fun updateFilteredApps(ruleForUid: suspend (Int) -> AppRuleState) {
        bulkUpdateMutex.withLock {
            appInfo.value
                .distinctBy { it.uid }
                .forEach { app ->
                    val state = ruleForUid(app.uid)
                    firewallRules.updateFirewallStatus(app.uid, state.firewall, state.connection)
                }
        }
    }

    private fun wifiState(blocked: Boolean, connection: AppConnectionStatus): AppRuleState =
        AppRuleState(
            AppFirewallStatus.None,
            when {
                blocked && connection in setOf(AppConnectionStatus.Allow, AppConnectionStatus.Unmetered) -> AppConnectionStatus.Unmetered
                blocked -> AppConnectionStatus.Both
                connection in setOf(AppConnectionStatus.Allow, AppConnectionStatus.Unmetered) -> AppConnectionStatus.Allow
                else -> AppConnectionStatus.Metered
            },
        )

    private fun mobileState(blocked: Boolean, connection: AppConnectionStatus): AppRuleState =
        AppRuleState(
            AppFirewallStatus.None,
            when {
                blocked && connection in setOf(AppConnectionStatus.Allow, AppConnectionStatus.Metered) -> AppConnectionStatus.Metered
                blocked -> AppConnectionStatus.Both
                connection in setOf(AppConnectionStatus.Allow, AppConnectionStatus.Metered) -> AppConnectionStatus.Allow
                else -> AppConnectionStatus.Unmetered
            },
        )

    private data class AppRuleState(
        val firewall: AppFirewallStatus,
        val connection: AppConnectionStatus,
    )

    private companion object {
        val BLOCKED_CONNECTION_IDS = setOf(
            AppConnectionStatus.Unmetered.id,
            AppConnectionStatus.Metered.id,
            AppConnectionStatus.Both.id,
        )
        val BYPASS_STATUS_IDS = setOf(
            AppFirewallStatus.BypassUniversal.id,
            AppFirewallStatus.BypassDnsFirewall.id,
        )
    }
}

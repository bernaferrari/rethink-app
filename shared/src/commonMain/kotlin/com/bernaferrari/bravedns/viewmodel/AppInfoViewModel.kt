package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.AppInfoDAO
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppSelectionAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilters
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallTopLevelFilter
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

/** One app's firewall rule before a Select-mode change, for Undo. */
data class AppRuleSnapshot(val uid: Int, val firewall: AppFirewallStatus, val connection: AppConnectionStatus)

/** One row's Wi‑Fi or mobile data tap: the rule it replaced and the rule it wrote. */
data class AppRuleChange(val before: AppRuleSnapshot, val after: AppRuleSnapshot)

/** Shared filtering and Select-mode rule changes for the app firewall list. */
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

    /** The filtered, sorted apps; null until the first load completes (never an "empty" flash). */
    val appInfo: StateFlow<List<AppInfo>?> =
        combine(appInfoDAO.getAllAppDetailsFlow(), effectiveFilters) { apps, filters ->
            filterAndSortApps(apps, filters)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(filters: RethinkFirewallFilters) {
        baseFilters.value = filters.copy(query = "")
        searchInput.value = filters.query
    }

    /**
     * Apply [action] to exactly [uids] (Select mode), never to whatever the filter shows. Returns
     * each app's previous rule so [restore] can undo it. Network actions keep the other network's
     * flag and return the app to Normal handling; mode actions clear both network flags, as the
     * per-app controls always have.
     */
    suspend fun applyToSelected(uids: Set<Int>, action: RethinkAppSelectionAction): List<AppRuleSnapshot> =
        bulkUpdateMutex.withLock {
            uids.map { uid ->
                val before = AppRuleSnapshot(uid, firewallRules.firewallStatus(uid), firewallRules.connectionStatus(uid))
                val next = selectionRule(action, before.connection)
                firewallRules.updateFirewallStatus(uid, next.firewall, next.connection)
                before
            }
        }

    /** Put back the rules [applyToSelected] replaced. */
    suspend fun restore(snapshots: List<AppRuleSnapshot>) = bulkUpdateMutex.withLock {
        snapshots.forEach { firewallRules.updateFirewallStatus(it.uid, it.firewall, it.connection) }
    }

    /**
     * A row's Wi‑Fi (or mobile data) tap: flip that network's flag, keep the other's, and return
     * the app to Normal handling, as App detail's tiles do. Returns what it replaced for [undo].
     */
    suspend fun toggleNetwork(uid: Int, wifi: Boolean): AppRuleChange = bulkUpdateMutex.withLock {
        val before = snapshot(uid)
        val next = if (wifi) {
            wifiState(blocked = !before.connection.blocksWifi, before.connection)
        } else {
            mobileState(blocked = !before.connection.blocksMobile, before.connection)
        }
        firewallRules.updateFirewallStatus(uid, next.firewall, next.connection)
        AppRuleChange(before, AppRuleSnapshot(uid, next.firewall, next.connection))
    }

    /**
     * Put back the exact rule [change] replaced, mode included. Only while the app still holds
     * what [change] wrote: a later edit (another tap, App detail, Select mode) stands. True when
     * it restored.
     */
    suspend fun undo(change: AppRuleChange): Boolean = bulkUpdateMutex.withLock {
        if (snapshot(change.after.uid) != change.after) return@withLock false
        val before = change.before
        firewallRules.updateFirewallStatus(before.uid, before.firewall, before.connection)
        true
    }

    private suspend fun snapshot(uid: Int) =
        AppRuleSnapshot(uid, firewallRules.firewallStatus(uid), firewallRules.connectionStatus(uid))

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
                    status == AppFirewallStatus.None.id && connection in setOf(AppConnectionStatus.Unmetered.id, AppConnectionStatus.Both.id)
                RethinkFirewallFilter.BlockedMobile ->
                    status == AppFirewallStatus.None.id && connection in setOf(AppConnectionStatus.Metered.id, AppConnectionStatus.Both.id)
                RethinkFirewallFilter.Bypass -> status == AppFirewallStatus.BypassUniversal.id
                RethinkFirewallFilter.SkipAllFiltering -> status == AppFirewallStatus.BypassDnsFirewall.id
                RethinkFirewallFilter.Excluded -> status == AppFirewallStatus.Exclude.id
                RethinkFirewallFilter.Lockdown -> status == AppFirewallStatus.Isolate.id
            }
        return regularMatch || (filter == RethinkFirewallFilter.Bypass && app.isProxyExcluded)
    }

    private fun matchesSearch(app: AppInfo, query: String): Boolean =
        query.isBlank() || app.appName.contains(query.trim(), ignoreCase = true)

    private fun selectionRule(action: RethinkAppSelectionAction, connection: AppConnectionStatus): AppRuleState = when (action) {
        RethinkAppSelectionAction.BlockWifi -> wifiState(blocked = true, connection)
        RethinkAppSelectionAction.BlockMobile -> mobileState(blocked = true, connection)
        RethinkAppSelectionAction.AllowWifi -> wifiState(blocked = false, connection)
        RethinkAppSelectionAction.AllowMobile -> mobileState(blocked = false, connection)
        RethinkAppSelectionAction.AllowAll -> AppRuleState(AppFirewallStatus.None, AppConnectionStatus.Allow)
        RethinkAppSelectionAction.TrustedOnly -> AppRuleState(AppFirewallStatus.Isolate, AppConnectionStatus.Allow)
        RethinkAppSelectionAction.SkipUniversalRules -> AppRuleState(AppFirewallStatus.BypassUniversal, AppConnectionStatus.Allow)
        RethinkAppSelectionAction.SkipAllFiltering -> AppRuleState(AppFirewallStatus.BypassDnsFirewall, AppConnectionStatus.Allow)
        RethinkAppSelectionAction.Exclude -> AppRuleState(AppFirewallStatus.Exclude, AppConnectionStatus.Allow)
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

    private val AppConnectionStatus.blocksWifi get() = this == AppConnectionStatus.Unmetered || this == AppConnectionStatus.Both
    private val AppConnectionStatus.blocksMobile get() = this == AppConnectionStatus.Metered || this == AppConnectionStatus.Both

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
    }
}

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Single Navigation 3 key graph for every host (Android + wasm).
 * Platform differences are data/backends, not parallel route types.
 */
@Serializable
sealed interface RethinkRoute : NavKey {
    @Serializable data object Home : RethinkRoute
    /**
     * Kept for saved back stacks and external requests: Stats became the Insights view of the
     * Activity tab. Opening it lands on [Activity] with Insights selected ([activityView]).
     */
    @Serializable data object Statistics : RethinkRoute
    /** The Activity tab: the log (connections and DNS lookups) and Insights (former Stats). */
    @Serializable data object Activity : RethinkRoute
    @Serializable data object Configure : RethinkRoute
    @Serializable data object About : RethinkRoute
    @Serializable data object Welcome : RethinkRoute
    @Serializable data object Alerts : RethinkRoute
    @Serializable data object Events : RethinkRoute
    @Serializable data object AppList : RethinkRoute
    @Serializable data class NetworkLogs(val query: String = "") : RethinkRoute
    @Serializable data object ConsoleLogs : RethinkRoute
    @Serializable data object DnsList : RethinkRoute
    @Serializable data class DnsDetail(val focusKey: String = "") : RethinkRoute
    @Serializable data object DnsSettings : RethinkRoute
    @Serializable data class ConfigureOtherDns(val dnsType: Int = 0) : RethinkRoute
    @Serializable data object BlockFreeDns : RethinkRoute
    @Serializable data object Blocklists : RethinkRoute
    @Serializable data class FirewallSettings(val focusKey: String = "") : RethinkRoute
    @Serializable data object UniversalFirewall : RethinkRoute
    @Serializable data class CustomRules(
        val uid: Int = -1000,
        val tab: Int = 0,
        val mode: Int = 1,
    ) : RethinkRoute
    @Serializable data class ProxySettings(val focusKey: String = "") : RethinkRoute
    @Serializable data object OrbotAppSelect : RethinkRoute
    @Serializable data object TcpProxy : RethinkRoute
    @Serializable data class TunnelSettings(val focusKey: String = "") : RethinkRoute
    @Serializable data class MiscSettings(val focusKey: String = "") : RethinkRoute
    @Serializable data object AdvancedSettings : RethinkRoute
    @Serializable data object AntiCensorship : RethinkRoute
    @Serializable data object AppLock : RethinkRoute
    @Serializable data object PingTest : RethinkRoute
    @Serializable data object Database : RethinkRoute
    @Serializable data object WireGuard : RethinkRoute
    @Serializable data object RpnAvailability : RethinkRoute
    @Serializable data object RpnCountries : RethinkRoute
    @Serializable data object RpnAccount : RethinkRoute
    @Serializable data object RpnSettings : RethinkRoute
    @Serializable data object Checkout : RethinkRoute
    @Serializable data class AppInfo(val uid: Int) : RethinkRoute
    @Serializable data class DetailedStatistics(val typeId: Int, val timeCategory: Int) : RethinkRoute
    @Serializable data class DomainConnections(
        val typeId: Int,
        val flag: String,
        val domain: String,
        val asn: String,
        val ip: String,
        val isBlocked: Boolean,
        val timeCategory: Int,
    ) : RethinkRoute
    @Serializable data class AppWiseIpLogs(val uid: Int, val isAsn: Boolean) : RethinkRoute
    @Serializable data class AppWiseDomainLogs(val uid: Int) : RethinkRoute
    @Serializable data class WireGuardDetail(val configId: Int, val wgType: Int) : RethinkRoute
    @Serializable data class WireGuardEditor(val configId: Int, val wgType: Int) : RethinkRoute
    @Serializable data class CountryProxy(val countryCode: String) : RethinkRoute
    @Serializable data class RpnServerDetails(val key: String) : RethinkRoute
    @Serializable data class ConfigureRethinkBasic(
        val screenTypeOrdinal: Int,
        val remoteName: String = "",
        val remoteUrl: String = "",
        val uid: Int = -1,
    ) : RethinkRoute
}

/**
 * The bottom-bar tabs, in order. Each owns a back stack. [Configure] is the Settings tab (the
 * name is kept so saved state and ids stay stable).
 */
enum class RethinkRootDestination(val route: RethinkRoute) {
    Home(RethinkRoute.Home),
    Apps(RethinkRoute.AppList),
    Activity(RethinkRoute.Activity),
    Configure(RethinkRoute.Configure),
}

/**
 * The tab a route *belongs to* when it is opened from outside a stack (a notification, an intent,
 * [RethinkNavOps.open]). Routes pushed with [RethinkNavOps.push] stay on whatever tab pushed them,
 * and the bar highlights that tab, not this one (App detail opened from Home highlights Home).
 */
fun RethinkRoute.rootDestination(): RethinkRootDestination? = when (this) {
    RethinkRoute.Home, RethinkRoute.Welcome, RethinkRoute.AppLock -> RethinkRootDestination.Home
    RethinkRoute.AppList, is RethinkRoute.AppInfo, is RethinkRoute.AppWiseIpLogs,
    is RethinkRoute.AppWiseDomainLogs -> RethinkRootDestination.Apps
    RethinkRoute.Activity, RethinkRoute.Statistics, is RethinkRoute.NetworkLogs,
    is RethinkRoute.DetailedStatistics, is RethinkRoute.DomainConnections -> RethinkRootDestination.Activity
    RethinkRoute.Configure, RethinkRoute.About, RethinkRoute.Alerts, RethinkRoute.Events,
    RethinkRoute.ConsoleLogs, RethinkRoute.DnsList,
    is RethinkRoute.DnsDetail, RethinkRoute.DnsSettings, is RethinkRoute.ConfigureOtherDns,
    RethinkRoute.BlockFreeDns, RethinkRoute.Blocklists, is RethinkRoute.FirewallSettings,
    RethinkRoute.UniversalFirewall, is RethinkRoute.CustomRules, is RethinkRoute.ProxySettings,
    RethinkRoute.OrbotAppSelect, RethinkRoute.TcpProxy, is RethinkRoute.TunnelSettings,
    is RethinkRoute.MiscSettings, RethinkRoute.AdvancedSettings, RethinkRoute.AntiCensorship,
    RethinkRoute.PingTest, RethinkRoute.Database, RethinkRoute.WireGuard,
    RethinkRoute.RpnAvailability, RethinkRoute.RpnCountries, RethinkRoute.RpnAccount,
    RethinkRoute.RpnSettings, RethinkRoute.Checkout,
    is RethinkRoute.WireGuardDetail, is RethinkRoute.WireGuardEditor,
    is RethinkRoute.CountryProxy, is RethinkRoute.RpnServerDetails,
    is RethinkRoute.ConfigureRethinkBasic -> RethinkRootDestination.Configure
}

/** The two views of the Activity tab. */
enum class RethinkActivityView { Log, Insights }

/**
 * The Activity view a request asks for, or null when it doesn't name one: Stats (and its "Show
 * all" lists) mean Insights; the unfiltered log means Log. Hosts keep the selected view as state
 * and apply this when a request arrives, so the tab's root stays one key.
 */
fun RethinkRoute.activityView(): RethinkActivityView? = when (this) {
    RethinkRoute.Statistics -> RethinkActivityView.Insights
    is RethinkRoute.NetworkLogs -> RethinkActivityView.Log.takeIf { query.isBlank() }
    else -> null
}

/**
 * The tab whose root this route *is* (Home, Apps, Activity, Settings, and the aliases that open
 * them: Stats and the unfiltered log), or null for a screen inside a tab. Pushing one switches tab.
 */
fun RethinkRoute.tabRoot(): RethinkRootDestination? {
    val root = rootDestination() ?: return null
    return root.takeIf { canonicalStackFor(this) == listOf(root.route) }
}

fun RethinkRoute.showsNavigationChrome(): Boolean =
    this !is RethinkRoute.Welcome && this !is RethinkRoute.AppLock

fun canonicalStackFor(destination: RethinkRoute): List<RethinkRoute> = when {
    destination == RethinkRoute.Home || destination == RethinkRoute.Configure ||
        destination == RethinkRoute.AppList || destination == RethinkRoute.Activity ||
        destination == RethinkRoute.Welcome || destination == RethinkRoute.AppLock -> listOf(destination)
    // Aliases of the Activity root: the view they name is applied by the host ([activityView]).
    destination.activityView() != null -> listOf(RethinkRoute.Activity)
    else -> {
        val root = destination.rootDestination()?.route ?: RethinkRoute.Home
        listOf(root, destination)
    }
}

/** True when an external request should push (same tab) instead of replacing the stack. */
fun shouldPushExternal(current: RethinkRoute?, destination: RethinkRoute): Boolean =
    current != null &&
        current.rootDestination() == destination.rootDestination() &&
        canonicalStackFor(destination).size > 1

/**
 * Stack after [RethinkNavOps.open]. Same-root nested destinations push, or pop back to an
 * existing entry. Switching roots (or opening a tab/fullscreen root) uses [canonicalStackFor].
 */
fun stackAfterOpen(current: List<RethinkRoute>, destination: RethinkRoute): List<RethinkRoute> {
    if (!shouldPushExternal(current.lastOrNull(), destination)) {
        return canonicalStackFor(destination)
    }
    val existing = current.indexOf(destination)
    return if (existing >= 0) current.take(existing + 1) else current + destination
}

/**
 * In-app / intent jump: same-root nested destinations push; other roots become
 * [canonicalStackFor]. Unlike [stackAfterOpen], does not pop back to an existing entry.
 */
fun stackAfterOpenFromApp(
    current: List<RethinkRoute>,
    destination: RethinkRoute,
): List<RethinkRoute> {
    // A tab root (or an alias of one) resets that tab instead of stacking a second root on it.
    if (destination.tabRoot() != null) return canonicalStackFor(destination)
    val currentRoot = current.lastOrNull()?.rootDestination()
    if (currentRoot != null && currentRoot == destination.rootDestination()) {
        return if (current.lastOrNull() == destination) current else current + destination
    }
    return canonicalStackFor(destination)
}

/**
 * Stack after a back press, or null to leave the stack so the host can finish
 * (Home as the only entry). The other tab roots go to Home instead of no-op.
 */
fun stackAfterPop(current: List<RethinkRoute>): List<RethinkRoute>? {
    if (current.size > 1) return current.dropLast(1)
    return when (current.singleOrNull()) {
        RethinkRoute.Configure, RethinkRoute.Statistics, RethinkRoute.AppList, RethinkRoute.Activity ->
            listOf(RethinkRoute.Home)
        else -> null
    }
}

/** Stack ops exposed to entry bodies (same contract on Android and wasm). */
class RethinkNavOps(
    private val pop: () -> Unit,
    private val pushRoute: (RethinkRoute) -> Unit,
    private val openRoute: (RethinkRoute) -> Unit,
    private val selectRootTab: (RethinkRootDestination) -> Unit,
    val current: () -> RethinkRoute?,
) {
    fun popBackStack() = pop()
    fun push(route: RethinkRoute) = pushRoute(route)
    fun open(route: RethinkRoute) = openRoute(route)
    fun selectRoot(destination: RethinkRootDestination) = selectRootTab(destination)
}

val rethinkNavSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(RethinkRoute.Home::class, RethinkRoute.Home.serializer())
                    subclass(RethinkRoute.Statistics::class, RethinkRoute.Statistics.serializer())
                    subclass(RethinkRoute.Configure::class, RethinkRoute.Configure.serializer())
                    subclass(RethinkRoute.About::class, RethinkRoute.About.serializer())
                    subclass(RethinkRoute.Welcome::class, RethinkRoute.Welcome.serializer())
                    subclass(RethinkRoute.Alerts::class, RethinkRoute.Alerts.serializer())
                    subclass(RethinkRoute.Events::class, RethinkRoute.Events.serializer())
                    subclass(RethinkRoute.AppList::class, RethinkRoute.AppList.serializer())
                    subclass(RethinkRoute.NetworkLogs::class, RethinkRoute.NetworkLogs.serializer())
                    subclass(RethinkRoute.ConsoleLogs::class, RethinkRoute.ConsoleLogs.serializer())
                    subclass(RethinkRoute.DnsList::class, RethinkRoute.DnsList.serializer())
                    subclass(RethinkRoute.DnsDetail::class, RethinkRoute.DnsDetail.serializer())
                    subclass(RethinkRoute.DnsSettings::class, RethinkRoute.DnsSettings.serializer())
                    subclass(RethinkRoute.ConfigureOtherDns::class, RethinkRoute.ConfigureOtherDns.serializer())
                    subclass(RethinkRoute.BlockFreeDns::class, RethinkRoute.BlockFreeDns.serializer())
                    subclass(RethinkRoute.Blocklists::class, RethinkRoute.Blocklists.serializer())
                    subclass(RethinkRoute.FirewallSettings::class, RethinkRoute.FirewallSettings.serializer())
                    subclass(RethinkRoute.UniversalFirewall::class, RethinkRoute.UniversalFirewall.serializer())
                    subclass(RethinkRoute.CustomRules::class, RethinkRoute.CustomRules.serializer())
                    subclass(RethinkRoute.ProxySettings::class, RethinkRoute.ProxySettings.serializer())
                    subclass(RethinkRoute.OrbotAppSelect::class, RethinkRoute.OrbotAppSelect.serializer())
                    subclass(RethinkRoute.TcpProxy::class, RethinkRoute.TcpProxy.serializer())
                    subclass(RethinkRoute.TunnelSettings::class, RethinkRoute.TunnelSettings.serializer())
                    subclass(RethinkRoute.MiscSettings::class, RethinkRoute.MiscSettings.serializer())
                    subclass(RethinkRoute.AdvancedSettings::class, RethinkRoute.AdvancedSettings.serializer())
                    subclass(RethinkRoute.AntiCensorship::class, RethinkRoute.AntiCensorship.serializer())
                    subclass(RethinkRoute.AppLock::class, RethinkRoute.AppLock.serializer())
                    subclass(RethinkRoute.PingTest::class, RethinkRoute.PingTest.serializer())
                    subclass(RethinkRoute.Database::class, RethinkRoute.Database.serializer())
                    subclass(RethinkRoute.WireGuard::class, RethinkRoute.WireGuard.serializer())
                    subclass(RethinkRoute.RpnAvailability::class, RethinkRoute.RpnAvailability.serializer())
                    subclass(RethinkRoute.RpnCountries::class, RethinkRoute.RpnCountries.serializer())
                    subclass(RethinkRoute.RpnAccount::class, RethinkRoute.RpnAccount.serializer())
                    subclass(RethinkRoute.RpnSettings::class, RethinkRoute.RpnSettings.serializer())
                    subclass(RethinkRoute.Checkout::class, RethinkRoute.Checkout.serializer())
                    subclass(RethinkRoute.AppInfo::class, RethinkRoute.AppInfo.serializer())
                    subclass(RethinkRoute.DetailedStatistics::class, RethinkRoute.DetailedStatistics.serializer())
                    subclass(RethinkRoute.DomainConnections::class, RethinkRoute.DomainConnections.serializer())
                    subclass(RethinkRoute.AppWiseIpLogs::class, RethinkRoute.AppWiseIpLogs.serializer())
                    subclass(RethinkRoute.AppWiseDomainLogs::class, RethinkRoute.AppWiseDomainLogs.serializer())
                    subclass(RethinkRoute.WireGuardDetail::class, RethinkRoute.WireGuardDetail.serializer())
                    subclass(RethinkRoute.WireGuardEditor::class, RethinkRoute.WireGuardEditor.serializer())
                    subclass(RethinkRoute.CountryProxy::class, RethinkRoute.CountryProxy.serializer())
                    subclass(RethinkRoute.RpnServerDetails::class, RethinkRoute.RpnServerDetails.serializer())
                    subclass(RethinkRoute.ConfigureRethinkBasic::class, RethinkRoute.ConfigureRethinkBasic.serializer())
                    subclass(RethinkRoute.Activity::class, RethinkRoute.Activity.serializer())
                }
            }
    }

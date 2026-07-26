/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.navigation

import kotlinx.serialization.Serializable

/**
 * Target-neutral route contract. Android navigation and the WASM demo both map to these keys;
 * no Android NavController, ViewModel, database, or service type is allowed in this layer.
 */
@Serializable
sealed interface RethinkRoute {
    @Serializable data object Home : RethinkRoute
    @Serializable data object Statistics : RethinkRoute
    @Serializable data object Configure : RethinkRoute
    @Serializable data object About : RethinkRoute
    @Serializable data object Welcome : RethinkRoute
    @Serializable data object Alerts : RethinkRoute
    @Serializable data object Events : RethinkRoute
    @Serializable data object AppList : RethinkRoute
    @Serializable data object NetworkLogs : RethinkRoute
    @Serializable data object ConsoleLogs : RethinkRoute
    @Serializable data object DnsList : RethinkRoute
    @Serializable data object DnsDetail : RethinkRoute
    @Serializable data object FirewallSettings : RethinkRoute
    @Serializable data object UniversalFirewall : RethinkRoute
    @Serializable data object CustomRules : RethinkRoute
    @Serializable data object ProxySettings : RethinkRoute
    @Serializable data object TcpProxy : RethinkRoute
    @Serializable data object TunnelSettings : RethinkRoute
    @Serializable data object MiscSettings : RethinkRoute
    @Serializable data object AdvancedSettings : RethinkRoute
    @Serializable data object AntiCensorship : RethinkRoute
    @Serializable data object AppLock : RethinkRoute
    @Serializable data object PingTest : RethinkRoute
    @Serializable data object Database : RethinkRoute
    @Serializable data object WireGuard : RethinkRoute
    @Serializable data object RpnAvailability : RethinkRoute
    @Serializable data object RpnCountries : RethinkRoute
    @Serializable data object RpnAccount : RethinkRoute
    @Serializable data object Checkout : RethinkRoute
    @Serializable data class AppInfo(val uid: Int) : RethinkRoute
    @Serializable data class DetailedStatistics(val type: Int, val period: Int) : RethinkRoute
    @Serializable data class AppWiseIpLogs(val uid: Int, val asn: Boolean) : RethinkRoute
    @Serializable data class AppWiseDomainLogs(val uid: Int) : RethinkRoute
    @Serializable data class WireGuardDetail(val id: Int, val type: Int) : RethinkRoute
    @Serializable data class WireGuardEditor(val id: Int, val type: Int) : RethinkRoute
    @Serializable data class CountryProxy(val countryCode: String) : RethinkRoute
}

enum class RethinkRootDestination(val route: RethinkRoute) {
    Home(RethinkRoute.Home),
    Statistics(RethinkRoute.Statistics),
    Configure(RethinkRoute.Configure),
}

fun RethinkRoute.rootDestination(): RethinkRootDestination = when (this) {
    RethinkRoute.Home, RethinkRoute.Welcome -> RethinkRootDestination.Home
    RethinkRoute.Statistics, is RethinkRoute.DetailedStatistics -> RethinkRootDestination.Statistics
    RethinkRoute.Configure, RethinkRoute.AppList, RethinkRoute.DnsList, RethinkRoute.DnsDetail,
    RethinkRoute.FirewallSettings, RethinkRoute.UniversalFirewall, RethinkRoute.CustomRules,
    RethinkRoute.ProxySettings, RethinkRoute.TcpProxy, RethinkRoute.TunnelSettings,
    RethinkRoute.MiscSettings, RethinkRoute.AdvancedSettings, RethinkRoute.AntiCensorship,
    RethinkRoute.AppLock, RethinkRoute.PingTest, RethinkRoute.Database, RethinkRoute.WireGuard,
    is RethinkRoute.WireGuardDetail, is RethinkRoute.WireGuardEditor, RethinkRoute.RpnAvailability,
    RethinkRoute.RpnCountries, is RethinkRoute.CountryProxy, RethinkRoute.RpnAccount,
    RethinkRoute.Checkout, RethinkRoute.NetworkLogs, RethinkRoute.ConsoleLogs,
    is RethinkRoute.AppWiseIpLogs, is RethinkRoute.AppWiseDomainLogs, RethinkRoute.Alerts,
    RethinkRoute.Events, is RethinkRoute.AppInfo, RethinkRoute.About -> RethinkRootDestination.Configure
}

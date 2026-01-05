package com.celzero.bravedns.ui.compose.navigation

import kotlinx.serialization.Serializable
import com.celzero.bravedns.ui.compose.wireguard.WgType

sealed interface HomeRoute {

    @Serializable
    data object Home : HomeRoute

    @Serializable
    data object Stats : HomeRoute

    @Serializable
    data object Configure : HomeRoute

    @Serializable
    data object About : HomeRoute

    @Serializable
    data object Alerts : HomeRoute

    @Serializable
    data object RpnCountries : HomeRoute

    @Serializable
    data object RpnAvailability : HomeRoute
    
    @Serializable
    data object Events : HomeRoute

    @Serializable
    data object FirewallSettings : HomeRoute

    @Serializable
    data object AdvancedSettings : HomeRoute

    @Serializable
    data object AntiCensorship : HomeRoute

    @Serializable
    data object TunnelSettings : HomeRoute
    
    @Serializable
    data object MiscSettings : HomeRoute
    
    @Serializable
    data object ConsoleLogs : HomeRoute

    @Serializable
    data object NetworkLogs : HomeRoute

    @Serializable
    data object AppList : HomeRoute

    @Serializable
    data object CustomRules : HomeRoute

    @Serializable
    data object ProxySettings : HomeRoute

    @Serializable
    data object TcpProxyMain : HomeRoute

    @Serializable
    data object Welcome : HomeRoute

    @Serializable
    data object PingTest : HomeRoute

    @Serializable
    data object AppLock : HomeRoute

    @Serializable
    data object DnsDetail : HomeRoute
    
    @Serializable
    data class DetailedStats(val typeId: Int, val timeCategory: Int) : HomeRoute

    @Serializable
    data class RpnWinProxyDetails(val countryCode: String) : HomeRoute

    @Serializable
    data class DomainConnections(
        val typeId: Int,
        val flag: String,
        val domain: String,
        val asn: String,
        val ip: String,
        val isBlocked: Boolean,
        val timeCategory: Int
    ) : HomeRoute
    
    @Serializable
    data class AppInfo(val uid: Int) : HomeRoute
    
    @Serializable
    data class WgConfigDetail(val configId: Int, val wgType: WgType) : HomeRoute

    @Serializable
    data class WgConfigEditor(val configId: Int, val wgType: WgType) : HomeRoute
    
    // We handle ConfigureRethinkBasic carefully as it uses an enum index
    @Serializable
    data class ConfigureRethinkBasic(
        val screenTypeOrdinal: Int,
        val remoteName: String = "",
        val remoteUrl: String = "",
        val uid: Int = -1
    ) : HomeRoute

    @Serializable
    data object DnsList : HomeRoute

    @Serializable
    data class AppWiseIpLogs(val uid: Int, val isAsn: Boolean) : HomeRoute

    @Serializable
    data class ConfigureOtherDns(val dnsType: Int) : HomeRoute

    @Serializable
    data object UniversalFirewallSettings : HomeRoute

    @Serializable
    data class AppWiseDomainLogs(val uid: Int) : HomeRoute

    @Serializable
    data object Checkout : HomeRoute

    @Serializable
    data object WgMain : HomeRoute
}

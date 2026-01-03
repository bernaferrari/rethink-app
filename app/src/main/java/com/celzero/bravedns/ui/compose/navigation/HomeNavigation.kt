/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.celzero.bravedns.R
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.ui.compose.alerts.AlertsScreen
import com.celzero.bravedns.ui.compose.about.AboutScreen
import com.celzero.bravedns.ui.compose.about.AboutUiState
import com.celzero.bravedns.ui.compose.app.AppInfoScreen
import com.celzero.bravedns.ui.compose.configure.ConfigureScreen
import com.celzero.bravedns.ui.compose.events.EventsScreen
import com.celzero.bravedns.ui.compose.firewall.FirewallSettingsScreen
import com.celzero.bravedns.ui.compose.home.HomeScreen
import com.celzero.bravedns.ui.compose.settings.AdvancedSettingsScreen
import com.celzero.bravedns.ui.compose.settings.AntiCensorshipScreen
import com.celzero.bravedns.ui.compose.settings.AppLockScreen
import com.celzero.bravedns.ui.compose.settings.AppLockResult
import com.celzero.bravedns.ui.compose.settings.MiscSettingsScreen
import com.celzero.bravedns.ui.compose.settings.TunnelSettingsScreen
import com.celzero.bravedns.ui.compose.settings.ConsoleLogScreen
import com.celzero.bravedns.ui.compose.settings.ProxySettingsScreen
import com.celzero.bravedns.ui.compose.proxy.TcpProxyMainScreen
import com.celzero.bravedns.ui.compose.logs.NetworkLogsScreen
import com.celzero.bravedns.ui.compose.settings.PingTestScreen
import com.celzero.bravedns.ui.compose.logs.AppWiseIpLogsScreen
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.bravedns.ui.compose.apps.AppListScreen
import com.celzero.bravedns.ui.compose.firewall.CustomRulesScreen
import com.celzero.bravedns.ui.compose.home.WelcomeScreen
import com.celzero.bravedns.ui.compose.home.HomeScreenUiState
import com.celzero.bravedns.ui.compose.wireguard.WgConfigDetailScreen
import com.celzero.bravedns.ui.compose.wireguard.WgConfigEditorScreen
import com.celzero.bravedns.ui.compose.wireguard.WgType
import com.celzero.bravedns.ui.compose.rpn.RpnAvailabilityScreen
import com.celzero.bravedns.ui.compose.rpn.RpnCountriesScreen
import com.celzero.bravedns.ui.compose.rpn.RpnWinProxyDetailsScreen
import com.celzero.bravedns.ui.compose.logs.DomainConnectionsInputType
import com.celzero.bravedns.ui.compose.logs.DomainConnectionsScreen
import com.celzero.bravedns.ui.compose.statistics.DetailedStatisticsScreen
import com.celzero.bravedns.ui.compose.statistics.SummaryStatisticsScreen
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel
import com.celzero.bravedns.viewmodel.EventsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import com.celzero.bravedns.viewmodel.ConsoleLogViewModel
import com.celzero.bravedns.database.ConsoleLogRepository
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.ui.compose.dns.ConfigureRethinkBasicScreen
import com.celzero.bravedns.ui.compose.dns.ConfigureRethinkScreenType
import com.celzero.bravedns.ui.compose.dns.DnsDetailScreen
import com.celzero.bravedns.ui.compose.dns.DnsListScreen
import com.celzero.bravedns.ui.compose.dns.DnsSettingsViewModel
import com.celzero.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.celzero.bravedns.viewmodel.RethinkEndpointViewModel
import com.celzero.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.celzero.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.celzero.bravedns.viewmodel.AppInfoViewModel
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.ui.compose.dns.ConfigureOtherDnsScreen
import com.celzero.bravedns.ui.compose.dns.DnsScreenType
import com.celzero.bravedns.ui.compose.firewall.UniversalFirewallSettingsScreen
import com.celzero.bravedns.ui.compose.settings.CheckoutScreen
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.viewmodel.DoHEndpointViewModel
import com.celzero.bravedns.viewmodel.DoTEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsProxyEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsCryptEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsCryptRelayEndpointViewModel
import com.celzero.bravedns.viewmodel.ODoHEndpointViewModel
import com.celzero.bravedns.viewmodel.CheckoutViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.asFlow
import com.celzero.bravedns.ui.compose.logs.AppWiseDomainLogsScreen
import com.celzero.bravedns.ui.compose.logs.AppWiseDomainLogsState
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.WgConfigViewModel
import com.celzero.bravedns.ui.compose.wireguard.WgMainScreen



enum class HomeDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int
) {
    HOME("home", R.string.txt_home, R.drawable.ic_home_black_24dp),
    STATS("stats", R.string.title_statistics, R.drawable.ic_statistics),
    CONFIGURE("configure", R.string.lbl_configure, R.drawable.ic_settings),
    ABOUT("about", R.string.title_about, R.drawable.ic_about)
}

sealed interface HomeNavRequest {
    data class DetailedStats(
        val type: SummaryStatisticsType,
        val timeCategory: SummaryStatisticsViewModel.TimeCategory
    ) : HomeNavRequest
    data object Alerts : HomeNavRequest
    data object RpnCountries : HomeNavRequest
    data object RpnAvailability : HomeNavRequest
    data object Events : HomeNavRequest
    data object FirewallSettings : HomeNavRequest
    data object AdvancedSettings : HomeNavRequest
    data object AntiCensorship : HomeNavRequest
    data object TunnelSettings : HomeNavRequest
    data object MiscSettings : HomeNavRequest
    data object ConsoleLogs : HomeNavRequest
    data object NetworkLogs : HomeNavRequest
    data object AppList : HomeNavRequest
    data object CustomRules : HomeNavRequest
    data object ProxySettings : HomeNavRequest
    data object TcpProxyMain : HomeNavRequest
    data object Welcome : HomeNavRequest
    data object AppLock : HomeNavRequest
    data object PingTest : HomeNavRequest
    data object DnsDetail : HomeNavRequest
    data class WgConfigDetail(val configId: Int, val wgType: WgType) : HomeNavRequest
    data class WgConfigEditor(val configId: Int, val wgType: WgType) : HomeNavRequest
    data class RpnWinProxyDetails(val countryCode: String) : HomeNavRequest
    data class AppInfo(val uid: Int) : HomeNavRequest
    data class DomainConnections(
        val type: DomainConnectionsInputType,
        val flag: String,
        val domain: String,
        val asn: String,
        val ip: String,
        val isBlocked: Boolean,
        val timeCategory: DomainConnectionsViewModel.TimeCategory
    ) : HomeNavRequest
    data object DnsList : HomeNavRequest
    data class AppWiseIpLogs(val uid: Int, val isAsn: Boolean) : HomeNavRequest
    data class ConfigureRethinkBasic(
        val screenType: ConfigureRethinkScreenType,
        val remoteName: String = "",
        val remoteUrl: String = "",
        val uid: Int = -1
    ) : HomeNavRequest
    data class ConfigureOtherDns(val dnsType: Int) : HomeNavRequest
    data object UniversalFirewallSettings : HomeNavRequest
    data class AppWiseDomainLogs(val uid: Int) : HomeNavRequest
    data object Checkout : HomeNavRequest
    data object WgMain : HomeNavRequest
}

private const val ROUTE_DETAILED_STATS = "detailedStats"
private const val ROUTE_ALERTS = "alerts"
private const val ROUTE_RPN_COUNTRIES = "rpnCountries"
private const val ROUTE_RPN_AVAILABILITY = "rpnAvailability"
private const val ROUTE_DOMAIN_CONNECTIONS = "domainConnections"
private const val ROUTE_EVENTS = "events"
private const val ROUTE_RPN_WIN_PROXY_DETAILS = "rpnWinProxyDetails"
private const val ROUTE_APP_INFO = "appInfo"
private const val ROUTE_FIREWALL_SETTINGS = "firewallSettings"
private const val ROUTE_ADVANCED_SETTINGS = "advancedSettings"
private const val ROUTE_ANTI_CENSORSHIP = "antiCensorship"
private const val ROUTE_TUNNEL_SETTINGS = "tunnelSettings"
private const val ROUTE_MISC_SETTINGS = "miscSettings"
private const val ROUTE_CONSOLE_LOGS = "consoleLogs"
private const val ROUTE_NETWORK_LOGS = "networkLogs"
private const val ROUTE_APP_LIST = "appList"
private const val ROUTE_CUSTOM_RULES = "customRules"
private const val ROUTE_PROXY_SETTINGS = "proxySettings"
private const val ROUTE_TCP_PROXY_MAIN = "tcpProxyMain"
private const val ROUTE_WELCOME = "welcome"
private const val ROUTE_APP_LOCK = "appLock"
private const val ROUTE_PING_TEST = "pingTest"
private const val ROUTE_DNS_DETAIL = "dnsDetail"
private const val ROUTE_WG_CONFIG_DETAIL = "wgConfigDetail"
private const val ROUTE_WG_CONFIG_EDITOR = "wgConfigEditor"
private const val ROUTE_CONFIGURE_RETHINK_BASIC = "configureRethinkBasic"
private const val ROUTE_DNS_LIST = "dnsList"
private const val ROUTE_APP_WISE_IP_LOGS = "appWiseIpLogs"
private const val ROUTE_CONFIGURE_OTHER_DNS = "configureOtherDns"
private const val ROUTE_UNIVERSAL_FIREWALL_SETTINGS = "universalFirewallSettings"
private const val ROUTE_APP_WISE_DOMAIN_LOGS = "appWiseDomainLogs"
private const val ROUTE_CHECKOUT = "checkout"
private const val ROUTE_WG_MAIN = "wgMain"

private fun domainConnectionsRoute(
    type: DomainConnectionsInputType,
    flag: String,
    domain: String,
    asn: String,
    ip: String,
    isBlocked: Boolean,
    timeCategory: DomainConnectionsViewModel.TimeCategory
): String {
    val encodedFlag = Uri.encode(flag)
    val encodedDomain = Uri.encode(domain)
    val encodedAsn = Uri.encode(asn)
    val encodedIp = Uri.encode(ip)
    return "$ROUTE_DOMAIN_CONNECTIONS/${type.type}/${timeCategory.value}" +
        "?flag=$encodedFlag&domain=$encodedDomain&asn=$encodedAsn&ip=$encodedIp&blocked=$isBlocked"
}

private fun detailedStatsRoute(typeId: Int, timeCategory: Int): String {
    return "$ROUTE_DETAILED_STATS/$typeId/$timeCategory"
}

@Composable
fun HomeScreenRoot(
    homeUiState: HomeScreenUiState,
    onHomeStartStopClick: () -> Unit,
    onHomeDnsClick: () -> Unit,
    onHomeFirewallClick: () -> Unit,
    onHomeProxyClick: () -> Unit,
    onHomeLogsClick: () -> Unit,
    onHomeAppsClick: () -> Unit,
    onHomeSponsorClick: () -> Unit,
    summaryViewModel: SummaryStatisticsViewModel,
    onOpenDetailedStats: (SummaryStatisticsType) -> Unit,
    isDebug: Boolean,
    onConfigureAppsClick: () -> Unit,
    onConfigureDnsClick: () -> Unit,
    onConfigureFirewallClick: () -> Unit,
    onFirewallUniversalClick: () -> Unit,
    onFirewallCustomIpClick: () -> Unit,
    onFirewallAppWiseIpClick: () -> Unit,
    onConfigureProxyClick: () -> Unit,
    onConfigureNetworkClick: () -> Unit,
    onConfigureOthersClick: () -> Unit,
    onConfigureLogsClick: () -> Unit,
    onConfigureAntiCensorshipClick: () -> Unit,
    onConfigureAdvancedClick: () -> Unit,
    aboutUiState: AboutUiState,
    onSponsorClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onAppUpdateClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onGithubClick: () -> Unit,
    onFaqClick: () -> Unit,
    onDocsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onEmailClick: () -> Unit,
    onRedditClick: () -> Unit,
    onElementClick: () -> Unit,
    onMastodonClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onVpnProfileClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDbStatsClick: () -> Unit,
    onFlightRecordClick: () -> Unit,
    onEventLogsClick: () -> Unit,
    onTokenClick: () -> Unit,
    onTokenDoubleTap: () -> Unit,
    onFossClick: () -> Unit,
    onFlossFundsClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    detailedStatsViewModel: DetailedStatisticsViewModel,
    domainConnectionsViewModel: DomainConnectionsViewModel,
    eventsViewModel: EventsViewModel,
    eventDao: EventDao,
    appInfoEventLogger: EventLogger,
    appInfoIpRulesViewModel: CustomIpViewModel,
    appInfoDomainRulesViewModel: CustomDomainViewModel,
    appInfoNetworkLogsViewModel: AppConnectionsViewModel,
    persistentState: PersistentState,
    appConfig: AppConfig,
    onOpenVpnProfile: () -> Unit,
    onRefreshDatabase: (() -> Unit)? = null,
    consoleLogViewModel: ConsoleLogViewModel,
    consoleLogRepository: ConsoleLogRepository,
    onShareConsoleLogs: () -> Unit,
    onConsoleLogsDeleteComplete: () -> Unit,
    proxyAppsMappingViewModel: ProxyAppsMappingViewModel,
    dnsSettingsViewModel: DnsSettingsViewModel,
    appDownloadManager: AppDownloadManager,
    onDnsCustomDnsClick: () -> Unit,
    onDnsRethinkPlusDnsClick: () -> Unit,
    onDnsLocalBlocklistConfigureClick: () -> Unit,
    homeNavRequest: HomeNavRequest?,
    onHomeNavConsumed: () -> Unit,
    onAppLockResult: (AppLockResult) -> Unit = {},
    // ConfigureRethinkBasic dependencies
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    remoteFileTagViewModel: RethinkRemoteFileTagViewModel,
    localFileTagViewModel: RethinkLocalFileTagViewModel,
    remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel,
    localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel,
    appInfoViewModel: AppInfoViewModel,
    refreshDatabase: RefreshDatabase,
    connectionTrackerViewModel: ConnectionTrackerViewModel,
    dnsLogViewModel: DnsLogViewModel,
    rethinkLogViewModel: RethinkLogViewModel,
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    rethinkLogRepository: RethinkLogRepository,
    onConfigureOtherDns: (Int) -> Unit,
    // ConfigureOtherDns dependencies
    dohViewModel: DoHEndpointViewModel,
    dotViewModel: DoTEndpointViewModel,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel,
    oDohViewModel: ODoHEndpointViewModel,
    // UniversalFirewallSettings callbacks
    onNavigateToLogs: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    // WireGuard dependencies
    wgConfigViewModel: WgConfigViewModel,
    // Checkout dependencies
    checkoutViewModel: CheckoutViewModel,
    onNavigateToProxy: () -> Unit,
    // WgMain callbacks
    onWgCreateClick: () -> Unit,
    onWgImportClick: () -> Unit,
    onWgQrScanClick: () -> Unit
) {


    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HomeDestination.HOME.route

    LaunchedEffect(homeNavRequest) {
        val request = homeNavRequest ?: return@LaunchedEffect
        when (request) {
            is HomeNavRequest.DetailedStats -> {
                navController.navigate(
                    detailedStatsRoute(request.type.tid, request.timeCategory.value)
                )
            }
            HomeNavRequest.Alerts -> {
                navController.navigate(ROUTE_ALERTS)
            }
            HomeNavRequest.RpnCountries -> {
                navController.navigate(ROUTE_RPN_COUNTRIES)
            }
            HomeNavRequest.RpnAvailability -> {
                navController.navigate(ROUTE_RPN_AVAILABILITY)
            }
            HomeNavRequest.Events -> {
                navController.navigate(ROUTE_EVENTS)
            }
            HomeNavRequest.FirewallSettings -> {
                navController.navigate(ROUTE_FIREWALL_SETTINGS)
            }
            HomeNavRequest.AdvancedSettings -> {
                navController.navigate(ROUTE_ADVANCED_SETTINGS)
            }
            HomeNavRequest.AntiCensorship -> {
                navController.navigate(ROUTE_ANTI_CENSORSHIP)
            }
            HomeNavRequest.TunnelSettings -> {
                navController.navigate(ROUTE_TUNNEL_SETTINGS)
            }
            HomeNavRequest.MiscSettings -> {
                navController.navigate(ROUTE_MISC_SETTINGS)
            }
            HomeNavRequest.ConsoleLogs -> {
                navController.navigate(ROUTE_CONSOLE_LOGS)
            }
            HomeNavRequest.NetworkLogs -> {
                navController.navigate(ROUTE_NETWORK_LOGS)
            }
            HomeNavRequest.AppList -> {
                navController.navigate(ROUTE_APP_LIST)
            }
            HomeNavRequest.CustomRules -> {
                navController.navigate(ROUTE_CUSTOM_RULES)
            }
            HomeNavRequest.ProxySettings -> {
                navController.navigate(ROUTE_PROXY_SETTINGS)
            }
            HomeNavRequest.TcpProxyMain -> {
                navController.navigate(ROUTE_TCP_PROXY_MAIN)
            }
            HomeNavRequest.Welcome -> {
                navController.navigate(ROUTE_WELCOME)
            }
            HomeNavRequest.PingTest -> {
                navController.navigate(ROUTE_PING_TEST)
            }
            HomeNavRequest.AppLock -> {
                navController.navigate(ROUTE_APP_LOCK)
            }
            HomeNavRequest.DnsDetail -> {
                navController.navigate(ROUTE_DNS_DETAIL)
            }
            is HomeNavRequest.RpnWinProxyDetails -> {
                navController.navigate("$ROUTE_RPN_WIN_PROXY_DETAILS/${Uri.encode(request.countryCode)}")
            }
            is HomeNavRequest.DomainConnections -> {
                navController.navigate(
                    domainConnectionsRoute(
                        request.type,
                        request.flag,
                        request.domain,
                        request.asn,
                        request.ip,
                        request.isBlocked,
                        request.timeCategory
                    )
                )
            }
            is HomeNavRequest.AppInfo -> {
                navController.navigate("$ROUTE_APP_INFO/${request.uid}")
            }
            is HomeNavRequest.WgConfigDetail -> {
                navController.navigate("$ROUTE_WG_CONFIG_DETAIL/${request.configId}/${request.wgType.value}")
            }
            is HomeNavRequest.WgConfigEditor -> {
                navController.navigate("$ROUTE_WG_CONFIG_EDITOR/${request.configId}/${request.wgType.value}")
            }
            is HomeNavRequest.ConfigureRethinkBasic -> {
                val encodedName = Uri.encode(request.remoteName)
                val encodedUrl = Uri.encode(request.remoteUrl)
                navController.navigate(
                    "$ROUTE_CONFIGURE_RETHINK_BASIC/${request.screenType.ordinal}?name=$encodedName&url=$encodedUrl&uid=${request.uid}"
                )
            }
            HomeNavRequest.DnsList -> {
                navController.navigate(ROUTE_DNS_LIST)
            }
            is HomeNavRequest.AppWiseIpLogs -> {
                navController.navigate("$ROUTE_APP_WISE_IP_LOGS/${request.uid}/${request.isAsn}")
            }
            is HomeNavRequest.ConfigureOtherDns -> {
                navController.navigate("$ROUTE_CONFIGURE_OTHER_DNS/${request.dnsType}")
            }
            HomeNavRequest.UniversalFirewallSettings -> {
                navController.navigate(ROUTE_UNIVERSAL_FIREWALL_SETTINGS)
            }
            is HomeNavRequest.AppWiseDomainLogs -> {
                navController.navigate("$ROUTE_APP_WISE_DOMAIN_LOGS/${request.uid}")
            }
            HomeNavRequest.Checkout -> {
                navController.navigate(ROUTE_CHECKOUT)
            }
            HomeNavRequest.WgMain -> {
                navController.navigate(ROUTE_WG_MAIN)
            }
        }
        onHomeNavConsumed()
    }

    BackHandler(enabled = currentRoute != HomeDestination.HOME.route) {
        navController.navigate(HomeDestination.HOME.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                HomeDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = destination.iconRes),
                                contentDescription = stringResource(id = destination.labelRes)
                            )
                        },
                        label = {
                            androidx.compose.material3.Text(
                                text = stringResource(id = destination.labelRes)
                            )
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = HomeDestination.HOME.route
        ) {
            composable(HomeDestination.HOME.route) {
                HomeScreen(
                    uiState = homeUiState,
                    onStartStopClick = onHomeStartStopClick,
                    onDnsClick = onHomeDnsClick,
                    onFirewallClick = onHomeFirewallClick,
                    onProxyClick = onHomeProxyClick,
                    onLogsClick = onHomeLogsClick,
                    onAppsClick = onHomeAppsClick,
                    onSponsorClick = onHomeSponsorClick
                )
            }
            composable(HomeDestination.STATS.route) {
                SummaryStatisticsScreen(
                    viewModel = summaryViewModel,
                    onSeeMoreClick = onOpenDetailedStats
                )
            }
            composable(ROUTE_ALERTS) {
                AlertsScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_RPN_COUNTRIES) {
                RpnCountriesScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_RPN_AVAILABILITY) {
                RpnAvailabilityScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_EVENTS) {
                EventsScreen(
                    viewModel = eventsViewModel,
                    eventDao = eventDao,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_FIREWALL_SETTINGS) {
                FirewallSettingsScreen(
                    onUniversalFirewallClick = onFirewallUniversalClick,
                    onCustomIpDomainClick = onFirewallCustomIpClick,
                    onAppWiseIpDomainClick = onFirewallAppWiseIpClick,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_ADVANCED_SETTINGS) {
                AdvancedSettingsScreen(
                    persistentState = persistentState,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_ANTI_CENSORSHIP) {
                AntiCensorshipScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_TUNNEL_SETTINGS) {
                TunnelSettingsScreen(
                    persistentState = persistentState,
                    appConfig = appConfig,
                    eventLogger = appInfoEventLogger,
                    onOpenVpnProfile = onOpenVpnProfile,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_MISC_SETTINGS) {
                MiscSettingsScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() },
                    onRefreshDatabase = onRefreshDatabase
                )
            }
            composable(ROUTE_PING_TEST) {
                PingTestScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CONSOLE_LOGS) {
                ConsoleLogScreen(
                    viewModel = consoleLogViewModel,
                    consoleLogRepository = consoleLogRepository,
                    persistentState = persistentState,
                    onShareClick = onShareConsoleLogs,
                    onDeleteComplete = onConsoleLogsDeleteComplete,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_NETWORK_LOGS) {
                NetworkLogsScreen(
                    connectionTrackerViewModel = connectionTrackerViewModel,
                    dnsLogViewModel = dnsLogViewModel,
                    rethinkLogViewModel = rethinkLogViewModel,
                    connectionTrackerRepository = connectionTrackerRepository,
                    dnsLogRepository = dnsLogRepository,
                    rethinkLogRepository = rethinkLogRepository,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(ROUTE_APP_LIST) {
                AppListScreen(
                    viewModel = appInfoViewModel,
                    eventLogger = appInfoEventLogger,
                    refreshDatabase = refreshDatabase,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(ROUTE_CUSTOM_RULES) {
                CustomRulesScreen(
                    domainViewModel = appInfoDomainRulesViewModel,
                    ipViewModel = appInfoIpRulesViewModel,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(ROUTE_PROXY_SETTINGS) {
                ProxySettingsScreen(
                    appConfig = appConfig,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(ROUTE_TCP_PROXY_MAIN) {
                TcpProxyMainScreen(
                    appConfig = appConfig,
                    mappingViewModel = proxyAppsMappingViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_WELCOME) {
                WelcomeScreen(
                    onFinish = { navController.popBackStack() }
                )
            }
            composable(ROUTE_APP_LOCK) {
                AppLockScreen(
                    persistentState = persistentState,
                    onAuthResult = { result ->
                        onAppLockResult(result)
                        navController.popBackStack()
                    }
                )
            }
            composable(ROUTE_DNS_DETAIL) {
                DnsDetailScreen(
                    viewModel = dnsSettingsViewModel,
                    persistentState = persistentState,
                    appDownloadManager = appDownloadManager,
                    onCustomDnsClick = onDnsCustomDnsClick,
                    onRethinkPlusDnsClick = onDnsRethinkPlusDnsClick,
                    onLocalBlocklistConfigureClick = onDnsLocalBlocklistConfigureClick,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_APP_INFO/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.IntType })
            ) { entry ->
                val uid = entry.arguments?.getInt("uid") ?: 0
                AppInfoScreen(
                    uid = uid,
                    eventLogger = appInfoEventLogger,
                    ipRulesViewModel = appInfoIpRulesViewModel,
                    domainRulesViewModel = appInfoDomainRulesViewModel,
                    networkLogsViewModel = appInfoNetworkLogsViewModel,
                    onBackClick = { navController.popBackStack() },
                    onAppWiseIpLogsClick = { u, isAsn ->
                        navController.navigate("$ROUTE_APP_WISE_IP_LOGS/$u/$isAsn")
                    },
                    onCustomRulesClick = { u ->
                        navController.navigate(ROUTE_CUSTOM_RULES)
                    }
                )
            }
            composable(ROUTE_DNS_LIST) {
                DnsListScreen(
                    appConfig = appConfig,
                    onConfigureOtherDns = onConfigureOtherDns,
                    onConfigureRethinkBasic = { type ->
                        val request = HomeNavRequest.ConfigureRethinkBasic(
                            ConfigureRethinkScreenType.entries[type]
                        )
                        // Trigger navigation via state update or direct navigate?
                        // Using direct navigate similar to other screens
                        // We need to encode if we use the URL builder
                        // But wait, here we are inside NavHost.
                        // We can construct the route directly or use the HomeNavRequest logic if we expose a handler?
                        // Or just navigate manually.
                        navController.navigate("$ROUTE_CONFIGURE_RETHINK_BASIC/$type?name=&url=&uid=-1")
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_APP_WISE_IP_LOGS/{uid}/{isAsn}",
                arguments = listOf(
                    navArgument("uid") { type = NavType.IntType },
                    navArgument("isAsn") { type = NavType.BoolType }
                )
            ) { entry ->
                val uid = entry.arguments?.getInt("uid") ?: -1
                val isAsn = entry.arguments?.getBoolean("isAsn") ?: false
                AppWiseIpLogsScreen(
                    uid = uid,
                    isAsn = isAsn,
                    viewModel = appInfoNetworkLogsViewModel,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_RPN_WIN_PROXY_DETAILS/{countryCode}",
                arguments = listOf(navArgument("countryCode") { type = NavType.StringType })
            ) { entry ->
                val cc = entry.arguments?.getString("countryCode").orEmpty()
                RpnWinProxyDetailsScreen(
                    countryCode = cc,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_DOMAIN_CONNECTIONS/{type}/{timeCategory}?flag={flag}&domain={domain}&asn={asn}&ip={ip}&blocked={blocked}",
                arguments =
                    listOf(
                        navArgument("type") { type = NavType.IntType },
                        navArgument("timeCategory") { type = NavType.IntType },
                        navArgument("flag") { type = NavType.StringType; defaultValue = "" },
                        navArgument("domain") { type = NavType.StringType; defaultValue = "" },
                        navArgument("asn") { type = NavType.StringType; defaultValue = "" },
                        navArgument("ip") { type = NavType.StringType; defaultValue = "" },
                        navArgument("blocked") { type = NavType.BoolType; defaultValue = false }
                    )
            ) { entry ->
                val typeId = entry.arguments?.getInt("type") ?: DomainConnectionsInputType.DOMAIN.type
                val tcValue = entry.arguments?.getInt("timeCategory")
                    ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR.value
                val flag = entry.arguments?.getString("flag").orEmpty()
                val domain = entry.arguments?.getString("domain").orEmpty()
                val asn = entry.arguments?.getString("asn").orEmpty()
                val ip = entry.arguments?.getString("ip").orEmpty()
                val isBlocked = entry.arguments?.getBoolean("blocked") ?: false
                val type = DomainConnectionsInputType.fromValue(typeId)
                val timeCategory =
                    DomainConnectionsViewModel.TimeCategory.fromValue(tcValue)
                        ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
                DomainConnectionsScreen(
                    viewModel = domainConnectionsViewModel,
                    type = type,
                    flag = flag,
                    domain = domain,
                    asn = asn,
                    ip = ip,
                    isBlocked = isBlocked,
                    timeCategory = timeCategory,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_DETAILED_STATS/{typeId}/{timeCategory}",
                arguments =
                    listOf(
                        navArgument("typeId") { type = NavType.IntType },
                        navArgument("timeCategory") { type = NavType.IntType }
                    )
            ) { entry ->
                val typeId = entry.arguments?.getInt("typeId")
                    ?: SummaryStatisticsType.MOST_CONNECTED_APPS.tid
                val timeCategoryValue = entry.arguments?.getInt("timeCategory")
                    ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR.value
                val type = SummaryStatisticsType.getType(typeId)
                val timeCategory =
                    SummaryStatisticsViewModel.TimeCategory.fromValue(timeCategoryValue)
                        ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR
                DetailedStatisticsScreen(
                    viewModel = detailedStatsViewModel,
                    type = type,
                    timeCategory = timeCategory,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_WG_CONFIG_DETAIL/{configId}/{wgType}",
                arguments = listOf(
                    navArgument("configId") { type = NavType.IntType },
                    navArgument("wgType") { type = NavType.IntType }
                )
            ) { entry ->
                val configId = entry.arguments?.getInt("configId") ?: -1
                val wgTypeValue = entry.arguments?.getInt("wgType") ?: 0
                val wgType = WgType.fromInt(wgTypeValue)
                WgConfigDetailScreen(
                    configId = configId,
                    wgType = wgType,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    mappingViewModel = proxyAppsMappingViewModel,
                    onEditConfig = { id, type ->
                        navController.navigate("$ROUTE_WG_CONFIG_EDITOR/$id/${type.value}")
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_WG_CONFIG_EDITOR/{configId}/{wgType}",
                arguments = listOf(
                    navArgument("configId") { type = NavType.IntType },
                    navArgument("wgType") { type = NavType.IntType }
                )
            ) { entry ->
                val configId = entry.arguments?.getInt("configId") ?: 0
                val wgTypeValue = entry.arguments?.getInt("wgType") ?: 0
                val wgType = WgType.fromInt(wgTypeValue)
                WgConfigEditorScreen(
                    configId = configId,
                    wgType = wgType,
                    persistentState = persistentState,
                    onBackClick = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }
            composable(HomeDestination.CONFIGURE.route) {
                ConfigureScreen(
                    isDebug = isDebug,
                    onAppsClick = onConfigureAppsClick,
                    onDnsClick = onConfigureDnsClick,
                    onFirewallClick = onConfigureFirewallClick,
                    onProxyClick = onConfigureProxyClick,
                    onNetworkClick = onConfigureNetworkClick,
                    onOthersClick = onConfigureOthersClick,
                    onLogsClick = onConfigureLogsClick,
                    onAntiCensorshipClick = onConfigureAntiCensorshipClick,
                    onAdvancedClick = onConfigureAdvancedClick
                )
            }
            composable(HomeDestination.ABOUT.route) {
                AboutScreen(
                    uiState = aboutUiState,
                    onSponsorClick = onSponsorClick,
                    onTelegramClick = onTelegramClick,
                    onBugReportClick = onBugReportClick,
                    onWhatsNewClick = onWhatsNewClick,
                    onAppUpdateClick = onAppUpdateClick,
                    onContributorsClick = onContributorsClick,
                    onTranslateClick = onTranslateClick,
                    onWebsiteClick = onWebsiteClick,
                    onGithubClick = onGithubClick,
                    onFaqClick = onFaqClick,
                    onDocsClick = onDocsClick,
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onTermsOfServiceClick = onTermsOfServiceClick,
                    onLicenseClick = onLicenseClick,
                    onTwitterClick = onTwitterClick,
                    onEmailClick = onEmailClick,
                    onRedditClick = onRedditClick,
                    onElementClick = onElementClick,
                    onMastodonClick = onMastodonClick,
                    onAppInfoClick = onAppInfoClick,
                    onVpnProfileClick = onVpnProfileClick,
                    onNotificationClick = onNotificationClick,
                    onStatsClick = onStatsClick,
                    onDbStatsClick = onDbStatsClick,
                    onFlightRecordClick = onFlightRecordClick,
                    onEventLogsClick = onEventLogsClick,
                    onTokenClick = onTokenClick,
                    onTokenDoubleTap = onTokenDoubleTap,
                    onFossClick = onFossClick,
                    onFlossFundsClick = onFlossFundsClick
                )
            }
            composable(
                route = "$ROUTE_CONFIGURE_RETHINK_BASIC/{screenType}?name={name}&url={url}&uid={uid}",
                arguments = listOf(
                    navArgument("screenType") { type = NavType.IntType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("uid") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                val screenTypeOrdinal = entry.arguments?.getInt("screenType") ?: 0
                val screenType = ConfigureRethinkScreenType.entries.getOrElse(screenTypeOrdinal) {
                    ConfigureRethinkScreenType.REMOTE
                }
                val remoteName = entry.arguments?.getString("name").orEmpty()
                val remoteUrl = entry.arguments?.getString("url").orEmpty()
                val uid = entry.arguments?.getInt("uid") ?: -1
                ConfigureRethinkBasicScreen(
                    screenType = screenType,
                    remoteName = remoteName,
                    remoteUrl = remoteUrl,
                    uid = uid,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    appDownloadManager = appDownloadManager,
                    rethinkEndpointViewModel = rethinkEndpointViewModel,
                    remoteFileTagViewModel = remoteFileTagViewModel,
                    localFileTagViewModel = localFileTagViewModel,
                    remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                    localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_CONFIGURE_OTHER_DNS/{dnsType}",
                arguments = listOf(navArgument("dnsType") { type = NavType.IntType })
            ) { entry ->
                val dnsType = entry.arguments?.getInt("dnsType") ?: 0
                ConfigureOtherDnsScreen(
                    dnsType = DnsScreenType.fromIndex(dnsType),
                    appConfig = appConfig,
                    persistentState = persistentState,
                    dohViewModel = dohViewModel,
                    dotViewModel = dotViewModel,
                    dnsProxyViewModel = dnsProxyViewModel,
                    dnsCryptViewModel = dnsCryptViewModel,
                    dnsCryptRelayViewModel = dnsCryptRelayViewModel,
                    oDohViewModel = oDohViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_UNIVERSAL_FIREWALL_SETTINGS) {
                UniversalFirewallSettingsScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    connTrackerRepository = connectionTrackerRepository,
                    onNavigateToLogs = onNavigateToLogs,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CHECKOUT) {
                val paymentStatus by checkoutViewModel.paymentStatus.collectAsState()
                val workInfoList by checkoutViewModel.paymentWorkInfo.asFlow().collectAsState(emptyList())
                
                LaunchedEffect(workInfoList) {
                    checkoutViewModel.updatePaymentStatusFromWorkInfo(workInfoList)
                }
                
                CheckoutScreen(
                    paymentStatus = paymentStatus,
                    onStartPayment = { checkoutViewModel.startPayment() },
                    onNavigateToProxy = onNavigateToProxy,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_APP_WISE_DOMAIN_LOGS/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.IntType })
            ) { entry ->
                val uid = entry.arguments?.getInt("uid") ?: 0
                val context = LocalContext.current
                
                LaunchedEffect(uid) {
                    appInfoNetworkLogsViewModel.setUid(uid)
                    appInfoNetworkLogsViewModel.setFilter("", AppConnectionsViewModel.FilterType.DOMAIN)
                }

                val appName = remember(uid) { "App $uid" } // TODO: Fetch app name asynchronously
                // Flow<PagingData>
                val items = remember(uid) { 
                    appInfoNetworkLogsViewModel.appDomainLogs.asFlow() 
                }.collectAsLazyPagingItems()

                val state = AppWiseDomainLogsState(
                    uid = uid,
                    isActiveConns = false,
                    isRethinkApp = appName == "com.celzero.bravedns",
                    searchHint = stringResource(R.string.search_custom_domains),
                    appIcon = null, // TODO: Load icon if needed
                    showToggleGroup = true,
                    showDeleteIcon = true,
                    selectedCategory = AppConnectionsViewModel.TimeCategory.SEVEN_DAYS
                )
                
                AppWiseDomainLogsScreen(
                    state = state,
                    items = items,
                    eventLogger = appInfoEventLogger,
                    onTimeCategoryChange = { appInfoNetworkLogsViewModel.timeCategoryChanged(it, true) },
                    onFilterChange = { appInfoNetworkLogsViewModel.setFilter(it, AppConnectionsViewModel.FilterType.DOMAIN) },
                    onDeleteLogs = { appInfoNetworkLogsViewModel.deleteLogs(uid) },
                    defaultIcon = null
                )
            }
            composable(ROUTE_WG_MAIN) {
                WgMainScreen(
                    wgConfigViewModel = wgConfigViewModel,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { navController.popBackStack() },
                    onCreateClick = onWgCreateClick,
                    onImportClick = onWgImportClick,
                    onQrScanClick = onWgQrScanClick,
                    onConfigDetailClick = { configId, wgType ->
                        navController.navigate("$ROUTE_WG_CONFIG_DETAIL/$configId/${wgType.value}")
                    }
                )
            }
        }
    }
}

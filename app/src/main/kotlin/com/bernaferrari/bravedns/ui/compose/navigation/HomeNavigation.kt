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
package com.bernaferrari.bravedns.ui.compose.navigation

import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.ui.compose.alerts.AlertsScreen
import com.bernaferrari.bravedns.ui.compose.about.AboutScreen
import com.bernaferrari.bravedns.ui.compose.about.AboutUiState
import com.bernaferrari.bravedns.ui.compose.app.AppInfoScreen
import com.bernaferrari.bravedns.ui.compose.configure.ConfigureScreen
import com.bernaferrari.bravedns.ui.compose.configure.SettingsSearchDestination
import com.bernaferrari.bravedns.ui.compose.events.EventsScreen
import com.bernaferrari.bravedns.ui.compose.firewall.FirewallSettingsScreen
import com.bernaferrari.bravedns.ui.compose.home.HomeScreen
import com.bernaferrari.bravedns.ui.compose.home.HomeGuidedTour
import com.bernaferrari.bravedns.ui.compose.settings.AdvancedSettingsScreen
import com.bernaferrari.bravedns.ui.compose.settings.AntiCensorshipScreen
import com.bernaferrari.bravedns.ui.compose.settings.AppLockScreen
import com.bernaferrari.bravedns.ui.compose.settings.AppLockResult
import com.bernaferrari.bravedns.ui.compose.settings.MiscSettingsScreen
import com.bernaferrari.bravedns.ui.compose.settings.TunnelSettingsScreen
import com.bernaferrari.bravedns.ui.compose.settings.ConsoleLogScreen
import com.bernaferrari.bravedns.ui.compose.settings.ProxySettingsScreen
import com.bernaferrari.bravedns.ui.compose.proxy.TcpProxyMainScreen
import com.bernaferrari.bravedns.ui.compose.logs.NetworkLogsScreen
import com.bernaferrari.bravedns.ui.compose.settings.PingTestScreen
import com.bernaferrari.bravedns.ui.dialog.WgIncludeAppsScreen
import com.bernaferrari.bravedns.ui.compose.logs.AppWiseIpLogsScreen
import com.bernaferrari.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.bernaferrari.bravedns.ui.compose.apps.AppListScreen
import com.bernaferrari.bravedns.ui.compose.firewall.CustomRulesScreen
import com.bernaferrari.bravedns.ui.compose.home.WelcomeScreen
import com.bernaferrari.bravedns.ui.compose.home.HomeScreenUiState
import com.bernaferrari.bravedns.ui.compose.firewall.RulesMode
import com.bernaferrari.bravedns.ui.compose.firewall.RulesTab
import com.bernaferrari.bravedns.ui.compose.wireguard.WgConfigDetailScreen
import com.bernaferrari.bravedns.ui.compose.wireguard.WgConfigEditorScreen
import com.bernaferrari.bravedns.ui.compose.wireguard.WgType
import com.bernaferrari.bravedns.ui.compose.rpn.RpnAvailabilityScreen
import com.bernaferrari.bravedns.ui.compose.rpn.RpnCountriesScreen
import com.bernaferrari.bravedns.ui.compose.rpn.RpnWinProxyDetailsScreen
import com.bernaferrari.bravedns.ui.compose.rpn.RpnAccountScreen
import com.bernaferrari.bravedns.ui.compose.rpn.RpnServerSettingsScreen
import com.bernaferrari.bravedns.ui.compose.logs.DomainConnectionsInputType
import com.bernaferrari.bravedns.ui.compose.logs.DomainConnectionsScreen
import com.bernaferrari.bravedns.ui.compose.statistics.DetailedStatisticsScreen
import com.bernaferrari.bravedns.ui.compose.statistics.SummaryStatisticsScreen
import com.bernaferrari.bravedns.ui.compose.database.DatabaseScreen
import com.bernaferrari.bravedns.database.EventDao
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel
import com.bernaferrari.bravedns.viewmodel.CustomDomainViewModel
import com.bernaferrari.bravedns.viewmodel.CustomIpViewModel
import com.bernaferrari.bravedns.viewmodel.DomainConnectionsViewModel
import com.bernaferrari.bravedns.viewmodel.DetailedStatisticsViewModel
import com.bernaferrari.bravedns.viewmodel.EventsViewModel
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel
import com.bernaferrari.bravedns.viewmodel.ConsoleLogViewModel
import com.bernaferrari.bravedns.database.ConsoleLogRepository
import com.bernaferrari.bravedns.download.AppDownloadManager
import com.bernaferrari.bravedns.ui.compose.dns.ConfigureRethinkBasicScreen
import com.bernaferrari.bravedns.ui.compose.dns.ConfigureRethinkScreenType
import com.bernaferrari.bravedns.ui.compose.dns.DnsDetailScreen
import com.bernaferrari.bravedns.ui.compose.dns.DnsListScreen
import com.bernaferrari.bravedns.ui.compose.dns.DnsSettingsViewModel
import com.bernaferrari.bravedns.ui.compose.dns.BlockFreeDnsScreen
import com.bernaferrari.bravedns.viewmodel.LocalBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkLocalFileTagViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkRemoteFileTagViewModel
import com.bernaferrari.bravedns.viewmodel.AppInfoViewModel
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.viewmodel.ConnectionTrackerViewModel
import com.bernaferrari.bravedns.viewmodel.DnsLogViewModel
import com.bernaferrari.bravedns.viewmodel.RethinkLogViewModel
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.RethinkLogRepository
import com.bernaferrari.bravedns.ui.compose.dns.ConfigureOtherDnsScreen
import com.bernaferrari.bravedns.ui.compose.dns.DnsScreenType
import com.bernaferrari.bravedns.ui.compose.firewall.UniversalFirewallSettingsScreen
import com.bernaferrari.bravedns.ui.compose.settings.CheckoutScreen
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.viewmodel.DoHEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DoTEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsProxyEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsCryptEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsCryptRelayEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.ODoHEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.CheckoutViewModel
import com.bernaferrari.bravedns.viewmodel.ManagePurchaseViewModel
import com.bernaferrari.bravedns.viewmodel.PurchaseHistoryViewModel
import com.bernaferrari.bravedns.viewmodel.ServerOrderHistoryViewModel
import com.bernaferrari.bravedns.viewmodel.BlockFreeDnsViewModel
import com.bernaferrari.bravedns.ui.compose.logs.AppWiseDomainLogsScreen
import com.bernaferrari.bravedns.ui.compose.theme.rememberReducedMotion
import com.bernaferrari.bravedns.viewmodel.WgConfigViewModel
import com.bernaferrari.bravedns.ui.compose.wireguard.WgMainScreen
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

enum class HomeDestination(
    val root: RethinkRootDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(RethinkRootDestination.Home, R.string.txt_home, Icons.Filled.Home, Icons.Filled.Home),
    STATS(RethinkRootDestination.Statistics, R.string.title_statistics, Icons.Filled.Star, Icons.Filled.Star),
    CONFIGURE(RethinkRootDestination.Configure, R.string.title_settings, Icons.Filled.Settings, Icons.Filled.Settings);

    val route: RethinkRoute get() = root.route
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
    data class CustomRules(
        val uid: Int = UID_EVERYBODY,
        val tab: CustomRulesTab = CustomRulesTab.IP,
        val mode: CustomRulesMode = CustomRulesMode.APP_SPECIFIC
    ) : HomeNavRequest
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
    data object RpnAccount : HomeNavRequest
    data object WgMain : HomeNavRequest
    data object Database : HomeNavRequest
}


private fun HomeNavRequest.toRethinkRoute(): RethinkRoute = when (this) {
    is HomeNavRequest.DetailedStats ->
        RethinkRoute.DetailedStatistics(type.tid, timeCategory.value)
    HomeNavRequest.Alerts -> RethinkRoute.Alerts
    HomeNavRequest.RpnCountries -> RethinkRoute.RpnCountries
    HomeNavRequest.RpnAvailability -> RethinkRoute.RpnAvailability
    HomeNavRequest.Events -> RethinkRoute.Events
    HomeNavRequest.FirewallSettings -> RethinkRoute.FirewallSettings()
    HomeNavRequest.AdvancedSettings -> RethinkRoute.AdvancedSettings
    HomeNavRequest.AntiCensorship -> RethinkRoute.AntiCensorship
    HomeNavRequest.TunnelSettings -> RethinkRoute.TunnelSettings()
    HomeNavRequest.MiscSettings -> RethinkRoute.MiscSettings()
    HomeNavRequest.ConsoleLogs -> RethinkRoute.ConsoleLogs
    HomeNavRequest.NetworkLogs -> RethinkRoute.NetworkLogs
    HomeNavRequest.AppList -> RethinkRoute.AppList
    is HomeNavRequest.CustomRules ->
        RethinkRoute.CustomRules(uid = uid, tab = tab.value, mode = mode.value)
    HomeNavRequest.ProxySettings -> RethinkRoute.ProxySettings()
    HomeNavRequest.TcpProxyMain -> RethinkRoute.TcpProxy
    HomeNavRequest.Welcome -> RethinkRoute.Welcome
    HomeNavRequest.PingTest -> RethinkRoute.PingTest
    HomeNavRequest.AppLock -> RethinkRoute.AppLock
    HomeNavRequest.DnsDetail -> RethinkRoute.DnsDetail()
    is HomeNavRequest.RpnWinProxyDetails -> RethinkRoute.CountryProxy(countryCode)
    is HomeNavRequest.DomainConnections ->
        RethinkRoute.DomainConnections(
            typeId = type.type,
            flag = flag,
            domain = domain,
            asn = asn,
            ip = ip,
            isBlocked = isBlocked,
            timeCategory = timeCategory.value,
        )
    is HomeNavRequest.AppInfo -> RethinkRoute.AppInfo(uid)
    is HomeNavRequest.WgConfigDetail ->
        RethinkRoute.WireGuardDetail(configId, wgType.value)
    is HomeNavRequest.WgConfigEditor ->
        RethinkRoute.WireGuardEditor(configId, wgType.value)
    is HomeNavRequest.ConfigureRethinkBasic ->
        RethinkRoute.ConfigureRethinkBasic(
            screenTypeOrdinal = screenType.ordinal,
            remoteName = remoteName,
            remoteUrl = remoteUrl,
            uid = uid,
        )
    HomeNavRequest.DnsList -> RethinkRoute.DnsList
    is HomeNavRequest.AppWiseIpLogs -> RethinkRoute.AppWiseIpLogs(uid, isAsn)
    is HomeNavRequest.ConfigureOtherDns -> RethinkRoute.ConfigureOtherDns(dnsType)
    HomeNavRequest.UniversalFirewallSettings -> RethinkRoute.UniversalFirewall
    is HomeNavRequest.AppWiseDomainLogs -> RethinkRoute.AppWiseDomainLogs(uid)
    HomeNavRequest.Checkout -> RethinkRoute.Checkout
    HomeNavRequest.RpnAccount -> RethinkRoute.RpnAccount
    HomeNavRequest.WgMain -> RethinkRoute.WireGuard
    HomeNavRequest.Database -> RethinkRoute.Database
}

@Composable
fun HomeScreenRoot(
    homeUiState: HomeScreenUiState,
    onHomeStartStopClick: () -> Unit,
    summaryViewModel: SummaryStatisticsViewModel,
    onOpenDetailedStats: (SummaryStatisticsType) -> Unit,
    startDestination: RethinkRoute,
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
    onGeneralSettingsClick: () -> Unit,
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
    onThemeModeChanged: ((Int) -> Unit)? = null,
    onThemeColorChanged: ((Int) -> Unit)? = null,
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
    checkoutViewModel: CheckoutViewModel?,
    managePurchaseViewModel: ManagePurchaseViewModel,
    purchaseHistoryViewModel: PurchaseHistoryViewModel,
    serverOrderHistoryViewModel: ServerOrderHistoryViewModel,
    blockFreeDnsViewModel: BlockFreeDnsViewModel,
    onNavigateToProxy: () -> Unit,
    // WgMain callbacks
    onWgCreateClick: () -> Unit,
    onWgImportClick: () -> Unit,
    onWgQrScanClick: () -> Unit,
    appDatabase: AppDatabase
) {
    val navigationItems = HomeDestination.entries.map { destination ->
        RethinkNavigationItem(
            id = destination.root.name,
            label = stringResource(destination.labelRes),
            selectedIcon = destination.selectedIcon,
            unselectedIcon = destination.unselectedIcon,
        )
    }

    // Map external activity requests onto the shared route type (pendingRoute).
    var pendingRoute by remember { mutableStateOf<RethinkRoute?>(null) }
    LaunchedEffect(homeNavRequest) {
        val request = homeNavRequest ?: return@LaunchedEffect
        pendingRoute = request.toRethinkRoute()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RethinkAppNavigation(
            destinations = navigationItems,
            startRoute = startDestination,
            pendingRoute = pendingRoute,
            onRouteNavigated = {
                pendingRoute = null
                onHomeNavConsumed()
            },
            modifier = Modifier.fillMaxSize(),
            entryBuilder = { nav ->

            entry<RethinkRoute.Home> {
                var showGuidedTour by remember {
                    mutableStateOf(!persistentState.guidedTourCompleted || persistentState.guidedTourVersion < 1)
                }
                HomeScreen(
                    uiState = homeUiState,
                    onStartStopClick = onHomeStartStopClick,
                )
                if (showGuidedTour) HomeGuidedTour {
                    persistentState.guidedTourCompleted = true
                    persistentState.guidedTourVersion = 1
                    showGuidedTour = false
                }
            }
            entry<RethinkRoute.Statistics> {
                SummaryStatisticsScreen(
                    viewModel = summaryViewModel,
                    persistentState = persistentState,
                    onSeeMoreClick = onOpenDetailedStats
                )
            }
            entry<RethinkRoute.Alerts> {
                AlertsScreen(onBackClick = { nav.popBackStack() })
            }
            entry<RethinkRoute.RpnCountries> {
                RpnCountriesScreen(
                    onBackClick = { nav.popBackStack() },
                    onServerDetails = { nav.push(RethinkRoute.CountryProxy(it)) },
                    onServerSettings = { nav.push(RethinkRoute.RpnSettings) },
                )
            }
            entry<RethinkRoute.RpnSettings> {
                RpnServerSettingsScreen(persistentState = persistentState, onBackClick = { nav.popBackStack() })
            }
            entry<RethinkRoute.RpnAvailability> {
                RpnAvailabilityScreen(onBackClick = { nav.popBackStack() })
            }
            entry<RethinkRoute.Events> {
                EventsScreen(
                    viewModel = eventsViewModel,
                    eventDao = eventDao,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.FirewallSettings> { args ->
                FirewallSettingsScreen(
                    onUniversalFirewallClick = onFirewallUniversalClick,
                    onCustomIpDomainClick = onFirewallCustomIpClick,
                    onAppWiseIpDomainClick = onFirewallAppWiseIpClick,
                    initialFocusKey = args.focusKey.takeIf { it.isNotBlank() },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.AdvancedSettings> {
                AdvancedSettingsScreen(
                    persistentState = persistentState,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.AntiCensorship> {
                AntiCensorshipScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.TunnelSettings> { args ->
                TunnelSettingsScreen(
                    persistentState = persistentState,
                    appConfig = appConfig,
                    eventLogger = appInfoEventLogger,
                    onOpenVpnProfile = onOpenVpnProfile,
                    initialFocusKey = args.focusKey.takeIf { it.isNotBlank() },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.MiscSettings> { args ->
                MiscSettingsScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    initialFocusKey = args.focusKey.takeIf { it.isNotBlank() },
                    onBackClick = { nav.popBackStack() },
                    onOpenAbout = { nav.push(RethinkRoute.About) },
                    onRefreshDatabase = onRefreshDatabase,
                )
            }
            entry<RethinkRoute.PingTest> {
                PingTestScreen(
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.ConsoleLogs> {
                ConsoleLogScreen(
                    viewModel = consoleLogViewModel,
                    consoleLogRepository = consoleLogRepository,
                    persistentState = persistentState,
                    onShareClick = onShareConsoleLogs,
                    onDeleteComplete = onConsoleLogsDeleteComplete,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.NetworkLogs> {
                NetworkLogsScreen(
                    connectionTrackerViewModel = connectionTrackerViewModel,
                    dnsLogViewModel = dnsLogViewModel,
                    rethinkLogViewModel = rethinkLogViewModel,
                    connectionTrackerRepository = connectionTrackerRepository,
                    dnsLogRepository = dnsLogRepository,
                    rethinkLogRepository = rethinkLogRepository,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.AppList> {
                AppListScreen(
                    viewModel = appInfoViewModel,
                    eventLogger = appInfoEventLogger,
                    refreshDatabase = refreshDatabase,
                    onAppClick = { uid -> nav.push(RethinkRoute.AppInfo(uid)) },
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.CustomRules> { args ->
                CustomRulesScreen(
                    uid = args.uid,
                    initialTab = RulesTab.fromValue(args.tab),
                    initialMode = RulesMode.fromValue(args.mode),
                    domainViewModel = appInfoDomainRulesViewModel,
                    ipViewModel = appInfoIpRulesViewModel,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.ProxySettings> { args ->
                ProxySettingsScreen(
                    appConfig = appConfig,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    mappingViewModel = proxyAppsMappingViewModel,
                    initialFocusKey = args.focusKey.takeIf { it.isNotBlank() },
                    onWireguardClick = { nav.push(RethinkRoute.WireGuard) },
                    onOpenOrbotApps = {
                        nav.push(RethinkRoute.OrbotAppSelect)
                    },
                    onNavigateToDns = { nav.push(RethinkRoute.DnsDetail()) },
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.OrbotAppSelect> {
                WgIncludeAppsScreen(
                    viewModel = proxyAppsMappingViewModel,
                    proxyId = ProxyManager.ID_ORBOT_BASE,
                    proxyName = ProxyManager.ORBOT_PROXY_NAME,
                    onDismiss = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.TcpProxy> {
                TcpProxyMainScreen(
                    appConfig = appConfig,
                    mappingViewModel = proxyAppsMappingViewModel,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.Welcome> {
                WelcomeScreen(
                    onFinish = {
                        persistentState.firstTimeLaunch = false
                        nav.open(RethinkRoute.Home)
                    }
                )
            }
            entry<RethinkRoute.AppLock> {
                AppLockScreen(
                    persistentState = persistentState,
                    onAuthResult = { result ->
                        onAppLockResult(result)
                        nav.popBackStack()
                    }
                )
            }
            entry<RethinkRoute.DnsDetail> { args ->
                DnsDetailScreen(
                    viewModel = dnsSettingsViewModel,
                    persistentState = persistentState,
                    appDownloadManager = appDownloadManager,
                    initialFocusKey = args.focusKey.takeIf { it.isNotBlank() },
                    onCustomDnsClick = onDnsCustomDnsClick,
                    onRethinkPlusDnsClick = onDnsRethinkPlusDnsClick,
                    onLocalBlocklistConfigureClick = onDnsLocalBlocklistConfigureClick,
                    onBlockFreeDnsClick = { nav.push(RethinkRoute.BlockFreeDns) },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.BlockFreeDns> {
                BlockFreeDnsScreen(blockFreeDnsViewModel, persistentState) { nav.popBackStack() }
            }
            entry<RethinkRoute.AppInfo> { args ->
                AppInfoScreen(
                    uid = args.uid,
                    eventLogger = appInfoEventLogger,
                    ipRulesViewModel = appInfoIpRulesViewModel,
                    domainRulesViewModel = appInfoDomainRulesViewModel,
                    networkLogsViewModel = appInfoNetworkLogsViewModel,
                    onBackClick = { nav.popBackStack() },
                    onAppWiseIpLogsClick = { u, isAsn ->
                        nav.push(RethinkRoute.AppWiseIpLogs(u, isAsn))
                    },
                    onCustomIpRulesClick = { u ->
                        nav.push(
                            RethinkRoute.CustomRules(
                                uid = u,
                                tab = CustomRulesTab.IP.value,
                                mode = CustomRulesMode.APP_SPECIFIC.value
                            )
                        )
                    },
                    onCustomDomainRulesClick = { u ->
                        nav.push(
                            RethinkRoute.CustomRules(
                                uid = u,
                                tab = CustomRulesTab.DOMAIN.value,
                                mode = CustomRulesMode.APP_SPECIFIC.value
                            )
                        )
                    }
                )
            }
            entry<RethinkRoute.DnsList> {
                DnsListScreen(
                    appConfig = appConfig,
                    onConfigureOtherDns = onConfigureOtherDns,
                    onConfigureRethinkBasic = { type ->
                        nav.push(
                            RethinkRoute.ConfigureRethinkBasic(
                                screenTypeOrdinal = ConfigureRethinkScreenType.entries[type].ordinal,
                                remoteName = "",
                                remoteUrl = "",
                                uid = -1
                            )
                        )
                    },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.AppWiseIpLogs> { args ->
                AppWiseIpLogsScreen(
                    uid = args.uid,
                    isAsn = args.isAsn,
                    viewModel = appInfoNetworkLogsViewModel,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.CountryProxy> { args ->
                RpnWinProxyDetailsScreen(
                    countryCode = args.countryCode,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.DomainConnections> { args ->
                val timeCategory = DomainConnectionsViewModel.TimeCategory.fromValue(args.timeCategory)
                    ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
                val type = DomainConnectionsInputType.fromValue(args.typeId)

                DomainConnectionsScreen(
                    viewModel = domainConnectionsViewModel,
                    type = type,
                    flag = args.flag,
                    domain = args.domain,
                    asn = args.asn,
                    ip = args.ip,
                    isBlocked = args.isBlocked,
                    timeCategory = timeCategory,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.DetailedStatistics> { args ->
                val type = SummaryStatisticsType.getType(args.typeId)
                val timeCategory = SummaryStatisticsViewModel.TimeCategory.fromValue(args.timeCategory)

                DetailedStatisticsScreen(
                    type = type,
                    timeCategory = timeCategory ?: SummaryStatisticsViewModel.TimeCategory.TWENTY_FOUR_HOUR,
                    viewModel = detailedStatsViewModel,
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.WireGuardDetail> { args ->
                WgConfigDetailScreen(
                    configId = args.configId,
                    wgType = WgType.fromInt(args.wgType),
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    mappingViewModel = proxyAppsMappingViewModel,
                    onEditConfig = { id, type ->
                        nav.push(RethinkRoute.WireGuardEditor(id, type.value))
                    },
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.WireGuardEditor> { args ->
                WgConfigEditorScreen(
                    configId = args.configId,
                    wgType = WgType.fromInt(args.wgType),
                    persistentState = persistentState,
                    onBackClick = { nav.popBackStack() },
                    onSaveSuccess = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.Configure> {
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
                    onAdvancedClick = onConfigureAdvancedClick,
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    onThemeModeChanged = onThemeModeChanged,
                    onThemeColorChanged = onThemeColorChanged,
                    onSearchDestinationClick = { destination ->
                        when (destination) {
                            SettingsSearchDestination.Apps -> nav.push(RethinkRoute.AppList)
                            is SettingsSearchDestination.Dns -> nav.push(
                                RethinkRoute.DnsDetail(destination.focusKey)
                            )
                            is SettingsSearchDestination.Firewall -> nav.push(
                                RethinkRoute.FirewallSettings(destination.focusKey)
                            )
                            is SettingsSearchDestination.Proxy -> nav.push(
                                RethinkRoute.ProxySettings(destination.focusKey)
                            )
                            is SettingsSearchDestination.Network -> nav.push(
                                RethinkRoute.TunnelSettings(destination.focusKey)
                            )
                            is SettingsSearchDestination.General -> {
                                // Appearance is on the Settings root; other general keys open Misc.
                                val focus = destination.focusKey
                                if (
                                    focus == "general_appearance" ||
                                    focus == "general_theme_mode" ||
                                    focus == "general_theme_color"
                                ) {
                                    // Already on Configure; appearance is at the top.
                                } else {
                                    nav.push(RethinkRoute.MiscSettings(focus))
                                }
                            }
                            SettingsSearchDestination.Logs -> nav.push(RethinkRoute.NetworkLogs)
                            SettingsSearchDestination.AntiCensorship -> nav.push(RethinkRoute.AntiCensorship)
                            SettingsSearchDestination.Advanced -> nav.push(RethinkRoute.AdvancedSettings)
                        }
                    }
                )
            }
            entry<RethinkRoute.About> {
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
                    onGeneralSettingsClick = onGeneralSettingsClick,
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
                    onFlossFundsClick = onFlossFundsClick,
                    onBackClick = { nav.popBackStack() },
                )
            }
            entry<RethinkRoute.ConfigureRethinkBasic> { args ->
                val screenType = ConfigureRethinkScreenType.entries.getOrElse(args.screenTypeOrdinal) {
                    ConfigureRethinkScreenType.REMOTE
                }
                ConfigureRethinkBasicScreen(
                    screenType = screenType,
                    uid = args.uid,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    appDownloadManager = appDownloadManager,
                    rethinkEndpointViewModel = rethinkEndpointViewModel,
                    remoteFileTagViewModel = remoteFileTagViewModel,
                    localFileTagViewModel = localFileTagViewModel,
                    remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                    localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.ConfigureOtherDns> { args ->
                ConfigureOtherDnsScreen(
                    dnsType = DnsScreenType.fromIndex(args.dnsType),
                    appConfig = appConfig,
                    persistentState = persistentState,
                    dohViewModel = dohViewModel,
                    dotViewModel = dotViewModel,
                    dnsProxyViewModel = dnsProxyViewModel,
                    dnsCryptViewModel = dnsCryptViewModel,
                    dnsCryptRelayViewModel = dnsCryptRelayViewModel,
                    oDohViewModel = oDohViewModel,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.UniversalFirewall> {
                UniversalFirewallSettingsScreen(
                    persistentState = persistentState,
                    eventLogger = appInfoEventLogger,
                    connTrackerRepository = connectionTrackerRepository,
                    onNavigateToLogs = onNavigateToLogs,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.Checkout> {
                val currentCheckoutViewModel = checkoutViewModel
                if (currentCheckoutViewModel == null) {
                    Text(text = stringResource(id = R.string.checkout_unavailable_desc))
                } else {
                    val paymentStatus by currentCheckoutViewModel.paymentStatus.collectAsStateWithLifecycle()
                    val workInfoList by currentCheckoutViewModel.paymentWorkInfo

                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    LaunchedEffect(workInfoList) {
                        currentCheckoutViewModel.updatePaymentStatusFromWorkInfo(workInfoList)
                    }

                    CheckoutScreen(
                        paymentStatus = paymentStatus,
                        onStartPayment = { currentCheckoutViewModel.startPayment() },
                        onNavigateToProxy = onNavigateToProxy,
                        onManageAccount = { nav.push(RethinkRoute.RpnAccount) },
                        onBackClick = { nav.popBackStack() }
                    )
                }
            }
            entry<RethinkRoute.RpnAccount> {
                RpnAccountScreen(
                    manageViewModel = managePurchaseViewModel,
                    historyViewModel = purchaseHistoryViewModel,
                    ordersViewModel = serverOrderHistoryViewModel,
                    onBackClick = { nav.popBackStack() },
                )
            }
            entry<RethinkRoute.AppWiseDomainLogs> { args ->
                AppWiseDomainLogsScreen(
                    uid = args.uid,
                    viewModel = appInfoNetworkLogsViewModel,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.WireGuard> {
                WgMainScreen(
                    wgConfigViewModel = wgConfigViewModel,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    eventLogger = appInfoEventLogger,
                    onBackClick = { nav.popBackStack() },
                    onCreateClick = onWgCreateClick,
                    onImportClick = onWgImportClick,
                    onQrScanClick = onWgQrScanClick,
                    onConfigDetailClick = { configId, wgType ->
                        nav.push(RethinkRoute.WireGuardDetail(configId, wgType.value))
                    }
                )
            }
            entry<RethinkRoute.Database> {
                DatabaseScreen(
                    onBackClick = { nav.popBackStack() },
                    appDatabase = appDatabase
                )
            }

            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

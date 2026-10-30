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

import com.bernaferrari.bravedns.ui.compose.firewall.LocalSelectedFirewallAppUid
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.ui.compose.alerts.AlertsScreen
import com.bernaferrari.bravedns.ui.compose.about.AboutScreen
import com.bernaferrari.bravedns.ui.compose.about.AboutUiState
import com.bernaferrari.bravedns.ui.compose.app.AppInfoScreen
import com.bernaferrari.bravedns.ui.compose.configure.ConfigureScreen
import com.bernaferrari.bravedns.ui.compose.configure.SettingsSearchDestination
import com.bernaferrari.bravedns.ui.compose.configure.isAppearanceFocus
import com.bernaferrari.bravedns.ui.compose.events.EventsScreen
import com.bernaferrari.bravedns.ui.compose.firewall.FirewallSettingsScreen
import com.bernaferrari.bravedns.ui.compose.home.HomeScreen

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
import com.bernaferrari.bravedns.ui.compose.theme.CompactEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.wireguard.WgIncludeAppsScreen
import com.bernaferrari.bravedns.ui.compose.logs.AppWiseIpLogsScreen
import com.bernaferrari.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.bernaferrari.bravedns.ui.compose.apps.AppListScreen
import com.bernaferrari.bravedns.ui.compose.firewall.CustomRulesScreen
import com.bernaferrari.bravedns.ui.compose.home.WelcomeScreen
import com.bernaferrari.bravedns.ui.compose.home.HomeScreenUiState
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesMode
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesTab
import com.bernaferrari.bravedns.ui.compose.wireguard.WgConfigDetailScreen
import com.bernaferrari.bravedns.ui.compose.wireguard.WgConfigEditorScreen
import kotlinx.serialization.Serializable
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
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
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
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.viewmodel.WgConfigViewModel
import com.bernaferrari.bravedns.ui.compose.wireguard.WgMainScreen
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY

enum class HomeDestination(
    val root: RethinkRootDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        RethinkRootDestination.Home,
        R.string.txt_home,
        MaterialSymbols.Filled.Home,
        MaterialSymbols.Outlined.Home,
    ),
    STATS(
        RethinkRootDestination.Statistics,
        R.string.title_statistics,
        MaterialSymbols.Filled.InsertChart,
        MaterialSymbols.Outlined.InsertChart,
    ),
    CONFIGURE(
        RethinkRootDestination.Configure,
        R.string.title_settings,
        MaterialSymbols.Filled.Settings,
        MaterialSymbols.Outlined.Settings,
    );

    val route: RethinkRoute get() = root.route
}


@Serializable
enum class CustomRulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): CustomRulesTab = entries.firstOrNull { it.value == value } ?: IP
    }
}

@Serializable
enum class CustomRulesMode(val value: Int) {
    ALL_RULES(0),
    APP_SPECIFIC(1);

    companion object {
        fun fromValue(value: Int): CustomRulesMode = entries.firstOrNull { it.value == value } ?: APP_SPECIFIC
    }
}

/** App-side enum payloads -> the typed [RethinkRoute] graph (which stores primitives). */
fun detailedStatsRoute(
    type: SummaryStatisticsType,
    timeCategory: SummaryStatisticsViewModel.TimeCategory,
): RethinkRoute = RethinkRoute.DetailedStatistics(type.tid, timeCategory.value)

fun customRulesRoute(
    uid: Int = UID_EVERYBODY,
    tab: CustomRulesTab = CustomRulesTab.IP,
    mode: CustomRulesMode = CustomRulesMode.APP_SPECIFIC,
): RethinkRoute = RethinkRoute.CustomRules(uid, tab.value, mode.value)

fun wireGuardDetailRoute(configId: Int, wgType: WgType): RethinkRoute =
    RethinkRoute.WireGuardDetail(configId, wgType.value)

fun wireGuardEditorRoute(configId: Int, wgType: WgType): RethinkRoute =
    RethinkRoute.WireGuardEditor(configId, wgType.value)

fun domainConnectionsRoute(
    type: DomainConnectionsInputType,
    flag: String,
    domain: String,
    asn: String,
    ip: String,
    isBlocked: Boolean,
    timeCategory: DomainConnectionsViewModel.TimeCategory,
): RethinkRoute = RethinkRoute.DomainConnections(
    typeId = type.type,
    flag = flag,
    domain = domain,
    asn = asn,
    ip = ip,
    isBlocked = isBlocked,
    timeCategory = timeCategory.value,
)

fun configureRethinkBasicRoute(
    screenType: ConfigureRethinkScreenType,
    remoteName: String = "",
    remoteUrl: String = "",
    uid: Int = -1,
): RethinkRoute = RethinkRoute.ConfigureRethinkBasic(
    screenTypeOrdinal = screenType.ordinal,
    remoteName = remoteName,
    remoteUrl = remoteUrl,
    uid = uid,
)

private fun RethinkRoute.forAndroid(): RethinkRoute = when (this) {
    RethinkRoute.DnsSettings -> RethinkRoute.DnsDetail()
    RethinkRoute.Blocklists -> RethinkRoute.ConfigureRethinkBasic(
        screenTypeOrdinal = ConfigureRethinkScreenType.REMOTE.ordinal,
    )
    is RethinkRoute.RpnServerDetails -> RethinkRoute.CountryProxy(key)
    else -> this
}

data class HomeConfigureActions(
    val onConfigureAppsClick: () -> Unit,
    val onConfigureDnsClick: () -> Unit,
    val onConfigureFirewallClick: () -> Unit,
    val onFirewallUniversalClick: () -> Unit,
    val onFirewallCustomIpClick: () -> Unit,
    val onFirewallAppWiseIpClick: () -> Unit,
    val onConfigureProxyClick: () -> Unit,
    val onConfigureNetworkClick: () -> Unit,
    val onConfigureOthersClick: () -> Unit,
    val onConfigureLogsClick: () -> Unit,
    val onConfigureAntiCensorshipClick: () -> Unit,
    val onConfigureAdvancedClick: () -> Unit,
    val onNavigateToLogs: (String) -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onNavigateToProxy: () -> Unit,
)

data class HomeAboutActions(
    val onTelegramClick: () -> Unit,
    val onBugReportClick: () -> Unit,
    val onWhatsNewClick: () -> Unit,
    val onAppUpdateClick: () -> Unit,
    val onWebsiteClick: () -> Unit,
    val onGithubClick: () -> Unit,
    val onFaqClick: () -> Unit,
    val onDocsClick: () -> Unit,
    val onPrivacyPolicyClick: () -> Unit,
    val onTermsOfServiceClick: () -> Unit,
    val onLicenseClick: () -> Unit,
    val onTwitterClick: () -> Unit,
    val onEmailClick: () -> Unit,
    val onRedditClick: () -> Unit,
    val onElementClick: () -> Unit,
    val onMastodonClick: () -> Unit,
    val onGeneralSettingsClick: () -> Unit,
    val onAppInfoClick: () -> Unit,
    val onVpnProfileClick: () -> Unit,
    val onNotificationClick: () -> Unit,
    val onStatsClick: () -> Unit,
    val onDbStatsClick: () -> Unit,
    val onFlightRecordClick: () -> Unit,
    val onEventLogsClick: () -> Unit,
    val onTokenClick: () -> Unit,
    val onFossClick: () -> Unit,
    val onFlossFundsClick: () -> Unit,
)

data class HomeDnsActions(
    val onDnsCustomDnsClick: () -> Unit,
    val onDnsRethinkPlusDnsClick: () -> Unit,
    val onDnsLocalBlocklistConfigureClick: () -> Unit,
    val onConfigureOtherDns: (Int) -> Unit,
)

data class HomeWgActions(
    val onWgCreateClick: () -> Unit,
    val onWgImportClick: () -> Unit,
    val onWgQrScanClick: () -> Unit,
)

@Composable
fun HomeScreenRoot(
    homeUiState: HomeScreenUiState,
    onHomeStartStopClick: () -> Unit,
    summaryViewModel: SummaryStatisticsViewModel,
    onOpenDetailedStats: (SummaryStatisticsType) -> Unit,
    startDestination: RethinkRoute,
    isDebug: Boolean,
    configureActions: HomeConfigureActions,
    aboutUiState: AboutUiState,
    aboutActions: HomeAboutActions,
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
    dnsActions: HomeDnsActions,
    homeNavRequest: RethinkRoute?,
    onHomeNavConsumed: () -> Unit,
    onAppLockResult: (AppLockResult) -> Unit = {},
    rethinkEndpointViewModel: RethinkEndpointViewModel,
    remoteFileTagViewModel: RethinkRemoteFileTagViewModel,
    localFileTagViewModel: RethinkLocalFileTagViewModel,
    remoteBlocklistPacksMapViewModel: RemoteBlocklistPacksMapViewModel,
    localBlocklistPacksMapViewModel: LocalBlocklistPacksMapViewModel,
    appInfoViewModel: AppInfoViewModel,
    refreshDatabase: RefreshDatabase,
    connectionTrackerViewModel: ConnectionTrackerViewModel,
    dnsLogViewModel: DnsLogViewModel,
    connectionTrackerRepository: ConnectionTrackerRepository,
    dnsLogRepository: DnsLogRepository,
    dohViewModel: DoHEndpointViewModel,
    dotViewModel: DoTEndpointViewModel,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel,
    oDohViewModel: ODoHEndpointViewModel,
    wgConfigViewModel: WgConfigViewModel,
    checkoutViewModel: CheckoutViewModel?,
    managePurchaseViewModel: ManagePurchaseViewModel,
    purchaseHistoryViewModel: PurchaseHistoryViewModel,
    serverOrderHistoryViewModel: ServerOrderHistoryViewModel,
    blockFreeDnsViewModel: BlockFreeDnsViewModel,
    wgActions: HomeWgActions,
    appDatabase: AppDatabase
) {
    val latestHomeUiState = rememberUpdatedState(homeUiState)
    val navigationItems = HomeDestination.entries.map { destination ->
        RethinkNavigationItem(
            id = destination.root.name,
            label = stringResource(destination.labelRes),
            selectedIcon = destination.selectedIcon,
            unselectedIcon = destination.unselectedIcon,
        )
    }

    // Intents/notifications still arrive as Activity [homeNavRequest]. The nav host
    // pushes when the pending route shares the current tab root; it only replaces
    // the stack on a different-root / cold-start open().
    var pendingRoute by remember { mutableStateOf<RethinkRoute?>(null) }
    LaunchedEffect(homeNavRequest) {
        pendingRoute = homeNavRequest?.forAndroid()
    }

    RethinkAppNavigation(
        destinations = navigationItems,
        startRoute = startDestination,
        pendingRoute = pendingRoute,
        onRouteNavigated = {
            pendingRoute = null
            onHomeNavConsumed()
        },
        modifier = Modifier.fillMaxSize(),
        snackbar = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        entryBuilder = { hostNav ->
            val nav = RethinkNavOps(
                pop = { hostNav.popBackStack() },
                pushRoute = { hostNav.push(it.forAndroid()) },
                openRoute = { hostNav.open(it.forAndroid()) },
                selectRootTab = { hostNav.selectRoot(it) },
                current = hostNav.current,
            )

            val appInfoContent: @Composable (uid: Int, onBack: () -> Unit) -> Unit = { uid, onBack ->
                key(uid) {
                AppInfoScreen(
                    uid = uid,
                    eventLogger = appInfoEventLogger,
                    ipRulesViewModel = appInfoIpRulesViewModel,
                    domainRulesViewModel = appInfoDomainRulesViewModel,
                    networkLogsViewModel = appInfoNetworkLogsViewModel,
                    onBackClick = onBack,
                    onAppWiseIpLogsClick = { u, isAsn ->
                        nav.push(RethinkRoute.AppWiseIpLogs(u, isAsn))
                    },
                    onAppWiseDomainLogsClick = { u ->
                        nav.push(RethinkRoute.AppWiseDomainLogs(u))
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
            }

            entry<RethinkRoute.Home> {
                HomeScreen(
                    uiState = latestHomeUiState.value,
                    onStartStopClick = onHomeStartStopClick,
                    onActivityClick = { nav.selectRoot(RethinkRootDestination.Statistics) },
                    onAppsClick = { nav.push(RethinkRoute.AppList) },
                    onLogsClick = { nav.push(RethinkRoute.NetworkLogs()) },
                    onDnsClick = { nav.push(RethinkRoute.DnsSettings) },
                    onAppClick = { uid -> nav.push(RethinkRoute.AppInfo(uid)) },
                )
            }
            entry<RethinkRoute.Statistics> {
                BackHandler(enabled = nav.current() is RethinkRoute.Statistics) {
                    nav.popBackStack()
                }
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
                    onUniversalFirewallClick = configureActions.onFirewallUniversalClick,
                    onCustomIpDomainClick = configureActions.onFirewallCustomIpClick,
                    onAppWiseIpDomainClick = configureActions.onFirewallAppWiseIpClick,
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
                    connectionTrackerRepository = connectionTrackerRepository,
                    dnsLogRepository = dnsLogRepository,
                    persistentState = persistentState,
                    onAppLogsClick = { nav.push(RethinkRoute.ConsoleLogs) },
                    onBackClick = { nav.popBackStack() }
                )
            }

            entry<RethinkRoute.AppList> {
                AppListWithDetail(
                    appList = { onAppClick ->
                        AppListScreen(
                            viewModel = appInfoViewModel,
                            eventLogger = appInfoEventLogger,
                            refreshDatabase = refreshDatabase,
                            onAppClick = onAppClick,
                            onBackClick = { nav.popBackStack() }
                        )
                    },
                    appInfo = appInfoContent,
                    onOpenApp = { uid -> nav.push(RethinkRoute.AppInfo(uid)) },
                )
            }

            entry<RethinkRoute.CustomRules> { args ->
                CustomRulesScreen(
                    uid = args.uid,
                    initialTab = RethinkRulesTab.fromValue(args.tab),
                    initialMode = RethinkRulesMode.fromValue(args.mode),
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
                    onCustomDnsClick = dnsActions.onDnsCustomDnsClick,
                    onRethinkPlusDnsClick = dnsActions.onDnsRethinkPlusDnsClick,
                    onLocalBlocklistConfigureClick = dnsActions.onDnsLocalBlocklistConfigureClick,
                    onBlockFreeDnsClick = { nav.push(RethinkRoute.BlockFreeDns) },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.DnsSettings> {
                DnsDetailScreen(
                    viewModel = dnsSettingsViewModel,
                    persistentState = persistentState,
                    appDownloadManager = appDownloadManager,
                    onCustomDnsClick = dnsActions.onDnsCustomDnsClick,
                    onRethinkPlusDnsClick = dnsActions.onDnsRethinkPlusDnsClick,
                    onLocalBlocklistConfigureClick = dnsActions.onDnsLocalBlocklistConfigureClick,
                    onBlockFreeDnsClick = { nav.push(RethinkRoute.BlockFreeDns) },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.BlockFreeDns> {
                BlockFreeDnsScreen(blockFreeDnsViewModel, persistentState) { nav.popBackStack() }
            }
            entry<RethinkRoute.AppInfo> { args ->
                appInfoContent(args.uid) { nav.popBackStack() }
            }
            entry<RethinkRoute.DnsList> {
                DnsListScreen(
                    appConfig = appConfig,
                    onConfigureOtherDns = dnsActions.onConfigureOtherDns,
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
            entry<RethinkRoute.RpnServerDetails> { args ->
                RpnWinProxyDetailsScreen(
                    countryCode = args.key,
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
                BackHandler(enabled = nav.current() is RethinkRoute.Configure) {
                    nav.popBackStack()
                }
                ConfigureScreen(
                    isDebug = isDebug,
                    onAppsClick = { nav.push(RethinkRoute.AppList) },
                    // Private-DNS dialog lives on the Activity; consume via same-root push.
                    onDnsClick = configureActions.onConfigureDnsClick,
                    onFirewallClick = { nav.push(RethinkRoute.FirewallSettings()) },
                    onProxyClick = { nav.push(RethinkRoute.ProxySettings()) },
                    onNetworkClick = { nav.push(RethinkRoute.TunnelSettings()) },
                    onOthersClick = { nav.push(RethinkRoute.MiscSettings()) },
                    onLogsClick = { nav.push(RethinkRoute.NetworkLogs()) },
                    onAntiCensorshipClick = { nav.push(RethinkRoute.AntiCensorship) },
                    onAdvancedClick = { nav.push(RethinkRoute.AdvancedSettings) },
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
                                if (!destination.isAppearanceFocus()) {
                                    nav.push(RethinkRoute.MiscSettings(destination.focusKey))
                                }
                            }
                            SettingsSearchDestination.Logs -> nav.push(RethinkRoute.NetworkLogs())
                            SettingsSearchDestination.AntiCensorship -> nav.push(RethinkRoute.AntiCensorship)
                            SettingsSearchDestination.Advanced -> nav.push(RethinkRoute.AdvancedSettings)
                        }
                    }
                )
            }
            entry<RethinkRoute.About> {
                AboutScreen(
                    uiState = aboutUiState,
                    onTelegramClick = aboutActions.onTelegramClick,
                    onBugReportClick = aboutActions.onBugReportClick,
                    onWhatsNewClick = aboutActions.onWhatsNewClick,
                    onAppUpdateClick = aboutActions.onAppUpdateClick,
                    onWebsiteClick = aboutActions.onWebsiteClick,
                    onGithubClick = aboutActions.onGithubClick,
                    onFaqClick = aboutActions.onFaqClick,
                    onDocsClick = aboutActions.onDocsClick,
                    onPrivacyPolicyClick = aboutActions.onPrivacyPolicyClick,
                    onTermsOfServiceClick = aboutActions.onTermsOfServiceClick,
                    onLicenseClick = aboutActions.onLicenseClick,
                    onTwitterClick = aboutActions.onTwitterClick,
                    onEmailClick = aboutActions.onEmailClick,
                    onRedditClick = aboutActions.onRedditClick,
                    onElementClick = aboutActions.onElementClick,
                    onMastodonClick = aboutActions.onMastodonClick,
                    onGeneralSettingsClick = aboutActions.onGeneralSettingsClick,
                    onAppInfoClick = aboutActions.onAppInfoClick,
                    onVpnProfileClick = aboutActions.onVpnProfileClick,
                    onNotificationClick = aboutActions.onNotificationClick,
                    onStatsClick = aboutActions.onStatsClick,
                    onDbStatsClick = aboutActions.onDbStatsClick,
                    onFlightRecordClick = aboutActions.onFlightRecordClick,
                    onEventLogsClick = aboutActions.onEventLogsClick,
                    onTokenClick = aboutActions.onTokenClick,
                    onFossClick = aboutActions.onFossClick,
                    onFlossFundsClick = aboutActions.onFlossFundsClick,
                    onBackClick = { nav.popBackStack() },
                )
            }
            entry<RethinkRoute.Blocklists> {
                ConfigureRethinkBasicScreen(
                    screenType = ConfigureRethinkScreenType.REMOTE,
                    uid = -1,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    appDownloadManager = appDownloadManager,
                    rethinkEndpointViewModel = rethinkEndpointViewModel,
                    remoteFileTagViewModel = remoteFileTagViewModel,
                    localFileTagViewModel = localFileTagViewModel,
                    remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                    localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                    onEditConfiguration = { type, name, url -> nav.push(configureRethinkBasicRoute(type, name, url)) },
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.ConfigureRethinkBasic> { args ->
                val screenType = ConfigureRethinkScreenType.entries.getOrElse(args.screenTypeOrdinal) {
                    ConfigureRethinkScreenType.REMOTE
                }
                ConfigureRethinkBasicScreen(
                    screenType = screenType,
                    remoteName = args.remoteName,
                    remoteUrl = args.remoteUrl,
                    uid = args.uid,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    appDownloadManager = appDownloadManager,
                    rethinkEndpointViewModel = rethinkEndpointViewModel,
                    remoteFileTagViewModel = remoteFileTagViewModel,
                    localFileTagViewModel = localFileTagViewModel,
                    remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                    localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                    onEditConfiguration = { type, name, url -> nav.push(configureRethinkBasicRoute(type, name, url)) },
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
                    onNavigateToLogs = configureActions.onNavigateToLogs,
                    onOpenAccessibilitySettings = configureActions.onOpenAccessibilitySettings,
                    onBackClick = { nav.popBackStack() }
                )
            }
            entry<RethinkRoute.Checkout> {
                val currentCheckoutViewModel = checkoutViewModel
                if (currentCheckoutViewModel == null) {
                    // Builds without a store have no checkout; say so on a real screen with a way back.
                    RethinkTopBarLazyColumnScreen(
                        title = stringResource(R.string.app_name),
                        onBackClick = { nav.popBackStack() },
                    ) {
                        item { CompactEmptyState(message = stringResource(R.string.checkout_unavailable_desc)) }
                    }
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
                        onNavigateToProxy = configureActions.onNavigateToProxy,
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
                    onViewPlans = viewPlansAction(checkoutViewModel != null, nav),
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
                    onCreateClick = wgActions.onWgCreateClick,
                    onImportClick = wgActions.onWgImportClick,
                    onQrScanClick = wgActions.onWgQrScanClick,
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
}

/** Only offer plans where this channel can actually sell them. */
private fun viewPlansAction(canCheckout: Boolean, nav: RethinkNavOps): (() -> Unit)? =
    if (canCheckout) {
        { nav.push(RethinkRoute.Checkout) }
    } else {
        null
    }

/**
 * Wide windows: the app list beside the selected app's rules (list–detail). Phones and 7-inch
 * tablets push the rules as their own screen.
 */
@Composable
private fun AppListWithDetail(
    appList: @Composable (onAppClick: (Int) -> Unit) -> Unit,
    appInfo: @Composable (uid: Int, onBack: () -> Unit) -> Unit,
    onOpenApp: (Int) -> Unit,
) {
    var selectedUid by rememberSaveable { mutableStateOf<Int?>(null) }
    RethinkAdaptiveWidth(Modifier.fillMaxSize()) { widthClass ->
        val twoPane = widthClass.isExpanded
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalSelectedFirewallAppUid provides selectedUid.takeIf { twoPane }) {
                    appList { uid -> if (twoPane) selectedUid = uid else onOpenApp(uid) }
                }
            }
            if (twoPane) {
                Box(Modifier.weight(1.15f).fillMaxHeight()) {
                    val uid = selectedUid
                    if (uid == null) {
                        RethinkEmptyState(
                            icon = MaterialSymbols.Filled.Apps,
                            title = stringResource(R.string.app_pane_empty_title),
                            message = stringResource(R.string.app_pane_empty_message),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        appInfo(uid) { selectedUid = null }
                    }
                }
            }
        }
    }
}

/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Full UI entry point in commonMain for wasmJs/web demo (and future non-Android hosts).
 * No VPN/DNS/Room at runtime — screens show realistic shells with demo/static data.
 */
package com.celzero.bravedns.ui.compose

import com.celzero.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.celzero.bravedns.di.RethinkWebDemoDependencies
import com.celzero.bravedns.ui.compose.about.RethinkAboutScreen
import com.celzero.bravedns.ui.compose.about.RethinkAboutUiState
import com.celzero.bravedns.ui.compose.about.RethinkWebDemoMetadata
import com.celzero.bravedns.ui.compose.apps.RethinkWebDemoAppIcon
import com.celzero.bravedns.ui.compose.configure.RethinkConfigureScreen
import com.celzero.bravedns.ui.compose.configure.RethinkConfigureSearchEntry
import com.celzero.bravedns.ui.compose.firewall.RethinkFirewallApp
import com.celzero.bravedns.ui.compose.home.RethinkHomeScreen
import com.celzero.bravedns.ui.compose.home.RethinkHomeUiState
import com.celzero.bravedns.ui.compose.home.RethinkPauseScreen
import com.celzero.bravedns.ui.compose.home.RethinkPauseState
import com.celzero.bravedns.ui.compose.home.RethinkPauseStrings
import com.celzero.bravedns.ui.compose.home.RethinkWelcomeContent
import com.celzero.bravedns.ui.compose.home.RethinkWelcomeFeature
import com.celzero.bravedns.ui.compose.home.RethinkWelcomeScreen
import com.celzero.bravedns.ui.compose.navigation.RethinkAdaptiveNavigationScaffold
import com.celzero.bravedns.ui.compose.navigation.RethinkRootDestination
import com.celzero.bravedns.ui.compose.settings.RethinkAppearanceMode
import com.celzero.bravedns.ui.compose.settings.RethinkAppearanceSettingsCard
import com.celzero.bravedns.ui.compose.statistics.RethinkStatisticsWindow
import com.celzero.bravedns.ui.compose.statistics.RethinkSummaryStatisticsRow
import com.celzero.bravedns.ui.compose.statistics.RethinkSummaryStatisticsScreen
import com.celzero.bravedns.ui.compose.statistics.RethinkSummaryStatisticsSection
import com.celzero.bravedns.ui.compose.statistics.RethinkSummaryStatisticsStrings
import com.celzero.bravedns.ui.compose.statistics.RethinkUsageOverview
import com.celzero.bravedns.ui.compose.theme.RethinkSharedTheme
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

enum class DemoOverlay {
    None,
    Welcome,
    Pause,
}

/**
 * The demo is a real navigation exercise, not a collection of visibility flags. A small typed
 * back stack keeps its nested screens honest and prevents a new screen from accidentally
 * remaining visible after a tab switch.
 */
internal sealed interface DemoDetail {
    data object Logs : DemoDetail
    data object ConsoleLogs : DemoDetail
    data object FirewallSettings : DemoDetail
    data object UniversalFirewall : DemoDetail
    data object CustomRules : DemoDetail
    data object FirewallApps : DemoDetail
    data class AppInfo(val app: RethinkFirewallApp) : DemoDetail
    data object MiscSettings : DemoDetail
    data object Database : DemoDetail
    data object RpnSettings : DemoDetail
    data object RpnCountries : DemoDetail
    data class RpnServerDetails(val key: String) : DemoDetail
    data object RpnAccount : DemoDetail
    data object BlockFreeDns : DemoDetail
    data object DnsList : DemoDetail
    data object TunnelSettings : DemoDetail
    data object ProxySettings : DemoDetail
    data object DnsSettings : DemoDetail
    data object Wireguard : DemoDetail
    data object OtherDns : DemoDetail
    data object Blocklists : DemoDetail
    data object PingTest : DemoDetail
    data object AntiCensorship : DemoDetail
    data object EventLogs : DemoDetail
}

/**
 * Portable RethinkDNS UI shell. Hosts (Android app, wasmJs) pass nothing — all copy is
 * inline demo strings so this compiles without resources on web.
 */
@Composable
fun RethinkDemoApp(
    darkTheme: Boolean = false,
    demoDependencies: RethinkWebDemoDependencies =
        RethinkWebDemoDependencies(appMetadata = RethinkWebDemoMetadata),
) {
    var appearanceMode by remember { mutableStateOf(RethinkAppearanceMode.System) }
    var appearancePresetId by remember { mutableStateOf(2) }
    val appearanceSeed =
        demoAppearancePresets.firstOrNull { it.id == appearancePresetId }?.color
            ?: Color(0xFF804136)
    RethinkSharedTheme(
        darkTheme = when (appearanceMode) {
            RethinkAppearanceMode.System -> darkTheme
            RethinkAppearanceMode.Light -> false
            RethinkAppearanceMode.Dark -> true
        },
        seedColor = appearanceSeed,
    ) {
        var destination by remember { mutableStateOf(RethinkRootDestination.Home) }
        var overlay by remember { mutableStateOf(DemoOverlay.Welcome) }
        var vpnOn by remember { mutableStateOf(false) }
        var statisticsWindow by remember { mutableStateOf(RethinkStatisticsWindow.TwentyFourHours) }
        var configureSearchOpen by remember { mutableStateOf(false) }
        var configureQuery by remember { mutableStateOf("") }
        val detailBackStack = remember { mutableStateListOf<DemoDetail>() }

        fun openDetail(detail: DemoDetail) {
            detailBackStack += detail
        }

        fun navigateBack() {
            if (detailBackStack.isNotEmpty()) detailBackStack.removeAt(detailBackStack.lastIndex)
        }

        fun openConfigureDetail(detail: DemoDetail) {
            destination = RethinkRootDestination.Configure
            detailBackStack.clear()
            openDetail(detail)
        }

        when (overlay) {
            DemoOverlay.Welcome -> RethinkWelcomeScreen(
                content = RethinkWelcomeContent(
                    title = "Welcome to RethinkDNS",
                    description = "Open-source privacy tools, designed to keep your connection in your control.",
                    heroIcon = MaterialSymbols.Filled.Shield,
                    features = listOf(
                        RethinkWelcomeFeature("App firewall", MaterialSymbols.Filled.Security),
                        RethinkWelcomeFeature("Private DNS", MaterialSymbols.Filled.Dns),
                        RethinkWelcomeFeature("Secure routing", MaterialSymbols.Filled.VpnKey),
                    ),
                ),
                ctaLabel = "Get started",
                onFinish = { overlay = DemoOverlay.None },
            )
            DemoOverlay.Pause -> RethinkPauseScreen(
                state = RethinkPauseState(
                    timerText = "00:09:42",
                    timerDescription = "3 apps are paused",
                ),
                strings = RethinkPauseStrings(title = "Paused", pauseLabel = "Paused", resume = "Resume protection"),
                onDecrease = {},
                onIncrease = {},
                onResume = { overlay = DemoOverlay.None },
            )
            DemoOverlay.None -> RethinkAdaptiveNavigationScaffold(
                modifier = Modifier.fillMaxSize(),
                destinations = demoNavigationItems,
                selectedId = destination.name,
                onDestinationSelected = { id ->
                    destination = RethinkRootDestination.valueOf(id)
                    detailBackStack.clear()
                },
            ) { padding ->
                when (destination) {
                    RethinkRootDestination.Home -> RethinkHomeScreen(
                        modifier = Modifier.padding(padding),
                            uiState = RethinkHomeUiState(
                                isVpnActive = vpnOn,
                                dnsLatency = "24 ms",
                                dnsConnectedName = "Cloudflare",
                                firewallUniversalRules = 12,
                                firewallIpRules = 3,
                                firewallDomainRules = 8,
                                proxyStatus = if (vpnOn) "Active" else "Inactive",
                                networkLogsCount = 4_320,
                                dnsLogsCount = 1_284,
                                appsAllowed = 115,
                                appsBlocked = 5,
                                appsTotal = 120,
                                appsBypassed = 2,
                                protectionStatus = if (vpnOn) "Protected" else "Not active",
                            ),
                            strings = demoHomeStrings,
                            onStartStopClick = { vpnOn = !vpnOn },
                            onDnsClick = { openConfigureDetail(DemoDetail.DnsList) },
                            onFirewallClick = { openConfigureDetail(DemoDetail.FirewallSettings) },
                            onProxyClick = { openConfigureDetail(DemoDetail.ProxySettings) },
                            onLogsClick = { openConfigureDetail(DemoDetail.Logs) },
                            onAppsClick = { openConfigureDetail(DemoDetail.FirewallApps) },
                    )
                    RethinkRootDestination.Statistics -> RethinkSummaryStatisticsScreen(
                        modifier = Modifier.padding(padding),
                        overview = RethinkUsageOverview(download = "1.8 GB", upload = "248 MB", connections = "4,320"),
                        selectedWindow = statisticsWindow,
                        onWindowSelected = { statisticsWindow = it },
                        strings = RethinkSummaryStatisticsStrings(
                            title = "Statistics",
                            overall = "Overall",
                            download = "Download",
                            upload = "Upload",
                            connections = "Connections",
                            oneHour = "1h",
                            twentyFourHours = "24h",
                            sevenDays = "7d",
                            noLogs = "No activity in this time range",
                            seeMore = "See more",
                            seeLess = "See less",
                        ),
                        sections = listOf(
                            RethinkSummaryStatisticsSection(
                                id = "connected-apps",
                                title = "Most connected apps",
                                accentColor = MaterialTheme.colorScheme.primary,
                                canSeeMore = true,
                                rows = listOf(
                                    RethinkSummaryStatisticsRow(
                                        "browser", "Browser", "1,240 connections · 684 MB", "684 MB",
                                        leadingContent = {
                                            RethinkWebDemoAppIcon(
                                                "com.demo.browser",
                                                "Browser",
                                                Modifier.size(SharedDimensions.iconContainerSm),
                                            )
                                        },
                                    ),
                                    RethinkSummaryStatisticsRow(
                                        "messenger", "Messenger", "684 connections · 248 MB", "248 MB",
                                        leadingContent = {
                                            RethinkWebDemoAppIcon(
                                                "com.demo.messenger",
                                                "Messenger",
                                                Modifier.size(SharedDimensions.iconContainerSm),
                                            )
                                        },
                                    ),
                                    RethinkSummaryStatisticsRow(
                                        "mail", "Mail", "312 connections · 96 MB", "96 MB",
                                        leadingContent = {
                                            RethinkWebDemoAppIcon(
                                                "com.google.android.gm",
                                                "Mail",
                                                Modifier.size(SharedDimensions.iconContainerSm),
                                            )
                                        },
                                    ),
                                ),
                            ),
                            RethinkSummaryStatisticsSection(
                                id = "blocked-domains",
                                title = "Most blocked domains",
                                accentColor = MaterialTheme.colorScheme.error,
                                canSeeMore = true,
                                rows = listOf(
                                    RethinkSummaryStatisticsRow("tracker", "tracker.example", "326 blocked connections", "326"),
                                    RethinkSummaryStatisticsRow("ads", "ads.example", "214 blocked connections", "214"),
                                    RethinkSummaryStatisticsRow("metrics", "metrics.example", "88 blocked connections", "88"),
                                ),
                            ),
                            RethinkSummaryStatisticsSection(
                                id = "countries",
                                title = "Most contacted countries",
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                rows = listOf(
                                    demoCountryStatistic("us", "United States", "🇺🇸", "1,240", "1,240 connections · 684 MB", listOf("Browser" to "612", "Messenger" to "384", "Mail" to "244")),
                                    demoCountryStatistic("de", "Germany", "🇩🇪", "866", "866 connections · 406 MB", listOf("Browser" to "411", "RethinkDNS" to "248", "Mail" to "207")),
                                    demoCountryStatistic("nl", "Netherlands", "🇳🇱", "508", "508 connections · 198 MB", listOf("Browser" to "278", "Messenger" to "146", "Cloud sync" to "84")),
                                    demoCountryStatistic("sg", "Singapore", "🇸🇬", "276", "276 connections · 124 MB", listOf("Messenger" to "158", "Browser" to "72", "Mail" to "46")),
                                    demoCountryStatistic("br", "Brazil", "🇧🇷", "204", "204 connections · 86 MB", listOf("Browser" to "114", "Social" to "56", "Mail" to "34")),
                                    demoCountryStatistic("ca", "Canada", "🇨🇦", "128", "128 connections · 64 MB", listOf("Browser" to "69", "Cloud sync" to "34", "Messenger" to "25")),
                                    demoCountryStatistic("gb", "United Kingdom", "🇬🇧", "96", "96 connections · 42 MB", listOf("Media" to "47", "Browser" to "31", "Mail" to "18")),
                                    demoCountryStatistic("jp", "Japan", "🇯🇵", "72", "72 connections · 31 MB", listOf("Browser" to "39", "Messenger" to "21", "Cloud sync" to "12")),
                                    demoCountryStatistic("fr", "France", "🇫🇷", "58", "58 connections · 24 MB", listOf("Browser" to "29", "Mail" to "18", "RethinkDNS" to "11")),
                                    demoCountryStatistic("au", "Australia", "🇦🇺", "41", "41 connections · 18 MB", listOf("Cloud sync" to "19", "Browser" to "14", "Messenger" to "8")),
                                ),
                            ),
                        ),
                    )
                    RethinkRootDestination.Configure -> when (val detail = detailBackStack.lastOrNull()) {
                        DemoDetail.Logs -> DemoLogsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.ConsoleLogs -> DemoConsoleLogsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.Database -> DemoDatabaseScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.MiscSettings -> DemoMiscSettingsScreen(
                            modifier = Modifier.padding(padding),
                            appearanceMode = appearanceMode,
                            appearancePresetId = appearancePresetId,
                            onAppearanceModeChange = { appearanceMode = it },
                            onAppearancePresetChange = { appearancePresetId = it },
                            onBack = ::navigateBack,
                        )
                        DemoDetail.RpnAccount -> DemoRpnAccountScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.RpnCountries -> DemoRpnCountriesScreen(
                            modifier = Modifier.padding(padding),
                            onBack = ::navigateBack,
                            onSettings = { openDetail(DemoDetail.RpnSettings) },
                            onServerDetails = { key -> openDetail(DemoDetail.RpnServerDetails(key)) },
                        )
                        is DemoDetail.RpnServerDetails -> DemoRpnServerDetailsScreen(
                            modifier = Modifier.padding(padding),
                            key = detail.key,
                            onBack = ::navigateBack,
                        )
                        is DemoDetail.AppInfo -> DemoAppInfoScreen(
                            modifier = Modifier.padding(padding),
                            app = detail.app,
                            onBack = ::navigateBack,
                        )
                        DemoDetail.FirewallApps -> DemoFirewallAppListScreen(
                            modifier = Modifier.padding(padding),
                            onBack = ::navigateBack,
                            onAppInfo = { openDetail(DemoDetail.AppInfo(it)) },
                        )
                        DemoDetail.CustomRules -> DemoCustomRulesScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.UniversalFirewall -> DemoUniversalFirewallScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.Blocklists -> DemoBlocklistEditorScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.OtherDns -> DemoOtherDnsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.Wireguard -> DemoWireguardScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.TunnelSettings -> DemoTunnelSettingsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.ProxySettings -> DemoProxySettingsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.FirewallSettings -> DemoFirewallSettingsScreen(
                            modifier = Modifier.padding(padding),
                            onBack = ::navigateBack,
                            onCustomRules = { openDetail(DemoDetail.CustomRules) },
                            onUniversalFirewall = { openDetail(DemoDetail.UniversalFirewall) },
                        )
                        DemoDetail.RpnSettings -> DemoRpnSettingsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.DnsList -> DemoDnsListScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.DnsSettings -> DemoDnsSettingsScreen(
                            modifier = Modifier.padding(padding),
                            onBack = ::navigateBack,
                            onCustomDns = { openDetail(DemoDetail.OtherDns) },
                        )
                        DemoDetail.PingTest -> DemoPingTestScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.AntiCensorship -> DemoAntiCensorshipScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.EventLogs -> DemoEventLogsScreen(Modifier.padding(padding), ::navigateBack)
                        DemoDetail.BlockFreeDns -> DemoBlockFreeDnsScreen(Modifier.padding(padding), ::navigateBack)
                        null -> {
                            val sections = demoConfigureSections(::openDetail)
                            RethinkConfigureScreen(
                                sections = sections,
                                searchEntries = sections.flatMap { section ->
                                    section.entries.map { entry ->
                                        RethinkConfigureSearchEntry(entry, "${section.title} > ${entry.title}")
                                    }
                                },
                                strings = demoConfigureStrings,
                                searchOpen = configureSearchOpen,
                                query = configureQuery,
                                onSearchOpenChange = { configureSearchOpen = it },
                                onQueryChange = { configureQuery = it },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                    RethinkRootDestination.About -> RethinkAboutScreen(
                        uiState = RethinkAboutUiState(
                            versionName = demoDependencies.appMetadata.versionName,
                            installSource = demoDependencies.appMetadata.installSource,
                            buildNumber = demoDependencies.appMetadata.buildNumber,
                            isDebug = demoDependencies.appMetadata.isDebug,
                        ),
                        strings = demoAboutStrings,
                        modifier = Modifier.padding(padding),
                        appearanceContent = {
                            RethinkAppearanceSettingsCard(
                                selectedMode = appearanceMode,
                                selectedPresetId = appearancePresetId,
                                presets = demoAppearancePresets,
                                strings = demoAppearanceStrings,
                                dynamicColor = Color(0xFF7C8BFF),
                                dynamicSupported = false,
                                onModeSelected = { appearanceMode = it },
                                onPresetSelected = { appearancePresetId = it.id },
                            )
                        },
                    )
                }
            }
        }
    }
}

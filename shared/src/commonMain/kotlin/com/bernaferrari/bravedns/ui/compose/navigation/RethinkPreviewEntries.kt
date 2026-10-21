/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bernaferrari.bravedns.di.RethinkWebDemoDependencies
import com.bernaferrari.bravedns.ui.compose.DemoAntiCensorshipScreen
import com.bernaferrari.bravedns.ui.compose.DemoAppInfoScreen
import com.bernaferrari.bravedns.ui.compose.DemoBlockFreeDnsScreen
import com.bernaferrari.bravedns.ui.compose.DemoBlocklistEditorScreen
import com.bernaferrari.bravedns.ui.compose.DemoConsoleLogsScreen
import com.bernaferrari.bravedns.ui.compose.DemoCustomRulesScreen
import com.bernaferrari.bravedns.ui.compose.DemoDatabaseScreen
import com.bernaferrari.bravedns.ui.compose.DemoDnsListScreen
import com.bernaferrari.bravedns.ui.compose.DemoDnsSettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoEventLogsScreen
import com.bernaferrari.bravedns.ui.compose.DemoFirewallAppListScreen
import com.bernaferrari.bravedns.ui.compose.DemoFirewallSettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoLogsScreen
import com.bernaferrari.bravedns.ui.compose.DemoMiscSettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoOtherDnsScreen
import com.bernaferrari.bravedns.ui.compose.DemoPingTestScreen
import com.bernaferrari.bravedns.ui.compose.DemoProxySettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoRpnAccountScreen
import com.bernaferrari.bravedns.ui.compose.DemoRpnCountriesScreen
import com.bernaferrari.bravedns.ui.compose.DemoRpnServerDetailsScreen
import com.bernaferrari.bravedns.ui.compose.DemoRpnSettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoTunnelSettingsScreen
import com.bernaferrari.bravedns.ui.compose.DemoUniversalFirewallScreen
import com.bernaferrari.bravedns.ui.compose.DemoWireguardScreen
import com.bernaferrari.bravedns.ui.compose.about.RethinkAboutScreen
import com.bernaferrari.bravedns.ui.compose.about.RethinkAboutUiState
import com.bernaferrari.bravedns.ui.compose.apps.RethinkWebDemoAppIcon
import com.bernaferrari.bravedns.ui.compose.configure.RethinkConfigureScreen
import com.bernaferrari.bravedns.ui.compose.configure.RethinkConfigureSearchEntry
import com.bernaferrari.bravedns.ui.compose.demoAboutStrings
import com.bernaferrari.bravedns.ui.compose.demoConfigureSections
import com.bernaferrari.bravedns.ui.compose.demoConfigureStrings
import com.bernaferrari.bravedns.ui.compose.demoCountryStatistic
import com.bernaferrari.bravedns.ui.compose.demoFirewallApps
import com.bernaferrari.bravedns.ui.compose.demoHomeStrings
import com.bernaferrari.bravedns.ui.compose.home.RethinkHomeScreen
import com.bernaferrari.bravedns.ui.compose.home.RethinkHomeUiState
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkStatisticsWindow
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkSummaryStatisticsRow
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkSummaryStatisticsScreen
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkSummaryStatisticsSection
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkSummaryStatisticsStrings
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkUsageOverview
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/**
 * Preview/fixture entry bodies for wasm (and any host without product backends).
 * Same [RethinkRoute] keys as Android — only the data wiring differs.
 */
fun EntryProviderScope<NavKey>.rethinkPreviewEntries(
    nav: RethinkNavOps,
    demoDependencies: RethinkWebDemoDependencies,
    vpnOn: MutableState<Boolean>,
    statisticsWindow: MutableState<RethinkStatisticsWindow>,
    configureSearchOpen: MutableState<Boolean>,
    configureQuery: MutableState<String>,
    appearanceContent: @Composable () -> Unit,
) {
    entry<RethinkRoute.Home> {
        val active = vpnOn.value
        run {
            RethinkHomeScreen(
                uiState = RethinkHomeUiState(
                    isVpnActive = active,
                    networkLogsCount = 4_320,
                    dnsLogsCount = 1_284,
                    protectionStatus = if (active) "Protected" else "Not active",
                ),
                strings = demoHomeStrings,
                onStartStopClick = { vpnOn.value = !vpnOn.value },
                showAtmosphere = false,
            )
        }
    }
    entry<RethinkRoute.Statistics> {
        run {
            RethinkSummaryStatisticsScreen(
                overview = RethinkUsageOverview(download = "1.8 GB", upload = "248 MB", connections = "4,320"),
                selectedWindow = statisticsWindow.value,
                onWindowSelected = { statisticsWindow.value = it },
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
                                "browser", "Browser", "1,240 connections", "684 MB",
                                share = 1f,
                                leadingContent = {
                                    RethinkWebDemoAppIcon(
                                        "com.demo.browser",
                                        "Browser",
                                        Modifier.size(SharedDimensions.iconContainerSm),
                                    )
                                },
                            ),
                            RethinkSummaryStatisticsRow(
                                "messenger", "Messenger", "684 connections", "248 MB",
                                share = 248f / 684f,
                                leadingContent = {
                                    RethinkWebDemoAppIcon(
                                        "com.demo.messenger",
                                        "Messenger",
                                        Modifier.size(SharedDimensions.iconContainerSm),
                                    )
                                },
                            ),
                            RethinkSummaryStatisticsRow(
                                "mail", "Mail", "312 connections", "96 MB",
                                share = 96f / 684f,
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
                            RethinkSummaryStatisticsRow("tracker", "tracker.example", "Browser · News", "326", share = 1f),
                            RethinkSummaryStatisticsRow("ads", "ads.example", "Browser · Social", "214", share = 214f / 326f),
                            RethinkSummaryStatisticsRow("metrics", "metrics.example", "Mail", "88", share = 88f / 326f),
                        ),
                    ),
                    RethinkSummaryStatisticsSection(
                        id = "countries",
                        title = "Most contacted countries",
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        rows = listOf(
                            demoCountryStatistic("us", "United States", "🇺🇸", "1,240", "512 MB", listOf("Browser" to "612", "Messenger" to "384", "Mail" to "244")),
                            demoCountryStatistic("de", "Germany", "🇩🇪", "866", "406 MB", listOf("Browser" to "411", "RethinkDNS" to "248", "Mail" to "207")),
                            demoCountryStatistic("nl", "Netherlands", "🇳🇱", "508", "198 MB", listOf("Browser" to "278", "Messenger" to "146", "Cloud sync" to "84")),
                            demoCountryStatistic("sg", "Singapore", "🇸🇬", "276", "124 MB", listOf("Messenger" to "158", "Browser" to "72", "Mail" to "46")),
                            demoCountryStatistic("br", "Brazil", "🇧🇷", "204", "86 MB", listOf("Browser" to "114", "Social" to "56", "Mail" to "34")),
                            demoCountryStatistic("ca", "Canada", "🇨🇦", "128", "64 MB", listOf("Browser" to "69", "Cloud sync" to "34", "Messenger" to "25")),
                            demoCountryStatistic("gb", "United Kingdom", "🇬🇧", "96", "42 MB", listOf("Media" to "47", "Browser" to "31", "Mail" to "18")),
                            demoCountryStatistic("jp", "Japan", "🇯🇵", "72", "31 MB", listOf("Browser" to "39", "Messenger" to "21", "Cloud sync" to "12")),
                            demoCountryStatistic("fr", "France", "🇫🇷", "58", "24 MB", listOf("Browser" to "29", "Mail" to "18", "RethinkDNS" to "11")),
                            demoCountryStatistic("au", "Australia", "🇦🇺", "41", "18 MB", listOf("Cloud sync" to "19", "Browser" to "14", "Messenger" to "8")),
                        ),
                    ),
                ),
            )
        }
    }
    entry<RethinkRoute.Configure> {
        run {
            val sections = demoConfigureSections(nav::push)
            RethinkConfigureScreen(
                sections = sections,
                searchEntries = sections.flatMap { section ->
                    section.entries.map { entry ->
                        RethinkConfigureSearchEntry(entry, "${section.title} > ${entry.title}")
                    }
                },
                strings = demoConfigureStrings,
                searchOpen = configureSearchOpen.value,
                query = configureQuery.value,
                onSearchOpenChange = { configureSearchOpen.value = it },
                onQueryChange = { configureQuery.value = it },
                appearanceContent = appearanceContent,
            )
        }
    }
    entry<RethinkRoute.NetworkLogs> {
        run { DemoLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ConsoleLogs> {
        run { DemoConsoleLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Database> {
        run { DemoDatabaseScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.MiscSettings> {
        run {
            DemoMiscSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onOpenAbout = { nav.push(RethinkRoute.About) },
                onBack = nav::popBackStack,
            )
        }
    }
    entry<RethinkRoute.About> {
        run {
            RethinkAboutScreen(
                uiState = RethinkAboutUiState(
                    versionName = demoDependencies.appMetadata.versionName,
                    installSource = demoDependencies.appMetadata.installSource,
                    buildNumber = demoDependencies.appMetadata.buildNumber,
                    isDebug = demoDependencies.appMetadata.isDebug,
                ),
                strings = demoAboutStrings,
                onBackClick = nav::popBackStack,
            )
        }
    }
    entry<RethinkRoute.RpnAccount> {
        run { DemoRpnAccountScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.RpnCountries> {
        run {
            DemoRpnCountriesScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onSettings = { nav.push(RethinkRoute.RpnSettings) },
                onServerDetails = { key -> nav.push(RethinkRoute.RpnServerDetails(key)) },
            )
        }
    }
    entry<RethinkRoute.RpnServerDetails> { key ->
        run {
            DemoRpnServerDetailsScreen(Modifier.fillMaxSize(), key.key, nav::popBackStack)
        }
    }
    entry<RethinkRoute.RpnSettings> {
        run { DemoRpnSettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.AppList> {
        run {
            DemoFirewallAppListScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onAppInfo = { app -> nav.push(RethinkRoute.AppInfo(app.uid)) },
            )
        }
    }
    entry<RethinkRoute.AppInfo> { key ->
        val app = demoFirewallApps.firstOrNull { it.uid == key.uid }
        run {
            DemoAppInfoScreen(Modifier.fillMaxSize(), app, nav::popBackStack)
        }
    }
    entry<RethinkRoute.CustomRules> {
        run { DemoCustomRulesScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.UniversalFirewall> {
        run { DemoUniversalFirewallScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Blocklists> {
        run { DemoBlocklistEditorScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ConfigureOtherDns> {
        run { DemoOtherDnsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.WireGuard> {
        run { DemoWireguardScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.TunnelSettings> {
        run { DemoTunnelSettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ProxySettings> {
        run { DemoProxySettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.FirewallSettings> {
        run {
            DemoFirewallSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onCustomRules = { nav.push(RethinkRoute.CustomRules()) },
                onUniversalFirewall = { nav.push(RethinkRoute.UniversalFirewall) },
            )
        }
    }
    entry<RethinkRoute.DnsList> {
        run { DemoDnsListScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.DnsSettings> {
        run {
            DemoDnsSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onCustomDns = { nav.push(RethinkRoute.ConfigureOtherDns()) },
            )
        }
    }
    entry<RethinkRoute.PingTest> {
        run { DemoPingTestScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.AntiCensorship> {
        run { DemoAntiCensorshipScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Events> {
        run { DemoEventLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.BlockFreeDns> {
        run { DemoBlockFreeDnsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
}

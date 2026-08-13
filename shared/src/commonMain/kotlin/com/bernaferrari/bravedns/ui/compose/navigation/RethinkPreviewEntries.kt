/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import com.bernaferrari.bravedns.ui.compose.settings.RethinkAppearanceMode
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
    appearanceMode: RethinkAppearanceMode,
    appearancePresetId: Int,
    onAppearanceModeChange: (RethinkAppearanceMode) -> Unit,
    onAppearancePresetChange: (Int) -> Unit,
    vpnOn: Boolean,
    onVpnToggle: () -> Unit,
    statisticsWindow: RethinkStatisticsWindow,
    onStatisticsWindowChange: (RethinkStatisticsWindow) -> Unit,
    configureSearchOpen: Boolean,
    onConfigureSearchOpenChange: (Boolean) -> Unit,
    configureQuery: String,
    onConfigureQueryChange: (String) -> Unit,
) {
    val homeUiState = RethinkHomeUiState(
        isVpnActive = vpnOn,
        networkLogsCount = 4_320,
        dnsLogsCount = 1_284,
        protectionStatus = if (vpnOn) "Protected" else "Not active",
    )

    entry<RethinkRoute.Home> {
        RethinkCenteredScreen {
            RethinkHomeScreen(
                uiState = homeUiState,
                strings = demoHomeStrings,
                onStartStopClick = onVpnToggle,
                showAtmosphere = false,
            )
        }
    }
    entry<RethinkRoute.Statistics> {
        RethinkCenteredScreen {
            RethinkSummaryStatisticsScreen(
                overview = RethinkUsageOverview(download = "1.8 GB", upload = "248 MB", connections = "4,320"),
                selectedWindow = statisticsWindow,
                onWindowSelected = onStatisticsWindowChange,
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
        }
    }
    entry<RethinkRoute.Configure> {
        RethinkCenteredScreen {
            val sections = demoConfigureSections(nav::push)
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
                onSearchOpenChange = onConfigureSearchOpenChange,
                onQueryChange = onConfigureQueryChange,
            )
        }
    }
    entry<RethinkRoute.NetworkLogs> {
        RethinkCenteredScreen { DemoLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ConsoleLogs> {
        RethinkCenteredScreen { DemoConsoleLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Database> {
        RethinkCenteredScreen { DemoDatabaseScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.MiscSettings> {
        RethinkCenteredScreen {
            DemoMiscSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                appearanceMode = appearanceMode,
                appearancePresetId = appearancePresetId,
                onAppearanceModeChange = onAppearanceModeChange,
                onAppearancePresetChange = onAppearancePresetChange,
                onOpenAbout = { nav.push(RethinkRoute.About) },
                onBack = nav::popBackStack,
            )
        }
    }
    entry<RethinkRoute.About> {
        RethinkCenteredScreen {
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
        RethinkCenteredScreen { DemoRpnAccountScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.RpnCountries> {
        RethinkCenteredScreen {
            DemoRpnCountriesScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onSettings = { nav.push(RethinkRoute.RpnSettings) },
                onServerDetails = { key -> nav.push(RethinkRoute.RpnServerDetails(key)) },
            )
        }
    }
    entry<RethinkRoute.RpnServerDetails> { key ->
        RethinkCenteredScreen {
            DemoRpnServerDetailsScreen(Modifier.fillMaxSize(), key.key, nav::popBackStack)
        }
    }
    entry<RethinkRoute.RpnSettings> {
        RethinkCenteredScreen { DemoRpnSettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.AppList> {
        RethinkCenteredScreen {
            DemoFirewallAppListScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onAppInfo = { app -> nav.push(RethinkRoute.AppInfo(app.uid)) },
            )
        }
    }
    entry<RethinkRoute.AppInfo> { key ->
        val app = demoFirewallApps.firstOrNull { it.uid == key.uid }
        RethinkCenteredScreen {
            DemoAppInfoScreen(Modifier.fillMaxSize(), app, nav::popBackStack)
        }
    }
    entry<RethinkRoute.CustomRules> {
        RethinkCenteredScreen { DemoCustomRulesScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.UniversalFirewall> {
        RethinkCenteredScreen { DemoUniversalFirewallScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Blocklists> {
        RethinkCenteredScreen { DemoBlocklistEditorScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ConfigureOtherDns> {
        RethinkCenteredScreen { DemoOtherDnsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.WireGuard> {
        RethinkCenteredScreen { DemoWireguardScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.TunnelSettings> {
        RethinkCenteredScreen { DemoTunnelSettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.ProxySettings> {
        RethinkCenteredScreen { DemoProxySettingsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.FirewallSettings> {
        RethinkCenteredScreen {
            DemoFirewallSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onCustomRules = { nav.push(RethinkRoute.CustomRules()) },
                onUniversalFirewall = { nav.push(RethinkRoute.UniversalFirewall) },
            )
        }
    }
    entry<RethinkRoute.DnsList> {
        RethinkCenteredScreen { DemoDnsListScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.DnsSettings> {
        RethinkCenteredScreen {
            DemoDnsSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onCustomDns = { nav.push(RethinkRoute.ConfigureOtherDns()) },
            )
        }
    }
    entry<RethinkRoute.PingTest> {
        RethinkCenteredScreen { DemoPingTestScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.AntiCensorship> {
        RethinkCenteredScreen { DemoAntiCensorshipScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.Events> {
        RethinkCenteredScreen { DemoEventLogsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
    entry<RethinkRoute.BlockFreeDns> {
        RethinkCenteredScreen { DemoBlockFreeDnsScreen(Modifier.fillMaxSize(), nav::popBackStack) }
    }
}

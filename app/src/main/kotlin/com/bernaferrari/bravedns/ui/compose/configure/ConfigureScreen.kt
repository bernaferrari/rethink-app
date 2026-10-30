/* Copyright 2024 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.settings.AppearanceSettingsCard
import com.bernaferrari.bravedns.ui.compose.theme.RethinkColorPreset
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

/** Android resources, navigation, and state adapter for the common Configure renderer. */
@Composable
fun ConfigureScreen(
    isDebug: Boolean,
    onAppsClick: () -> Unit,
    onDnsClick: () -> Unit,
    onFirewallClick: () -> Unit,
    onProxyClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onOthersClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAntiCensorshipClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onThemeModeChanged: ((Int) -> Unit)? = null,
    onThemeColorChanged: ((Int) -> Unit)? = null,
    onSearchDestinationClick: ((SettingsSearchDestination) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    fun open(destination: SettingsSearchDestination) = when (destination) {
        SettingsSearchDestination.Apps -> onAppsClick()
        is SettingsSearchDestination.Dns -> onDnsClick()
        is SettingsSearchDestination.Firewall -> onFirewallClick()
        is SettingsSearchDestination.Proxy -> onProxyClick()
        is SettingsSearchDestination.Network -> onNetworkClick()
        is SettingsSearchDestination.General -> onOthersClick()
        SettingsSearchDestination.Logs -> onLogsClick()
        SettingsSearchDestination.AntiCensorship -> onAntiCensorshipClick()
        SettingsSearchDestination.Advanced -> onAdvancedClick()
    }
    fun selectSearch(destination: SettingsSearchDestination) {
        searchOpen = false
        query = ""
        onSearchDestinationClick?.invoke(destination) ?: open(destination)
    }
    fun entry(
        id: String,
        title: String,
        icon: ImageVector,
        click: () -> Unit,
        subtitle: String? = null,
        keywords: List<String> = emptyList(),
        accent: Color = ConfigureAccents.apps,
    ) = RethinkConfigureEntry(
        id = id,
        title = title,
        icon = { Icon(icon, null) },
        onClick = click,
        subtitle = subtitle,
        keywords = keywords,
        accent = accent,
    )

    val protection = stringResource(R.string.lbl_protection)
    val system = stringResource(R.string.lbl_system)
    val advanced = stringResource(R.string.lbl_advanced)
    // Two tiers, one rule each: the four protection features as a 2x2 tile grid, everything else
    // as one list. No entry carries a subtitle (only some had one, and "Customize" said nothing);
    // the old "Advanced" section, which held an item also called "Advanced", folds into System.
    val sections = listOf(
        RethinkConfigureSection(protection, listOf(
            entry("apps", stringResource(R.string.lbl_apps), MaterialSymbols.Filled.Apps, onAppsClick, keywords = listOf("apps", "application", "app list"), accent = ConfigureAccents.apps),
            entry("dns", stringResource(R.string.lbl_dns), MaterialSymbols.Filled.Dns, onDnsClick, keywords = listOf("dns", "resolver", "blocklist"), accent = ConfigureAccents.dns),
            entry("firewall", stringResource(R.string.lbl_firewall), KeepoutIcons.Firewall, onFirewallClick, keywords = listOf("firewall", "rules", "allow", "block"), accent = ConfigureAccents.firewall),
            entry("proxy", stringResource(R.string.lbl_proxy), MaterialSymbols.Filled.ConversionPath, onProxyClick, keywords = listOf("proxy", "socks5", "orbot", "wireguard"), accent = ConfigureAccents.proxy),
        ), RethinkConfigureLayout.GridFour),
        RethinkConfigureSection(system, buildList {
            add(entry("network", stringResource(R.string.lbl_network), MaterialSymbols.Filled.VpnLock, onNetworkClick, keywords = listOf("network", "vpn", "tunnel"), accent = ConfigureAccents.network))
            add(entry("settings", stringResource(R.string.settings_general_header), MaterialSymbols.Filled.Tune, onOthersClick, keywords = listOf("settings", "general", "backup"), accent = ConfigureAccents.settings))
            add(entry("logs", stringResource(R.string.lbl_logs), MaterialSymbols.Filled.FormatListBulleted, onLogsClick, keywords = listOf("logs", "events", "console"), accent = ConfigureAccents.logs))
            add(entry("anti-censorship", stringResource(R.string.anti_censorship_title), MaterialSymbols.Filled.DominoMask, onAntiCensorshipClick, keywords = listOf("anti censorship", "dpi"), accent = ConfigureAccents.antiCensorship))
            if (isDebug) add(entry("advanced", advanced, MaterialSymbols.Filled.Science, onAdvancedClick, keywords = listOf("advanced", "experimental", "debug"), accent = ConfigureAccents.advanced))
        }, RethinkConfigureLayout.GridFour),
    )
    val deepSearch = buildSettingsSearchIndex(isDebug).map { source ->
        RethinkConfigureSearchEntry(
            entry(
                source.id,
                source.title,
                source.icon,
                { selectSearch(source.destination) },
                source.subtitle,
                source.keywords,
                source.destination.configureAccent(),
            ),
            source.path,
        )
    }
    val topSearch = sections.flatMap { section -> section.entries.map { RethinkConfigureSearchEntry(it, "${section.title} > ${it.title}") } }
    RethinkConfigureScreen(
        sections = sections,
        searchEntries = (deepSearch + topSearch).distinctBy(::rethinkConfigureSearchDedupKey),
        strings = RethinkConfigureStrings(
            title = stringResource(R.string.title_settings), searchHint = stringResource(R.string.configure_search_hint),
            openSearch = stringResource(R.string.configure_search_open), closeSearch = stringResource(R.string.configure_search_close),
            clearSearch = stringResource(R.string.cd_clear_search), noResultsTitle = stringResource(R.string.configure_search_empty_title), noResultsSubtitle = stringResource(R.string.configure_search_empty_subtitle),
        ),
        searchOpen = searchOpen, query = query,
        onSearchOpenChange = { searchOpen = it; if (!it) query = "" }, onQueryChange = { query = it },
        appearanceContent = {
            AppearanceSettingsCard(
                themePreference = persistentState.theme,
                colorPresetId = persistentState.themeColorPreset,
                onAppearanceModeSelected = { mode ->
                    val themeId = mode.toThemePreference()
                    persistentState.theme = themeId
                    onThemeModeChanged?.invoke(themeId)
                    eventLogger.log(
                        EventType.UI_SETTING_CHANGED,
                        Severity.LOW,
                        "Appearance",
                        EventSource.UI,
                        true,
                        "Theme set to ${mode.name.lowercase()}",
                    )
                },
                onColorPresetSelected = { preset: RethinkColorPreset ->
                    persistentState.themeColorPreset = preset.id
                    onThemeColorChanged?.invoke(preset.id)
                    eventLogger.log(
                        EventType.UI_SETTING_CHANGED,
                        Severity.LOW,
                        "Appearance color",
                        EventSource.UI,
                        true,
                        "Color preset set to ${preset.name.lowercase()}",
                    )
                },
                showSectionHeader = true,
            )
        },
    )
}

private fun SettingsSearchDestination.configureAccent(): Color = when (this) {
    SettingsSearchDestination.Apps -> ConfigureAccents.apps
    is SettingsSearchDestination.Dns -> ConfigureAccents.dns
    is SettingsSearchDestination.Firewall -> ConfigureAccents.firewall
    is SettingsSearchDestination.Proxy -> ConfigureAccents.proxy
    is SettingsSearchDestination.Network -> ConfigureAccents.network
    is SettingsSearchDestination.General -> ConfigureAccents.settings
    SettingsSearchDestination.Logs -> ConfigureAccents.logs
    SettingsSearchDestination.AntiCensorship -> ConfigureAccents.antiCensorship
    SettingsSearchDestination.Advanced -> ConfigureAccents.advanced
}

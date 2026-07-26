/* Copyright 2024 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.configure

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R

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
    onSearchDestinationClick: ((SettingsSearchDestination) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    val tint = remember { ConfigureTints() }
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
    fun entry(id: String, title: String, iconRes: Int, accent: Color, click: () -> Unit, subtitle: String? = null, keywords: List<String> = emptyList()) =
        RethinkConfigureEntry(id, title, { Icon(painterResource(iconRes), null) }, accent, click, subtitle, keywords)

    val protection = stringResource(R.string.lbl_protection)
    val system = stringResource(R.string.lbl_system)
    val advanced = stringResource(R.string.lbl_advanced)
    val sections = listOf(
        RethinkConfigureSection(protection, Color(0xFF804136), listOf(
            entry("apps", stringResource(R.string.lbl_apps), R.drawable.ic_app_info_accent, tint.apps, onAppsClick, keywords = listOf("apps", "application", "app list")),
            entry("dns", stringResource(R.string.lbl_dns), R.drawable.dns_home_screen, tint.dns, onDnsClick, keywords = listOf("dns", "resolver", "blocklist")),
            entry("firewall", stringResource(R.string.lbl_firewall), R.drawable.firewall_home_screen, tint.firewall, onFirewallClick, keywords = listOf("firewall", "rules", "allow", "block")),
            entry("proxy", stringResource(R.string.lbl_proxy), R.drawable.ic_proxy, tint.proxy, onProxyClick, keywords = listOf("proxy", "socks5", "orbot", "wireguard")),
        ), RethinkConfigureLayout.GridFour),
        RethinkConfigureSection(system, Color(0xFF755A54), listOf(
            entry("network", stringResource(R.string.lbl_network), R.drawable.ic_network_tunnel, tint.network, onNetworkClick, keywords = listOf("network", "vpn", "tunnel")),
            entry("settings", stringResource(R.string.settings_general_header), R.drawable.ic_other_settings, tint.settings, onOthersClick, keywords = listOf("settings", "general", "theme", "backup")),
            entry("logs", stringResource(R.string.lbl_logs), R.drawable.ic_logs_accent, tint.logs, onLogsClick, stringResource(R.string.settings_enable_logs_desc), listOf("logs", "events", "console")),
        ), RethinkConfigureLayout.GridTriad),
        RethinkConfigureSection(advanced, Color(0xFF6F5D2E), buildList {
            add(entry("anti-censorship", stringResource(R.string.anti_censorship_title), R.drawable.ic_anti_dpi, tint.antiCensorship, onAntiCensorshipClick, stringResource(R.string.anti_censorship_desc), listOf("anti censorship", "dpi")))
            if (isDebug) add(entry("advanced", advanced, R.drawable.ic_advanced_settings, tint.advanced, onAdvancedClick, stringResource(R.string.adv_set_experimental_desc), listOf("advanced", "experimental", "debug")))
        }, RethinkConfigureLayout.List),
    )
    val deepSearch = buildSettingsSearchIndex(isDebug).map { source ->
        RethinkConfigureSearchEntry(
            entry(source.id, source.title, source.iconRes, tint.forDestination(source.destination), { onSearchDestinationClick?.invoke(source.destination) ?: open(source.destination) }, source.subtitle, source.keywords),
            source.path,
        )
    }
    val topSearch = sections.flatMap { section -> section.entries.map { RethinkConfigureSearchEntry(it, "${section.title} > ${it.title}") } }
    RethinkConfigureScreen(
        sections = sections,
        searchEntries = (deepSearch + topSearch).distinctBy { it.entry.id },
        strings = RethinkConfigureStrings(
            title = stringResource(R.string.title_settings), searchHint = stringResource(R.string.configure_search_hint),
            openSearch = stringResource(R.string.configure_search_open), closeSearch = stringResource(R.string.configure_search_close),
            clearSearch = stringResource(R.string.cd_clear_search), noResultsTitle = stringResource(R.string.configure_search_empty_title), noResultsSubtitle = stringResource(R.string.configure_search_empty_subtitle),
        ),
        searchOpen = searchOpen, query = query,
        onSearchOpenChange = { searchOpen = it; if (!it) query = "" }, onQueryChange = { query = it },
    )
}

private data class ConfigureTints(
    val apps: Color = Color(0xFF74C5FF), val dns: Color = Color(0xFFC5ACFF), val firewall: Color = Color(0xFFFF907F), val proxy: Color = Color(0xFF46EBC8),
    val network: Color = Color(0xFFA3BCFF), val settings: Color = Color(0xFFFFD878), val logs: Color = Color(0xFF7EED92), val antiCensorship: Color = Color(0xFFFFA7E0), val advanced: Color = Color(0xFFFFE182),
) {
    fun forDestination(destination: SettingsSearchDestination) = when (destination) {
        SettingsSearchDestination.Apps -> apps; is SettingsSearchDestination.Dns -> dns; is SettingsSearchDestination.Firewall -> firewall
        is SettingsSearchDestination.Proxy -> proxy; is SettingsSearchDestination.Network -> network; is SettingsSearchDestination.General -> settings
        SettingsSearchDestination.Logs -> logs; SettingsSearchDestination.AntiCensorship -> antiCensorship; SettingsSearchDestination.Advanced -> advanced
    }
}

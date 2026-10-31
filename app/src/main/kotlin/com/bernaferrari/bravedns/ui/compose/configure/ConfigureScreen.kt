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
import com.bernaferrari.bravedns.ui.compose.database.BackupRestoreSheet
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

/**
 * The Settings tab: Protection (DNS, Firewall, Proxy & VPN) as colored tiles, App (Appearance, App
 * lock, Notifications, Backup & restore, General) and Advanced (Network, Anti-censorship, Logging,
 * Diagnostics, and Developer in debug builds), then About. Apps and the log are tabs of their own.
 *
 * [onOpen] receives every destination; Backup & restore opens its sheet here, over the hub.
 */
@Composable
fun ConfigureScreen(
    isDebug: Boolean,
    onOpen: (SettingsSearchDestination) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showBackupSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    fun open(destination: SettingsSearchDestination) {
        if (destination == SettingsSearchDestination.Backup) showBackupSheet = true else onOpen(destination)
    }
    fun selectSearch(destination: SettingsSearchDestination) {
        searchOpen = false
        query = ""
        open(destination)
    }
    fun entry(
        id: String,
        title: String,
        icon: ImageVector,
        destination: SettingsSearchDestination,
        accent: Color,
        keywords: List<String> = emptyList(),
        click: () -> Unit = { open(destination) },
    ) = RethinkConfigureEntry(
        id = id,
        title = title,
        icon = { Icon(icon, null) },
        onClick = click,
        keywords = keywords,
        accent = accent,
    )

    val appName = stringResource(R.string.app_name)
    val sections = listOf(
        // Protection: what Keepout does to traffic. Three tiles; the last one spans the row.
        RethinkConfigureSection(stringResource(R.string.lbl_protection), listOf(
            entry("dns", stringResource(R.string.lbl_dns), MaterialSymbols.Filled.Dns, SettingsSearchDestination.Dns(), ConfigureAccents.dns, listOf("dns", "resolver", "blocklist")),
            entry("firewall", stringResource(R.string.lbl_firewall), KeepoutIcons.Firewall, SettingsSearchDestination.Firewall(), ConfigureAccents.firewall, listOf("firewall", "rules", "allow", "block")),
            entry("proxy", stringResource(R.string.settings_proxy_vpn), MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy(), ConfigureAccents.proxy, listOf("proxy", "socks5", "orbot", "wireguard", "vpn")),
        ), RethinkConfigureLayout.GridFour),
        // App: how Keepout itself looks and behaves, as tiles like the other groups.
        RethinkConfigureSection(stringResource(R.string.settings_section_app), listOf(
            entry("appearance", stringResource(R.string.settings_theme_heading), MaterialSymbols.Filled.Palette, SettingsSearchDestination.Appearance, ConfigureAccents.appearance, listOf("theme", "dark", "color")),
            entry("app_lock", stringResource(R.string.app_lock_title), MaterialSymbols.Filled.Lock, SettingsSearchDestination.AppLock, ConfigureAccents.appLock, listOf("lock", "biometric", "fingerprint")),
            entry("notifications", stringResource(R.string.settings_notifications), MaterialSymbols.Filled.Notifications, SettingsSearchDestination.Notifications, ConfigureAccents.notifications, listOf("notifications", "alerts")),
            entry("backup", stringResource(R.string.brbs_title), MaterialSymbols.Filled.Backup, SettingsSearchDestination.Backup, ConfigureAccents.backup, listOf("backup", "restore")),
            entry("general", stringResource(R.string.settings_general_header).replaceFirstChar { it.titlecase() }, MaterialSymbols.Filled.Tune, SettingsSearchDestination.General(), ConfigureAccents.settings, listOf("general", "boot", "updates")),
        ), RethinkConfigureLayout.GridFour),
        // Advanced: engine knobs and troubleshooting, as tiles like Protection.
        RethinkConfigureSection(stringResource(R.string.lbl_advanced), buildList {
            add(entry("network", stringResource(R.string.lbl_network), MaterialSymbols.Filled.VpnLock, SettingsSearchDestination.Network(), ConfigureAccents.network, listOf("network", "vpn", "tunnel")))
            add(entry("anti-censorship", stringResource(R.string.anti_censorship_title), MaterialSymbols.Filled.DominoMask, SettingsSearchDestination.AntiCensorship, ConfigureAccents.antiCensorship, listOf("anti censorship", "dpi")))
            add(entry("logging", stringResource(R.string.settings_logging_title), MaterialSymbols.Filled.History, SettingsSearchDestination.Logging(), ConfigureAccents.logs, listOf("logs", "logging")))
            add(entry("diagnostics", stringResource(R.string.about_diagnostics), MaterialSymbols.Filled.BugReport, SettingsSearchDestination.Diagnostics, ConfigureAccents.diagnostics, listOf("diagnostics", "event logs", "ping")))
            if (isDebug) add(entry("developer", stringResource(R.string.settings_developer_title), MaterialSymbols.Filled.Science, SettingsSearchDestination.Developer, ConfigureAccents.advanced, listOf("experimental", "debug")))
        }, RethinkConfigureLayout.GridFour),
        RethinkConfigureSection("", listOf(
            entry("about", stringResource(R.string.settings_about_keepout, appName), MaterialSymbols.Filled.Info, SettingsSearchDestination.About, ConfigureAccents.about, listOf("about", "version")),
        ), RethinkConfigureLayout.List),
    )
    val deepSearch = buildSettingsSearchIndex(isDebug).map { source ->
        RethinkConfigureSearchEntry(
            RethinkConfigureEntry(
                id = source.id,
                title = source.title,
                icon = { Icon(source.icon, null) },
                onClick = { selectSearch(source.destination) },
                subtitle = source.subtitle,
                keywords = source.keywords,
                accent = source.destination.configureAccent(),
            ),
            source.path,
        )
    }
    val topSearch = sections.flatMap { section ->
        section.entries.map { entry ->
            RethinkConfigureSearchEntry(
                entry.copy(onClick = { searchOpen = false; query = ""; entry.onClick() }),
                if (section.title.isBlank()) entry.title else "${section.title} > ${entry.title}",
            )
        }
    }
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
    )
    if (showBackupSheet) BackupRestoreSheet(onDismiss = { showBackupSheet = false })
}

private fun SettingsSearchDestination.configureAccent(): Color = when (this) {
    is SettingsSearchDestination.Dns -> ConfigureAccents.dns
    is SettingsSearchDestination.Firewall -> ConfigureAccents.firewall
    is SettingsSearchDestination.Proxy -> ConfigureAccents.proxy
    SettingsSearchDestination.Appearance -> ConfigureAccents.appearance
    SettingsSearchDestination.AppLock -> ConfigureAccents.appLock
    SettingsSearchDestination.Notifications -> ConfigureAccents.notifications
    SettingsSearchDestination.Backup -> ConfigureAccents.backup
    is SettingsSearchDestination.General -> ConfigureAccents.settings
    is SettingsSearchDestination.Network -> ConfigureAccents.network
    SettingsSearchDestination.AntiCensorship -> ConfigureAccents.antiCensorship
    is SettingsSearchDestination.Logging -> ConfigureAccents.logs
    SettingsSearchDestination.Diagnostics -> ConfigureAccents.diagnostics
    SettingsSearchDestination.Developer -> ConfigureAccents.advanced
    SettingsSearchDestination.About -> ConfigureAccents.about
}

/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.configure

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.ui.compose.dns.isDnsAdvancedFocusKey
import com.bernaferrari.bravedns.ui.compose.firewall.FIREWALL_FOCUS_RULES
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkRoute
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

/** Where a Settings entry (or a search result) leads. Grouped as on the hub: Protection, App, Advanced. */
sealed interface SettingsSearchDestination {
    data class Dns(val focusKey: String = "") : SettingsSearchDestination
    data class Firewall(val focusKey: String = "") : SettingsSearchDestination
    data class Proxy(val focusKey: String = "") : SettingsSearchDestination
    data object Appearance : SettingsSearchDestination
    data object AppLock : SettingsSearchDestination
    /** Android's notification settings for Keepout (not a screen of ours). */
    data object Notifications : SettingsSearchDestination
    /** The Backup & restore sheet, opened over the hub. */
    data object Backup : SettingsSearchDestination
    data class General(val focusKey: String = "") : SettingsSearchDestination
    data class Network(val focusKey: String = "") : SettingsSearchDestination
    data object AntiCensorship : SettingsSearchDestination
    data class Logging(val focusKey: String = "") : SettingsSearchDestination
    data object Diagnostics : SettingsSearchDestination
    /** Debug builds only: the experimental engine switches. */
    data object Developer : SettingsSearchDestination
    data object About : SettingsSearchDestination
}

/** The screen a destination opens, or null for one handled in place (Notifications, Backup). */
fun SettingsSearchDestination.route(): RethinkRoute? = when (this) {
    // Expert DNS settings live on DNS › Advanced (WS4); the rest on the DNS screen.
    is SettingsSearchDestination.Dns ->
        if (isDnsAdvancedFocusKey(focusKey)) RethinkRoute.DnsAdvanced(focusKey) else RethinkRoute.DnsDetail(focusKey)
    is SettingsSearchDestination.Firewall -> RethinkRoute.FirewallSettings(focusKey)
    is SettingsSearchDestination.Proxy -> RethinkRoute.ProxySettings(focusKey)
    SettingsSearchDestination.Appearance -> RethinkRoute.AppearanceSettings
    SettingsSearchDestination.AppLock -> RethinkRoute.AppLockSettings
    is SettingsSearchDestination.General -> RethinkRoute.MiscSettings(focusKey)
    is SettingsSearchDestination.Network -> RethinkRoute.TunnelSettings(focusKey)
    SettingsSearchDestination.AntiCensorship -> RethinkRoute.AntiCensorship
    is SettingsSearchDestination.Logging -> RethinkRoute.LoggingSettings(focusKey)
    SettingsSearchDestination.Diagnostics -> RethinkRoute.Diagnostics
    SettingsSearchDestination.Developer -> RethinkRoute.AdvancedSettings
    SettingsSearchDestination.About -> RethinkRoute.About
    SettingsSearchDestination.Notifications, SettingsSearchDestination.Backup -> null
}

data class SettingsSearchIndexEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val path: String,
    val icon: ImageVector,
    val destination: SettingsSearchDestination,
    val keywords: List<String> = emptyList(),
)

@Composable
fun buildSettingsSearchIndex(isDebug: Boolean): List<SettingsSearchIndexEntry> {
    val protection = stringResource(R.string.lbl_protection)
    val app = stringResource(R.string.settings_section_app)
    val advanced = stringResource(R.string.lbl_advanced)
    val dns = stringResource(R.string.lbl_dns)
    val proxy = stringResource(R.string.settings_proxy_vpn)
    val network = stringResource(R.string.lbl_network)
    val general = stringResource(R.string.settings_general_header).replaceFirstChar { it.titlecase() }
    val firewall = stringResource(R.string.lbl_firewall)
    val logging = stringResource(R.string.settings_logging_title)
    val appearance = stringResource(R.string.settings_theme_heading)

    val topLevel =
        listOfNotNull(
            SettingsSearchIndexEntry("top.dns", dns, stringResource(R.string.dns_mode_info_title), "$protection > $dns", MaterialSymbols.Filled.Dns, SettingsSearchDestination.Dns(), listOf("dns", "resolver", "blocklist", "ads", "trackers")),
            SettingsSearchIndexEntry("top.firewall", firewall, stringResource(R.string.universal_firewall_explanation), "$protection > $firewall", KeepoutIcons.Firewall, SettingsSearchDestination.Firewall(), listOf("firewall", "rules", "allow", "block", "universal")),
            SettingsSearchIndexEntry("top.proxy", proxy, stringResource(R.string.settings_proxy_vpn_desc), "$protection > $proxy", MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy(), listOf("proxy", "vpn", "socks5", "http proxy", "orbot", "tor", "wireguard")),
            SettingsSearchIndexEntry("top.appearance", appearance, stringResource(R.string.settings_theme_color_desc), "$app > $appearance", MaterialSymbols.Filled.Palette, SettingsSearchDestination.Appearance, listOf("theme", "dark", "light", "color", "appearance")),
            SettingsSearchIndexEntry("top.app_lock", stringResource(R.string.app_lock_title), stringResource(R.string.hs_biometeric_desc), "$app > ${stringResource(R.string.app_lock_title)}", MaterialSymbols.Filled.Lock, SettingsSearchDestination.AppLock, listOf("lock", "biometric", "fingerprint", "face", "pin", "password", "security")),
            SettingsSearchIndexEntry("top.notifications", stringResource(R.string.settings_notifications), stringResource(R.string.about_settings_notification), "$app > ${stringResource(R.string.settings_notifications)}", MaterialSymbols.Filled.Notifications, SettingsSearchDestination.Notifications, listOf("notifications", "alerts")),
            SettingsSearchIndexEntry("top.backup", stringResource(R.string.brbs_title), stringResource(R.string.settings_import_export_desc), "$app > ${stringResource(R.string.brbs_title)}", MaterialSymbols.Filled.Backup, SettingsSearchDestination.Backup, listOf("backup", "restore", "export", "import")),
            SettingsSearchIndexEntry("top.general", general, stringResource(R.string.settings_autostart_bootup_heading), "$app > $general", MaterialSymbols.Filled.Tune, SettingsSearchDestination.General(), listOf("general", "boot", "startup", "updates")),
            SettingsSearchIndexEntry("top.network", network, stringResource(R.string.firewall_act_network_monitor_tab), "$advanced > $network", MaterialSymbols.Filled.VpnLock, SettingsSearchDestination.Network(), listOf("network", "vpn", "metered", "lan", "wifi", "mobile")),
            SettingsSearchIndexEntry("top.anti_censorship", stringResource(R.string.anti_censorship_title), stringResource(R.string.anti_censorship_desc), "$advanced > ${stringResource(R.string.anti_censorship_title)}", MaterialSymbols.Filled.DominoMask, SettingsSearchDestination.AntiCensorship, listOf("anti censorship", "dpi")),
            SettingsSearchIndexEntry("top.logging", logging, stringResource(R.string.settings_enable_logs_desc), "$advanced > $logging", MaterialSymbols.Filled.History, SettingsSearchDestination.Logging(), listOf("logs", "logging", "retention", "pcap")),
            SettingsSearchIndexEntry("top.diagnostics", stringResource(R.string.about_diagnostics), stringResource(R.string.diagnostics_desc), "$advanced > ${stringResource(R.string.about_diagnostics)}", MaterialSymbols.Filled.BugReport, SettingsSearchDestination.Diagnostics, listOf("diagnostics", "event logs", "console", "ping", "troubleshoot")),
            if (isDebug) SettingsSearchIndexEntry("top.developer", stringResource(R.string.settings_developer_title), stringResource(R.string.adv_set_experimental_desc), "$advanced > ${stringResource(R.string.settings_developer_title)}", MaterialSymbols.Filled.Science, SettingsSearchDestination.Developer, listOf("experimental", "debug", "advanced")) else null,
            SettingsSearchIndexEntry("top.about", stringResource(R.string.settings_about_keepout, stringResource(R.string.app_name)), stringResource(R.string.settings_misc_about_desc), stringResource(R.string.settings_about_keepout, stringResource(R.string.app_name)), MaterialSymbols.Filled.Info, SettingsSearchDestination.About, listOf("about", "version", "licenses", "website")),
        )

    val dnsEntries =
        listOf(
            searchEntry("dns.mode.system", R.string.network_dns, R.string.dc_other_dns_heading, "$protection > $dns > ${stringResource(R.string.dc_other_dns_heading)}", MaterialSymbols.Filled.VpnLock, SettingsSearchDestination.Dns("dns_mode_system"), listOf("system dns")),
            searchEntry("dns.mode.custom", R.string.dc_custom_dns_radio, R.string.dc_other_dns_heading, "$protection > $dns > ${stringResource(R.string.dc_other_dns_heading)}", MaterialSymbols.Filled.FilterList, SettingsSearchDestination.Dns("dns_mode_custom"), listOf("custom dns")),
            searchEntry("dns.mode.rethink", R.string.dc_rethink_dns_radio, R.string.dc_other_dns_heading, "$protection > $dns > ${stringResource(R.string.dc_other_dns_heading)}", MaterialSymbols.Filled.Star, SettingsSearchDestination.Dns("dns_mode_rethink"), listOf("rethink dns")),
            searchEntry("dns.mode.smart", R.string.smart_dns, R.string.dc_other_dns_heading, "$protection > $dns > ${stringResource(R.string.dc_other_dns_heading)}", MaterialSymbols.Filled.Database, SettingsSearchDestination.Dns("dns_mode_smart"), listOf("smart dns")),
            searchEntry("dns.block.local", R.string.dc_local_block_heading, R.string.dc_block_heading, "$protection > $dns > ${stringResource(R.string.dc_block_heading)}", KeepoutIcons.Blocklists, SettingsSearchDestination.Dns("dns_block_local"), listOf("local blocklist")),
            searchEntry("dns.block.custom_downloader", R.string.settings_custom_downloader_heading, R.string.dc_block_heading, "$protection > $dns > ${stringResource(R.string.dc_block_heading)}", MaterialSymbols.Filled.SystemUpdate, SettingsSearchDestination.Dns("dns_block_custom_downloader")),
            searchEntry("dns.block.periodic_updates", R.string.dc_check_update_heading, R.string.dc_block_heading, "$protection > $dns > ${stringResource(R.string.dc_block_heading)}", MaterialSymbols.Filled.Refresh, SettingsSearchDestination.Dns("dns_block_periodic_updates")),
            searchEntry("dns.filter.alg", R.string.cd_dns_alg_heading, R.string.dc_filtering_heading, "$protection > $dns > ${stringResource(R.string.dc_filtering_heading)}", MaterialSymbols.Filled.FilterList, SettingsSearchDestination.Dns("dns_filter_alg")),
            searchEntry("dns.filter.split", R.string.cd_split_dns_heading, R.string.dc_filtering_heading, "$protection > $dns > ${stringResource(R.string.dc_filtering_heading)}", MaterialSymbols.Filled.CallSplit, SettingsSearchDestination.Dns("dns_filter_split")),
            searchEntry("dns.filter.rules_as_firewall", R.string.cd_treat_dns_rules_firewall_heading, R.string.dc_filtering_heading, "$protection > $dns > ${stringResource(R.string.dc_filtering_heading)}", KeepoutIcons.Firewall, SettingsSearchDestination.Dns("dns_filter_rules_as_firewall")),
            searchEntry("dns.filter.record_types", R.string.cd_allowed_dns_record_types_heading, R.string.dc_filtering_heading, "$protection > $dns > ${stringResource(R.string.dc_filtering_heading)}", MaterialSymbols.Filled.Dns, SettingsSearchDestination.Dns("dns_filter_record_types"), listOf("record types")),
            searchEntry("dns.advanced.favicon", R.string.dc_dns_website_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Public, SettingsSearchDestination.Dns("dns_advanced_favicon")),
            searchEntry("dns.advanced.cache", R.string.dc_setting_dns_cache_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.PlayArrow, SettingsSearchDestination.Dns("dns_advanced_cache")),
            searchEntry("dns.advanced.proxy_dns", R.string.dc_proxy_dns_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Dns("dns_advanced_proxy_dns")),
            searchEntry("dns.advanced.undelegated", R.string.dc_use_sys_dns_undelegated_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.CallSplit, SettingsSearchDestination.Dns("dns_advanced_undelegated")),
            searchEntry("dns.advanced.fallback", R.string.use_fallback_dns_to_bypass, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Dns, SettingsSearchDestination.Dns("dns_advanced_fallback")),
            searchEntry("dns.advanced.leaks", R.string.dc_dns_leaks_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Shield, SettingsSearchDestination.Dns("dns_advanced_leaks"))
        )

    val firewallPath = "$protection > $firewall"
    val everyday = "$firewallPath > ${stringResource(R.string.firewall_section_everyday)}"
    val strict = "$firewallPath > ${stringResource(R.string.firewall_section_strict)}"
    val firewallEntries =
        listOf(
            searchEntry("firewall.rules", R.string.lbl_rules, R.string.univ_view_blocked_ip_desc, firewallPath, MaterialSymbols.Filled.Rule, SettingsSearchDestination.Firewall(FIREWALL_FOCUS_RULES), listOf("domain", "ip", "trust", "block", "allow")),
            searchEntry("firewall.new_apps", R.string.univ_rule_new_apps, R.string.univ_rule_new_apps_desc, everyday, MaterialSymbols.Filled.NewReleases, SettingsSearchDestination.Firewall(FirewallRuleset.RULE1B.id), listOf("install", "new")),
            searchEntry("firewall.screen_locked", R.string.univ_rule_screen_locked, R.string.univ_rule_screen_locked_desc, everyday, MaterialSymbols.Filled.ScreenLockPortrait, SettingsSearchDestination.Firewall(FirewallRuleset.RULE3.id), listOf("lock", "screen", "locked")),
            searchEntry("firewall.not_in_use", R.string.univ_firewall_rule_2, R.string.univ_rule_not_in_use_desc, everyday, MaterialSymbols.Filled.VisibilityOff, SettingsSearchDestination.Firewall(FirewallRuleset.RULE4.id), listOf("background", "not in use")),
            searchEntry("firewall.udp", R.string.univ_firewall_rule_4, R.string.univ_rule_udp_desc, strict, MaterialSymbols.Filled.SettingsEthernet, SettingsSearchDestination.Firewall(FirewallRuleset.RULE6.id), listOf("udp", "quic")),
            searchEntry("firewall.http", R.string.univ_rule_http, R.string.univ_rule_http_desc, strict, MaterialSymbols.Filled.Http, SettingsSearchDestination.Firewall(FirewallRuleset.RULE10.id), listOf("http", "port 80", "insecure")),
            searchEntry("firewall.unknown", R.string.univ_rule_unknown, R.string.univ_rule_unknown_desc, strict, MaterialSymbols.AutoMirrored.Filled.HelpOutline, SettingsSearchDestination.Firewall(FirewallRuleset.RULE5.id), listOf("unknown")),
            searchEntry("firewall.dns_bypass", R.string.univ_firewall_rule_5, R.string.univ_rule_dns_bypass_desc, strict, MaterialSymbols.Filled.Dns, SettingsSearchDestination.Firewall(FirewallRuleset.RULE7.id), listOf("dns bypass")),
            searchEntry("firewall.mobile_data", R.string.univ_firewall_rule_9, R.string.univ_rule_mobile_data_desc, strict, MaterialSymbols.Filled.CellTower, SettingsSearchDestination.Firewall(FirewallRuleset.RULE1F.id), listOf("mobile", "data", "cellular", "metered", "wifi")),
            searchEntry("firewall.lockdown", R.string.univ_rule_lockdown, R.string.univ_rule_lockdown_desc, "$firewallPath > ${stringResource(R.string.firewall_section_lockdown)}", MaterialSymbols.Filled.Lock, SettingsSearchDestination.Firewall(FirewallRuleset.RULE11.id), listOf("lockdown")),
        )

    val proxyPath = "$protection > $proxy"
    val proxyEntries =
        listOf(
            searchEntry("proxy.wireguard", R.string.proxy_row_wireguard, R.string.proxy_servers_section, proxyPath, MaterialSymbols.Filled.VpnKey, SettingsSearchDestination.Proxy("proxy_wireguard"), listOf("wg", "wireguard", "tunnel")),
            searchEntry("proxy.socks5", R.string.proxy_row_socks5, R.string.proxy_servers_section, proxyPath, MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy("proxy_socks"), listOf("socks")),
            searchEntry("proxy.http", R.string.proxy_row_http, R.string.proxy_servers_section, proxyPath, MaterialSymbols.Filled.Http, SettingsSearchDestination.Proxy("proxy_http")),
            searchEntry("proxy.orbot", R.string.proxy_row_orbot, R.string.proxy_tor_section, proxyPath, MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy("proxy_orbot"), listOf("tor", "orbot")),
            searchEntry("proxy.orbot.notification", R.string.settings_orbot_notification_action, R.string.orbot, proxyPath, MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, SettingsSearchDestination.Proxy("proxy_orbot_open_app"))
        )

    val networkEntries =
        listOf(
            searchEntry("network.allow_bypass", R.string.settings_allow_bypass_heading, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_bypass")),
            searchEntry("network.fail_open", R.string.fail_open_network_title, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_fail_open")),
            searchEntry("network.allow_lan", R.string.settings_allow_lan_heading, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_lan")),
            searchEntry("network.all_networks", R.string.settings_network_all_networks, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_all_networks")),
            searchEntry("network.exclude_apps_proxy", R.string.settings_exclude_apps_in_proxy, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_exclude_apps_proxy")),
            searchEntry("network.protocol_translation", R.string.settings_protocol_translation, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_protocol_translation")),
            searchEntry("dns.advanced.default_dns", R.string.settings_default_dns_heading, R.string.lbl_advanced, "$protection > $dns > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Dns, SettingsSearchDestination.Dns("dns_advanced_default_dns"), listOf("fallback dns", "default dns")),
            searchEntry("network.vpn_policy", R.string.vpn_policy_title, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_vpn_policy")),
            searchEntry("network.ip_protocol", R.string.settings_ip_dialog_title, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_ip_protocol")),
            searchEntry("network.connectivity_checks", R.string.settings_connectivity_checks, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_connectivity_checks")),
            searchEntry("network.ping_ips", R.string.settings_ping_ips, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.VpnLock, SettingsSearchDestination.Network("network_ping_ips")),
            searchEntry("network.mobile_metered", R.string.settings_treat_mobile_metered, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_mobile_metered")),
            searchEntry("network.wg_listen_port", R.string.settings_wg_listen_port, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_wg_listen_port")),
            searchEntry("network.wg_lockdown", R.string.settings_wg_lockdown, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_wg_lockdown")),
            searchEntry("network.endpoint_independence", R.string.settings_endpoint_independence, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_endpoint_independence")),
            searchEntry("network.allow_incoming_wg", R.string.settings_allow_incoming_wg_packets, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_incoming_wg")),
            searchEntry("network.tcp_keep_alive", R.string.settings_tcp_keep_alive, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_tcp_keep_alive")),
            searchEntry("network.jumbo_packets", R.string.settings_jumbo_packets, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_jumbo_packets")),
            searchEntry("network.vpn_metered", R.string.settings_vpn_builder_metered, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_vpn_metered")),
            searchEntry("network.custom_lan_ip", R.string.custom_lan_ip_title, R.string.lbl_advanced, "$advanced > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_custom_lan_ip")),
            searchEntry("network.dial_timeout", R.string.settings_dial_timeout, R.string.lbl_network, "$advanced > $network > ${stringResource(R.string.settings_dial_timeout)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_dial_timeout"), listOf("timeout"))
        )

    val generalPath = "$app > $general"
    val loggingPath = "$advanced > $logging"
    val appEntries =
        listOf(
            searchEntry("app.theme_color", R.string.settings_theme_color_heading, R.string.settings_theme_color_desc, "$app > $appearance", MaterialSymbols.Filled.Palette, SettingsSearchDestination.Appearance, listOf("color", "palette", "dynamic color")),
            searchEntry("general.autostart", R.string.settings_autostart_bootup_heading, R.string.settings_autostart_bootup_desc, generalPath, MaterialSymbols.Filled.PlayArrow, SettingsSearchDestination.General("general_autostart"), listOf("boot", "startup")),
            searchEntry("general.tombstone", R.string.tombstone_app_title, R.string.tombstone_app_desc, generalPath, MaterialSymbols.Filled.DeleteOutline, SettingsSearchDestination.General("general_tombstone"), listOf("remember uninstalled")),
            searchEntry("general.app_updates", R.string.settings_check_update_heading, R.string.settings_check_update_desc, generalPath, MaterialSymbols.Filled.SystemUpdate, SettingsSearchDestination.General("general_app_updates"), listOf("updates")),
            searchEntry("general.crash_reports", R.string.settings_firebase_error_reporting_heading, R.string.settings_firebase_error_reporting_desc, generalPath, MaterialSymbols.Filled.BugReport, SettingsSearchDestination.General("general_crash_reports"), listOf("firebase", "error reporting")),
            searchEntry("logging.logs", R.string.settings_enable_logs, R.string.settings_enable_logs_desc, loggingPath, MaterialSymbols.Filled.FormatListBulleted, SettingsSearchDestination.Logging("general_logs"), listOf("logs")),
            searchEntry("logging.ip_info", R.string.download_ip_info_title, R.string.settings_logging_section, loggingPath, MaterialSymbols.Filled.Public, SettingsSearchDestination.Logging("general_ip_info"), listOf("ipinfo", "provider")),
            searchEntry("logging.firewall_bubble", R.string.firewall_bubble_title, R.string.settings_logging_section, loggingPath, MaterialSymbols.Filled.ChatBubble, SettingsSearchDestination.Logging("general_firewall_bubble"), listOf("bubble")),
        )

    return topLevel + dnsEntries + firewallEntries + proxyEntries + networkEntries + appEntries
}

@Composable
private fun searchEntry(
    id: String,
    titleRes: Int,
    subtitleRes: Int,
    path: String,
    icon: ImageVector,
    destination: SettingsSearchDestination,
    keywords: List<String> = emptyList()
): SettingsSearchIndexEntry {
    return SettingsSearchIndexEntry(
        id = id,
        title = stringResource(id = titleRes),
        subtitle = stringResource(id = subtitleRes),
        path = path,
        icon = icon,
        destination = destination,
        keywords = keywords
    )
}

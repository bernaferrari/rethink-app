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
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

sealed interface SettingsSearchDestination {
    data object Apps : SettingsSearchDestination
    data class Dns(val focusKey: String = "") : SettingsSearchDestination
    data class Firewall(val focusKey: String = "") : SettingsSearchDestination
    data class Proxy(val focusKey: String = "") : SettingsSearchDestination
    data class Network(val focusKey: String = "") : SettingsSearchDestination
    data class General(val focusKey: String = "") : SettingsSearchDestination
    data object Logs : SettingsSearchDestination
    data object AntiCensorship : SettingsSearchDestination
    data object Advanced : SettingsSearchDestination
}

fun SettingsSearchDestination.General.isAppearanceFocus(): Boolean = when (focusKey) {
    "general_appearance", "general_theme_mode", "general_theme_color" -> true
    else -> false
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
    val system = stringResource(R.string.lbl_system)
    val dns = stringResource(R.string.lbl_dns)
    val proxy = stringResource(R.string.lbl_proxy)
    val network = stringResource(R.string.lbl_network)
    val general = stringResource(R.string.settings_general_header)
    val firewall = stringResource(R.string.lbl_firewall)

    val topLevel =
        listOf(
            SettingsSearchIndexEntry(
                id = "top.apps",
                title = stringResource(R.string.lbl_apps),
                subtitle = stringResource(R.string.apps_info_title),
                path = "$protection > ${stringResource(R.string.lbl_apps)}",
                icon = MaterialSymbols.Filled.Apps,
                destination = SettingsSearchDestination.Apps,
                keywords = listOf("apps", "application", "app list")
            ),
            SettingsSearchIndexEntry(
                id = "top.dns",
                title = dns,
                subtitle = stringResource(R.string.dns_mode_info_title),
                path = "$protection > $dns",
                icon = MaterialSymbols.Filled.Dns,
                destination = SettingsSearchDestination.Dns(),
                keywords = listOf("dns", "resolver", "blocklist")
            ),
            SettingsSearchIndexEntry(
                id = "top.firewall",
                title = firewall,
                subtitle = stringResource(R.string.firewall_mode_info_title),
                path = "$protection > $firewall",
                icon = KeepoutIcons.Firewall,
                destination = SettingsSearchDestination.Firewall(),
                keywords = listOf("firewall", "rules", "allow", "block")
            ),
            SettingsSearchIndexEntry(
                id = "top.proxy",
                title = proxy,
                subtitle = stringResource(R.string.cd_custom_dns_proxy_name_default),
                path = "$protection > $proxy",
                icon = MaterialSymbols.Filled.ConversionPath,
                destination = SettingsSearchDestination.Proxy(),
                keywords = listOf("proxy", "socks5", "http proxy", "orbot", "wireguard")
            ),
            SettingsSearchIndexEntry(
                id = "top.network",
                title = network,
                subtitle = stringResource(R.string.firewall_act_network_monitor_tab),
                path = "$system > $network",
                icon = MaterialSymbols.Filled.VpnLock,
                destination = SettingsSearchDestination.Network(),
                keywords = listOf("network", "vpn", "metered", "lan")
            ),
            SettingsSearchIndexEntry(
                id = "top.general",
                title = stringResource(R.string.title_settings),
                subtitle = general,
                path = "$system > ${stringResource(R.string.title_settings)}",
                icon = MaterialSymbols.Filled.Tune,
                destination = SettingsSearchDestination.General(),
                keywords = listOf("settings", "theme", "backup", "appearance")
            ),
            SettingsSearchIndexEntry(
                id = "top.logs",
                title = stringResource(R.string.lbl_logs),
                subtitle = stringResource(R.string.settings_enable_logs_desc),
                path = "$system > ${stringResource(R.string.lbl_logs)}",
                icon = MaterialSymbols.Filled.FormatListBulleted,
                destination = SettingsSearchDestination.Logs,
                keywords = listOf("logs", "events", "network logs")
            ),
            SettingsSearchIndexEntry(
                id = "top.anti_censorship",
                title = stringResource(R.string.anti_censorship_title),
                subtitle = stringResource(R.string.anti_censorship_desc),
                path = "$system > ${stringResource(R.string.anti_censorship_title)}",
                icon = MaterialSymbols.Filled.DominoMask,
                destination = SettingsSearchDestination.AntiCensorship,
                keywords = listOf("anti censorship", "dpi")
            )
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

    val firewallEntries =
        listOf(
            searchEntry("firewall.universal", R.string.univ_firewall_heading, R.string.firewall_act_universal_tab, "$protection > $firewall > ${stringResource(R.string.firewall_act_universal_tab)}", MaterialSymbols.Filled.Public, SettingsSearchDestination.Firewall("firewall_universal_main")),
            searchEntry("firewall.universal.blocked", R.string.univ_view_blocked_ip, R.string.firewall_act_universal_tab, "$protection > $firewall > ${stringResource(R.string.firewall_act_universal_tab)}", MaterialSymbols.Filled.Pin, SettingsSearchDestination.Firewall("firewall_universal_blocked")),
            searchEntry("firewall.apps.rules", R.string.app_ip_domain_rules, R.string.lbl_app_wise, "$protection > $firewall > ${stringResource(R.string.lbl_app_wise)}", MaterialSymbols.Filled.Pin, SettingsSearchDestination.Firewall("firewall_apps_rules"))
        )

    val proxyEntries =
        listOf(
            searchEntry("proxy.wireguard", R.string.setup_wireguard, R.string.settings_proxy_header, "$protection > $proxy > ${stringResource(R.string.setup_wireguard)}", MaterialSymbols.Filled.VpnKey, SettingsSearchDestination.Proxy("proxy_wireguard"), listOf("wg", "wireguard")),
            searchEntry("proxy.socks5", R.string.settings_socks5_heading, R.string.settings_proxy_header, "$protection > $proxy > ${stringResource(R.string.settings_socks5_heading)}", MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy("proxy_socks")),
            searchEntry("proxy.http", R.string.settings_https_heading, R.string.settings_proxy_header, "$protection > $proxy > ${stringResource(R.string.settings_https_heading)}", MaterialSymbols.Filled.Http, SettingsSearchDestination.Proxy("proxy_http")),
            searchEntry("proxy.orbot", R.string.orbot, R.string.settings_proxy_header, "$protection > $proxy > ${stringResource(R.string.orbot)}", MaterialSymbols.Filled.ConversionPath, SettingsSearchDestination.Proxy("proxy_orbot"), listOf("tor")),
            searchEntry("proxy.orbot.notification", R.string.settings_orbot_notification_action, R.string.orbot, "$protection > $proxy > ${stringResource(R.string.orbot)}", MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, SettingsSearchDestination.Proxy("proxy_orbot_open_app"))
        )

    val networkEntries =
        listOf(
            searchEntry("network.allow_bypass", R.string.settings_allow_bypass_heading, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_bypass")),
            searchEntry("network.fail_open", R.string.fail_open_network_title, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_fail_open")),
            searchEntry("network.allow_lan", R.string.settings_allow_lan_heading, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_lan")),
            searchEntry("network.all_networks", R.string.settings_network_all_networks, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_all_networks")),
            searchEntry("network.exclude_apps_proxy", R.string.settings_exclude_apps_in_proxy, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_exclude_apps_proxy")),
            searchEntry("network.protocol_translation", R.string.settings_protocol_translation, R.string.lbl_network, "$system > $network > ${stringResource(R.string.lbl_network)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_protocol_translation")),
            searchEntry("network.default_dns", R.string.settings_default_dns_heading, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_default_dns")),
            searchEntry("network.vpn_policy", R.string.vpn_policy_title, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_vpn_policy")),
            searchEntry("network.ip_protocol", R.string.settings_ip_dialog_title, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_ip_protocol")),
            searchEntry("network.connectivity_checks", R.string.settings_connectivity_checks, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_connectivity_checks")),
            searchEntry("network.ping_ips", R.string.settings_ping_ips, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.VpnLock, SettingsSearchDestination.Network("network_ping_ips")),
            searchEntry("network.mobile_metered", R.string.settings_treat_mobile_metered, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_mobile_metered")),
            searchEntry("network.wg_listen_port", R.string.settings_wg_listen_port, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_wg_listen_port")),
            searchEntry("network.wg_lockdown", R.string.settings_wg_lockdown, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_wg_lockdown")),
            searchEntry("network.endpoint_independence", R.string.settings_endpoint_independence, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_endpoint_independence")),
            searchEntry("network.allow_incoming_wg", R.string.settings_allow_incoming_wg_packets, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_allow_incoming_wg")),
            searchEntry("network.tcp_keep_alive", R.string.settings_tcp_keep_alive, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_tcp_keep_alive")),
            searchEntry("network.jumbo_packets", R.string.settings_jumbo_packets, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_jumbo_packets")),
            searchEntry("network.vpn_metered", R.string.settings_vpn_builder_metered, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_vpn_metered")),
            searchEntry("network.custom_lan_ip", R.string.custom_lan_ip_title, R.string.lbl_advanced, "$system > $network > ${stringResource(R.string.lbl_advanced)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_custom_lan_ip")),
            searchEntry("network.dial_timeout", R.string.settings_dial_timeout, R.string.lbl_network, "$system > $network > ${stringResource(R.string.settings_dial_timeout)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.Network("network_dial_timeout"), listOf("timeout"))
        )

    val generalEntries =
        listOf(
            searchEntry("general.appearance", R.string.settings_theme_heading, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_theme_heading)}", MaterialSymbols.Filled.Palette, SettingsSearchDestination.General("general_theme_mode"), listOf("theme", "light", "dark")),
            searchEntry("general.color_style", R.string.settings_theme_color_heading, R.string.settings_theme_color_desc, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_theme_heading)}", MaterialSymbols.Filled.Palette, SettingsSearchDestination.General("general_theme_color"), listOf("color", "palette", "dynamic color")),
            searchEntry("general.backup", R.string.brbs_backup_title, R.string.settings_import_export_desc, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.brbs_title)}", MaterialSymbols.Filled.Backup, SettingsSearchDestination.General("general_backup"), listOf("backup", "restore")),
            searchEntry("general.logs", R.string.settings_enable_logs, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.FormatListBulleted, SettingsSearchDestination.General("general_logs"), listOf("logs")),
            searchEntry("general.autostart", R.string.settings_autostart_bootup_heading, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.PlayArrow, SettingsSearchDestination.General("general_autostart")),
            searchEntry("general.tombstone", R.string.tombstone_app_title, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.DeleteOutline, SettingsSearchDestination.General("general_tombstone"), listOf("remember uninstalled")),
            searchEntry("general.firewall_bubble", R.string.firewall_bubble_title, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.ChatBubble, SettingsSearchDestination.General("general_firewall_bubble")),
            searchEntry("general.ip_info", R.string.download_ip_info_title, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.Public, SettingsSearchDestination.General("general_ip_info")),
            searchEntry("general.app_updates", R.string.settings_check_update_heading, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.SystemUpdate, SettingsSearchDestination.General("general_app_updates"), listOf("updates")),
            searchEntry("general.crash_reports", R.string.settings_firebase_error_reporting_heading, R.string.settings_general_customize, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.settings_general_header)}", MaterialSymbols.Filled.Settings, SettingsSearchDestination.General("general_crash_reports"), listOf("firebase", "error reporting")),
            searchEntry("general.website", R.string.about_website, R.string.title_about, "$system > ${stringResource(R.string.title_settings)} > ${stringResource(R.string.title_about)}", MaterialSymbols.Filled.Tune, SettingsSearchDestination.General("general_website"), listOf("website"))
        )

    val advancedEntries =
        if (isDebug) {
            listOf(
                SettingsSearchIndexEntry(
                    id = "top.advanced",
                    title = stringResource(R.string.lbl_advanced),
                    subtitle = stringResource(R.string.adv_set_experimental_desc),
                    path = "$system > ${stringResource(R.string.lbl_advanced)}",
                    icon = MaterialSymbols.Filled.Science,
                    destination = SettingsSearchDestination.Advanced,
                    keywords = listOf("experimental", "debug")
                )
            )
        } else {
            emptyList()
        }

    return topLevel + dnsEntries + firewallEntries + proxyEntries + networkEntries + generalEntries + advancedEntries
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

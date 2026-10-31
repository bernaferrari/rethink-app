/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

/**
 * DNS › Advanced: every expert setting that used to sit under the resolver choice. Same
 * preferences and callbacks as before; only the place changed. Smart DNS ("Automatic server
 * selection") and the Rethink DNS network (Sky/Max) live here, and so does the fallback DNS that
 * used to be in Network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDnsAdvancedScreen(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    navigation: RethinkDnsSettingsNavigation,
    onCustomDownloaderChange: (Boolean) -> Unit,
    onPeriodicUpdateChange: (Boolean) -> Unit,
    onDnsAlgChange: (Boolean) -> Unit,
    onSplitDnsChange: (Boolean) -> Unit,
    onRulesAsFirewallChange: (Boolean) -> Unit,
    onFaviconsChange: (Boolean) -> Unit,
    onDnsCacheChange: (Boolean) -> Unit,
    onProxyDnsChange: (Boolean) -> Unit,
    onUndelegatedDomainsChange: (Boolean) -> Unit,
    onFallbackDnsChange: (Boolean) -> Unit,
    onBlockFreeModeChange: (RethinkBlockFreeDnsMode) -> Unit,
    onPreventLeaksChange: (Boolean) -> Unit,
    onBackClick: (() -> Unit)? = null,
    focusedSettingId: String? = null,
    modifier: Modifier = Modifier,
    /** Shown under Automatic server selection while it is in use (the Smart DNS profile). */
    smartDnsSettings: (@Composable () -> Unit)? = null,
    /** Host-owned rows at the top of "More" (captive portals). */
    additionalSettings: (@Composable () -> Unit)? = null,
) {
    val motion = LocalRethinkMotion.current
    val listState = rememberLazyListState()
    LaunchedEffect(focusedSettingId) {
        dnsAdvancedSectionFor(focusedSettingId)?.let {
            if (motion.reducedMotion) listState.scrollToItem(it) else listState.animateScrollToItem(it)
        }
    }

    RethinkTopBarLazyColumnScreen(
        title = strings.advancedTitle,
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
    ) {
        rethinkSection(strings.serversSection, key = "servers") {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.autoSelect,
                    supporting = strings.autoSelectDescription,
                    leadingIcon = MaterialSymbols.Filled.AutoAwesome,
                    position = CardPosition.Single,
                    highlighted = focusedSettingId == "dns_mode_smart" || state.isSmartDnsEnabled,
                    trailing = { RethinkDnsStatusText(if (state.isSmartDnsEnabled) strings.inUse else strings.use, state.isSmartDnsEnabled) },
                    // In use: list its servers. Otherwise: switch to it, as the old Smart DNS card did.
                    onClick = if (state.isSmartDnsEnabled) navigation.onSmartDnsInfo else navigation.onSmartDns,
                )
            }
            if (state.isSmartDnsEnabled) smartDnsSettings?.invoke()
        }
        rethinkSection(strings.rethinkNetwork, key = "rethink_network") {
            RethinkListGroup(Modifier.selectableGroup()) {
                RethinkRadioListItem(
                    headline = strings.rethinkFastest,
                    supporting = strings.rethinkFastestDescription,
                    selected = !state.isRethinkMax,
                    onSelect = { if (state.isRethinkMax) navigation.onRethinkNetwork(false) },
                    leadingIcon = MaterialSymbols.Filled.Public,
                    position = CardPosition.First,
                )
                RethinkRadioListItem(
                    headline = strings.rethinkPrivate,
                    supporting = strings.rethinkPrivateDescription,
                    selected = state.isRethinkMax,
                    onSelect = { if (!state.isRethinkMax) navigation.onRethinkNetwork(true) },
                    leadingIcon = MaterialSymbols.Filled.Shield,
                    position = CardPosition.Last,
                )
            }
        }
        rethinkSection(strings.downloadsSection, key = "downloads") {
            RethinkListGroup {
                RethinkDnsToggleRow(strings.customDownloader, strings.customDownloaderDescription, state.useCustomDownloadManager, MaterialSymbols.Filled.Download, CardPosition.First, focusedSettingId == "dns_block_custom_downloader", onCustomDownloaderChange)
                RethinkDnsToggleRow(strings.periodicUpdates, strings.periodicUpdatesDescription, state.periodicallyCheckBlocklistUpdate, MaterialSymbols.Filled.Refresh, CardPosition.Last, focusedSettingId == "dns_block_periodic_updates", onPeriodicUpdateChange)
            }
        }
        rethinkSection(strings.filteringSection, key = "filtering") {
            val rows = buildList {
                add(RethinkDnsToggle(strings.dnsAlg, strings.dnsAlgDescription, state.enableDnsAlg, MaterialSymbols.Filled.Dns, "dns_filter_alg", onDnsAlgChange))
                if (state.showSplitDns) add(RethinkDnsToggle(strings.splitDns, strings.splitDnsDescription, state.splitDns, MaterialSymbols.Filled.CallSplit, "dns_filter_split", onSplitDnsChange))
                if (state.showBypassDnsBlock) add(RethinkDnsToggle(strings.rulesAsFirewall, strings.rulesAsFirewallDescription, state.bypassBlockInDns, KeepoutIcons.Firewall, "dns_filter_rules_as_firewall", onRulesAsFirewallChange))
            }
            RethinkListGroup {
                rows.forEachIndexed { index, row ->
                    RethinkDnsToggleRow(row.title, row.description, row.checked, row.icon, cardPositionFor(index, rows.lastIndex + 1), focusedSettingId == row.id, row.onChange)
                }
                RethinkListItem(
                    headline = strings.recordTypes,
                    supporting = strings.recordTypesDescription,
                    leadingIcon = MaterialSymbols.Filled.Dns,
                    position = CardPosition.Last,
                    highlighted = focusedSettingId == "dns_filter_record_types",
                    trailing = { RethinkDnsStatusText(if (state.dnsRecordTypesAutoMode) strings.auto else state.allowedDnsRecordTypesSize.toString(), true) },
                    onClick = navigation.onRecordTypes,
                )
            }
        }
        rethinkSection(strings.blockFreeSection, key = "trusted") {
            RethinkListGroup {
                Column(Modifier.selectableGroup()) {
                    RethinkBlockFreeDnsMode.entries.forEachIndexed { index, mode ->
                        RethinkRadioListItem(
                            headline = strings.blockFreeLabel(mode),
                            supporting = strings.blockFreeDescription(mode),
                            selected = state.blockFreeDnsMode == mode,
                            onSelect = { onBlockFreeModeChange(mode) },
                            leadingIcon = MaterialSymbols.Filled.Dns,
                            position = cardPositionFor(index, RethinkBlockFreeDnsMode.entries.lastIndex + 1),
                        )
                    }
                }
                RethinkListItem(
                    headline = strings.trustedEndpoint,
                    supporting = strings.trustedEndpointDescription,
                    leadingIcon = MaterialSymbols.Filled.Verified,
                    position = CardPosition.Last,
                    showTrailingChevron = true,
                    onClick = navigation.onTrustedEndpoint,
                )
            }
        }
        rethinkSection(strings.fallbackSection, key = "fallback") {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.fallbackServer,
                    supporting = strings.fallbackServerDescription,
                    leadingIcon = MaterialSymbols.Filled.Dns,
                    position = CardPosition.First,
                    highlighted = focusedSettingId == "dns_advanced_default_dns",
                    trailing = state.fallbackDnsName.takeIf { it.isNotBlank() }?.let { name -> { RethinkDnsStatusText(name, true) } },
                    onClick = navigation.onFallbackDns,
                )
                RethinkDnsToggleRow(strings.fallbackDns, strings.fallbackDnsDescription, state.useFallbackDnsToBypass, MaterialSymbols.Filled.Dns, CardPosition.Last, focusedSettingId == "dns_advanced_fallback", onFallbackDnsChange)
            }
        }
        rethinkSection(strings.moreSection, key = "more") {
            additionalSettings?.invoke()
            val rows = listOf(
                RethinkDnsToggle(strings.favicons, strings.faviconsDescription, state.fetchFavIcon, MaterialSymbols.Filled.Public, "dns_advanced_favicon", onFaviconsChange),
                RethinkDnsToggle(strings.dnsCache, strings.dnsCacheDescription, state.enableDnsCache, MaterialSymbols.Filled.Database, "dns_advanced_cache", onDnsCacheChange),
                RethinkDnsToggle(strings.proxyDns, strings.proxyDnsDescription, !state.proxyDns, MaterialSymbols.Filled.Dns, "dns_advanced_proxy_dns") { onProxyDnsChange(!it) },
                RethinkDnsToggle(strings.undelegatedDomains, strings.undelegatedDomainsDescription, state.useSystemDnsForUndelegatedDomains, MaterialSymbols.Filled.Public, "dns_advanced_undelegated", onUndelegatedDomainsChange),
                RethinkDnsToggle(strings.preventLeaks, strings.preventLeaksDescription, state.preventDnsLeaks, MaterialSymbols.Filled.Shield, "dns_advanced_leaks", onPreventLeaksChange),
            )
            RethinkListGroup {
                rows.forEachIndexed { index, row ->
                    RethinkDnsToggleRow(row.title, row.description, row.checked, row.icon, cardPositionFor(index, rows.lastIndex), focusedSettingId == row.id, row.onChange)
                }
            }
        }
    }
}

private fun dnsAdvancedSectionFor(id: String?): Int? = when (id) {
    null, "" -> null
    // Item indices: 0 servers, 1 Rethink DNS network, 2 downloads, 3 filtering, 4 trusted domains,
    // 5 fallback, 6 more.
    "dns_mode_smart" -> 0
    "dns_adv_rethink_network" -> 1
    "dns_block_custom_downloader", "dns_block_periodic_updates" -> 2
    "dns_filtering", "dns_filter_alg", "dns_filter_split", "dns_filter_rules_as_firewall", "dns_filter_record_types" -> 3
    "dns_trusted", "dns_block_free" -> 4
    "dns_advanced_default_dns", "dns_advanced_fallback" -> 5
    else -> 6
}

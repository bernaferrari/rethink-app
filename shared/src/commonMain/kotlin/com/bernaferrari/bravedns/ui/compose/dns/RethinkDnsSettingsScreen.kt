@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusIndicator
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion

enum class RethinkDnsType { Doh, RethinkRemote, SmartDns, Dot, Odoh, DnsCrypt, DnsProxy, System }
enum class RethinkBlockFreeDnsMode { Auto, Global, Fallback }

data class RethinkDnsSettingsState(
    val connectedDnsName: String = "--",
    val dnsLatency: String = "",
    val dnsType: RethinkDnsType = RethinkDnsType.Doh,
    val isSmartDnsEnabled: Boolean = false,
    val isSystemDnsEnabled: Boolean = false,
    val isRethinkDnsConnected: Boolean = false,
    val fetchFavIcon: Boolean = false,
    val preventDnsLeaks: Boolean = false,
    val enableDnsAlg: Boolean = false,
    val periodicallyCheckBlocklistUpdate: Boolean = false,
    val useCustomDownloadManager: Boolean = false,
    val enableDnsCache: Boolean = false,
    val proxyDns: Boolean = false,
    val useSystemDnsForUndelegatedDomains: Boolean = false,
    val useFallbackDnsToBypass: Boolean = false,
    val blockFreeDnsMode: RethinkBlockFreeDnsMode = RethinkBlockFreeDnsMode.Auto,
    val blocklistEnabled: Boolean = false,
    val numberOfLocalBlocklists: Int = 0,
    val bypassBlockInDns: Boolean = false,
    val splitDns: Boolean = false,
    val dnsRecordTypesAutoMode: Boolean = false,
    val allowedDnsRecordTypesSize: Int = 0,
    val showSplitDns: Boolean = false,
    val showBypassDnsBlock: Boolean = false,
    val isRefreshing: Boolean = false,
)

data class RethinkDnsSettingsStrings(
    val title: String,
    val refresh: String,
    val modesSection: String,
    val systemDns: String,
    val systemDnsDescription: String,
    val customDns: String,
    val customDnsDescription: String,
    val rethinkDns: String,
    val rethinkDnsDescription: String,
    val smartDns: String,
    val smartDnsDescription: String,
    val connectedDescription: String,
    val blockSection: String,
    val localBlocklists: String,
    val localBlocklistsDescription: @Composable (Int) -> String,
    val localBlocklistsDisabledDescription: String,
    val enabled: String,
    val disabled: String,
    val customDownloader: String,
    val customDownloaderDescription: String,
    val periodicUpdates: String,
    val periodicUpdatesDescription: String,
    val filteringSection: String,
    val dnsAlg: String,
    val dnsAlgDescription: String,
    val splitDns: String,
    val splitDnsDescription: String,
    val rulesAsFirewall: String,
    val rulesAsFirewallDescription: String,
    val recordTypes: String,
    val recordTypesDescription: String,
    val auto: String,
    val blockFreeSection: String,
    val blockFreeLabel: @Composable (RethinkBlockFreeDnsMode) -> String,
    val blockFreeDescription: @Composable (RethinkBlockFreeDnsMode) -> String,
    val trustedEndpoint: String,
    val trustedEndpointDescription: String,
    val advancedSection: String,
    val favicons: String,
    val faviconsDescription: String,
    val dnsCache: String,
    val dnsCacheDescription: String,
    val proxyDns: String,
    val proxyDnsDescription: String,
    val undelegatedDomains: String,
    val undelegatedDomainsDescription: String,
    val fallbackDns: String,
    val fallbackDnsDescription: String,
    val preventLeaks: String,
    val preventLeaksDescription: String,
    /** Accessibility label for a row's info button, e.g. "About System DNS". */
    val aboutItem: @Composable (String) -> String,
)

/** All DNS settings chrome and interactions, portable across Android and the WASM demo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDnsSettingsScreen(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    onRefresh: () -> Unit,
    onSystemDns: () -> Unit,
    onSystemDnsInfo: () -> Unit,
    onCustomDns: () -> Unit,
    onRethinkDns: () -> Unit,
    onSmartDns: () -> Unit,
    onSmartDnsInfo: () -> Unit,
    onLocalBlocklists: () -> Unit,
    onCustomDownloaderChange: (Boolean) -> Unit,
    onPeriodicUpdateChange: (Boolean) -> Unit,
    onDnsAlgChange: (Boolean) -> Unit,
    onSplitDnsChange: (Boolean) -> Unit,
    onRulesAsFirewallChange: (Boolean) -> Unit,
    onRecordTypes: () -> Unit,
    onFaviconsChange: (Boolean) -> Unit,
    onDnsCacheChange: (Boolean) -> Unit,
    onProxyDnsChange: (Boolean) -> Unit,
    onUndelegatedDomainsChange: (Boolean) -> Unit,
    onFallbackDnsChange: (Boolean) -> Unit,
    onBlockFreeModeChange: (RethinkBlockFreeDnsMode) -> Unit,
    onTrustedEndpoint: () -> Unit,
    onPreventLeaksChange: (Boolean) -> Unit,
    onBackClick: (() -> Unit)? = null,
    focusedSettingId: String? = null,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val listState = rememberLazyListState()
    LaunchedEffect(focusedSettingId) {
        dnsSettingsSectionFor(focusedSettingId)?.let {
            if (motion.reducedMotion) listState.scrollToItem(it) else listState.animateScrollToItem(it)
        }
    }

    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
        topBarActions = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                if (state.isRefreshing) {
                    LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
                } else {
                    Icon(MaterialSymbols.Filled.Refresh, strings.refresh)
                }
            }
        },
    ) {
        item {
            Column {
                SectionHeader(strings.modesSection)
                RethinkListGroup(Modifier.selectableGroup()) {
                    RethinkDnsRadioRow(strings.systemDns, strings.systemDnsDescription, state.isSystemDnsEnabled, MaterialSymbols.Filled.Settings, CardPosition.First, focusedSettingId == "dns_mode_system", onSystemDns, onSystemDnsInfo, strings.aboutItem(strings.systemDns))
                    RethinkDnsRadioRow(strings.customDns, strings.customDnsDescription, !state.isSystemDnsEnabled && !state.isRethinkDnsConnected && !state.isSmartDnsEnabled, MaterialSymbols.Filled.Dns, CardPosition.Middle, focusedSettingId == "dns_mode_custom", onCustomDns)
                    RethinkDnsRadioRow(strings.rethinkDns, strings.rethinkDnsDescription, state.isRethinkDnsConnected, MaterialSymbols.Filled.Security, CardPosition.Middle, focusedSettingId == "dns_mode_rethink", onRethinkDns)
                    RethinkDnsRadioRow(strings.smartDns, strings.smartDnsDescription, state.isSmartDnsEnabled, MaterialSymbols.Filled.Language, CardPosition.Last, focusedSettingId == "dns_mode_smart", onSmartDns, onSmartDnsInfo, strings.aboutItem(strings.smartDns))
                }
                // Part of the modes item so focus indices in dnsSettingsSectionFor stay stable.
                if (state.isRethinkDnsConnected) {
                    RethinkDnsConnectionCard(
                        state,
                        strings,
                        focusedSettingId == "dns_mode_rethink",
                        Modifier.padding(top = SharedDimensions.spacingSm),
                    )
                }
            }
        }
        item {
            Column {
                SectionHeader(strings.blockSection)
                RethinkListGroup {
                    RethinkListItem(
                        headline = strings.localBlocklists,
                        supporting = if (state.blocklistEnabled) strings.localBlocklistsDescription(state.numberOfLocalBlocklists) else strings.localBlocklistsDisabledDescription,
                        leadingIcon = MaterialSymbols.Filled.Security,
                        position = CardPosition.First,
                        highlighted = focusedSettingId == "dns_block_local",
                        trailing = { RethinkDnsStatusText(if (state.blocklistEnabled) strings.enabled else strings.disabled, state.blocklistEnabled) },
                        onClick = onLocalBlocklists,
                    )
                    RethinkDnsToggleRow(strings.customDownloader, strings.customDownloaderDescription, state.useCustomDownloadManager, MaterialSymbols.Filled.Storage, CardPosition.Middle, focusedSettingId == "dns_block_custom_downloader", onCustomDownloaderChange)
                    RethinkDnsToggleRow(strings.periodicUpdates, strings.periodicUpdatesDescription, state.periodicallyCheckBlocklistUpdate, MaterialSymbols.Filled.Refresh, CardPosition.Last, focusedSettingId == "dns_block_periodic_updates", onPeriodicUpdateChange)
                }
            }
        }
        item {
            val rows = buildList {
                add(RethinkDnsToggle(strings.dnsAlg, strings.dnsAlgDescription, state.enableDnsAlg, MaterialSymbols.Filled.Dns, "dns_filter_alg", onDnsAlgChange))
                if (state.showSplitDns) add(RethinkDnsToggle(strings.splitDns, strings.splitDnsDescription, state.splitDns, MaterialSymbols.Filled.Language, "dns_filter_split", onSplitDnsChange))
                if (state.showBypassDnsBlock) add(RethinkDnsToggle(strings.rulesAsFirewall, strings.rulesAsFirewallDescription, state.bypassBlockInDns, MaterialSymbols.Filled.Security, "dns_filter_rules_as_firewall", onRulesAsFirewallChange))
            }
            Column {
                SectionHeader(strings.filteringSection)
                RethinkListGroup {
                    rows.forEachIndexed { index, row ->
                        RethinkDnsToggleRow(row.title, row.description, row.checked, row.icon, cardPositionFor(index, rows.lastIndex + 1), focusedSettingId == row.id, row.onChange)
                    }
                    RethinkListItem(
                        headline = strings.recordTypes,
                        supporting = strings.recordTypesDescription,
                        leadingIcon = MaterialSymbols.Filled.Public,
                        position = CardPosition.Last,
                        highlighted = focusedSettingId == "dns_filter_record_types",
                        trailing = { RethinkDnsStatusText(if (state.dnsRecordTypesAutoMode) strings.auto else state.allowedDnsRecordTypesSize.toString(), true) },
                        onClick = onRecordTypes,
                    )
                }
            }
        }
        item {
            Column {
                SectionHeader(strings.blockFreeSection)
                RethinkListGroup {
                    Column(Modifier.selectableGroup()) {
                        RethinkBlockFreeDnsMode.entries.forEachIndexed { index, mode ->
                            RethinkDnsRadioRow(strings.blockFreeLabel(mode), strings.blockFreeDescription(mode), state.blockFreeDnsMode == mode, MaterialSymbols.Filled.Dns, cardPositionFor(index, RethinkBlockFreeDnsMode.entries.lastIndex + 1), false, { onBlockFreeModeChange(mode) })
                        }
                    }
                    RethinkListItem(
                        headline = strings.trustedEndpoint,
                        supporting = strings.trustedEndpointDescription,
                        leadingIcon = MaterialSymbols.Filled.Security,
                        position = CardPosition.Last,
                        showTrailingChevron = true,
                        onClick = onTrustedEndpoint,
                    )
                }
            }
        }
        item {
            val rows = listOf(
                RethinkDnsToggle(strings.favicons, strings.faviconsDescription, state.fetchFavIcon, MaterialSymbols.Filled.Language, "dns_advanced_favicon", onFaviconsChange),
                RethinkDnsToggle(strings.dnsCache, strings.dnsCacheDescription, state.enableDnsCache, MaterialSymbols.Filled.Storage, "dns_advanced_cache", onDnsCacheChange),
                RethinkDnsToggle(strings.proxyDns, strings.proxyDnsDescription, !state.proxyDns, MaterialSymbols.Filled.Dns, "dns_advanced_proxy_dns") { onProxyDnsChange(!it) },
                RethinkDnsToggle(strings.undelegatedDomains, strings.undelegatedDomainsDescription, state.useSystemDnsForUndelegatedDomains, MaterialSymbols.Filled.Public, "dns_advanced_undelegated", onUndelegatedDomainsChange),
                RethinkDnsToggle(strings.fallbackDns, strings.fallbackDnsDescription, state.useFallbackDnsToBypass, MaterialSymbols.Filled.Security, "dns_advanced_fallback", onFallbackDnsChange),
                RethinkDnsToggle(strings.preventLeaks, strings.preventLeaksDescription, state.preventDnsLeaks, MaterialSymbols.Filled.Security, "dns_advanced_leaks", onPreventLeaksChange),
            )
            Column {
                SectionHeader(strings.advancedSection)
                RethinkListGroup {
                    rows.forEachIndexed { index, row ->
                        RethinkDnsToggleRow(row.title, row.description, row.checked, row.icon, cardPositionFor(index, rows.lastIndex), focusedSettingId == row.id, row.onChange)
                    }
                }
            }
        }
    }
}

private data class RethinkDnsToggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val icon: ImageVector,
    val id: String,
    val onChange: (Boolean) -> Unit,
)

@Composable
private fun RethinkDnsRadioRow(
    title: String,
    description: String,
    selected: Boolean,
    icon: ImageVector,
    position: CardPosition,
    highlighted: Boolean,
    onClick: () -> Unit,
    onInfo: (() -> Unit)? = null,
    infoDescription: String? = null,
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        position = position,
        highlighted = highlighted,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfo != null) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            MaterialSymbols.Filled.Info,
                            infoDescription,
                            Modifier.size(SharedDimensions.iconSizeSm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The complete row is the selection target. A passive indicator avoids exposing
                // two identical actions to keyboard and screen-reader users.
                RadioButton(selected = selected, onClick = null)
            }
        },
        selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onClick() },
    )
}

@Composable
private fun RethinkDnsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    icon: ImageVector,
    position: CardPosition,
    highlighted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = RethinkSwitchListItem(
    headline = title,
    checked = checked,
    onCheckedChange = onCheckedChange,
    supporting = description,
    leadingIcon = icon,
    position = position,
    highlighted = highlighted,
)

@Composable
private fun RethinkDnsConnectionCard(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val endpoint = state.connectedDnsName.substringAfter(',', "").trim()
    val displayName = state.connectedDnsName.substringBefore(',').trim().ifBlank { "--" }
    val protocol = state.dnsType.protocol(endpoint)
    // "Rethink DNS" above "RethinkDNS" says the same thing twice; keep the overline only when it
    // adds context to the resolver name.
    val overline = strings.rethinkDns.takeUnless { it.isRedundantWith(displayName) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            // Same 32dp icon container + 16dp inset as the grouped rows above, so the card's
            // leading edge lines up with the radio rows' icons.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                RethinkIconWell(MaterialSymbols.Filled.Dns, size = SharedDimensions.iconContainerSm)
                Column(Modifier.weight(1f)) {
                    overline?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.dnsLatency.trim('(', ')', ' ').takeIf { it.isNotBlank() }?.let { latency ->
                    val tone = latencyTone(latency)
                    RethinkPill(latency, colors = RethinkPillDefaults.status(tone))
                }
            }
            Text(strings.connectedDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                RethinkPill(protocol, colors = RethinkPillDefaults.secondary())
                endpoint.takeIf { it.isNotBlank() }?.let {
                    RethinkPill(it, Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/** Latency reads as a status: fast uses the allowed role, sluggish mixed, very slow blocked. */
@Composable
private fun latencyTone(latency: String): Color {
    val ms = latency.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()
        ?: return RethinkStatusColors.idle
    return when {
        ms < FAST_LATENCY_MS -> RethinkStatusColors.allowed
        ms < SLOW_LATENCY_MS -> RethinkStatusColors.mixed
        else -> RethinkStatusColors.blocked
    }
}

private const val FAST_LATENCY_MS = 150.0
private const val SLOW_LATENCY_MS = 500.0

@Composable
private fun RethinkDnsStatusText(text: String, enabled: Boolean) = RethinkStatusLabel(
    label = text,
    color = if (enabled) RethinkStatusColors.allowed else RethinkStatusColors.idle,
    indicator = RethinkStatusIndicator.None,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
)

private fun RethinkDnsType.protocol(endpoint: String): String = when (this) {
    RethinkDnsType.Doh, RethinkDnsType.RethinkRemote, RethinkDnsType.SmartDns -> if (endpoint.startsWith("https://", true)) "HTTPS" else if (endpoint.startsWith("http://", true)) "HTTP" else "DNS"
    RethinkDnsType.Dot -> "DoT"
    RethinkDnsType.Odoh -> "ODoH"
    RethinkDnsType.DnsCrypt -> "DNSCrypt"
    RethinkDnsType.DnsProxy -> "DNS Proxy"
    RethinkDnsType.System -> "System DNS"
}

private fun dnsSettingsSectionFor(id: String?): Int? = when (id) {
    null, "" -> null
    "dns_mode", "dns_mode_system", "dns_mode_custom", "dns_mode_rethink", "dns_mode_smart" -> 0
    "dns_blocklist", "dns_block_local", "dns_block_custom_downloader", "dns_block_periodic_updates" -> 1
    "dns_filtering", "dns_filter_alg", "dns_filter_split", "dns_filter_rules_as_firewall", "dns_filter_record_types" -> 2
    else -> 4
}

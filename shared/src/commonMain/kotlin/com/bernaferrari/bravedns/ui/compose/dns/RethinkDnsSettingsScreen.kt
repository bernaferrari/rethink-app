/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

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
)

/** All DNS settings chrome and interactions, portable across Android and the WASM demo. */
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
    val listState = rememberLazyListState()
    val rotation by animateFloatAsState(
        targetValue = if (state.isRefreshing) 360f else 0f,
        animationSpec = if (state.isRefreshing) infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart) else tween(0),
        label = "dns_settings_refresh",
    )
    LaunchedEffect(focusedSettingId) {
        dnsSettingsSectionFor(focusedSettingId)?.let { listState.animateScrollToItem(it) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(MaterialSymbols.Filled.Refresh, strings.refresh, Modifier.rotate(rotation))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item {
                Column {
                    SectionHeader(strings.modesSection)
                    RethinkListGroup {
                        RethinkDnsRadioRow(strings.systemDns, strings.systemDnsDescription, state.isSystemDnsEnabled, MaterialSymbols.Filled.Settings, CardPosition.First, focusedSettingId == "dns_mode_system", onSystemDns, onSystemDnsInfo)
                        RethinkDnsRadioRow(strings.customDns, strings.customDnsDescription, !state.isSystemDnsEnabled && !state.isRethinkDnsConnected && !state.isSmartDnsEnabled, MaterialSymbols.Filled.Dns, CardPosition.Middle, focusedSettingId == "dns_mode_custom", onCustomDns)
                        RethinkDnsRadioRow(strings.rethinkDns, strings.rethinkDnsDescription, state.isRethinkDnsConnected, MaterialSymbols.Filled.Security, CardPosition.Middle, focusedSettingId == "dns_mode_rethink", onRethinkDns)
                        RethinkDnsRadioRow(strings.smartDns, strings.smartDnsDescription, state.isSmartDnsEnabled, MaterialSymbols.Filled.Language, CardPosition.Last, focusedSettingId == "dns_mode_smart", onSmartDns, onSmartDnsInfo)
                    }
                }
            }
            if (state.isRethinkDnsConnected) {
                item { RethinkDnsConnectionCard(state, strings, focusedSettingId == "dns_mode_rethink") }
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
                        RethinkBlockFreeDnsMode.entries.forEachIndexed { index, mode ->
                            RethinkDnsRadioRow(strings.blockFreeLabel(mode), strings.blockFreeDescription(mode), state.blockFreeDnsMode == mode, MaterialSymbols.Filled.Dns, cardPositionFor(index, RethinkBlockFreeDnsMode.entries.lastIndex + 1), false, { onBlockFreeModeChange(mode) })
                        }
                        RethinkListItem(
                            headline = strings.trustedEndpoint,
                            supporting = strings.trustedEndpointDescription,
                            leadingIcon = MaterialSymbols.Filled.Security,
                            position = CardPosition.Last,
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
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        position = position,
        highlighted = highlighted,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfo != null) IconButton(onClick = onInfo) { Icon(MaterialSymbols.Filled.Public, null, Modifier.size(18.dp)) }
                RadioButton(selected = selected, onClick = onClick)
            }
        },
        onClick = onClick,
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
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        position = position,
        highlighted = highlighted,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun RethinkDnsConnectionCard(state: RethinkDnsSettingsState, strings: RethinkDnsSettingsStrings, highlighted: Boolean) {
    val endpoint = state.connectedDnsName.substringAfter(',', "").trim()
    val displayName = state.connectedDnsName.substringBefore(',').trim().ifBlank { "--" }
    val protocol = state.dnsType.protocol(endpoint)
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                Icon(MaterialSymbols.Filled.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(strings.rethinkDns, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.dnsLatency.trim('(', ')').takeIf { it.isNotBlank() }?.let { RethinkDnsPill(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
            }
            Text(strings.connectedDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                RethinkDnsPill(protocol, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                endpoint.takeIf { it.isNotBlank() }?.let { RethinkDnsPill(it, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun RethinkDnsPill(text: String, container: Color, content: Color) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusPill), color = container) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RethinkDnsStatusText(text: String, enabled: Boolean) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}

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

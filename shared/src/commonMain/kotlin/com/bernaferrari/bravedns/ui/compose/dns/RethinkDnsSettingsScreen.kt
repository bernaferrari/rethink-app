@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
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
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable

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
    /** Recent answered lookups' latencies, newest first (from the DNS log). */
    val recentLatencies: List<Long> = emptyList(),
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
    val inUse: String = "In use",
    val medianLookup: String = "Median lookup time",
    val recentLookups: String = "Last lookups",
    val noLookupsYet: String = "No lookups yet",
)

/** All DNS settings chrome and interactions, portable across Android and the WASM demo. */
data class RethinkDnsSettingsNavigation(
    val onRefresh: () -> Unit,
    val onSystemDns: () -> Unit,
    val onSystemDnsInfo: () -> Unit,
    val onCustomDns: () -> Unit,
    val onRethinkDns: () -> Unit,
    val onSmartDns: () -> Unit,
    val onSmartDnsInfo: () -> Unit,
    val onLocalBlocklists: () -> Unit,
    val onRecordTypes: () -> Unit,
    val onTrustedEndpoint: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDnsSettingsScreen(
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
    additionalSettings: (@Composable () -> Unit)? = null,
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
            DnsRefreshAction(state.isRefreshing, strings.refresh, navigation.onRefresh)
        },
    ) {
        item {
            // System and Smart DNS have servers worth listing; the hero opens them for whichever is in use.
            val details = when {
                state.isSystemDnsEnabled -> navigation.onSystemDnsInfo
                state.isSmartDnsEnabled -> navigation.onSmartDnsInfo
                else -> null
            }
            DnsInUseHero(state, strings, highlighted = focusedSettingId == "dns_mode_rethink", onDetails = details)
        }
        rethinkSection(strings.modesSection) {
            DnsResolverChoices(state, strings, navigation, focusedSettingId)
        }
        rethinkSection(strings.blockSection) {
            RethinkListGroup {
                DnsLocalBlocklistRow(state, strings, focusedSettingId == "dns_block_local", navigation.onLocalBlocklists)
                RethinkDnsToggleRow(strings.customDownloader, strings.customDownloaderDescription, state.useCustomDownloadManager, MaterialSymbols.Filled.Storage, CardPosition.Middle, focusedSettingId == "dns_block_custom_downloader", onCustomDownloaderChange)
                RethinkDnsToggleRow(strings.periodicUpdates, strings.periodicUpdatesDescription, state.periodicallyCheckBlocklistUpdate, MaterialSymbols.Filled.Refresh, CardPosition.Last, focusedSettingId == "dns_block_periodic_updates", onPeriodicUpdateChange)
            }
        }
        rethinkSection(strings.filteringSection) {
            val rows = buildList {
                add(RethinkDnsToggle(strings.dnsAlg, strings.dnsAlgDescription, state.enableDnsAlg, MaterialSymbols.Filled.Dns, "dns_filter_alg", onDnsAlgChange))
                if (state.showSplitDns) add(RethinkDnsToggle(strings.splitDns, strings.splitDnsDescription, state.splitDns, MaterialSymbols.Filled.Language, "dns_filter_split", onSplitDnsChange))
                if (state.showBypassDnsBlock) add(RethinkDnsToggle(strings.rulesAsFirewall, strings.rulesAsFirewallDescription, state.bypassBlockInDns, MaterialSymbols.Filled.Security, "dns_filter_rules_as_firewall", onRulesAsFirewallChange))
            }
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
                    onClick = navigation.onRecordTypes,
                )
            }
        }
        rethinkSection(strings.blockFreeSection) {
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
                    leadingIcon = MaterialSymbols.Filled.Security,
                    position = CardPosition.Last,
                    showTrailingChevron = true,
                    onClick = navigation.onTrustedEndpoint,
                )
            }
        }
        if (additionalSettings != null) item { additionalSettings() }
        rethinkSection(strings.advancedSection) {
            val rows = listOf(
                RethinkDnsToggle(strings.favicons, strings.faviconsDescription, state.fetchFavIcon, MaterialSymbols.Filled.Language, "dns_advanced_favicon", onFaviconsChange),
                RethinkDnsToggle(strings.dnsCache, strings.dnsCacheDescription, state.enableDnsCache, MaterialSymbols.Filled.Storage, "dns_advanced_cache", onDnsCacheChange),
                RethinkDnsToggle(strings.proxyDns, strings.proxyDnsDescription, !state.proxyDns, MaterialSymbols.Filled.Dns, "dns_advanced_proxy_dns") { onProxyDnsChange(!it) },
                RethinkDnsToggle(strings.undelegatedDomains, strings.undelegatedDomainsDescription, state.useSystemDnsForUndelegatedDomains, MaterialSymbols.Filled.Public, "dns_advanced_undelegated", onUndelegatedDomainsChange),
                RethinkDnsToggle(strings.fallbackDns, strings.fallbackDnsDescription, state.useFallbackDnsToBypass, MaterialSymbols.Filled.Security, "dns_advanced_fallback", onFallbackDnsChange),
                RethinkDnsToggle(strings.preventLeaks, strings.preventLeaksDescription, state.preventDnsLeaks, MaterialSymbols.Filled.Security, "dns_advanced_leaks", onPreventLeaksChange),
            )
            RethinkListGroup {
                rows.forEachIndexed { index, row ->
                    RethinkDnsToggleRow(row.title, row.description, row.checked, row.icon, cardPositionFor(index, rows.lastIndex), focusedSettingId == row.id, row.onChange)
                }
            }
        }
    }
}

@Composable
private fun DnsRefreshAction(isRefreshing: Boolean, refreshLabel: String, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
        if (isRefreshing) {
            LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
        } else {
            Icon(MaterialSymbols.Filled.Refresh, refreshLabel)
        }
    }
}

@Composable
private fun DnsResolverChoices(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    navigation: RethinkDnsSettingsNavigation,
    focusedSettingId: String?,
) {
            // Four resolver choices as one radio group in a 2×2 grid: every card carries a radio, and
            // the chosen one fills with the secondary container and rounds further.
            val choices = listOf(
                DnsChoice(strings.systemDns, strings.systemDnsDescription, state.isSystemDnsEnabled, MaterialSymbols.Filled.Settings, "dns_mode_system", navigation.onSystemDns),
                DnsChoice(strings.customDns, strings.customDnsDescription, !state.isSystemDnsEnabled && !state.isRethinkDnsConnected && !state.isSmartDnsEnabled, MaterialSymbols.Filled.Dns, "dns_mode_custom", navigation.onCustomDns),
                DnsChoice(strings.rethinkDns, strings.rethinkDnsDescription, state.isRethinkDnsConnected, MaterialSymbols.Filled.Security, "dns_mode_rethink", navigation.onRethinkDns),
                DnsChoice(strings.smartDns, strings.smartDnsDescription, state.isSmartDnsEnabled, MaterialSymbols.Filled.Language, "dns_mode_smart", navigation.onSmartDns),
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                choices.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                        pair.forEach { choice ->
                            DnsChoiceCard(choice, focusedSettingId == choice.id, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
}

private class DnsChoice(
    val title: String,
    val description: String,
    val selected: Boolean,
    val icon: ImageVector,
    val id: String,
    val onClick: () -> Unit,
)

@Composable
private fun DnsChoiceCard(choice: DnsChoice, highlighted: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalRethinkMotion.current
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (choice.selected) SharedDimensions.groupCornerRadius else SharedDimensions.cornerRadiusLg,
        animationSpec = if (motion.reducedMotion) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dnsChoiceCorner",
    )
    val container = when {
        choice.selected -> scheme.secondaryContainer
        highlighted -> scheme.primaryContainer.copy(alpha = .42f)
        else -> scheme.surfaceContainerLow
    }
    Surface(
        modifier = modifier.selectable(selected = choice.selected, role = Role.RadioButton, onClick = choice.onClick),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = if (choice.selected) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Column(Modifier.padding(SharedDimensions.spacingLg), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(choice.icon, null, tint = if (choice.selected) scheme.onSecondaryContainer else scheme.primary, modifier = Modifier.size(SharedDimensions.iconSizeMd))
                Spacer(Modifier.weight(1f))
                // The whole card is the target; the radio only shows the state.
                RadioButton(selected = choice.selected, onClick = null)
            }
            Text(choice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                choice.description,
                style = MaterialTheme.typography.bodySmall,
                // Full emphasis when chosen: a faded description on the selected card read as disabled.
                color = if (choice.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What answers this phone's lookups right now: the resolver as a coloured hero, its protocol and
 * endpoint, and the median latency of the last lookups with a bar for each of them.
 */
@Composable
private fun DnsInUseHero(state: RethinkDnsSettingsState, strings: RethinkDnsSettingsStrings, highlighted: Boolean, onDetails: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val endpoint = state.connectedDnsName.substringAfter(',', "").trim()
    val displayName = state.connectedDnsName.substringBefore(',').trim().ifBlank { "--" }
    val protocol = state.dnsType.protocol(endpoint)
    val latency = dnsHeroLatency(state.recentLatencies, state.dnsLatency, LATENCY_BARS)
    val latencies = latency.samples
    val latencyLabel = latency.label
    val shape = RoundedCornerShape(SharedDimensions.groupCornerRadius)
    // The brand palette keeps primaryContainer at the seed's bright tone in dark theme too; the
    // secondary container is the same hue at a dark tone, so the hero dims with the rest of the UI.
    val dark = scheme.surface.luminance() < 0.5f
    val heroContainer = if (dark) scheme.secondaryContainer else scheme.primaryContainer
    val onHero = if (dark) scheme.onSecondaryContainer else scheme.onPrimaryContainer
    Surface(
        shape = shape,
        color = if (highlighted) heroContainer else heroContainer.copy(alpha = .78f),
        contentColor = onHero,
        modifier = Modifier.fillMaxWidth().clip(shape).then(
            if (onDetails != null) Modifier.clickable(onClickLabel = strings.aboutItem(displayName), onClick = onDetails) else Modifier,
        ),
    ) {
        Column(Modifier.padding(SharedDimensions.spacingXl), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.inUse, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (onDetails != null) Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            Text(
                displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The protocol only when it says something: a bare "DNS", or one that repeats the name, doesn't.
            val showProtocol = protocol != GENERIC_PROTOCOL && !protocol.equals(displayName, ignoreCase = true)
            val showEndpoint = endpoint.isNotBlank()
            if (showProtocol || showEndpoint) {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                    if (showProtocol) RethinkPill(protocol, colors = RethinkPillDefaults.onContainer(onHero), size = RethinkPillSize.Large)
                    if (showEndpoint) {
                        RethinkPill(endpoint, Modifier.weight(1f, fill = false), colors = RethinkPillDefaults.onContainer(onHero), size = RethinkPillSize.Large)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(latencyLabel ?: "—", style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                    Text(if (latencies.isEmpty()) strings.noLookupsYet else strings.medianLookup, style = MaterialTheme.typography.bodySmall)
                }
                if (latencies.isNotEmpty()) LatencyBars(latencies.reversed(), onHero, Modifier.weight(1f).height(LatencyChartHeight))
            }
        }
    }
}

/**
 * One rounded bar per recent lookup, oldest to newest, in the hero's own on-colour: height says
 * the speed, and green/red bars on the coloured container clashed with it.
 */
@Composable
private fun LatencyBars(latencies: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val max = (latencies.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        latencies.forEach { ms ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight((ms / max).coerceIn(0.08f, 1f))
                    .background(color.copy(alpha = LATENCY_BAR_ALPHA), RoundedCornerShape(SharedDimensions.cornerRadiusFull)),
            )
        }
    }
}

private const val LATENCY_BARS = 24
private const val LATENCY_BAR_ALPHA = 0.72f
private const val GENERIC_PROTOCOL = "DNS"
private val LatencyChartHeight = 56.dp

private data class RethinkDnsToggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val icon: ImageVector,
    val id: String,
    val onChange: (Boolean) -> Unit,
)

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
internal fun RethinkDnsStatusText(text: String, enabled: Boolean) = RethinkStatusLabel(
    label = text,
    color = if (enabled) RethinkStatusColors.allowed else RethinkStatusColors.idle,
    indicator = RethinkStatusIndicator.None,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
)

private fun RethinkDnsType.protocol(endpoint: String): String = when (this) {
    RethinkDnsType.Doh, RethinkDnsType.RethinkRemote, RethinkDnsType.SmartDns -> if (endpoint.startsWith("https://", true)) "HTTPS" else if (endpoint.startsWith("http://", true)) "HTTP" else GENERIC_PROTOCOL
    RethinkDnsType.Dot -> "DoT"
    RethinkDnsType.Odoh -> "ODoH"
    RethinkDnsType.DnsCrypt -> "DNSCrypt"
    RethinkDnsType.DnsProxy -> "DNS Proxy"
    RethinkDnsType.System -> "System DNS"
}

private fun dnsSettingsSectionFor(id: String?): Int? = when (id) {
    null, "" -> null
    // Item indices: 0 hero, 1 modes, 2 blocklists, 3 filtering, 4 block-free, 5 advanced.
    "dns_mode", "dns_mode_system", "dns_mode_custom", "dns_mode_rethink", "dns_mode_smart" -> 0
    "dns_blocklist", "dns_block_local", "dns_block_custom_downloader", "dns_block_periodic_updates" -> 2
    "dns_filtering", "dns_filter_alg", "dns_filter_split", "dns_filter_rules_as_firewall", "dns_filter_record_types" -> 3
    else -> 5
}

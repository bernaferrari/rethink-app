@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusIndicator
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

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
    /** Display name of the Rethink DNS blocklist in use ("Default", "Family"), when Rethink answers. */
    val rethinkPresetLabel: String = "",
    /** The resolver in use isn't answering, so lookups go to the fallback DNS. */
    val isDnsFailing: Boolean = false,
    /** Rethink DNS runs on its own resolver ("Max") instead of Cloudflare's edge ("Sky"). */
    val isRethinkMax: Boolean = false,
    /** Name of the fallback DNS (Android's own, Google, Cloudflare…). */
    val fallbackDnsName: String = "",
    /** The on-device lists have been downloaded at least once. */
    val localBlocklistsDownloaded: Boolean = false,
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
    val choiceSection: String = "Who answers your lookups",
    val recommended: String = "Recommended",
    val rethinkDetail: (String) -> String = { "Blocklist: $it" },
    val providerDetail: (name: String, protocol: String) -> String = { name, protocol -> "$name · $protocol" },
    val smartNote: String = "Automatic server selection is on. Change it in Advanced.",
    val heroFailing: String = "Not answering. Lookups use the fallback DNS for now.",
    val tryAnotherServer: String = "Try another server",
    val deviceSection: String = "Blocking on this device",
    val deviceOn: String = "On",
    val deviceOff: String = "Off",
    val deviceSetUp: String = "Download lists (about 60 MB)",
    val deviceTurnOn: String = "Turn on",
    val deviceChoose: String = "Choose lists",
    val deviceManage: String = "Manage",
    val advancedDescription: String = "Filtering, trusted domains, fallback DNS and more",
    val advancedTitle: String = "Advanced DNS",
    val serversSection: String = "Servers",
    val autoSelect: String = "Automatic server selection",
    val autoSelectDescription: String = "Picks from a rotating set of resolvers (Smart DNS).",
    val use: String = "Use",
    val rethinkNetwork: String = "Rethink DNS network",
    val rethinkFastest: String = "Fastest",
    val rethinkFastestDescription: String = "280+ locations, higher uptime. Runs on Cloudflare.",
    val rethinkPrivate: String = "Most private",
    val rethinkPrivateDescription: String = "50+ locations, more private. Runs its own resolver on Fly.io.",
    val downloadsSection: String = "Blocklist downloads",
    val fallbackSection: String = "Fallback",
    val fallbackServer: String = "Fallback DNS",
    val fallbackServerDescription: String = "Used when the DNS you chose can't be reached.",
    val moreSection: String = "More",
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
    /** The on-device blocklist maintenance sheet (update, re-download, delete, turn off). */
    val onLocalBlocklists: () -> Unit,
    val onRecordTypes: () -> Unit,
    val onTrustedEndpoint: () -> Unit,
    val onAdvanced: () -> Unit = {},
    /** Download the on-device lists, or turn them on once they are here. */
    val onLocalBlocklistSetUp: () -> Unit = onLocalBlocklists,
    /** Pick which on-device lists to use. */
    val onLocalBlocklistChoose: () -> Unit = onLocalBlocklists,
    val onFallbackDns: () -> Unit = {},
    /** Rethink DNS network: true for the private resolver ("Max"), false for the edge ("Sky"). */
    val onRethinkNetwork: (Boolean) -> Unit = {},
)

/**
 * The DNS screen: what answers lookups now (hero), who should answer them (three choices, chosen
 * by provider rather than protocol), on-device blocking, and one row into DNS › Advanced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDnsSettingsScreen(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    navigation: RethinkDnsSettingsNavigation,
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
            DnsRefreshAction(state.isRefreshing, strings.refresh, navigation.onRefresh)
        },
    ) {
        item(key = "hero") {
            // System and Smart DNS have servers worth listing; the hero opens them for whichever is in use.
            val details = when {
                state.isSystemDnsEnabled -> navigation.onSystemDnsInfo
                state.isSmartDnsEnabled -> navigation.onSmartDnsInfo
                else -> null
            }
            DnsInUseHero(state, strings, onDetails = details, onTryAnother = navigation.onCustomDns)
        }
        rethinkSection(strings.choiceSection, key = "choices") {
            DnsProviderChoices(state, strings, navigation, focusedSettingId)
        }
        rethinkSection(strings.deviceSection, key = "device") {
            DnsDeviceBlocklistCard(state, strings, navigation, highlighted = focusedSettingId == "dns_block_local")
        }
        item(key = "advanced") {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.advancedSection,
                    supporting = strings.advancedDescription,
                    leadingIcon = MaterialSymbols.Filled.Tune,
                    position = CardPosition.Single,
                    showTrailingChevron = true,
                    onClick = navigation.onAdvanced,
                )
            }
        }
    }
}

@Composable
internal fun DnsRefreshAction(isRefreshing: Boolean, refreshLabel: String, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
        if (isRefreshing) {
            LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
        } else {
            Icon(MaterialSymbols.Filled.Refresh, refreshLabel)
        }
    }
}

/**
 * Three full-width radio cards. Rethink DNS and Another provider open their lists (picking there
 * is what changes the resolver, exactly as before); Your network's DNS switches straight away.
 * The chosen card fills with the secondary container, rounds further and names what it uses.
 */
@Composable
private fun DnsProviderChoices(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    navigation: RethinkDnsSettingsNavigation,
    focusedSettingId: String?,
) {
    val choice = state.dnsChoice()
    val providerName = connectedDnsDisplayName(state.connectedDnsName)
    val choices = listOf(
        DnsChoice(
            title = strings.rethinkDns,
            description = strings.rethinkDnsDescription,
            selected = choice == RethinkDnsChoice.Rethink,
            icon = MaterialSymbols.Filled.Shield,
            id = "dns_mode_rethink",
            badge = strings.recommended,
            detail = state.rethinkPresetLabel.takeIf { it.isNotBlank() }?.let(strings.rethinkDetail),
            opensList = true,
            onClick = navigation.onRethinkDns,
        ),
        DnsChoice(
            title = strings.customDns,
            description = strings.customDnsDescription,
            selected = choice == RethinkDnsChoice.Provider,
            icon = MaterialSymbols.Filled.Public,
            id = "dns_mode_custom",
            detail = providerName.takeIf { it.isNotBlank() && it != "--" }?.let {
                strings.providerDetail(it, state.dnsType.shortProtocol())
            },
            opensList = true,
            onClick = navigation.onCustomDns,
        ),
        DnsChoice(
            title = strings.systemDns,
            description = strings.systemDnsDescription,
            selected = choice == RethinkDnsChoice.Network,
            icon = MaterialSymbols.Filled.Wifi,
            id = "dns_mode_system",
            onClick = navigation.onSystemDns,
        ),
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        choices.forEach { DnsChoiceCard(it, highlighted = focusedSettingId == it.id) }
        if (choice == RethinkDnsChoice.Automatic) {
            // Smart DNS still answers; say so, and where it is set, instead of leaving no card chosen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SharedDimensions.cornerRadiusLg))
                    .clickable(onClick = navigation.onAdvanced)
                    .padding(SharedDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                Icon(MaterialSymbols.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                Text(strings.smartNote, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val badge: String? = null,
    val detail: String? = null,
    val opensList: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun DnsChoiceCard(choice: DnsChoice, highlighted: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalRethinkMotion.current
    val corner by animateDpAsState(
        targetValue = if (choice.selected) SharedDimensions.groupCornerRadius else SharedDimensions.cornerRadiusLg,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dnsChoiceCorner",
    )
    val container = when {
        choice.selected -> scheme.secondaryContainer
        highlighted -> scheme.primaryContainer.copy(alpha = .42f)
        else -> scheme.surfaceContainerLow
    }
    val content = if (choice.selected) scheme.onSecondaryContainer else scheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth().selectable(selected = choice.selected, role = Role.RadioButton, onClick = choice.onClick),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(SharedDimensions.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            Icon(
                choice.icon,
                null,
                tint = if (choice.selected) scheme.onSecondaryContainer else scheme.primary,
                modifier = Modifier.padding(top = SharedDimensions.spacingGridTile).size(SharedDimensions.iconSizeMd),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(choice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    choice.badge?.let { RethinkPill(it, colors = RethinkPillDefaults.primary(), size = RethinkPillSize.Small) }
                }
                Text(
                    choice.description,
                    style = MaterialTheme.typography.bodySmall,
                    // Full emphasis when chosen: a faded description on the selected card read as disabled.
                    color = if (choice.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
                if (choice.selected && choice.detail != null) {
                    Row(
                        Modifier.padding(top = SharedDimensions.spacingXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            choice.detail,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (choice.opensList) Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                    }
                }
            }
            // The whole card is the target; the radio only shows the state.
            RadioButton(selected = choice.selected, onClick = null)
        }
    }
}

/**
 * On-device blocklists, said plainly: they work with any provider, and this is what they cost to
 * set up. The button is the next step (download, turn on, or pick lists); Manage opens upkeep.
 */
@Composable
private fun DnsDeviceBlocklistCard(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    navigation: RethinkDnsSettingsNavigation,
    highlighted: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    RethinkContainedCard(
        color = if (highlighted) scheme.primaryContainer.copy(alpha = .42f) else scheme.surfaceContainerLow,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Icon(KeepoutIcons.Blocklists, null, tint = scheme.primary, modifier = Modifier.padding(top = SharedDimensions.spacingGridTile).size(SharedDimensions.iconSizeMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(strings.localBlocklists, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.blocklistEnabled) strings.localBlocklistsDescription(state.numberOfLocalBlocklists) else strings.localBlocklistsDisabledDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            RethinkDnsStatusText(if (state.blocklistEnabled) strings.deviceOn else strings.deviceOff, state.blocklistEnabled)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = SharedDimensions.iconSizeMd + SharedDimensions.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                state.blocklistEnabled -> FilledTonalButton(onClick = navigation.onLocalBlocklistChoose) { Text(strings.deviceChoose) }
                state.localBlocklistsDownloaded -> FilledTonalButton(onClick = navigation.onLocalBlocklistSetUp) { Text(strings.deviceTurnOn) }
                else -> FilledTonalButton(onClick = navigation.onLocalBlocklistSetUp) {
                    Icon(MaterialSymbols.Filled.Download, null, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                    Spacer(Modifier.size(SharedDimensions.spacingSm))
                    Text(strings.deviceSetUp)
                }
            }
            if (state.localBlocklistsDownloaded) {
                TextButton(onClick = navigation.onLocalBlocklists) { Text(strings.deviceManage) }
            }
        }
    }
}

/**
 * What answers this phone's lookups right now: the resolver as a coloured hero, its protocol and
 * endpoint, and the median latency of the last lookups with a bar for each of them. A resolver
 * that isn't answering turns the hero into an error with the way out.
 */
@Composable
private fun DnsInUseHero(
    state: RethinkDnsSettingsState,
    strings: RethinkDnsSettingsStrings,
    onDetails: (() -> Unit)?,
    onTryAnother: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val endpoint = connectedDnsEndpoint(state.connectedDnsName)
    val storedName = connectedDnsDisplayName(state.connectedDnsName).ifBlank { "--" }
    val choice = state.dnsChoice()
    // Rethink DNS is named as such; its blocklist ("Default", "Family") is the pill beside it.
    val displayName = if (choice == RethinkDnsChoice.Rethink) strings.rethinkDns else storedName
    val protocol = state.dnsType.protocol(endpoint)
    val latency = dnsHeroLatency(state.recentLatencies, state.dnsLatency, LATENCY_BARS)
    val latencies = latency.samples
    val latencyLabel = latency.label
    val shape = RoundedCornerShape(SharedDimensions.groupCornerRadius)
    // The brand palette keeps primaryContainer at the seed's bright tone in dark theme too; the
    // secondary container is the same hue at a dark tone, so the hero dims with the rest of the UI.
    val dark = scheme.surface.luminance() < 0.5f
    val failing = state.isDnsFailing
    val heroContainer = when {
        failing -> scheme.errorContainer
        dark -> scheme.secondaryContainer.copy(alpha = .78f)
        else -> scheme.primaryContainer.copy(alpha = .78f)
    }
    val onHero = when {
        failing -> scheme.onErrorContainer
        dark -> scheme.onSecondaryContainer
        else -> scheme.onPrimaryContainer
    }
    Surface(
        shape = shape,
        color = heroContainer,
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
            val preset = state.rethinkPresetLabel.takeIf { choice == RethinkDnsChoice.Rethink && it.isNotBlank() }
            val showProtocol = protocol != GENERIC_PROTOCOL && !protocol.equals(displayName, ignoreCase = true)
            val showEndpoint = endpoint.isNotBlank()
            if (preset != null || showProtocol || showEndpoint) {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                    if (preset != null) RethinkPill(preset, colors = RethinkPillDefaults.onContainer(onHero), size = RethinkPillSize.Large, leadingIcon = KeepoutIcons.Blocklists)
                    if (showProtocol) RethinkPill(protocol, colors = RethinkPillDefaults.onContainer(onHero), size = RethinkPillSize.Large)
                    if (showEndpoint) {
                        RethinkPill(endpoint, Modifier.weight(1f, fill = false), colors = RethinkPillDefaults.onContainer(onHero), size = RethinkPillSize.Large)
                    }
                }
            }
            if (failing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    Icon(MaterialSymbols.Filled.Warning, null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                    Text(strings.heroFailing, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                FilledTonalButton(onClick = onTryAnother) { Text(strings.tryAnotherServer) }
            } else {
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

internal data class RethinkDnsToggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val icon: ImageVector,
    val id: String,
    val onChange: (Boolean) -> Unit,
)

@Composable
internal fun RethinkDnsToggleRow(
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

internal fun RethinkDnsType.protocol(endpoint: String): String = when (this) {
    RethinkDnsType.Doh, RethinkDnsType.RethinkRemote, RethinkDnsType.SmartDns -> if (endpoint.startsWith("https://", true)) "HTTPS" else if (endpoint.startsWith("http://", true)) "HTTP" else GENERIC_PROTOCOL
    RethinkDnsType.Dot -> "DoT"
    RethinkDnsType.Odoh -> "ODoH"
    RethinkDnsType.DnsCrypt -> "DNSCrypt"
    RethinkDnsType.DnsProxy -> "DNS Proxy"
    RethinkDnsType.System -> "System DNS"
}

/** The protocol as people see it on the provider list: a detail of the provider, not its identity. */
internal fun RethinkDnsType.shortProtocol(): String = when (this) {
    RethinkDnsType.Doh, RethinkDnsType.RethinkRemote, RethinkDnsType.SmartDns -> "DoH"
    RethinkDnsType.Dot -> "DoT"
    RethinkDnsType.Odoh -> "ODoH"
    RethinkDnsType.DnsCrypt -> "DNSCrypt"
    RethinkDnsType.DnsProxy -> "DNS proxy"
    RethinkDnsType.System -> "DNS"
}

private fun dnsSettingsSectionFor(id: String?): Int? = when (id) {
    null, "" -> null
    // Item indices: 0 hero, 1 choices, 2 on-device blocklists, 3 advanced.
    "dns_mode", "dns_mode_system", "dns_mode_custom", "dns_mode_rethink" -> 0
    "dns_blocklist", "dns_block_local" -> 1
    else -> 3
}

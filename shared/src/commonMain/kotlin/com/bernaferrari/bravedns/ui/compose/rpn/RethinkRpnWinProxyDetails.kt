/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMetric
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMetricSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

data class RethinkRpnWinProxyDetailsState(
    val countryCode: String,
    val appsCount: String,
    val domainsCount: String,
    val ipsCount: String,
    val proxyError: String = "",
    val proxyName: String = "",
    val proxyWho: String = "",
    val proxyLatency: String,
    val proxyLastConnected: String,
    val proxyStatus: String,
    val isProxyActive: Boolean,
    val options: RethinkRpnWinServerOptions? = null,
)

data class RethinkRpnWinServerOptions(
    val hopEnabled: Boolean,
    val catchAll: Boolean,
    val lockdown: Boolean,
    val mobileOnly: Boolean,
    val ssidBased: Boolean,
)

data class RethinkRpnWinProxyDetailsStrings(
    val title: String,
    val fallback: String,
    val proxyName: String,
    val apps: String,
    val domains: String,
    val ips: String,
    val who: String,
    val error: String,
    val latency: String,
    val lastConnected: String,
    val status: String,
    val serverOptions: String,
    val hop: String,
    val catchAll: String,
    val lockdown: String,
    val mobileOnly: String,
    val wifiOnly: String,
    val editWifi: String,
    val selectApps: String,
)

/** Shared RPN Windows proxy detail screen. Android supplies service state and persists option changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkRpnWinProxyDetailsScreen(
    state: RethinkRpnWinProxyDetailsState,
    strings: RethinkRpnWinProxyDetailsStrings,
    onBackClick: () -> Unit,
    onHopChanged: (Boolean) -> Unit,
    onCatchAllChanged: (Boolean) -> Unit,
    onLockdownChanged: (Boolean) -> Unit,
    onMobileOnlyChanged: (Boolean) -> Unit,
    onSsidChanged: (Boolean) -> Unit,
    onEditSsids: () -> Unit,
    onSelectApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { RethinkLargeTopBar(title = strings.title, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(
                horizontal = SharedDimensions.screenPaddingHorizontal,
                vertical = SharedDimensions.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            RethinkRpnWinStats(state, strings)
            RethinkRpnWinDetails(state, strings)
            state.options?.let { options ->
                RethinkRpnWinServerOptions(
                    options = options,
                    strings = strings,
                    onHopChanged = onHopChanged,
                    onCatchAllChanged = onCatchAllChanged,
                    onLockdownChanged = onLockdownChanged,
                    onMobileOnlyChanged = onMobileOnlyChanged,
                    onSsidChanged = onSsidChanged,
                    onEditSsids = onEditSsids,
                )
            }
            PrimaryButton(
                text = strings.selectApps,
                onClick = onSelectApps,
                modifier = Modifier.fillMaxWidth(),
                icon = MaterialSymbols.Filled.Apps,
            )
        }
    }
}

@Composable
private fun RethinkRpnWinStats(state: RethinkRpnWinProxyDetailsState, strings: RethinkRpnWinProxyDetailsStrings) {
    RethinkContainedCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RethinkRpnWinStat(strings.apps, state.appsCount, Modifier.weight(1f))
            RethinkRpnWinStat(strings.domains, state.domainsCount, Modifier.weight(1f))
            RethinkRpnWinStat(strings.ips, state.ipsCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RethinkRpnWinStat(label: String, value: String, modifier: Modifier) =
    RethinkMetric(value, label, modifier, size = RethinkMetricSize.Large, horizontalAlignment = Alignment.CenterHorizontally)

@Composable
private fun RethinkRpnWinDetails(state: RethinkRpnWinProxyDetailsState, strings: RethinkRpnWinProxyDetailsStrings) {
    val statusColor = if (state.isProxyActive) RethinkStatusColors.allowed else RethinkStatusColors.idle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SharedDimensions.spacingLg)) {
            Text(state.proxyName.ifBlank { strings.proxyName }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(state.countryCode.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            val rowModifier = Modifier.padding(vertical = SharedDimensions.spacingSm)
            RethinkKeyValueRow(strings.who, state.proxyWho.ifBlank { strings.fallback }, rowModifier)
            if (state.proxyError.isNotEmpty()) RethinkKeyValueRow(strings.error, state.proxyError, rowModifier, valueColor = MaterialTheme.colorScheme.error)
            RethinkKeyValueRow(strings.latency, state.proxyLatency, rowModifier)
            RethinkKeyValueRow(strings.lastConnected, state.proxyLastConnected, rowModifier)
            RethinkKeyValueRow(strings.status, state.proxyStatus, rowModifier, valueColor = statusColor)
        }
    }
}

@Composable
private fun RethinkRpnWinServerOptions(
    options: RethinkRpnWinServerOptions,
    strings: RethinkRpnWinProxyDetailsStrings,
    onHopChanged: (Boolean) -> Unit,
    onCatchAllChanged: (Boolean) -> Unit,
    onLockdownChanged: (Boolean) -> Unit,
    onMobileOnlyChanged: (Boolean) -> Unit,
    onSsidChanged: (Boolean) -> Unit,
    onEditSsids: () -> Unit,
) {
    Column {
        SectionHeader(strings.serverOptions)
        RethinkListGroup {
            RethinkSwitchListItem(strings.hop, options.hopEnabled, onHopChanged, position = CardPosition.First)
            RethinkSwitchListItem(strings.catchAll, options.catchAll, onCatchAllChanged, position = CardPosition.Middle)
            RethinkSwitchListItem(strings.lockdown, options.lockdown, onLockdownChanged, position = CardPosition.Middle)
            RethinkSwitchListItem(strings.mobileOnly, options.mobileOnly, onMobileOnlyChanged, position = CardPosition.Middle)
            RethinkSwitchListItem(strings.wifiOnly, options.ssidBased, onSsidChanged, position = CardPosition.Middle)
            // A grouped navigation row instead of a detached text button under the list.
            RethinkListItem(
                headline = strings.editWifi,
                leadingIcon = MaterialSymbols.Filled.Wifi,
                position = CardPosition.Last,
                enabled = options.ssidBased,
                // Opens the SSID editor dialog in place, so no navigation chevron.
                onClick = onEditSsids,
            )
        }
    }
}

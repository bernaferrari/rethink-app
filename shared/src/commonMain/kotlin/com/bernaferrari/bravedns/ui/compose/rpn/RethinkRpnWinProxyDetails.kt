/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
    Scaffold(
        modifier = modifier,
        topBar = { RethinkLargeTopBar(title = strings.title, onBackClick = onBackClick) },
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
            Button(onClick = onSelectApps, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius)) {
                androidx.compose.material3.Icon(MaterialSymbols.Filled.Apps, null)
                Spacer(Modifier.width(SharedDimensions.spacingXs))
                Text(strings.selectApps)
            }
        }
    }
}

@Composable
private fun RethinkRpnWinStats(state: RethinkRpnWinProxyDetailsState, strings: RethinkRpnWinProxyDetailsStrings) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg),
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
private fun RethinkRpnWinStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RethinkRpnWinDetails(state: RethinkRpnWinProxyDetailsState, strings: RethinkRpnWinProxyDetailsStrings) {
    val statusColor = if (state.isProxyActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SharedDimensions.spacingLg)) {
            Text(state.proxyName.ifBlank { strings.proxyName }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(state.countryCode.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            RethinkRpnWinDetailRow(strings.who, state.proxyWho.ifBlank { strings.fallback })
            if (state.proxyError.isNotEmpty()) RethinkRpnWinDetailRow(strings.error, state.proxyError, MaterialTheme.colorScheme.error)
            RethinkRpnWinDetailRow(strings.latency, state.proxyLatency)
            RethinkRpnWinDetailRow(strings.lastConnected, state.proxyLastConnected)
            RethinkRpnWinDetailRow(strings.status, state.proxyStatus, statusColor)
        }
    }
}

@Composable
private fun RethinkRpnWinDetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingSm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Text(
            strings.serverOptions.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = SharedDimensions.spacingLg),
        )
        RethinkListGroup {
            RethinkRpnWinOption(strings.hop, options.hopEnabled, CardPosition.First, onHopChanged)
            RethinkRpnWinOption(strings.catchAll, options.catchAll, CardPosition.Middle, onCatchAllChanged)
            RethinkRpnWinOption(strings.lockdown, options.lockdown, CardPosition.Middle, onLockdownChanged)
            RethinkRpnWinOption(strings.mobileOnly, options.mobileOnly, CardPosition.Middle, onMobileOnlyChanged)
            RethinkRpnWinOption(strings.wifiOnly, options.ssidBased, CardPosition.Last, onSsidChanged)
        }
        TextButton(
            onClick = onEditSsids,
            enabled = options.ssidBased,
            modifier = Modifier.align(Alignment.End),
        ) { Text(strings.editWifi) }
    }
}

@Composable
private fun RethinkRpnWinOption(
    label: String,
    checked: Boolean,
    position: CardPosition,
    onCheckedChange: (Boolean) -> Unit,
) = RethinkListItem(
    headline = label,
    position = position,
    onClick = { onCheckedChange(!checked) },
    trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
)

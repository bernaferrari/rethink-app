/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Common render layer for the real Home screen. Platform code owns only live state, Android
 * resources, and side effects; the layout and every visual decision live here.
 */
package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

data class RethinkHomeUiState(
    val isVpnActive: Boolean = false,
    val dnsLatency: String = "-- ms",
    val dnsConnectedName: String = "",
    val firewallUniversalRules: Int = 0,
    val firewallIpRules: Int = 0,
    val firewallDomainRules: Int = 0,
    val proxyStatus: String = "",
    val networkLogsCount: Long = 0,
    val dnsLogsCount: Long = 0,
    val appsAllowed: Int = 0,
    val appsBlocked: Int = 0,
    val appsTotal: Int = 0,
    val appsBypassed: Int = 0,
    val protectionStatus: String = "",
    val isProtectionFailing: Boolean = false,
)

data class RethinkHomeStrings(
    val home: String,
    val status: String,
    val protection: String,
    val protected: String,
    val notActive: String,
    val start: String,
    val stop: String,
    val inactive: String,
    val latency: String,
    val dns: String,
    val firewall: String,
    val proxy: String,
    val logs: String,
    val network: String,
    val blocked: String,
    val apps: String,
    val allowed: String,
    val bypassed: String,
    val universalRules: String,
)

/**
 * Portable defaults plus an Android resource injection point. This keeps the real Android
 * artwork intact while the same screen remains usable by the WASM host.
 */
data class RethinkHomeIcons(
    val dns: @Composable () -> Unit = { Icon(MaterialSymbols.Filled.Dns, null) },
    val firewall: @Composable () -> Unit = { Icon(MaterialSymbols.Filled.Security, null) },
    val proxy: @Composable () -> Unit = { Icon(MaterialSymbols.Filled.VpnKey, null) },
    val logs: @Composable () -> Unit = { Icon(MaterialSymbols.Filled.Subject, null) },
    val apps: @Composable () -> Unit = { Icon(MaterialSymbols.Filled.Apps, null) },
)

private val homeIconApps = Color(0xFF74C5FF)
private val homeIconDns = Color(0xFFC5ACFF)
private val homeIconFirewall = Color(0xFFFF907F)
private val homeIconProxy = Color(0xFF46EBC8)
private val homeIconLogs = Color(0xFF7EED92)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkHomeScreen(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    onStartStopClick: () -> Unit,
    icons: RethinkHomeIcons = RethinkHomeIcons(),
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dnsSummary = uiState.dnsConnectedName.takeIf(String::isNotBlank)?.let {
        "$it · ${uiState.dnsLatency}"
    } ?: strings.inactive
    val firewallSummary = "${uiState.firewallUniversalRules} ${strings.universalRules} · ${uiState.firewallIpRules} IP · ${uiState.firewallDomainRules} domain"
    val proxySummary = uiState.proxyStatus.ifEmpty { strings.inactive }
    val logsSummary = "${uiState.networkLogsCount} ${strings.network} · ${uiState.dnsLogsCount} DNS"

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(strings.home, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item { ProtectionCard(uiState, strings, onStartStopClick) }
            item {
                HomeStatusSection(
                    title = strings.status,
                    items = listOf(
                        HomeStatusItem(strings.dns, dnsSummary, homeIconDns, icons.dns),
                        HomeStatusItem(strings.firewall, firewallSummary, homeIconFirewall, icons.firewall),
                        HomeStatusItem(strings.proxy, proxySummary, homeIconProxy, icons.proxy),
                        HomeStatusItem(strings.logs, logsSummary, homeIconLogs, icons.logs),
                    ),
                )
            }
            item { AppsHealthCard(uiState, strings, icons.apps) }
        }
    }
}

private data class HomeStatusItem(
    val headline: String,
    val supporting: String,
    val accent: Color,
    val icon: @Composable () -> Unit,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatusSection(title: String, items: List<HomeStatusItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = SharedDimensions.spacingLg, bottom = SharedDimensions.spacingSm),
        )
        items.forEachIndexed { index, item ->
            RethinkListItem(
                headline = item.headline,
                supporting = item.supporting,
                position = cardPositionFor(index, items.lastIndex),
                leadingContent = {
                    Surface(
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        color = item.accent,
                        modifier = Modifier.size(SharedDimensions.iconContainerSm),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = 0.8f),
                            ) { item.icon() }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProtectionCard(uiState: RethinkHomeUiState, strings: RethinkHomeStrings, onStartStopClick: () -> Unit) {
    val accent = when {
        uiState.isProtectionFailing -> homeIconFirewall
        uiState.isVpnActive -> homeIconProxy
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = when {
        uiState.isProtectionFailing -> homeIconFirewall
        uiState.isVpnActive -> homeIconProxy
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val status = uiState.protectionStatus.ifEmpty { if (uiState.isVpnActive) strings.protected else strings.notActive }
    val cardShape = RoundedCornerShape(SharedDimensions.heroCornerRadius)

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().clip(cardShape),
    ) {
        Column(Modifier.padding(SharedDimensions.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
            ) {
                Surface(
                    shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                    color = container,
                    modifier = Modifier.size(SharedDimensions.iconContainerLg),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                uiState.isProtectionFailing -> MaterialSymbols.Filled.WarningAmber
                                uiState.isVpnActive -> MaterialSymbols.Filled.Shield
                                else -> MaterialSymbols.Filled.ShieldMoon
                            },
                            contentDescription = null,
                            tint = if (uiState.isVpnActive || uiState.isProtectionFailing) MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = 0.8f) else accent,
                            modifier = Modifier.size(SharedDimensions.iconSizeMd),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(strings.protection, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                StartStopButton(
                    isPlaying = uiState.isVpnActive,
                    contentDescription = if (uiState.isVpnActive) strings.stop else strings.start,
                    onClick = onStartStopClick,
                )
            }
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                MetricChip(strings.latency, uiState.dnsLatency, Modifier.weight(1f))
                MetricChip(strings.network, uiState.networkLogsCount.toString(), Modifier.weight(1f))
                MetricChip(strings.blocked, uiState.appsBlocked.toString(), Modifier.weight(1f), if (uiState.appsBlocked > 0) homeIconFirewall else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun StartStopButton(isPlaying: Boolean, contentDescription: String, onClick: () -> Unit) {
    val controlShape = RoundedCornerShape(SharedDimensions.buttonCornerRadius)
    Surface(
        onClick = onClick,
        shape = controlShape,
        color = if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(SharedDimensions.heroButtonHeight).clip(controlShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) MaterialSymbols.Filled.Stop else MaterialSymbols.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = if (isPlaying) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsHealthCard(
    uiState: RethinkHomeUiState,
    strings: RethinkHomeStrings,
    appIcon: @Composable () -> Unit,
) {
    val progress = remember(uiState.appsAllowed, uiState.appsTotal) {
        if (uiState.appsTotal > 0) uiState.appsAllowed.toFloat() / uiState.appsTotal else 0f
    }
    val cardShape = RoundedCornerShape(SharedDimensions.heroCornerRadius)
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        modifier = Modifier.clip(cardShape),
    ) {
        Column(Modifier.padding(SharedDimensions.cardPadding)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(SharedDimensions.iconContainerRadius), color = homeIconApps, modifier = Modifier.size(SharedDimensions.iconContainerMd)) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = 0.8f),
                        ) { appIcon() }
                    }
                }
                Text(strings.apps, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill), color = homeIconApps) {
                    Text(uiState.appsTotal.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(SharedDimensions.progressBarHeight).clip(RoundedCornerShape(SharedDimensions.cornerRadiusPill)),
                color = homeIconApps,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppStat(strings.allowed, uiState.appsAllowed.toString(), MaterialTheme.colorScheme.secondary)
                AppStat(strings.blocked, uiState.appsBlocked.toString(), if (uiState.appsBlocked > 0) homeIconFirewall else MaterialTheme.colorScheme.onSurfaceVariant)
                AppStat(strings.bypassed, uiState.appsBypassed.toString(), homeIconDns)
            }
        }
    }
}

@Composable
private fun AppStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

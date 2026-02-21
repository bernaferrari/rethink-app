/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.firewall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.util.BackgroundAccessibilityService
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UniversalFirewallStatEntry(
    val ruleId: String,
    val count: Int,
    val percent: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalFirewallSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    connTrackerRepository: ConnectionTrackerRepository,
    onNavigateToLogs: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf<List<UniversalFirewallStatEntry>>(emptyList()) }
    var isLoadingStats by remember { mutableStateOf(true) }

    var blockWhenDeviceLocked by remember { mutableStateOf(persistentState.getBlockWhenDeviceLocked()) }
    var blockAppWhenBackground by remember { mutableStateOf(persistentState.getBlockAppWhenBackground()) }
    var udpBlocked by remember { mutableStateOf(persistentState.getUdpBlocked()) }
    var blockUnknownConnections by remember { mutableStateOf(persistentState.getBlockUnknownConnections()) }
    var disallowDnsBypass by remember { mutableStateOf(persistentState.getDisallowDnsBypass()) }
    var blockNewApp by remember { mutableStateOf(persistentState.getBlockNewlyInstalledApp()) }
    var blockMeteredConnections by remember { mutableStateOf(persistentState.getBlockMeteredConnections()) }
    var blockHttpConnections by remember { mutableStateOf(persistentState.getBlockHttpConnections()) }
    var universalLockdown by remember { mutableStateOf(persistentState.getUniversalLockdown()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun loadStats() {
        isLoadingStats = true
        scope.launch(Dispatchers.IO) {
            val blockedUniversalRules = connTrackerRepository.getBlockedUniversalRulesCount()
            val deviceLocked =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE3.id) }
            val backgroundMode =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE4.id) }
            val unknown =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE5.id) }
            val udp =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE6.id) }
            val dnsBypass =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE7.id) }
            val newApp =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE1B.id) }
            val metered =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE1F.id) }
            val http =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE10.id) }
            val lockdown =
                blockedUniversalRules.filter { it.blockedByRule.contains(FirewallRuleset.RULE11.id) }

            val blockedCountList = listOf(
                deviceLocked.size,
                backgroundMode.size,
                unknown.size,
                udp.size,
                dnsBypass.size,
                newApp.size,
                metered.size,
                http.size,
                lockdown.size
            )

            val maxValue = blockedCountList.maxOrNull()?.toDouble() ?: 0.0

            fun calculatePercentage(count: Int): Int {
                if (maxValue == 0.0) return 0
                return ((count / maxValue) * 100).toInt()
            }

            val updatedStats = listOf(
                UniversalFirewallStatEntry(FirewallRuleset.RULE3.id, deviceLocked.size, calculatePercentage(deviceLocked.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE4.id, backgroundMode.size, calculatePercentage(backgroundMode.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE5.id, unknown.size, calculatePercentage(unknown.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE6.id, udp.size, calculatePercentage(udp.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE7.id, dnsBypass.size, calculatePercentage(dnsBypass.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE1B.id, newApp.size, calculatePercentage(newApp.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE1F.id, metered.size, calculatePercentage(metered.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE10.id, http.size, calculatePercentage(http.size)),
                UniversalFirewallStatEntry(FirewallRuleset.RULE11.id, lockdown.size, calculatePercentage(lockdown.size))
            )

            withContext(Dispatchers.Main) {
                stats = updatedStats
                isLoadingStats = false
            }
        }
    }

    fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Univ firewall setting", EventSource.UI, false, details)
    }

    fun statsFor(ruleId: String): UniversalFirewallStatEntry? {
        return stats.firstOrNull { it.ruleId == ruleId }
    }

    fun handleStatsClick(ruleId: String) {
        val size = statsFor(ruleId)?.count ?: 0
        if (size > 0) {
            onNavigateToLogs(ruleId)
        }
    }

    fun handleBackgroundToggle(enabled: Boolean) {
        if (!enabled) {
            blockAppWhenBackground = false
            persistentState.setBlockAppWhenBackground(false)
            logEvent("Univ firewall background mode changed toggled to false")
            return
        }

        val isAccessibilityServiceRunning = Utilities.isAccessibilityServiceEnabled(
            context,
            BackgroundAccessibilityService::class.java
        )
        val isAccessibilityServiceEnabled = Utilities.isAccessibilityServiceEnabledViaSettingsSecure(
            context,
            BackgroundAccessibilityService::class.java
        )
        val isAccessibilityServiceFunctional = isAccessibilityServiceRunning && isAccessibilityServiceEnabled

        if (isAccessibilityServiceFunctional) {
            blockAppWhenBackground = true
            persistentState.setBlockAppWhenBackground(true)
            logEvent("Univ firewall background mode changed toggled to true")
            return
        }

        showPermissionDialog = true
        blockAppWhenBackground = false
        persistentState.setBlockAppWhenBackground(false)
        logEvent("Univ firewall background mode change to true failed due to accessibility service not enabled")
    }

    LaunchedEffect(Unit) {
        loadStats()
    }

    Scaffold(
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.firewall_act_universal_tab),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        val blockedTotal = stats.sumOf { it.count }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Dimensions.spacingLg,
                            vertical = Dimensions.spacingMd
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                    ) {
                        Text(
                            text = stringResource(R.string.firewall_act_universal_tab),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.universal_firewall_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (isLoadingStats) {
                                        stringResource(R.string.lbl_loading)
                                    } else {
                                        "$blockedTotal blocked"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        horizontal = Dimensions.spacingMd,
                                        vertical = 4.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.univ_firewall_heading))
                RethinkListGroup {
                    ToggleWithStats(
                        iconRes = R.drawable.ic_device_lock,
                        label = stringResource(R.string.univ_firewall_rule_1),
                        checked = blockWhenDeviceLocked,
                        onCheckedChange = {
                            blockWhenDeviceLocked = it
                            persistentState.setBlockWhenDeviceLocked(it)
                            logEvent("Univ firewall device locked mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE3.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE3.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_foreground,
                        label = stringResource(R.string.univ_firewall_rule_2),
                        checked = blockAppWhenBackground,
                        onCheckedChange = { handleBackgroundToggle(it) },
                        stats = statsFor(FirewallRuleset.RULE4.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE4.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_unknown_app,
                        label = stringResource(R.string.univ_firewall_rule_3),
                        checked = blockUnknownConnections,
                        onCheckedChange = {
                            blockUnknownConnections = it
                            persistentState.setBlockUnknownConnections(it)
                            logEvent("Univ firewall unknown connection mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE5.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE5.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_udp,
                        label = stringResource(R.string.univ_firewall_rule_4),
                        checked = udpBlocked,
                        onCheckedChange = {
                            udpBlocked = it
                            persistentState.setUdpBlocked(it)
                            logEvent("Univ firewall UDP connection mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE6.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE6.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_prevent_dns_leaks,
                        label = stringResource(R.string.univ_firewall_rule_5),
                        checked = disallowDnsBypass,
                        onCheckedChange = {
                            disallowDnsBypass = it
                            persistentState.setDisallowDnsBypass(it)
                            logEvent("Univ firewall DNS bypass mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE7.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE7.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_app_info,
                        label = stringResource(R.string.univ_firewall_rule_6),
                        checked = blockNewApp,
                        onCheckedChange = {
                            blockNewApp = it
                            persistentState.setBlockNewlyInstalledApp(it)
                            logEvent("Univ firewall new app block mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE1B.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE1B.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_univ_metered,
                        label = stringResource(R.string.univ_firewall_rule_9),
                        checked = blockMeteredConnections,
                        onCheckedChange = {
                            blockMeteredConnections = it
                            persistentState.setBlockMeteredConnections(it)
                            logEvent("Univ firewall metered connection block mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE1F.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE1F.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_http,
                        label = stringResource(R.string.univ_firewall_rule_8),
                        checked = blockHttpConnections,
                        onCheckedChange = {
                            blockHttpConnections = it
                            persistentState.setBlockHttpConnections(it)
                            logEvent("Univ firewall HTTP block mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE10.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE10.id) }
                    )
                    ToggleWithStats(
                        iconRes = R.drawable.ic_global_lockdown,
                        label = stringResource(R.string.univ_firewall_rule_10),
                        checked = universalLockdown,
                        onCheckedChange = {
                            universalLockdown = it
                            persistentState.setUniversalLockdown(it)
                            logEvent("Univ firewall universal lockdown mode changed toggled to $it")
                        },
                        stats = statsFor(FirewallRuleset.RULE11.id),
                        loading = isLoadingStats,
                        onStatsClick = { handleStatsClick(FirewallRuleset.RULE11.id) },
                        showDivider = false
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(text = stringResource(R.string.alert_permission_accessibility)) },
            text = { Text(text = stringResource(R.string.alert_firewall_accessibility_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        onOpenAccessibilitySettings()
                    }
                ) {
                    Text(text = stringResource(R.string.univ_accessibility_dialog_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(text = stringResource(R.string.univ_accessibility_dialog_negative))
                }
            }
        )
    }
}

@Composable
private fun ToggleWithStats(
    iconRes: Int,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stats: UniversalFirewallStatEntry?,
    loading: Boolean,
    onStatsClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) }
            )
        }
        StatsRow(
            stats = stats,
            loading = loading,
            onClick = onStatsClick
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                thickness = Dimensions.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun StatsRow(
    stats: UniversalFirewallStatEntry?,
    loading: Boolean,
    onClick: () -> Unit
) {
    val countText = if (loading || stats == null) "-" else stats.count.toString()
    val progressValue = if (loading || stats == null) 0f else stats.percent / 100f
    val enabled = (stats?.count ?: 0) > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { progressValue },
            modifier = Modifier
                .weight(1f)
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        if (enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_right_arrow_white),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

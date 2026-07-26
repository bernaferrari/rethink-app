/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.BackgroundAccessibilityService
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android persistence, repository, and accessibility adapter for shared universal-firewall UI. */
@Composable
fun UniversalFirewallSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    connTrackerRepository: ConnectionTrackerRepository,
    onNavigateToLogs: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blockedCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loadingStats by remember { mutableStateOf(true) }
    var deviceLock by remember { mutableStateOf(persistentState.getBlockWhenDeviceLocked()) }
    var background by remember { mutableStateOf(persistentState.getBlockAppWhenBackground()) }
    var unknown by remember { mutableStateOf(persistentState.getBlockUnknownConnections()) }
    var udp by remember { mutableStateOf(persistentState.getUdpBlocked()) }
    var dnsBypass by remember { mutableStateOf(persistentState.getDisallowDnsBypass()) }
    var newApp by remember { mutableStateOf(persistentState.getBlockNewlyInstalledApp()) }
    var metered by remember { mutableStateOf(persistentState.getBlockMeteredConnections()) }
    var http by remember { mutableStateOf(persistentState.getBlockHttpConnections()) }
    var lockdown by remember { mutableStateOf(persistentState.getUniversalLockdown()) }

    LaunchedEffect(Unit) {
        loadingStats = true
        scope.launch(Dispatchers.IO) {
            val rows = connTrackerRepository.getBlockedUniversalRulesCount()
            val ids = listOf(
                FirewallRuleset.RULE3.id, FirewallRuleset.RULE4.id, FirewallRuleset.RULE5.id,
                FirewallRuleset.RULE6.id, FirewallRuleset.RULE7.id, FirewallRuleset.RULE1B.id,
                FirewallRuleset.RULE1F.id, FirewallRuleset.RULE10.id, FirewallRuleset.RULE11.id,
            )
            val counts = ids.associateWith { id -> rows.count { it.blockedByRule.contains(id) } }
            withContext(Dispatchers.Main) { blockedCounts = counts; loadingStats = false }
        }
    }

    fun log(detail: String) = eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Univ firewall setting", EventSource.UI, false, detail)
    fun change(id: String, enabled: Boolean): RethinkUniversalFirewallChange = when (id) {
        FirewallRuleset.RULE3.id -> { deviceLock = enabled; persistentState.setBlockWhenDeviceLocked(enabled); log("Device lock: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE4.id -> {
            if (!enabled) {
                background = false; persistentState.setBlockAppWhenBackground(false); log("Background mode: false"); RethinkUniversalFirewallChange.Applied
            } else {
                val service = Utilities.isAccessibilityServiceEnabled(context, BackgroundAccessibilityService::class.java) &&
                    Utilities.isAccessibilityServiceEnabledViaSettingsSecure(context, BackgroundAccessibilityService::class.java)
                if (service) {
                    background = true; persistentState.setBlockAppWhenBackground(true); log("Background mode: true"); RethinkUniversalFirewallChange.Applied
                } else {
                    background = false; persistentState.setBlockAppWhenBackground(false); log("Background mode requires accessibility"); RethinkUniversalFirewallChange.RequiresAccessibility
                }
            }
        }
        FirewallRuleset.RULE5.id -> { unknown = enabled; persistentState.setBlockUnknownConnections(enabled); log("Unknown connections: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE6.id -> { udp = enabled; persistentState.setUdpBlocked(enabled); log("UDP: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE7.id -> { dnsBypass = enabled; persistentState.setDisallowDnsBypass(enabled); log("DNS bypass: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE1B.id -> { newApp = enabled; persistentState.setBlockNewlyInstalledApp(enabled); log("New apps: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE1F.id -> { metered = enabled; persistentState.setBlockMeteredConnections(enabled); log("Metered connections: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE10.id -> { http = enabled; persistentState.setBlockHttpConnections(enabled); log("HTTP: $enabled"); RethinkUniversalFirewallChange.Applied }
        FirewallRuleset.RULE11.id -> { lockdown = enabled; persistentState.setUniversalLockdown(enabled); log("Lockdown: $enabled"); RethinkUniversalFirewallChange.Applied }
        else -> RethinkUniversalFirewallChange.Applied
    }
    val labels = androidUniversalFirewallLabels()
    val settings = listOf(
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE3.id, labels.deviceLock, deviceLock, RethinkUniversalFirewallIcon.DeviceLock, blockedCounts[FirewallRuleset.RULE3.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE4.id, labels.background, background, RethinkUniversalFirewallIcon.Background, blockedCounts[FirewallRuleset.RULE4.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE5.id, labels.unknown, unknown, RethinkUniversalFirewallIcon.Unknown, blockedCounts[FirewallRuleset.RULE5.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE6.id, labels.udp, udp, RethinkUniversalFirewallIcon.Udp, blockedCounts[FirewallRuleset.RULE6.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE7.id, labels.dnsBypass, dnsBypass, RethinkUniversalFirewallIcon.Dns, blockedCounts[FirewallRuleset.RULE7.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE1B.id, labels.newApp, newApp, RethinkUniversalFirewallIcon.NewApp, blockedCounts[FirewallRuleset.RULE1B.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE1F.id, labels.metered, metered, RethinkUniversalFirewallIcon.Metered, blockedCounts[FirewallRuleset.RULE1F.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE10.id, labels.http, http, RethinkUniversalFirewallIcon.Http, blockedCounts[FirewallRuleset.RULE10.id] ?: 0),
        RethinkUniversalFirewallSetting(FirewallRuleset.RULE11.id, labels.lockdown, lockdown, RethinkUniversalFirewallIcon.Lockdown, blockedCounts[FirewallRuleset.RULE11.id] ?: 0),
    )
    RethinkUniversalFirewallSettingsScreen(
        settings = settings,
        strings = RethinkUniversalFirewallStrings(
            title = stringResource(R.string.univ_firewall_heading),
            explanation = stringResource(R.string.universal_firewall_explanation),
            blocked = { count -> stringResource(R.string.two_argument_colon, stringResource(R.string.lbl_blocked), count.toString()) },
            loading = stringResource(R.string.lbl_loading),
            logs = stringResource(R.string.lbl_logs),
            accessibilityTitle = stringResource(R.string.alert_permission_accessibility),
            accessibilityDescription = stringResource(R.string.alert_firewall_accessibility_explanation),
            accessibilityConfirm = stringResource(R.string.univ_accessibility_dialog_positive),
            accessibilityDismiss = stringResource(R.string.univ_accessibility_dialog_negative),
        ),
        isLoadingStats = loadingStats,
        onSettingChange = ::change,
        onLogsClick = onNavigateToLogs,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onBackClick = onBackClick,
    )
}

private data class AndroidUniversalFirewallLabels(
    val deviceLock: String, val background: String, val unknown: String, val udp: String, val dnsBypass: String,
    val newApp: String, val metered: String, val http: String, val lockdown: String,
)

@Composable
private fun androidUniversalFirewallLabels() = AndroidUniversalFirewallLabels(
    deviceLock = stringResource(R.string.univ_firewall_rule_1),
    background = stringResource(R.string.univ_firewall_rule_2),
    unknown = stringResource(R.string.univ_firewall_rule_3),
    udp = stringResource(R.string.univ_firewall_rule_4),
    dnsBypass = stringResource(R.string.univ_firewall_rule_5),
    newApp = stringResource(R.string.univ_firewall_rule_6),
    metered = stringResource(R.string.univ_firewall_rule_9),
    http = stringResource(R.string.univ_firewall_rule_8),
    lockdown = stringResource(R.string.univ_firewall_rule_10),
)

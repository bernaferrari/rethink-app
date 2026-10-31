/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.CustomDomainDAO
import com.bernaferrari.bravedns.database.CustomIpDao
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.BackgroundAccessibilityService
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Android adapter for [RethinkFirewallSettingsScreen]: the universal rules read and write the same
 * [PersistentState] flags as before (the firewall reads them unchanged), the Rules entry counts every
 * IP and domain rule, and each row's blocked count comes from the connection log.
 */
@Composable
fun FirewallSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    connTrackerRepository: ConnectionTrackerRepository,
    onRulesClick: () -> Unit,
    onNavigateToLogs: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val ipDao = koinInject<CustomIpDao>()
    val domainDao = koinInject<CustomDomainDAO>()
    val rulesCount by remember(ipDao, domainDao) {
        combine(ipDao.observeEveryIpRuleCount(), domainDao.observeEveryDomainRuleCount()) { ips, domains -> ips + domains }
    }.collectAsState(initial = null)
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
        val rows = withContext(Dispatchers.IO) { connTrackerRepository.getBlockedUniversalRulesCount() }
        blockedCounts = rows.associate { it.blockedByRule to it.count }
        loadingStats = false
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

    fun setting(rule: FirewallRuleset, label: Int, description: Int, checked: Boolean, icon: RethinkUniversalFirewallIcon) =
        RethinkUniversalFirewallSetting(rule.id, context.getString(label), checked, icon, blockedCounts[rule.id] ?: 0, context.getString(description))

    val everyday = listOf(
        setting(FirewallRuleset.RULE1B, R.string.univ_rule_new_apps, R.string.univ_rule_new_apps_desc, newApp, RethinkUniversalFirewallIcon.NewApp),
        setting(FirewallRuleset.RULE3, R.string.univ_rule_screen_locked, R.string.univ_rule_screen_locked_desc, deviceLock, RethinkUniversalFirewallIcon.DeviceLock),
        setting(FirewallRuleset.RULE4, R.string.univ_firewall_rule_2, R.string.univ_rule_not_in_use_desc, background, RethinkUniversalFirewallIcon.Background),
    )
    val strict = listOf(
        setting(FirewallRuleset.RULE6, R.string.univ_firewall_rule_4, R.string.univ_rule_udp_desc, udp, RethinkUniversalFirewallIcon.Udp),
        setting(FirewallRuleset.RULE10, R.string.univ_rule_http, R.string.univ_rule_http_desc, http, RethinkUniversalFirewallIcon.Http),
        setting(FirewallRuleset.RULE5, R.string.univ_rule_unknown, R.string.univ_rule_unknown_desc, unknown, RethinkUniversalFirewallIcon.Unknown),
        setting(FirewallRuleset.RULE7, R.string.univ_firewall_rule_5, R.string.univ_rule_dns_bypass_desc, dnsBypass, RethinkUniversalFirewallIcon.Dns),
        setting(FirewallRuleset.RULE1F, R.string.univ_firewall_rule_9, R.string.univ_rule_mobile_data_desc, metered, RethinkUniversalFirewallIcon.Metered),
    )
    val lockdownSetting = setting(FirewallRuleset.RULE11, R.string.univ_rule_lockdown, R.string.univ_rule_lockdown_desc, lockdown, RethinkUniversalFirewallIcon.Lockdown)

    val rulesTitle = stringResource(R.string.lbl_rules)
    val rulesFormat = stringResource(R.string.firewall_rules_count)
    RethinkFirewallSettingsScreen(
        strings = RethinkFirewallSettingsStrings(
            title = stringResource(R.string.lbl_firewall),
            explanation = stringResource(R.string.universal_firewall_explanation),
            rulesTitle = rulesTitle,
            rulesDescription = stringResource(R.string.univ_view_blocked_ip_desc),
            rulesWithCount = { count -> if (count == 0) rulesTitle else rulesFormat.format(count) },
            everydaySection = stringResource(R.string.firewall_section_everyday),
            strictSection = stringResource(R.string.firewall_section_strict),
            lockdownSection = stringResource(R.string.firewall_section_lockdown),
            blocked = { count -> stringResource(R.string.two_argument_colon, stringResource(R.string.lbl_blocked), count.toString()) },
            lockdownConfirmTitle = stringResource(R.string.firewall_lockdown_confirm_title),
            lockdownConfirmMessage = stringResource(R.string.firewall_lockdown_confirm_message),
            lockdownConfirm = stringResource(R.string.firewall_lockdown_confirm),
            cancel = stringResource(R.string.lbl_cancel),
            accessibilityTitle = stringResource(R.string.alert_permission_accessibility),
            accessibilityDescription = stringResource(R.string.alert_firewall_accessibility_explanation),
            accessibilityConfirm = stringResource(R.string.univ_accessibility_dialog_positive),
            accessibilityDismiss = stringResource(R.string.univ_accessibility_dialog_negative),
        ),
        everyday = everyday,
        strict = strict,
        lockdown = lockdownSetting,
        rulesCount = rulesCount,
        isLoadingStats = loadingStats,
        onSettingChange = ::change,
        onRulesClick = onRulesClick,
        onLogsClick = onNavigateToLogs,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        initialFocusKey = initialFocusKey,
        onBackClick = onBackClick,
    )
}

/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.BackgroundAccessibilityService
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class UniversalFirewallSettingsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()
    private val connTrackerRepository by inject<ConnectionTrackerRepository>()

    private var blockedUniversalRules: List<ConnectionTracker> = emptyList()
    private var stats by mutableStateOf<List<StatEntry>>(emptyList())
    private var isLoadingStats by mutableStateOf(true)

    private var blockWhenDeviceLocked by mutableStateOf(false)
    private var blockAppWhenBackground by mutableStateOf(false)
    private var udpBlocked by mutableStateOf(false)
    private var blockUnknownConnections by mutableStateOf(false)
    private var disallowDnsBypass by mutableStateOf(false)
    private var blockNewApp by mutableStateOf(false)
    private var blockMeteredConnections by mutableStateOf(false)
    private var blockHttpConnections by mutableStateOf(false)
    private var universalLockdown by mutableStateOf(false)
    private var showPermissionDialog by mutableStateOf(false)

    companion object {
        const val RULES_SEARCH_ID = "R:"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }
        syncToggleStates()
        loadStats()

        setContent {
            RethinkTheme {
                UniversalFirewallScreen()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()
        syncToggleStates()
        checkAppNotInUseRule()
        loadStats()
    }

    private fun syncToggleStates() {
        blockWhenDeviceLocked = persistentState.getBlockWhenDeviceLocked()
        blockAppWhenBackground = persistentState.getBlockAppWhenBackground()
        udpBlocked = persistentState.getUdpBlocked()
        blockUnknownConnections = persistentState.getBlockUnknownConnections()
        disallowDnsBypass = persistentState.getDisallowDnsBypass()
        blockNewApp = persistentState.getBlockNewlyInstalledApp()
        blockMeteredConnections = persistentState.getBlockMeteredConnections()
        blockHttpConnections = persistentState.getBlockHttpConnections()
        universalLockdown = persistentState.getUniversalLockdown()
    }

    private fun checkAppNotInUseRule() {
        if (!persistentState.getBlockAppWhenBackground()) return

        val isAccessibilityServiceRunning =
            Utilities.isAccessibilityServiceEnabled(
                this,
                BackgroundAccessibilityService::class.java
            )
        val isAccessibilityServiceEnabled =
            Utilities.isAccessibilityServiceEnabledViaSettingsSecure(
                this,
                BackgroundAccessibilityService::class.java
            )

        Napier.d(
            "backgroundEnabled? ${persistentState.getBlockAppWhenBackground()}, isServiceEnabled? $isAccessibilityServiceEnabled, isServiceRunning? $isAccessibilityServiceRunning"
        )
        val isAccessibilityServiceFunctional =
            isAccessibilityServiceRunning && isAccessibilityServiceEnabled

        if (!isAccessibilityServiceFunctional) {
            persistentState.setBlockAppWhenBackground(false)
            blockAppWhenBackground = false
            Utilities.showToastUiCentered(
                this,
                getString(R.string.accessibility_failure_toast),
                Toast.LENGTH_SHORT
            )
            return
        }

        if (isAccessibilityServiceRunning) {
            blockAppWhenBackground = persistentState.getBlockAppWhenBackground()
            return
        }
    }

    private fun handleBackgroundToggle(enabled: Boolean) {
        if (!enabled) {
            blockAppWhenBackground = false
            persistentState.setBlockAppWhenBackground(false)
            logEvent("Univ firewall background mode changed toggled to false")
            return
        }

        val isAccessibilityServiceRunning =
            Utilities.isAccessibilityServiceEnabled(
                this,
                BackgroundAccessibilityService::class.java
            )
        val isAccessibilityServiceEnabled =
            Utilities.isAccessibilityServiceEnabledViaSettingsSecure(
                this,
                BackgroundAccessibilityService::class.java
            )
        val isAccessibilityServiceFunctional =
            isAccessibilityServiceRunning && isAccessibilityServiceEnabled

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

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Utilities.showToastUiCentered(
                this,
                getString(R.string.alert_firewall_accessibility_exception),
                Toast.LENGTH_SHORT
            )
            Napier.e("Failure accessing accessibility settings: ${e.message}", e)
        }
    }

    private fun loadStats() {
        isLoadingStats = true
        io {
            // get stats for all the firewall rules
            // update the UI with the stats
            // 1. device locked - 2. background mode - 3. unknown 4. udp 5. dns bypass 6. new app 7.
            // metered 8. http 9. universal lockdown
            // instead get all the stats in one go and update the UI
            blockedUniversalRules = connTrackerRepository.getBlockedUniversalRulesCount()
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
                blockedUniversalRules.filter {
                    it.blockedByRule.contains(FirewallRuleset.RULE1F.id)
                }
            val http =
                blockedUniversalRules.filter {
                    it.blockedByRule.contains(FirewallRuleset.RULE10.id)
                }
            val universalLockdown =
                blockedUniversalRules.filter {
                    it.blockedByRule.contains(FirewallRuleset.RULE11.id)
                }

            val blockedCountList =
                listOf(
                    deviceLocked.size,
                    backgroundMode.size,
                    unknown.size,
                    udp.size,
                    dnsBypass.size,
                    newApp.size,
                    metered.size,
                    http.size,
                    universalLockdown.size
                )

            val maxValue = blockedCountList.maxOrNull()?.toDouble() ?: 0.0

            val updatedStats = listOf(
                StatEntry(FirewallRuleset.RULE3.id, deviceLocked.size, calculatePercentage(deviceLocked.size, maxValue)),
                StatEntry(FirewallRuleset.RULE4.id, backgroundMode.size, calculatePercentage(backgroundMode.size, maxValue)),
                StatEntry(FirewallRuleset.RULE5.id, unknown.size, calculatePercentage(unknown.size, maxValue)),
                StatEntry(FirewallRuleset.RULE6.id, udp.size, calculatePercentage(udp.size, maxValue)),
                StatEntry(FirewallRuleset.RULE7.id, dnsBypass.size, calculatePercentage(dnsBypass.size, maxValue)),
                StatEntry(FirewallRuleset.RULE1B.id, newApp.size, calculatePercentage(newApp.size, maxValue)),
                StatEntry(FirewallRuleset.RULE1F.id, metered.size, calculatePercentage(metered.size, maxValue)),
                StatEntry(FirewallRuleset.RULE10.id, http.size, calculatePercentage(http.size, maxValue)),
                StatEntry(FirewallRuleset.RULE11.id, universalLockdown.size, calculatePercentage(universalLockdown.size, maxValue))
            )

            uiCtx {
                if (!canPerformUiAction()) return@uiCtx
                stats = updatedStats
                isLoadingStats = false
            }
        }
    }

    private fun calculatePercentage(count: Int, maxValue: Double): Int {
        if (maxValue == 0.0) return 0
        return ((count / maxValue) * 100).toInt()
    }

    private fun canPerformUiAction(): Boolean {
        return !isFinishing &&
            !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) &&
            !isChangingConfigurations
    }

    private fun startActivity(rule: String?) {
        if (rule.isNullOrEmpty()) return

        // if the rules are not blocked, then no need to start the activity
        val size = stats.firstOrNull { it.ruleId == rule }?.count ?: 0
        if (size == 0) return

        val intent = Intent(this, NetworkLogsActivity::class.java)
        val searchParam = RULES_SEARCH_ID + rule
        intent.putExtra(Constants.SEARCH_QUERY, searchParam)
        startActivity(intent)
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Univ firewall setting", EventSource.UI, false, details)
    }

    @Composable
    private fun UniversalFirewallScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            SectionHeader(
                title = stringResourceCompat(R.string.firewall_act_universal_tab),
                description = stringResourceCompat(R.string.universal_firewall_explanation)
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_device_lock,
                label = stringResourceCompat(R.string.univ_firewall_rule_1),
                checked = blockWhenDeviceLocked,
                onCheckedChange = {
                    blockWhenDeviceLocked = it
                    persistentState.setBlockWhenDeviceLocked(it)
                    logEvent("Univ firewall device locked mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE3.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE3.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_foreground,
                label = stringResourceCompat(R.string.univ_firewall_rule_2),
                checked = blockAppWhenBackground,
                onCheckedChange = { handleBackgroundToggle(it) },
                stats = statsFor(FirewallRuleset.RULE4.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE4.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_unknown_app,
                label = stringResourceCompat(R.string.univ_firewall_rule_3),
                checked = blockUnknownConnections,
                onCheckedChange = {
                    blockUnknownConnections = it
                    persistentState.setBlockUnknownConnections(it)
                    logEvent("Univ firewall unknown connection mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE5.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE5.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_udp,
                label = stringResourceCompat(R.string.univ_firewall_rule_4),
                checked = udpBlocked,
                onCheckedChange = {
                    udpBlocked = it
                    persistentState.setUdpBlocked(it)
                    logEvent("Univ firewall UDP connection mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE6.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE6.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_prevent_dns_leaks,
                label = stringResourceCompat(R.string.univ_firewall_rule_5),
                checked = disallowDnsBypass,
                onCheckedChange = {
                    disallowDnsBypass = it
                    persistentState.setDisallowDnsBypass(it)
                    logEvent("Univ firewall DNS bypass mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE7.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE7.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_app_info,
                label = stringResourceCompat(R.string.univ_firewall_rule_6),
                checked = blockNewApp,
                onCheckedChange = {
                    blockNewApp = it
                    persistentState.setBlockNewlyInstalledApp(it)
                    logEvent("Univ firewall new app block mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE1B.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE1B.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_univ_metered,
                label = stringResourceCompat(R.string.univ_firewall_rule_9),
                checked = blockMeteredConnections,
                onCheckedChange = {
                    blockMeteredConnections = it
                    persistentState.setBlockMeteredConnections(it)
                    logEvent("Univ firewall metered connection block mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE1F.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE1F.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_http,
                label = stringResourceCompat(R.string.univ_firewall_rule_8),
                checked = blockHttpConnections,
                onCheckedChange = {
                    blockHttpConnections = it
                    persistentState.setBlockHttpConnections(it)
                    logEvent("Univ firewall HTTP block mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE10.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE10.id) }
            )
            ToggleWithStats(
                iconRes = R.drawable.ic_global_lockdown,
                label = stringResourceCompat(R.string.univ_firewall_rule_10),
                checked = universalLockdown,
                onCheckedChange = {
                    universalLockdown = it
                    persistentState.setUniversalLockdown(it)
                    logEvent("Univ firewall universal lockdown mode changed toggled to $it")
                },
                stats = statsFor(FirewallRuleset.RULE11.id),
                loading = isLoadingStats,
                onStatsClick = { startActivity(FirewallRuleset.RULE11.id) }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text(text = stringResourceCompat(R.string.alert_permission_accessibility)) },
                text = { Text(text = stringResourceCompat(R.string.alert_firewall_accessibility_explanation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            openAccessibilitySettings()
                        }
                    ) {
                        Text(text = stringResourceCompat(R.string.univ_accessibility_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text(text = stringResourceCompat(R.string.univ_accessibility_dialog_negative))
                    }
                }
            )
        }
    }

    @Composable
    private fun SectionHeader(title: String, description: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    @Composable
    private fun ToggleWithStats(
        iconRes: Int,
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        stats: StatEntry?,
        loading: Boolean,
        onStatsClick: () -> Unit
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
        }
    }

    @Composable
    private fun StatsRow(
        stats: StatEntry?,
        loading: Boolean,
        onClick: () -> Unit
    ) {
        val countText = if (loading || stats == null) "-" else stats.count.toString()
        val progressValue = if (loading || stats == null) 0.1f else stats.percent / 100f
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
            Icon(
                painter = painterResource(R.drawable.ic_right_arrow_white),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    private fun statsFor(ruleId: String): StatEntry? {
        return stats.firstOrNull { it.ruleId == ruleId }
    }

    private fun io(f: suspend () -> Unit): Job {
        return lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) {
            context.getString(id, *args)
        } else {
            context.getString(id)
        }
    }

    private data class StatEntry(
        val ruleId: String,
        val count: Int,
        val percent: Int
    )
}

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.AppUpdates
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.AutoStart
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.CrashReports
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.FirewallBubble
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.IpInfo
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.Logs
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.Tombstone
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.isFdroidFlavour

/**
 * Settings › App › General: how Keepout itself behaves (start on boot, uninstalled apps, update
 * checks, crash reports). Backup & restore and About are rows on the Settings hub; logging has its
 * own screen ([LoggingSettingsScreen]).
 */
@Composable
fun MiscSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
) {
    var autoStartEnabled by remember { mutableStateOf(persistentState.prefAutoStartBootUp) }
    var tombstoneEnabled by remember { mutableStateOf(persistentState.tombstoneApps) }
    var checkUpdatesEnabled by remember { mutableStateOf(persistentState.checkForAppUpdate) }
    var firebaseEnabled by remember { mutableStateOf(persistentState.firebaseErrorReportingEnabled) }
    val isFdroid = isFdroidFlavour()
    val toggles = listOfNotNull(
        RethinkMiscToggle("general_autostart", stringResource(R.string.settings_autostart_bootup_heading), stringResource(R.string.settings_autostart_bootup_desc), autoStartEnabled, AutoStart),
        RethinkMiscToggle("general_tombstone", stringResource(R.string.tombstone_app_title), stringResource(R.string.tombstone_app_desc), tombstoneEnabled, Tombstone),
        if (!isFdroid) RethinkMiscToggle("general_app_updates", stringResource(R.string.settings_check_update_heading), stringResource(R.string.settings_check_update_desc), checkUpdatesEnabled, AppUpdates) else null,
        if (!isFdroid) RethinkMiscToggle("general_crash_reports", stringResource(R.string.settings_firebase_error_reporting_heading), stringResource(R.string.settings_firebase_error_reporting_desc), firebaseEnabled, CrashReports) else null,
    )
    RethinkMiscSettingsScreen(
        strings = settingsListStrings(
            title = stringResource(R.string.settings_general_header).titlecaseFirst(),
            section = stringResource(R.string.settings_misc_behavior_section),
        ),
        toggles = toggles,
        onToggleChange = { id, enabled ->
            when (id) {
                "general_autostart" -> { autoStartEnabled = enabled; persistentState.prefAutoStartBootUp = enabled }
                "general_tombstone" -> { tombstoneEnabled = enabled; persistentState.tombstoneApps = enabled }
                "general_app_updates" -> { checkUpdatesEnabled = enabled; persistentState.checkForAppUpdate = enabled }
                "general_crash_reports" -> { firebaseEnabled = enabled; persistentState.firebaseErrorReportingEnabled = enabled; com.bernaferrari.bravedns.util.FirebaseErrorReporting.setEnabled(enabled) }
            }
            logSettingChange(eventLogger, "General setting", id, enabled)
        },
        onBackClick = onBackClick,
        focusedSettingId = initialFocusKey?.takeIf { it.isNotBlank() },
    )
}

/**
 * Settings › Advanced › Logging: what Keepout records on this device (connection and DNS logs, how
 * long they're kept, provider details for addresses, packet capture) and the firewall bubble.
 */
@Composable
fun LoggingSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
) {
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    var firewallBubbleEnabled by remember { mutableStateOf(persistentState.firewallBubbleEnabled) }
    var ipInfoEnabled by remember { mutableStateOf(persistentState.downloadIpInfo) }
    val toggles = listOfNotNull(
        RethinkMiscToggle("general_logs", stringResource(R.string.settings_enable_logs), stringResource(R.string.settings_enable_logs_desc), logsEnabled, Logs),
        RethinkMiscToggle("general_ip_info", stringResource(R.string.download_ip_info_title), stringResource(R.string.download_ip_info_desc, stringResource(R.string.lbl_ipinfo_inc)).replace("..", "."), ipInfoEnabled, IpInfo),
        if (isAtleastQ()) RethinkMiscToggle("general_firewall_bubble", stringResource(R.string.firewall_bubble_title), stringResource(R.string.firewall_bubble_desc), firewallBubbleEnabled, FirewallBubble) else null,
    )
    RethinkMiscSettingsScreen(
        strings = settingsListStrings(
            title = stringResource(R.string.settings_logging_title),
            section = stringResource(R.string.settings_logging_section),
        ),
        toggles = toggles,
        onToggleChange = { id, enabled ->
            when (id) {
                "general_logs" -> { logsEnabled = enabled; persistentState.logsEnabled = enabled }
                "general_firewall_bubble" -> { firewallBubbleEnabled = enabled; persistentState.firewallBubbleEnabled = enabled }
                "general_ip_info" -> { ipInfoEnabled = enabled; persistentState.downloadIpInfo = enabled }
            }
            logSettingChange(eventLogger, "Logging setting", id, enabled)
        },
        additionalSettings = { Column { LogRetentionSetting(persistentState); PcapCaptureSetting() } },
        onBackClick = onBackClick,
        focusedSettingId = initialFocusKey?.takeIf { it.isNotBlank() },
    )
}

/** Only title and section are shown when the backup and about rows are off. */
private fun settingsListStrings(title: String, section: String) = RethinkMiscSettingsStrings(
    title = title,
    backupSection = "",
    backupTitle = "",
    backupDescription = "",
    generalSection = section,
    aboutSection = "",
    websiteTitle = "",
    websiteDescription = "",
    aboutTitle = "",
    aboutDescription = "",
)

private fun logSettingChange(eventLogger: EventLogger, message: String, id: String, enabled: Boolean) =
    eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, message, EventSource.UI, true, "$id set to $enabled")

private fun String.titlecaseFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

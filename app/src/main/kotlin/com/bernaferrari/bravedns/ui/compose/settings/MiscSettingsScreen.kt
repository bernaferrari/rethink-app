/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.bottomsheet.BackupRestoreSheet
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.AppUpdates
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.AutoStart
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.CrashReports
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.Downloader
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.FirewallBubble
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.IpInfo
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.Logs
import com.bernaferrari.bravedns.ui.compose.settings.RethinkMiscSettingIcon.Tombstone
import com.bernaferrari.bravedns.util.UIUtils.openUrl
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.isFdroidFlavour

/** Android preference, backup and external-link adapter for [RethinkMiscSettingsScreen]. */
@Composable
fun MiscSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
    onOpenAbout: () -> Unit = {},
    onRefreshDatabase: (() -> Unit)? = null,
    onThemeModeChanged: ((Int) -> Unit)? = null,
    onThemeColorChanged: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    var autoStartEnabled by remember { mutableStateOf(persistentState.prefAutoStartBootUp) }
    var tombstoneEnabled by remember { mutableStateOf(persistentState.tombstoneApps) }
    var firewallBubbleEnabled by remember { mutableStateOf(persistentState.firewallBubbleEnabled) }
    var ipInfoEnabled by remember { mutableStateOf(persistentState.downloadIpInfo) }
    var checkUpdatesEnabled by remember { mutableStateOf(persistentState.checkForAppUpdate) }
    var firebaseEnabled by remember { mutableStateOf(persistentState.firebaseErrorReportingEnabled) }
    var customDownloadEnabled by remember { mutableStateOf(persistentState.useCustomDownloadManager) }
    var showBackupSheet by remember { mutableStateOf(false) }
    val showFirewallBubble = isAtleastQ()
    val isFdroid = isFdroidFlavour()
    val toggles = listOfNotNull(
        RethinkMiscToggle("general_logs", stringResource(R.string.settings_enable_logs), stringResource(R.string.settings_enable_logs_desc), logsEnabled, Logs),
        RethinkMiscToggle("general_autostart", stringResource(R.string.settings_autostart_bootup_heading), stringResource(R.string.settings_autostart_bootup_desc), autoStartEnabled, AutoStart),
        RethinkMiscToggle("general_tombstone", stringResource(R.string.tombstone_app_title), stringResource(R.string.tombstone_app_desc), tombstoneEnabled, Tombstone),
        if (showFirewallBubble) RethinkMiscToggle("general_firewall_bubble", stringResource(R.string.firewall_bubble_title), stringResource(R.string.firewall_bubble_desc), firewallBubbleEnabled, FirewallBubble) else null,
        RethinkMiscToggle("general_ip_info", stringResource(R.string.download_ip_info_title), stringResource(R.string.download_ip_info_desc, stringResource(R.string.lbl_ipinfo_inc)), ipInfoEnabled, IpInfo),
        if (!isFdroid) RethinkMiscToggle("general_app_updates", stringResource(R.string.settings_check_update_heading), stringResource(R.string.settings_check_update_desc), checkUpdatesEnabled, AppUpdates) else null,
        if (!isFdroid) RethinkMiscToggle("general_crash_reports", stringResource(R.string.settings_firebase_error_reporting_heading), stringResource(R.string.settings_firebase_error_reporting_desc), firebaseEnabled, CrashReports) else null,
        RethinkMiscToggle("general_custom_downloader", stringResource(R.string.settings_custom_downloader_heading), stringResource(R.string.settings_custom_downloader_desc), customDownloadEnabled, Downloader),
    )

    RethinkMiscSettingsScreen(
        strings = RethinkMiscSettingsStrings(
            title = stringResource(R.string.settings_general_header).titlecaseFirst(),
            backupSection = stringResource(R.string.brbs_title),
            backupTitle = stringResource(R.string.brbs_backup_title),
            backupDescription = stringResource(R.string.brbs_backup_desc),
            generalSection = stringResource(R.string.settings_general_header).titlecaseFirst(),
            aboutSection = stringResource(R.string.title_about),
            websiteTitle = stringResource(R.string.about_website),
            websiteDescription = stringResource(R.string.about_website_link),
            aboutTitle = stringResource(R.string.title_about),
            aboutDescription = stringResource(R.string.app_name),
        ),
        toggles = toggles,
        onToggleChange = { id, enabled ->
            when (id) {
                "general_logs" -> { logsEnabled = enabled; persistentState.logsEnabled = enabled }
                "general_autostart" -> { autoStartEnabled = enabled; persistentState.prefAutoStartBootUp = enabled }
                "general_tombstone" -> { tombstoneEnabled = enabled; persistentState.tombstoneApps = enabled }
                "general_firewall_bubble" -> { firewallBubbleEnabled = enabled; persistentState.firewallBubbleEnabled = enabled }
                "general_ip_info" -> { ipInfoEnabled = enabled; persistentState.downloadIpInfo = enabled }
                "general_app_updates" -> { checkUpdatesEnabled = enabled; persistentState.checkForAppUpdate = enabled }
                "general_crash_reports" -> { firebaseEnabled = enabled; persistentState.firebaseErrorReportingEnabled = enabled }
                "general_custom_downloader" -> { customDownloadEnabled = enabled; persistentState.useCustomDownloadManager = enabled }
            }
            eventLogger.log(
                EventType.UI_SETTING_CHANGED,
                Severity.LOW,
                "General setting",
                EventSource.UI,
                true,
                "$id set to $enabled",
            )
        },
        onBackupRestore = { showBackupSheet = true },
        onOpenWebsite = { openUrl(context, context.getString(R.string.about_website_link)) },
        onOpenAbout = onOpenAbout,
        appearanceContent = {
            AppearanceSettingsCard(
                themePreference = persistentState.theme,
                colorPresetId = persistentState.themeColorPreset,
                onAppearanceModeSelected = { mode ->
                    val themeId = mode.toThemePreference()
                    persistentState.theme = themeId
                    onThemeModeChanged?.invoke(themeId)
                    eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, "Appearance", EventSource.UI, true, "Theme set to ${mode.name.lowercase()}")
                },
                onColorPresetSelected = { preset ->
                    persistentState.themeColorPreset = preset.id
                    onThemeColorChanged?.invoke(preset.id)
                    eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, "Appearance color", EventSource.UI, true, "Color preset set to ${preset.name.lowercase()}")
                },
                showSectionHeader = true,
            )
        },
        onBackClick = onBackClick,
        focusedSettingId = initialFocusKey?.takeIf { it.isNotBlank() },
    )
    if (showBackupSheet) BackupRestoreSheet(onDismiss = { showBackupSheet = false })
}

private fun String.titlecaseFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

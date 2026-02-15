/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.bottomsheet.BackupRestoreSheet
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.isFdroidFlavour
import com.celzero.bravedns.util.Themes

@Composable
fun MiscSettingsScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null,
    onRefreshDatabase: (() -> Unit)? = null
) {
    var logsEnabled by remember { mutableStateOf(persistentState.logsEnabled) }
    var checkUpdatesEnabled by remember { mutableStateOf(persistentState.checkForAppUpdate) }
    var firebaseEnabled by remember { mutableStateOf(persistentState.firebaseErrorReportingEnabled) }
    var ipInfoEnabled by remember { mutableStateOf(persistentState.downloadIpInfo) }
    var customDownloadEnabled by remember { mutableStateOf(persistentState.useCustomDownloadManager) }
    var autoStartEnabled by remember { mutableStateOf(persistentState.prefAutoStartBootUp) }
    var tombstoneEnabled by remember { mutableStateOf(persistentState.tombstoneApps) }
    var firewallBubbleEnabled by remember { mutableStateOf(persistentState.firewallBubbleEnabled) }
    var appearanceMode by remember { mutableStateOf(themePreferenceToAppearanceMode(persistentState.theme)) }
    var showBackupSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.settings_general_header),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            RethinkAnimatedSection(index = 0) {
                SectionHeader(title = stringResource(R.string.settings_theme_heading))
                RethinkListGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.cardPadding, vertical = Dimensions.spacingMd),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_theme_dialog_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(
                                id = R.string.settings_selected_theme,
                                appearanceMode.toDisplayName()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppearanceMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = option == appearanceMode,
                                    onClick = {
                                        if (appearanceMode == option) return@SegmentedButton
                                        appearanceMode = option
                                        persistentState.theme = option.toThemePreference()
                                        logEvent(
                                            eventLogger = eventLogger,
                                            msg = "Appearance",
                                            details = "Theme set to ${option.name.lowercase()}"
                                        )
                                        (context as? Activity)?.recreate()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppearanceMode.entries.size
                                    ),
                                    label = {
                                        Text(
                                            text = option.toDisplayName(),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            RethinkAnimatedSection(index = 1) {
                SectionHeader(title = stringResource(R.string.settings_general_header))
                RethinkListGroup {
                ToggleListItem(
                    title = stringResource(id = R.string.settings_enable_logs),
                    description = stringResource(id = R.string.settings_enable_logs_desc),
                    iconRes = R.drawable.ic_logs_accent,
                    checked = logsEnabled,
                    onCheckedChange = { enabled ->
                        logsEnabled = enabled
                        persistentState.logsEnabled = enabled
                        logEvent(eventLogger, "Logs", "User ${if (enabled) "enabled" else "disabled"} logs")
                    }
                )

                ToggleListItem(
                    title = stringResource(id = R.string.settings_autostart_bootup_heading),
                    description = stringResource(id = R.string.settings_autostart_bootup_desc),
                    iconRes = R.drawable.ic_auto_start,
                    checked = autoStartEnabled,
                    onCheckedChange = { enabled ->
                        autoStartEnabled = enabled
                        persistentState.prefAutoStartBootUp = enabled
                        logEvent(eventLogger, "Auto-start", "Auto-start on power-up set to $enabled")
                    }
                )

                ToggleListItem(
                    title = stringResource(id = R.string.tombstone_app_title),
                    description = stringResource(id = R.string.tombstone_app_desc),
                    iconRes = R.drawable.ic_tombstone,
                    checked = tombstoneEnabled,
                    onCheckedChange = { enabled ->
                        tombstoneEnabled = enabled
                        persistentState.tombstoneApps = enabled
                        logEvent(eventLogger, "Tombstone apps", "Remember uninstalled apps set to $enabled")
                    }
                )

                if (isAtleastQ()) {
                    ToggleListItem(
                        title = stringResource(id = R.string.firewall_bubble_title),
                        description = stringResource(id = R.string.firewall_bubble_desc),
                        iconRes = R.drawable.ic_firewall_bubble,
                        checked = firewallBubbleEnabled,
                        onCheckedChange = { enabled ->
                            firewallBubbleEnabled = enabled
                            persistentState.firewallBubbleEnabled = enabled
                            logEvent(eventLogger, "Firewall bubble", "Firewall bubble set to $enabled")
                        }
                    )
                }

                if (!isFdroidFlavour()) {
                    ToggleListItem(
                        title = stringResource(id = R.string.settings_check_update_heading),
                        description = stringResource(id = R.string.settings_check_update_desc),
                        iconRes = R.drawable.ic_update,
                        checked = checkUpdatesEnabled,
                        onCheckedChange = { enabled ->
                            checkUpdatesEnabled = enabled
                            persistentState.checkForAppUpdate = enabled
                        }
                    )

                    ToggleListItem(
                        title = stringResource(id = R.string.settings_firebase_error_reporting_heading),
                        description = stringResource(id = R.string.settings_firebase_error_reporting_desc),
                        iconRes = R.drawable.ic_info,
                        checked = firebaseEnabled,
                        onCheckedChange = { enabled ->
                            firebaseEnabled = enabled
                            persistentState.firebaseErrorReportingEnabled = enabled
                        }
                    )
                }

                ToggleListItem(
                    title = stringResource(id = R.string.download_ip_info_title),
                    description = stringResource(id = R.string.download_ip_info_desc, stringResource(id = R.string.lbl_ipinfo_inc)),
                    iconRes = R.drawable.ic_ip_info,
                    checked = ipInfoEnabled,
                    onCheckedChange = { enabled ->
                        ipInfoEnabled = enabled
                        persistentState.downloadIpInfo = enabled
                    }
                )

                ToggleListItem(
                    title = stringResource(id = R.string.settings_custom_downloader_heading),
                    description = stringResource(id = R.string.settings_custom_downloader_desc),
                    iconRes = R.drawable.ic_settings,
                    checked = customDownloadEnabled,
                    showDivider = false,
                    onCheckedChange = { enabled ->
                        customDownloadEnabled = enabled
                        persistentState.useCustomDownloadManager = enabled
                    }
                )
            }
            }

            RethinkAnimatedSection(index = 2) {
                SectionHeader(title = stringResource(id = R.string.settings_import_export_heading))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.brbs_backup_title),
                        supporting = stringResource(id = R.string.settings_import_export_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_backup),
                        onClick = { showBackupSheet = true }
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.dc_refresh_toast),
                        supporting = stringResource(id = R.string.settings_import_export_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_refresh_white),
                        showDivider = false,
                        onClick = {
                            onRefreshDatabase?.invoke()
                            logEvent(eventLogger, "Database refresh", "User refreshed database")
                        }
                    )
                }
            }

            RethinkAnimatedSection(index = 3) {
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_website),
                        supporting = stringResource(id = R.string.about_website_link),
                        leadingIconPainter = painterResource(id = R.drawable.ic_website),
                        showDivider = false,
                        onClick = { openUrl(context, context.resources.getString(R.string.about_website_link)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
        }

        if (showBackupSheet) {
            BackupRestoreSheet(onDismiss = { showBackupSheet = false })
        }
    }
}

@Composable
private fun ToggleListItem(
    title: String,
    description: String,
    iconRes: Int,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIconPainter = painterResource(id = iconRes),
        showDivider = showDivider,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

private fun logEvent(eventLogger: EventLogger, msg: String, details: String) {
    eventLogger.log(
        type = EventType.UI_SETTING_CHANGED,
        severity = Severity.LOW,
        message = msg,
        source = EventSource.UI,
        userAction = true,
        details = details
    )
}

private enum class AppearanceMode {
    AUTO,
    LIGHT,
    DARK
}

private fun AppearanceMode.toThemePreference(): Int {
    return when (this) {
        AppearanceMode.AUTO -> Themes.SYSTEM_DEFAULT.id
        AppearanceMode.LIGHT -> Themes.LIGHT_PLUS.id
        AppearanceMode.DARK -> Themes.DARK_PLUS.id
    }
}

private fun themePreferenceToAppearanceMode(preference: Int): AppearanceMode {
    return when (preference) {
        Themes.SYSTEM_DEFAULT.id -> AppearanceMode.AUTO
        Themes.LIGHT.id, Themes.LIGHT_PLUS.id -> AppearanceMode.LIGHT
        else -> AppearanceMode.DARK
    }
}

@Composable
private fun AppearanceMode.toDisplayName(): String {
    return when (this) {
        AppearanceMode.AUTO -> stringResource(id = R.string.settings_theme_dialog_themes_1)
        AppearanceMode.LIGHT -> stringResource(id = R.string.settings_theme_dialog_themes_2)
        AppearanceMode.DARK -> stringResource(id = R.string.settings_theme_dialog_themes_3)
    }
}

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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.bottomsheet.BackupRestoreSheet
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities.isFdroidFlavour

@OptIn(ExperimentalMaterial3Api::class)
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
    var showBackupSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.brbs_backup_restore_desc)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.settings_enable_logs))
                            Text(text = stringResource(id = R.string.settings_enable_logs_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = logsEnabled,
                            onCheckedChange = { enabled ->
                                logsEnabled = enabled
                                persistentState.logsEnabled = enabled
                                logEvent(eventLogger, "Logs", "User ${if (enabled) "enabled" else "disabled"} logs")
                            }
                        )
                    }

                    if (!isFdroidFlavour()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(id = R.string.settings_check_update_heading))
                                Text(text = stringResource(id = R.string.settings_check_update_desc), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = checkUpdatesEnabled,
                                onCheckedChange = { enabled ->
                                    checkUpdatesEnabled = enabled
                                    persistentState.checkForAppUpdate = enabled
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(id = R.string.settings_firebase_error_reporting_heading))
                                Text(text = stringResource(id = R.string.settings_firebase_error_reporting_desc), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = firebaseEnabled,
                                onCheckedChange = { enabled ->
                                    firebaseEnabled = enabled
                                    persistentState.firebaseErrorReportingEnabled = enabled
                                }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.download_ip_info_title))
                            Text(text = stringResource(id = R.string.download_ip_info_desc, stringResource(id = R.string.lbl_ipinfo_inc)), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = ipInfoEnabled,
                            onCheckedChange = { enabled ->
                                ipInfoEnabled = enabled
                                persistentState.downloadIpInfo = enabled
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.settings_custom_downloader_heading))
                            Text(text = stringResource(id = R.string.settings_custom_downloader_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = customDownloadEnabled,
                            onCheckedChange = { enabled ->
                                customDownloadEnabled = enabled
                                persistentState.useCustomDownloadManager = enabled
                            }
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(id = R.string.brbs_backup_restore_desc), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showBackupSheet = true }) {
                        Text(text = stringResource(id = R.string.brbs_backup_title))
                    }
                    Button(onClick = {
                        onRefreshDatabase?.invoke()
                        logEvent(eventLogger, "Database refresh", "User refreshed database")
                    }) {
                        Text(text = stringResource(id = R.string.dc_refresh_toast))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { openUrl(context, context.getString(R.string.about_website_link)) }) {
                Text(text = stringResource(id = R.string.about_website))
            }
        }

        if (showBackupSheet) {
            BackupRestoreSheet(
                onDismiss = { showBackupSheet = false }
            )
        }
    }
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

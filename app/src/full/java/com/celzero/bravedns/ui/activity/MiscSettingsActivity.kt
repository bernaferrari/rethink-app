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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.bottomsheet.BackupRestoreDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.isFdroidFlavour
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MiscSettingsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val rdb by inject<RefreshDatabase>()
    private val eventLogger by inject<EventLogger>()
    private lateinit var backupLauncher: ActivityResultLauncher<Intent>
    private lateinit var restoreLauncher: ActivityResultLauncher<Intent>

    enum class BioMetricType(val action: Int, val mins: Long) {
        OFF(0, -1L),
        IMMEDIATE(1, 0L),
        FIVE_MIN(2, 5L),
        FIFTEEN_MIN(3, 15L);

        companion object {
            fun fromValue(action: Int): BioMetricType {
                return entries.firstOrNull { it.action == action } ?: OFF
            }
        }

        fun enabled(): Boolean {
            return this != OFF
        }
    }

    companion object {
        const val THEME_CHANGED_RESULT = 24
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

        backupLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }
        restoreLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }

        setContent {
            RethinkTheme {
                MiscSettingsContent()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun MiscSettingsContent() {
        var logsEnabled by mutableStateOf(persistentState.logsEnabled)
        var checkUpdatesEnabled by mutableStateOf(persistentState.checkForAppUpdate)
        var firebaseEnabled by mutableStateOf(persistentState.firebaseErrorReportingEnabled)
        var ipInfoEnabled by mutableStateOf(persistentState.downloadIpInfo)
        var customDownloadEnabled by mutableStateOf(persistentState.useCustomDownloadManager)

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                logEvent("Logs", "User ${if (enabled) "enabled" else "disabled"} logs")
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
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(id = R.string.brbs_backup_restore_desc), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { openBackupRestoreDialog() }) {
                        Text(text = stringResource(id = R.string.brbs_backup_title))
                    }
                    Button(onClick = { refreshDatabase() }) {
                        Text(text = stringResource(id = R.string.dc_refresh_toast))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { openUrl(this@MiscSettingsActivity, getString(R.string.about_website_link)) }) {
                Text(text = stringResource(id = R.string.about_website))
            }
        }
    }

    private fun openBackupRestoreDialog() {
        val dialog =
            BackupRestoreDialog(
                activity = this,
                backupLauncher = backupLauncher,
                restoreLauncher = restoreLauncher,
                onDismiss = {}
            )
        dialog.show()
    }

    private fun refreshDatabase() {
        lifecycleScope.launch { rdb.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE) }
        logEvent("Database refresh", "User refreshed database")
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(
            type = EventType.UI_SETTING_CHANGED,
            severity = Severity.LOW,
            message = msg,
            source = EventSource.UI,
            userAction = true,
            details = details
        )
    }
}

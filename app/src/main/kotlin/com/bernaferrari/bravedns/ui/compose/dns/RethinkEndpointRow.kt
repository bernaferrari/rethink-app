/*
 * Copyright 2022 RethinkDNS and its authors
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

package com.bernaferrari.bravedns.ui.compose.dns

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.RethinkDnsEndpoint
import com.bernaferrari.bravedns.database.isEditable
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.dns.ConfigureRethinkScreenType
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.ui.components.DnsRowAction

private const val TAG = "RethinkEndpointRow"

@Composable
fun RethinkEndpointRow(
    endpoint: RethinkDnsEndpoint,
    appConfig: AppConfig,
    onEditConfiguration: (ConfigureRethinkScreenType, String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failingToast = stringResource(R.string.status_failing)
    val startRethinkToast = stringResource(R.string.ssv_toast_start_rethink)
    val statusStrings = rememberDnsStatusStrings()
    val connectedPlus =
        stringResource(
            R.string.dns_connected_rethink_plus,
            endpoint.blocklistCount.toString()
        )
    val explanation =
        rememberDnsStatusExplanation(
            key = "${endpoint.url}:${endpoint.blocklistCount}",
            isSelected = endpoint.isActive,
            smartDnsEnabled = appConfig.isSmartDnsEnabled(),
            tag = TAG,
            statusTextMapper = { status ->
                if (status != R.string.dns_connected) {
                    (statusStrings[status] ?: "").replaceFirstChar(Char::titlecase)
                } else if (endpoint.blocklistCount > 0) {
                    connectedPlus
                } else {
                    statusStrings[status] ?: ""
                }
            }
        )
    val editEnabled = endpoint.isEditable(context)
    // Stock endpoints show as presets ("Family", not "RDNS Adult"); the stored name stays.
    val preset = RethinkDnsPreset.fromStoredName(endpoint.name)
    val description = when {
        preset == RethinkDnsPreset.Custom && endpoint.blocklistCount > 0 ->
            stringResource(R.string.rethink_preset_custom_count, endpoint.blocklistCount)
        preset != null -> stringResource(preset.descriptionRes())
        else -> endpoint.desc
    }

    val displayName = rethinkPresetName(endpoint.name)

    DnsEndpointManagedRow(
        key = endpoint.url,
        title = displayName,
        description = description,
        supporting = explanation.ifEmpty { null },
        selected = endpoint.isActive,
        action = if (editEnabled) DnsRowAction.Edit else DnsRowAction.Info,
        onSelectionChange = {
            launchDnsEndpointSelectionUpdate(scope, context, TAG, failingToast) {
                endpoint.isActive = true
                appConfig.handleRethinkChanges(endpoint)
            }
        },
        onInfo = { show ->
            show(
                DnsInfoDialogModel(
                    title = displayName,
                    message = endpoint.url + "\n\n" + endpoint.desc,
                    copyValue = endpoint.url,
                    confirmTextRes =
                        if (editEnabled) {
                            R.string.rt_edit_dialog_positive
                        } else {
                            R.string.dns_info_positive
                        },
                    onConfirm = {
                        if (editEnabled) {
                            openEditConfiguration(context, startRethinkToast, endpoint, onEditConfiguration)
                        }
                    }
                )
            )
        }
    )
}

private fun openEditConfiguration(
    context: Context,
    startRethinkToast: String,
    endpoint: RethinkDnsEndpoint,
    onEditConfiguration: (ConfigureRethinkScreenType, String, String) -> Unit
) {
    if (!VpnController.hasTunnel()) {
        Utilities.showToastUiCentered(
            context,
            startRethinkToast,
            Toast.LENGTH_SHORT
        )
        return
    }

    onEditConfiguration(ConfigureRethinkScreenType.REMOTE, endpoint.name, endpoint.url)
}

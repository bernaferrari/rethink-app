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

package com.celzero.bravedns.adapter

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.RethinkDnsEndpoint
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ONE_SEC = 1000L
private const val TAG = "RethinkEndpointAdapter"

@Composable
fun RethinkEndpointRow(endpoint: RethinkDnsEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var explanation by remember(endpoint.url) { mutableStateOf("") }

    LaunchedEffect(endpoint.url, endpoint.isActive, endpoint.blocklistCount) {
        if (endpoint.isActive && VpnController.hasTunnel() && !appConfig.isSmartDnsEnabled()) {
            while (isActive) {
                val status =
                    withContext(Dispatchers.IO) {
                        val state = VpnController.getDnsStatus(Backend.Preferred)
                        UIUtils.getDnsStatusStringRes(state)
                    }
                explanation =
                    if (status != R.string.dns_connected) {
                        context.getString(status).replaceFirstChar(Char::titlecase)
                    } else if (endpoint.blocklistCount > 0) {
                        context.getString(
                            R.string.dns_connected_rethink_plus,
                            endpoint.blocklistCount.toString()
                        )
                    } else {
                        context.getString(status)
                    }
                delay(ONE_SEC)
            }
        } else if (endpoint.isActive) {
            explanation = context.getString(R.string.rt_filter_parent_selected)
        } else {
            explanation = ""
        }
    }

    val infoIcon =
        if (endpoint.isEditable(context)) {
            R.drawable.ic_edit_icon
        } else {
            R.drawable.ic_info
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable { updateConnection(scope, endpoint, appConfig) },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = endpoint.name, style = MaterialTheme.typography.bodyLarge)
            if (explanation.isNotEmpty()) {
                Text(text = explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { showDohMetadataDialog(context, endpoint) }) {
            Icon(painter = painterResource(id = infoIcon), contentDescription = null)
        }
        Checkbox(
            checked = endpoint.isActive,
            onCheckedChange = { updateConnection(scope, endpoint, appConfig) }
        )
    }
}

private fun updateConnection(
    scope: CoroutineScope,
    endpoint: RethinkDnsEndpoint,
    appConfig: AppConfig
) {
    Napier.d("$TAG rdns update; ${endpoint.name}, ${endpoint.url}, ${endpoint.isActive}")
    scope.launch(Dispatchers.IO) {
        endpoint.isActive = true
        appConfig.handleRethinkChanges(endpoint)
    }
}

private fun showDohMetadataDialog(context: Context, endpoint: RethinkDnsEndpoint) {
    val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
    builder.setTitle(endpoint.name)
    builder.setMessage(endpoint.url + "\n\n" + endpoint.desc)
    builder.setCancelable(true)
    if (endpoint.isEditable(context)) {
        builder.setPositiveButton(context.getString(R.string.rt_edit_dialog_positive)) { _, _ ->
            openEditConfiguration(context, endpoint)
        }
    } else {
        builder.setPositiveButton(context.getString(R.string.dns_info_positive)) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
    }
    builder.setNeutralButton(context.getString(R.string.dns_info_neutral)) { _: DialogInterface, _: Int ->
        clipboardCopy(context, endpoint.url, context.getString(R.string.copy_clipboard_label))
        Utilities.showToastUiCentered(
            context,
            context.getString(R.string.info_dialog_url_copy_toast_msg),
            Toast.LENGTH_SHORT
        )
    }
    builder.create().show()
}

private fun openEditConfiguration(context: Context, endpoint: RethinkDnsEndpoint) {
    if (!VpnController.hasTunnel()) {
        Utilities.showToastUiCentered(
            context,
            context.getString(R.string.ssv_toast_start_rethink),
            Toast.LENGTH_SHORT
        )
        return
    }

    val intent = Intent(context, ConfigureRethinkBasicActivity::class.java)
    intent.putExtra(
        ConfigureRethinkBasicActivity.INTENT,
        ConfigureRethinkBasicActivity.FragmentLoader.REMOTE.ordinal
    )
    intent.putExtra(ConfigureRethinkBasicActivity.RETHINK_BLOCKLIST_NAME, endpoint.name)
    intent.putExtra(ConfigureRethinkBasicActivity.RETHINK_BLOCKLIST_URL, endpoint.url)
    context.startActivity(intent)
}

/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.celzero.bravedns.adapter

import android.content.Context
import android.content.DialogInterface
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
import com.celzero.bravedns.database.DnsCryptEndpoint
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ONE_SEC = 1000L

@Composable
fun DnsCryptRow(endpoint: DnsCryptEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var explanation by remember(endpoint.id) { mutableStateOf("") }

    LaunchedEffect(endpoint.id, endpoint.isSelected) {
        if (endpoint.isSelected && VpnController.hasTunnel() && !appConfig.isSmartDnsEnabled()) {
            while (isActive) {
                val status =
                    withContext(Dispatchers.IO) {
                        val state = VpnController.getDnsStatus(Backend.Preferred)
                        UIUtils.getDnsStatusStringRes(state)
                    }
                explanation = context.getString(status).replaceFirstChar(Char::titlecase)
                delay(ONE_SEC)
            }
        } else if (endpoint.isSelected) {
            explanation = context.getString(R.string.rt_filter_parent_selected)
        } else {
            explanation = ""
        }
    }

    val infoIcon =
        if (endpoint.isDeletable()) {
            R.drawable.ic_fab_uninstall
        } else {
            R.drawable.ic_info
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable { updateDnsCryptDetails(scope, endpoint, appConfig) },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = endpoint.dnsCryptName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (explanation.isNotEmpty()) {
                Text(text = explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { showExplanationOnImageClick(context, scope, endpoint, appConfig) }) {
            Icon(painter = painterResource(id = infoIcon), contentDescription = null)
        }
        Checkbox(
            checked = endpoint.isSelected,
            onCheckedChange = { updateDnsCryptDetails(scope, endpoint, appConfig) }
        )
    }
}

private fun showExplanationOnImageClick(
    context: Context,
    scope: CoroutineScope,
    dnsCryptEndpoint: DnsCryptEndpoint,
    appConfig: AppConfig
) {
    if (dnsCryptEndpoint.isDeletable()) showDeleteDialog(context, scope, appConfig, dnsCryptEndpoint.id)
    else {
        showDialogExplanation(
            context,
            dnsCryptEndpoint.dnsCryptName,
            dnsCryptEndpoint.dnsCryptURL,
            dnsCryptEndpoint.dnsCryptExplanation
        )
    }
}

private fun showDeleteDialog(
    context: Context,
    scope: CoroutineScope,
    appConfig: AppConfig,
    id: Int
) {
    val builder = MaterialAlertDialogBuilder(context)
    builder.setTitle(R.string.dns_crypt_custom_url_remove_dialog_title)
    builder.setMessage(R.string.dns_crypt_url_remove_dialog_message)
    builder.setCancelable(true)
    builder.setPositiveButton(context.getString(R.string.lbl_delete)) { _, _ ->
        deleteEndpoint(context, scope, appConfig, id)
    }

    builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ -> }
    val alertDialog: androidx.appcompat.app.AlertDialog = builder.create()
    alertDialog.setCancelable(true)
    alertDialog.show()
}

private fun showDialogExplanation(context: Context, title: String, url: String, message: String?) {
    val builder = MaterialAlertDialogBuilder(context)
    builder.setTitle(title)
    if (message == null) builder.setMessage(url)
    else builder.setMessage(url + "\n\n" + cryptDesc(context, message))
    builder.setCancelable(true)
    builder.setPositiveButton(context.getString(R.string.dns_info_positive)) { dialogInterface, _ ->
        dialogInterface.dismiss()
    }

    builder.setNeutralButton(context.getString(R.string.dns_info_neutral)) { _: DialogInterface, _: Int ->
        clipboardCopy(context, url, context.getString(R.string.copy_clipboard_label))
        Utilities.showToastUiCentered(
            context,
            context.getString(R.string.info_dialog_url_copy_toast_msg),
            Toast.LENGTH_SHORT
        )
    }
    val alertDialog: androidx.appcompat.app.AlertDialog = builder.create()
    alertDialog.setCancelable(true)
    alertDialog.show()
}

private fun cryptDesc(context: Context, message: String?): String {
    if (message.isNullOrEmpty()) return ""

    return try {
        if (message.contains("R.string.")) {
            val m = message.substringAfter("R.string.")
            val resId: Int =
                context.resources.getIdentifier(m, "string", context.packageName)
            context.getString(resId)
        } else {
            message
        }
    } catch (_: Exception) {
        ""
    }
}

private fun updateDnsCryptDetails(
    scope: CoroutineScope,
    endpoint: DnsCryptEndpoint,
    appConfig: AppConfig
) {
    scope.launch(Dispatchers.IO) {
        endpoint.isSelected = true
        appConfig.handleDnscryptChanges(endpoint)
    }
}

private fun deleteEndpoint(
    context: Context,
    scope: CoroutineScope,
    appConfig: AppConfig,
    id: Int
) {
    scope.launch(Dispatchers.IO) {
        appConfig.deleteDnscryptEndpoint(id)
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.dns_crypt_url_remove_success),
                Toast.LENGTH_SHORT
            )
        }
    }
}

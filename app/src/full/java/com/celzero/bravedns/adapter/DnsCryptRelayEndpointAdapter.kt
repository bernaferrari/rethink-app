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
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RelayRow(endpoint: DnsCryptRelayEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSelected by remember(endpoint.id) { mutableStateOf(endpoint.isSelected) }
    var explanation by remember(endpoint.id) { mutableStateOf("") }

    LaunchedEffect(endpoint.id, isSelected) {
        if (isSelected && !appConfig.isSmartDnsEnabled()) {
            val status =
                withContext(Dispatchers.IO) {
                    val state = VpnController.getDnsStatus(Backend.Preferred)
                    UIUtils.getDnsStatusStringRes(state)
                }
            explanation = context.getString(status).replaceFirstChar(Char::titlecase)
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

    val updateSelection: (Boolean) -> Unit = { checked ->
        isSelected = checked
        scope.launch(Dispatchers.IO) {
            endpoint.isSelected = checked
            appConfig.handleDnsrelayChanges(endpoint)
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable { updateSelection(!isSelected) },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = endpoint.dnsCryptRelayName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (explanation.isNotEmpty()) {
                Text(text = explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { promptUser(context, endpoint, appConfig) }) {
            Icon(painter = painterResource(id = infoIcon), contentDescription = null)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = updateSelection
        )
    }
}

private fun promptUser(
    context: Context,
    endpoint: DnsCryptRelayEndpoint,
    appConfig: AppConfig
) {
    if (endpoint.isDeletable()) {
        showDeleteDialog(context, appConfig, endpoint.id)
    } else {
        showDialogExplanation(
            context,
            endpoint.dnsCryptRelayName,
            endpoint.dnsCryptRelayURL,
            endpoint.dnsCryptRelayExplanation
        )
    }
}

private fun showDialogExplanation(context: Context, title: String, url: String, message: String?) {
    val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
    builder.setTitle(title)
    if (message != null) builder.setMessage(url + "\n\n" + relayDesc(context, message))
    else builder.setMessage(url)
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
    builder.create().show()
}

private fun relayDesc(context: Context, message: String?): String {
    if (message.isNullOrEmpty()) return ""

    return try {
        // fixme: find a better way to handle this
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

private fun showDeleteDialog(context: Context, appConfig: AppConfig, id: Int) {
    val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
    builder.setCancelable(true)
    builder.setTitle(context.getString(R.string.dns_crypt_relay_remove_dialog_title))
    builder.setMessage(context.getString(R.string.dns_crypt_relay_remove_dialog_message))
    builder.setPositiveButton(context.getString(R.string.lbl_delete)) { dialog, _ ->
        val scope =
            (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)
        scope.launch(Dispatchers.IO) {
            appConfig.deleteDnscryptRelayEndpoint(id)
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                Utilities.showToastUiCentered(
                    context,
                    context.getString(R.string.dns_crypt_relay_remove_success),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }
    builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { dialog, _ ->
        dialog.dismiss()
    }
    builder.create().show()
}

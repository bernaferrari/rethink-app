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
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class RelayDialogState {
    data class Info(val title: String, val url: String, val message: String?) : RelayDialogState()
    data class Delete(val id: Int) : RelayDialogState()
}

@Composable
fun RelayRow(endpoint: DnsCryptRelayEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSelected by remember(endpoint.id) { mutableStateOf(endpoint.isSelected) }
    var explanation by remember(endpoint.id) { mutableStateOf("") }
    var dialogState by remember(endpoint.id) { mutableStateOf<RelayDialogState?>(null) }

    LaunchedEffect(endpoint.id, isSelected) {
        if (isSelected && !appConfig.isSmartDnsEnabled()) {
            val status =
                withContext(Dispatchers.IO) {
                    val state = VpnController.getDnsStatus(Backend.Preferred)
                    UIUtils.getDnsStatusStringRes(state)
                }
            explanation = context.resources.getString(status).replaceFirstChar(Char::titlecase)
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
        IconButton(
            onClick = {
                dialogState =
                    if (endpoint.isDeletable()) {
                        RelayDialogState.Delete(endpoint.id)
                    } else {
                        RelayDialogState.Info(
                            endpoint.dnsCryptRelayName,
                            endpoint.dnsCryptRelayURL,
                            endpoint.dnsCryptRelayExplanation
                        )
                    }
            }
        ) {
            Icon(painter = painterResource(id = infoIcon), contentDescription = null)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = updateSelection
        )
    }

    dialogState?.let { state ->
        when (state) {
            is RelayDialogState.Delete -> {
                AlertDialog(
                    onDismissRequest = { dialogState = null },
                    title = { Text(text = context.resources.getString(R.string.dns_crypt_relay_remove_dialog_title)) },
                    text = { Text(text = context.resources.getString(R.string.dns_crypt_relay_remove_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteRelayEndpoint(context, scope, appConfig, state.id)
                                dialogState = null
                            }
                        ) {
                            Text(text = context.resources.getString(R.string.lbl_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogState = null }) {
                            Text(text = context.resources.getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            is RelayDialogState.Info -> {
                val desc =
                    if (state.message != null) {
                        state.url + "\n\n" + relayDesc(context, state.message)
                    } else {
                        state.url
                    }
                AlertDialog(
                    onDismissRequest = { dialogState = null },
                    title = { Text(text = state.title) },
                    text = { Text(text = desc) },
                    confirmButton = {
                        TextButton(onClick = { dialogState = null }) {
                            Text(text = context.resources.getString(R.string.dns_info_positive))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                clipboardCopy(
                                    context,
                                    state.url,
                                    context.resources.getString(R.string.copy_clipboard_label)
                                )
                                Utilities.showToastUiCentered(
                                    context,
                                    context.resources.getString(R.string.info_dialog_url_copy_toast_msg),
                                    Toast.LENGTH_SHORT
                                )
                            }
                        ) {
                            Text(text = context.resources.getString(R.string.dns_info_neutral))
                        }
                    }
                )
            }
        }
    }
}

private fun relayDesc(context: Context, message: String?): String {
    if (message.isNullOrEmpty()) return ""

    return try {
        // fixme: find a better way to handle this
        if (message.contains("R.string.")) {
            val m = message.substringAfter("R.string.")
            val resId: Int =
                context.resources.getIdentifier(m, "string", context.packageName)
            context.resources.getString(resId)
        } else {
            message
        }
    } catch (_: Exception) {
        ""
    }
}

private fun deleteRelayEndpoint(
    context: Context,
    scope: CoroutineScope,
    appConfig: AppConfig,
    id: Int
) {
    scope.launch(Dispatchers.IO) {
        appConfig.deleteDnscryptRelayEndpoint(id)
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                context.resources.getString(R.string.dns_crypt_relay_remove_success),
                Toast.LENGTH_SHORT
            )
        }
    }
}

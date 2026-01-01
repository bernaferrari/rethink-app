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
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DnsCryptRelayEndpointAdapter(
    private val context: Context,
    val lifecycleOwner: LifecycleOwner,
    private val appConfig: AppConfig
) :
    PagingDataAdapter<
        DnsCryptRelayEndpoint,
        DnsCryptRelayEndpointAdapter.DnsCryptRelayEndpointViewHolder
    >(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<DnsCryptRelayEndpoint>() {
                override fun areItemsTheSame(
                    oldConnection: DnsCryptRelayEndpoint,
                    newConnection: DnsCryptRelayEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected == newConnection.isSelected)
                }

                override fun areContentsTheSame(
                    oldConnection: DnsCryptRelayEndpoint,
                    newConnection: DnsCryptRelayEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected != newConnection.isSelected)
                }
            }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DnsCryptRelayEndpointViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return DnsCryptRelayEndpointViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: DnsCryptRelayEndpointViewHolder, position: Int) {
        val dnsCryptRelayEndpoint: DnsCryptRelayEndpoint = getItem(position) ?: return
        holder.update(dnsCryptRelayEndpoint)
    }

    inner class DnsCryptRelayEndpointViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(endpoint: DnsCryptRelayEndpoint) {
            composeView.setContent {
                RethinkTheme {
                    RelayRow(endpoint = endpoint)
                }
            }
        }
    }

    @Composable
    fun RelayRow(endpoint: DnsCryptRelayEndpoint) {
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

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable {
                        val newValue = !isSelected
                        isSelected = newValue
                        updateDNSCryptRelayDetails(endpoint, newValue)
                    },
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
            IconButton(onClick = { promptUser(endpoint) }) {
                Icon(painter = painterResource(id = infoIcon), contentDescription = null)
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { checked ->
                    isSelected = checked
                    updateDNSCryptRelayDetails(endpoint, checked)
                }
            )
        }
    }

    private fun promptUser(endpoint: DnsCryptRelayEndpoint) {
        if (endpoint.isDeletable()) showDeleteDialog(endpoint.id)
        else {
            showDialogExplanation(
                endpoint.dnsCryptRelayName,
                endpoint.dnsCryptRelayURL,
                endpoint.dnsCryptRelayExplanation
            )
        }
    }

    private fun showDialogExplanation(title: String, url: String, message: String?) {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        builder.setTitle(title)
        if (message != null) builder.setMessage(url + "\n\n" + relayDesc(message))
        else builder.setMessage(url)
        builder.setCancelable(true)
        builder.setPositiveButton(context.getString(R.string.dns_info_positive)) {
            dialogInterface,
            _ ->
            dialogInterface.dismiss()
        }

        builder.setNeutralButton(context.getString(R.string.dns_info_neutral)) {
            _: DialogInterface,
            _: Int ->
            clipboardCopy(context, url, context.getString(R.string.copy_clipboard_label))
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.info_dialog_url_copy_toast_msg),
                Toast.LENGTH_SHORT
            )
        }
        builder.create().show()
    }

    private fun relayDesc(message: String?): String {
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

    private fun showDeleteDialog(id: Int) {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        builder.setCancelable(true)
        builder.setTitle(context.getString(R.string.dns_crypt_relay_remove_dialog_title))
        builder.setMessage(context.getString(R.string.dns_crypt_relay_remove_dialog_message))
        builder.setPositiveButton(context.getString(R.string.lbl_delete)) { dialog, _ ->
            io {
                appConfig.deleteDnscryptRelayEndpoint(id)
                uiCtx {
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

    private fun updateDNSCryptRelayDetails(endpoint: DnsCryptRelayEndpoint, isSelected: Boolean) {
        io {
            endpoint.isSelected = isSelected
            appConfig.handleDnsrelayChanges(endpoint)
        }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

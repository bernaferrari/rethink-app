/*
 * Copyright 2023 RethinkDNS and its authors
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

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.WgAddPeerDialog
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.wireguard.Peer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WgPeersAdapter(
    val context: Context,
    private var themeId: Int,
    private val configId: Int,
    private var peers: MutableList<Peer>
) : RecyclerView.Adapter<WgPeersAdapter.WgPeersViewHolder>() {

    override fun onBindViewHolder(holder: WgPeersViewHolder, position: Int) {
        val peer: Peer = peers[position]
        holder.update(peer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WgPeersViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return WgPeersViewHolder(composeView)
    }

    override fun getItemCount(): Int {
        return peers.size
    }

    inner class WgPeersViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(wgPeer: Peer) {
            composeView.setContent {
                RethinkTheme {
                    WgPeerRow(wgPeer)
                }
            }
        }
    }

    @Composable
    fun WgPeerRow(wgPeer: Peer) {
        val endpoint =
            if (wgPeer.getEndpoint().isPresent) {
                wgPeer.getEndpoint().get().toString()
            } else {
                null
            }
        val allowedIps =
            if (wgPeer.getAllowedIps().isNotEmpty()) {
                wgPeer.getAllowedIps().joinToString { it.toString() }
            } else {
                null
            }
        val keepAlive =
            if (wgPeer.persistentKeepalive.isPresent) {
                UIUtils.getDurationInHumanReadableFormat(
                    context,
                    wgPeer.persistentKeepalive.get()
                )
            } else {
                null
            }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Column(
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.lbl_peer),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { openEditPeerDialog(wgPeer) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit_icon_grey),
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = { showDeleteInterfaceDialog(wgPeer) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = null
                        )
                    }
                }

                LabelValue(
                    label = stringResource(id = R.string.lbl_public_key),
                    value = wgPeer.getPublicKey().base64().tos().orEmpty()
                )
                if (!allowedIps.isNullOrEmpty()) {
                    LabelValue(
                        label = stringResource(id = R.string.lbl_allowed_ips),
                        value = allowedIps
                    )
                }
                if (!endpoint.isNullOrEmpty()) {
                    LabelValue(
                        label = stringResource(id = R.string.parse_error_inet_endpoint),
                        value = endpoint
                    )
                }
                if (!keepAlive.isNullOrEmpty()) {
                    LabelValue(
                        label = stringResource(id = R.string.lbl_persistent_keepalive),
                        value = keepAlive
                    )
                }
            }
        }
    }

    @Composable
    fun LabelValue(label: String, value: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun openEditPeerDialog(wgPeer: Peer) {
        // send 0 as peerId to indicate that it is a new peer
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val addPeerDialog = WgAddPeerDialog(context as Activity, themeId, configId, wgPeer)
        addPeerDialog.setCanceledOnTouchOutside(false)
        addPeerDialog.show()
        addPeerDialog.setOnDismissListener { dataChanged() }
    }

    fun dataChanged() {
        peers.clear()
        io {
            val p = WireguardManager.getPeers(configId)
            peers.addAll(p)
            uiCtx { this?.notifyDataSetChanged() }
        }
    }

    private fun showDeleteInterfaceDialog(wgPeer: Peer) {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        val delText =
            context.getString(
                R.string.two_argument_space,
                context.getString(R.string.config_delete_dialog_title),
                context.getString(R.string.lbl_peer)
            )
        builder.setTitle(delText)
        builder.setMessage(context.getString(R.string.config_delete_dialog_desc))
        builder.setCancelable(true)
        builder.setPositiveButton(delText) { _, _ -> deletePeer(wgPeer) }
        builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ -> }
        builder.create().show()
    }

    private fun deletePeer(wgPeer: Peer) {
        io {
            WireguardManager.deletePeer(configId, wgPeer)
            peers = WireguardManager.getPeers(configId)
            uiCtx { this.notifyDataSetChanged() }
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}

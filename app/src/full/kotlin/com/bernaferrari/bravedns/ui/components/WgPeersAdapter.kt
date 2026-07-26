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
package com.bernaferrari.bravedns.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerItem
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerRow
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerRowStrings
import com.bernaferrari.bravedns.ui.dialog.WgAddPeerDialog
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.wireguard.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WgPeerRow(
    context: Context,
    configId: Int,
    wgPeer: Peer,
    onPeerChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showEditDialog by remember(wgPeer.getPublicKey()) { mutableStateOf(false) }
    val endpoint =
        if ((wgPeer.getEndpoint() != null)) {
            wgPeer.getEndpoint()!!.toString()
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
        if ((wgPeer.persistentKeepalive != null)) {
            UIUtils.getDurationInHumanReadableFormat(
                context,
                wgPeer.persistentKeepalive!!
            )
        } else {
            null
        }

    val deleteTitle =
        context.getString(
            R.string.two_argument_space,
            context.getString(R.string.config_delete_dialog_title),
            context.getString(R.string.lbl_peer),
        )
    RethinkWireguardPeerRow(
        item = RethinkWireguardPeerItem(
            publicKey = wgPeer.getPublicKey().base64().orEmpty(),
            allowedIps = allowedIps,
            endpoint = endpoint,
            persistentKeepalive = keepAlive,
        ),
        strings = RethinkWireguardPeerRowStrings(
            peer = stringResource(R.string.lbl_peer),
            publicKey = stringResource(R.string.lbl_public_key),
            allowedIps = stringResource(R.string.lbl_allowed_ips),
            endpoint = stringResource(R.string.parse_error_inet_endpoint),
            persistentKeepalive = stringResource(R.string.lbl_persistent_keepalive),
            editDescription = stringResource(R.string.rt_edit_dialog_positive),
            deleteDescription = stringResource(R.string.lbl_delete),
            deleteTitle = deleteTitle,
            deleteMessage = stringResource(R.string.config_delete_dialog_desc),
            deleteConfirm = deleteTitle,
            cancel = stringResource(R.string.lbl_cancel),
        ),
        onEdit = { showEditDialog = true },
        onDelete = { deletePeer(scope, configId, wgPeer, onPeerChanged) },
    )

    if (showEditDialog) {
        WgAddPeerDialog(
            configId = configId,
            wgPeer = wgPeer,
            onDismiss = {
                showEditDialog = false
                onPeerChanged()
            }
        )
    }
}

private fun deletePeer(
    scope: kotlinx.coroutines.CoroutineScope,
    configId: Int,
    wgPeer: Peer,
    onPeerChanged: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        WireguardManager.deletePeer(configId, wgPeer)
        withContext(Dispatchers.Main) { onPeerChanged() }
    }
}

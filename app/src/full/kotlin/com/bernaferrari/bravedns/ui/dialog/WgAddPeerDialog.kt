/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.dialog

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.WireguardManager
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerEditor
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerEditorStrings
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardPeerState
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialog
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialogColumn
import com.bernaferrari.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.wireguard.Peer
import com.bernaferrari.bravedns.wireguard.util.ErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import Logger

/** Android validation, persistence, and toast adapter for the shared WireGuard peer editor. */
@Composable
fun WgAddPeerDialog(
    configId: Int,
    wgPeer: Peer?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    RethinkWireguardDialog(onDismissRequest = onDismiss) {
        RethinkWireguardDialogColumn(scrollable = true) {
            RethinkWireguardPeerEditor(
                initialState = RethinkWireguardPeerState(
                    publicKey = wgPeer?.getPublicKey()?.base64().orEmpty(),
                    presharedKey = wgPeer?.getPreSharedKey()?.base64().orEmpty(),
                    allowedIps = wgPeer?.getAllowedIps()?.joinToString { it.toString() }.orEmpty(),
                    endpoint = wgPeer?.getEndpoint()?.toString().orEmpty(),
                    persistentKeepalive = wgPeer?.persistentKeepalive?.toString().orEmpty(),
                ),
                strings = RethinkWireguardPeerEditorStrings(
                    title = stringResource(R.string.add_peer),
                    publicKey = stringResource(R.string.lbl_public_key),
                    presharedKey = stringResource(R.string.lbl_preshared_key),
                    persistentKeepalive = stringResource(R.string.lbl_persistent_keepalive),
                    endpoint = stringResource(R.string.parse_error_inet_endpoint),
                    allowedIps = stringResource(R.string.lbl_allowed_ips),
                    save = stringResource(R.string.lbl_save),
                    dismiss = stringResource(R.string.lbl_dismiss),
                ),
                keepaliveHint = { value -> value.toIntOrNull()?.let { getDurationInHumanReadableFormat(context, it) } },
                onSave = { state ->
                    scope.launch {
                        savePeer(
                            context = context,
                            configId = configId,
                            wgPeer = wgPeer,
                            publicKey = state.publicKey,
                            presharedKey = state.presharedKey,
                            allowedIps = state.allowedIps,
                            endpoint = state.endpoint,
                            keepAlive = state.persistentKeepalive,
                            onSuccess = onDismiss,
                            onError = { Utilities.showToastUiCentered(context, it, Toast.LENGTH_SHORT) },
                        )
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

private suspend fun savePeer(
    context: android.content.Context,
    configId: Int,
    wgPeer: Peer?,
    publicKey: String,
    presharedKey: String,
    allowedIps: String,
    endpoint: String,
    keepAlive: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val builder = Peer.Builder()
        if (allowedIps.isNotEmpty()) builder.parseAllowedIPs(allowedIps)
        if (endpoint.isNotEmpty()) builder.parseEndpoint(endpoint)
        if (keepAlive.isNotEmpty()) builder.parsePersistentKeepalive(keepAlive)
        if (presharedKey.isNotEmpty()) builder.parsePreSharedKey(presharedKey)
        if (publicKey.isNotEmpty()) builder.parsePublicKey(publicKey)
        val newPeer = builder.build()
        withContext(Dispatchers.IO) {
            if (wgPeer != null) WireguardManager.deletePeer(configId, wgPeer)
            WireguardManager.addPeer(configId, newPeer)
        }
        onSuccess()
    } catch (error: Throwable) {
        Logger.e(Logger.LOG_TAG_UI, "Error while adding peer", error as? Exception)
        onError(ErrorMessages[context, error])
    }
}

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
package com.celzero.bravedns.ui.dialog

import Logger
import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils.getDurationInHumanReadableFormat
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.wireguard.Peer
import com.celzero.bravedns.wireguard.util.ErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WgAddPeerDialog(
    private val activity: Activity,
    themeID: Int,
    private var configId: Int,
    private val wgPeer: Peer?
) : Dialog(activity, themeID) {

    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(context)
        composeView.setContent {
            RethinkTheme {
                WgAddPeerContent(onDismiss = { dismiss() })
            }
        }
        setContentView(composeView)
        initView()
    }

    private fun initView() {
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        if (wgPeer != null) {
            isEditing = true
        }
    }

    @Composable
    private fun WgAddPeerContent(onDismiss: () -> Unit) {
        var publicKey by remember {
            mutableStateOf(wgPeer?.getPublicKey()?.base64()?.tos().orEmpty())
        }
        var presharedKey by remember {
            mutableStateOf(
                if (wgPeer?.getPreSharedKey()?.isPresent == true) {
                    wgPeer.getPreSharedKey().get().base64().tos().orEmpty()
                } else {
                    ""
                }
            )
        }
        var allowedIps by remember {
            mutableStateOf(wgPeer?.getAllowedIps()?.joinToString { it.toString() }.orEmpty())
        }
        var endpoint by remember {
            mutableStateOf(
                if (wgPeer?.getEndpoint()?.isPresent == true) {
                    wgPeer.getEndpoint().get().toString()
                } else {
                    ""
                }
            )
        }
        var keepAlive by remember {
            mutableStateOf(
                if (wgPeer?.persistentKeepalive?.isPresent == true) {
                    wgPeer.persistentKeepalive.get().toString()
                } else {
                    ""
                }
            )
        }
        var keepAliveHint by remember {
            mutableStateOf(
                if (wgPeer?.persistentKeepalive?.isPresent == true) {
                    getDurationInHumanReadableFormat(activity, wgPeer.persistentKeepalive.get())
                } else {
                    ""
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = context.getString(R.string.add_peer),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = publicKey,
                onValueChange = { publicKey = it },
                label = { Text(text = context.getString(R.string.lbl_public_key)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = presharedKey,
                onValueChange = { presharedKey = it },
                label = { Text(text = context.getString(R.string.lbl_preshared_key)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = keepAlive,
                onValueChange = { value ->
                    keepAlive = value
                    keepAliveHint =
                        value.toIntOrNull()?.let { getDurationInHumanReadableFormat(activity, it) }
                            .orEmpty()
                },
                label = { Text(text = context.getString(R.string.lbl_persistent_keepalive)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (keepAliveHint.isNotBlank()) {
                Text(text = keepAliveHint, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(text = context.getString(R.string.parse_error_inet_endpoint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = allowedIps,
                onValueChange = { allowedIps = it },
                label = { Text(text = context.getString(R.string.lbl_allowed_ips)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = context.getString(R.string.lbl_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    savePeer(
                        publicKey = publicKey,
                        presharedKey = presharedKey,
                        allowedIps = allowedIps,
                        endpoint = endpoint,
                        keepAlive = keepAlive
                    )
                }) {
                    Text(text = context.getString(R.string.lbl_save))
                }
            }
        }
    }

    private fun savePeer(
        publicKey: String,
        presharedKey: String,
        allowedIps: String,
        endpoint: String,
        keepAlive: String
    ) {
        try {
            val builder = Peer.Builder()
            if (allowedIps.isNotEmpty()) builder.parseAllowedIPs(allowedIps)
            if (endpoint.isNotEmpty()) builder.parseEndpoint(endpoint)
            if (keepAlive.isNotEmpty()) builder.parsePersistentKeepalive(keepAlive)
            if (presharedKey.isNotEmpty()) builder.parsePreSharedKey(presharedKey)
            if (publicKey.isNotEmpty()) builder.parsePublicKey(publicKey)
            val newPeer = builder.build()

            ui {
                ioCtx {
                    if (wgPeer != null && isEditing) WireguardManager.deletePeer(configId, wgPeer)
                    WireguardManager.addPeer(configId, newPeer)
                }
                this.dismiss()
            }
        } catch (e: Throwable) {
            val ex = Logger.throwableToException(e)
            Logger.e(Logger.LOG_TAG_PROXY, "Error while adding peer", ex)
            Utilities.showToastUiCentered(
                context,
                ErrorMessages[context, e],
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun ui(f: suspend () -> Unit) {
        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.Main) { f() }
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }
}

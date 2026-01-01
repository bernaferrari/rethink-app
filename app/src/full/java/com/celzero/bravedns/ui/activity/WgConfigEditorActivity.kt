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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.WgConfigDetailActivity.Companion.INTENT_EXTRA_WG_TYPE
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.wireguard.Config
import com.celzero.bravedns.wireguard.WgInterface
import com.celzero.bravedns.wireguard.util.ErrorMessages
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class WgConfigEditorActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    private var wgConfig: Config? = null
    private var wgInterface: WgInterface? = null
    private var configId: Int = -1
    private var wgType: WgConfigDetailActivity.WgType = WgConfigDetailActivity.WgType.DEFAULT

    private var interfaceName by mutableStateOf("")
    private var privateKey by mutableStateOf("")
    private var publicKey by mutableStateOf("")
    private var addresses by mutableStateOf("")
    private var listenPort by mutableStateOf("")
    private var dnsServers by mutableStateOf("")
    private var mtu by mutableStateOf("")
    private var amzProps by mutableStateOf("")
    private var showListenPortState by mutableStateOf(false)

    companion object {
        const val INTENT_EXTRA_WG_ID = "WIREGUARD_TUNNEL_ID"
        private const val CLIPBOARD_PUBLIC_KEY_LBL = "Public Key"
        private const val DEFAULT_MTU = "-1"
        // when dns is set to auto, the default dns is set to 1.1.1.1. this differs from official
        // wireguard for android, because rethink requires a dns to be set in "Simple" mode
        private const val DEFAULT_DNS = "1.1.1.1"
        private const val DEFAULT_LISTEN_PORT = "0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        configId = intent.getIntExtra(INTENT_EXTRA_WG_ID, WireguardManager.INVALID_CONF_ID)
        wgType =
            WgConfigDetailActivity.WgType.fromInt(
                intent.getIntExtra(
                    INTENT_EXTRA_WG_TYPE,
                    WgConfigDetailActivity.WgType.DEFAULT.value
                )
            )

        setContent {
            RethinkTheme {
                WgConfigEditorScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun init() {
        io {
            wgConfig = WireguardManager.getConfigById(configId)
            wgInterface = wgConfig?.getInterface()

            uiCtx {
                interfaceName = wgConfig?.getName().orEmpty()
                privateKey = wgInterface?.getKeyPair()?.getPrivateKey()?.base64()?.tos().orEmpty()
                publicKey = wgInterface?.getKeyPair()?.getPublicKey()?.base64()?.tos().orEmpty()

                var dns = wgInterface?.dnsServers?.joinToString { it.hostAddress?.toString() ?: "" }
                val searchDomains = wgInterface?.dnsSearchDomains?.joinToString { it }
                dns =
                    if (!searchDomains.isNullOrEmpty()) {
                        "$dns,$searchDomains"
                    } else {
                        dns
                    }
                dnsServers = dns.orEmpty()

                addresses =
                    if (wgInterface?.getAddresses()?.isEmpty() != true) {
                        wgInterface?.getAddresses()?.joinToString { it.toString() }.orEmpty()
                    } else {
                        ""
                    }

                showListenPortState = showListenPort()
                listenPort = if (showListenPortState) {
                    wgInterface?.listenPort?.get()?.toString().orEmpty()
                } else {
                    ""
                }

                mtu = if (wgInterface?.mtu?.isPresent == true) {
                    wgInterface?.mtu?.get()?.toString().orEmpty()
                } else {
                    ""
                }

                amzProps = if (wgInterface?.isAmnezia() == true) {
                    wgInterface?.getAmzProps().orEmpty()
                } else {
                    ""
                }
            }
        }
    }

    private fun showListenPort(): Boolean {
        val isPresent =
            wgInterface?.listenPort?.isPresent == true && wgInterface?.listenPort?.get() != 1
        val byType = wgType.isOneWg() || (!persistentState.randomizeListenPort && wgType.isDefault())
        return isPresent && byType
    }

    private fun generateKeys() {
        val key = Backend.newWgPrivateKey()
        privateKey = key.base64().toString()
        publicKey = key.mult().base64().toString()
    }

    private fun saveConfig() {
        val name = interfaceName
        val addr = addresses
        val mtuValue = mtu.ifEmpty { DEFAULT_MTU }
        val listenPortValue = listenPort.ifEmpty { DEFAULT_LISTEN_PORT }
        val dns = dnsServers.ifEmpty { DEFAULT_DNS }
        val privateKeyValue = privateKey
        io {
            val isInterfaceAdded =
                addWgInterface(name, addr, mtuValue, listenPortValue, dns, privateKeyValue)
            if (isInterfaceAdded != null) {
                uiCtx {
                    Utilities.showToastUiCentered(
                        this,
                        getString(R.string.config_add_success_toast),
                        Toast.LENGTH_LONG
                    )
                    finish()
                }
            }
        }
    }

    private suspend fun addWgInterface(
        name: String,
        addresses: String,
        mtu: String,
        listenPort: String,
        dnsServers: String,
        privateKey: String
    ): Config? {
        try {
            val wgInterface =
                WgInterface.Builder()
                    .parsePrivateKey(privateKey)
                    .parseAddresses(addresses)
                    .parseListenPort(listenPort)
                    .parseDnsServers(dnsServers)
                    .parseMtu(mtu)
                    .build()
            wgConfig = WireguardManager.addOrUpdateInterface(configId, name, wgInterface)
            return wgConfig
        } catch (e: Throwable) {
            val error = ErrorMessages[this, e]
            Napier.e("err while parsing wg interface: $error", e)
            uiCtx { Utilities.showToastUiCentered(this, error, Toast.LENGTH_LONG) }
            return null
        }
    }

    private fun copyPublicKey() {
        clipboardCopy(this, publicKey, CLIPBOARD_PUBLIC_KEY_LBL)
        Utilities.showToastUiCentered(
            this,
            getString(R.string.public_key_copy_toast_msg),
            Toast.LENGTH_SHORT
        )
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WgConfigEditorScreen() {
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.lbl_configure),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = interfaceName,
                    onValueChange = { interfaceName = it },
                    label = { Text(stringResource(R.string.cd_dns_crypt_dialog_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text(stringResource(R.string.lbl_private_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { generateKeys() }) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                )

                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.lbl_public_key)) },
                    modifier =
                        Modifier.fillMaxWidth().clickable(enabled = publicKey.isNotEmpty()) {
                            copyPublicKey()
                        },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { copyPublicKey() }, enabled = publicKey.isNotEmpty()) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                        }
                    }
                )

                OutlinedTextField(
                    value = addresses,
                    onValueChange = { addresses = it },
                    label = { Text(stringResource(R.string.lbl_addresses)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showListenPortState) {
                    OutlinedTextField(
                        value = listenPort,
                        onValueChange = { listenPort = it },
                        label = { Text(stringResource(R.string.lbl_listen_port)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = dnsServers,
                    onValueChange = { dnsServers = it },
                    label = { Text(stringResource(R.string.lbl_dns_servers)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = mtu,
                    onValueChange = { mtu = it },
                    label = { Text(stringResource(R.string.lbl_mtu)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (amzProps.isNotEmpty()) {
                    Text(
                        text = amzProps,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { finish() }) {
                        Text(text = stringResource(R.string.lbl_cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { saveConfig() }) {
                        Text(text = stringResource(R.string.lbl_save))
                    }
                }
            }
        }
    }
}

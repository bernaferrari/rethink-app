/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.wireguard

import Logger
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.wireguard.RethinkWireguardEditor
import com.celzero.bravedns.ui.compose.wireguard.RethinkWireguardEditorState
import com.celzero.bravedns.ui.compose.wireguard.RethinkWireguardEditorStrings
import com.celzero.bravedns.wireguard.WgInterface
import com.celzero.bravedns.wireguard.util.ErrorMessages
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLIPBOARD_PUBLIC_KEY_LBL = "Public Key"
private const val DEFAULT_MTU = "-1"

// when dns is set to auto, the default dns is set to 1.1.1.1. this differs from official
// wireguard for android, because rethink requires a dns to be set in "Simple" mode
private const val DEFAULT_DNS = "1.1.1.1"
private const val DEFAULT_LISTEN_PORT = "0"

@Keep
enum class WgType(val value: Int) {
    DEFAULT(0),
    ONE_WG(1);

    fun isOneWg() = this == ONE_WG

    fun isDefault() = this == DEFAULT

    companion object {
        fun fromInt(value: Int): WgType = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

@Composable
fun WgConfigEditorScreen(
    configId: Int,
    wgType: WgType,
    persistentState: PersistentState,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val publicKeyCopyToast = stringResource(R.string.public_key_copy_toast_msg)
    val configAddSuccessToast = stringResource(R.string.config_add_success_toast)
    var state by remember { mutableStateOf(RethinkWireguardEditorState()) }

    fun showListenPort(wgIface: WgInterface?): Boolean {
        val isPresent = wgIface?.listenPort != null && wgIface.listenPort != 1
        return isPresent && (wgType.isOneWg() || (!persistentState.randomizeListenPort && wgType.isDefault()))
    }

    LaunchedEffect(configId) {
        withContext(Dispatchers.IO) {
            val config = WireguardManager.getConfigById(configId)
            val iface = config?.getInterface()
            val dns = iface?.dnsServers?.joinToString().orEmpty()
            val searchDomains = iface?.dnsSearchDomains?.joinToString().orEmpty()
            withContext(Dispatchers.Main) {
                state = RethinkWireguardEditorState(
                    interfaceName = config?.getName().orEmpty(),
                    privateKey = iface?.getKeyPair()?.getPrivateKey()?.base64().orEmpty(),
                    publicKey = iface?.getKeyPair()?.getPublicKey()?.base64().orEmpty(),
                    addresses = iface?.getAddresses()?.joinToString { it.toString() }.orEmpty(),
                    dnsServers = listOf(dns, searchDomains).filter { it.isNotEmpty() }.joinToString(","),
                    listenPort = iface?.listenPort?.toString().orEmpty(),
                    mtu = iface?.mtu?.toString().orEmpty(),
                    advancedProperties = if (iface?.isAmnezia() == true) iface.getAmzProps().orEmpty() else "",
                    showListenPort = showListenPort(iface),
                )
            }
        }
    }

    fun generateKeys() {
        val key = Backend.newWgPrivateKey()
        state = state.copy(privateKey = key.base64().toString(), publicKey = key.mult().base64().toString())
    }

    fun copyPublicKey() {
        clipboardCopy(context, state.publicKey, CLIPBOARD_PUBLIC_KEY_LBL)
        Utilities.showToastUiCentered(context, publicKeyCopyToast, Toast.LENGTH_SHORT)
    }

    fun saveConfig() {
        val editorState = state
        scope.launch(Dispatchers.IO) {
            try {
                val newWgInterface = WgInterface.Builder()
                    .parsePrivateKey(editorState.privateKey)
                    .parseAddresses(editorState.addresses)
                    .parseListenPort(editorState.listenPort.ifEmpty { DEFAULT_LISTEN_PORT })
                    .parseDnsServers(editorState.dnsServers.ifEmpty { DEFAULT_DNS })
                    .parseMtu(editorState.mtu.ifEmpty { DEFAULT_MTU })
                    .build()
                if (WireguardManager.addOrUpdateInterface(configId, editorState.interfaceName, newWgInterface) != null) {
                    withContext(Dispatchers.Main) {
                        Utilities.showToastUiCentered(context, configAddSuccessToast, Toast.LENGTH_LONG)
                        onSaveSuccess()
                    }
                }
            } catch (error: Throwable) {
                val message = ErrorMessages[context, error]
                Logger.e(Logger.LOG_TAG_UI, "err while parsing wg interface: $message", error as? Exception)
                withContext(Dispatchers.Main) { Utilities.showToastUiCentered(context, message, Toast.LENGTH_LONG) }
            }
        }
    }

    val density = LocalDensity.current
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val actionBarBottomInset = when {
        imeBottomInset > 0.dp -> imeBottomInset
        navBottomInset > 0.dp -> navBottomInset
        else -> 48.dp
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val previousSoftInputMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (previousSoftInputMode != null) window.setSoftInputMode(previousSoftInputMode) }
    }

    RethinkWireguardEditor(
        state = state,
        strings = RethinkWireguardEditorStrings(
            title = stringResource(R.string.lbl_configure),
            configuration = stringResource(R.string.lbl_configure),
            setup = stringResource(R.string.setup_wireguard),
            network = stringResource(R.string.lbl_network),
            advanced = stringResource(R.string.lbl_advanced),
            name = stringResource(R.string.cd_dns_crypt_dialog_name),
            addresses = stringResource(R.string.lbl_addresses),
            dnsServers = stringResource(R.string.lbl_dns_servers),
            privateKey = stringResource(R.string.lbl_private_key),
            publicKey = stringResource(R.string.lbl_public_key),
            listenPort = stringResource(R.string.lbl_listen_port),
            mtu = stringResource(R.string.lbl_mtu),
            generateKeys = stringResource(R.string.cd_generate_keys),
            copyPublicKey = stringResource(R.string.cd_copy_public_key),
            cancel = stringResource(R.string.lbl_cancel),
            save = stringResource(R.string.lbl_save),
        ),
        actionBottomInset = actionBarBottomInset,
        onStateChange = { state = it },
        onBackClick = onBackClick,
        onGenerateKeys = ::generateKeys,
        onCopyPublicKey = ::copyPublicKey,
        onSaveClick = ::saveConfig,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

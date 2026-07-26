/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.dialog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.toRethinkWireguardHopItem
import com.bernaferrari.bravedns.ui.components.updateWireguardHop
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardHopItem
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardHopPicker
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardHopPickerStrings
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialog
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialogColumn
import com.bernaferrari.bravedns.wireguard.Config

@Composable
fun WgHopDialog(
    srcId: Int,
    hopables: List<Config>,
    selectedId: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    RethinkWireguardDialog(onDismissRequest = onDismiss) {
        var selectedHopId by remember(selectedId) { mutableStateOf(selectedId.takeIf { it >= 0 }?.toString()) }
        var hopItems by remember(hopables) { mutableStateOf<List<RethinkWireguardHopItem>>(emptyList()) }
        LaunchedEffect(srcId, hopables, selectedHopId) {
            hopItems = hopables.mapNotNull { it.toRethinkWireguardHopItem(context, srcId, selectedHopId?.toIntOrNull()) }
        }
        RethinkWireguardDialogColumn(modifier = Modifier.fillMaxSize()) {
            RethinkWireguardHopPicker(
                items = hopItems,
                selectedId = selectedHopId,
                strings = RethinkWireguardHopPickerStrings(
                    title = stringResource(R.string.hop_add_remove_title),
                    done = stringResource(R.string.ada_noapp_dialog_positive),
                    ipv4 = stringResource(R.string.settings_ip_text_ipv4),
                    ipv6 = stringResource(R.string.settings_ip_text_ipv6),
                    splitTunnel = stringResource(R.string.lbl_split),
                    amnezia = stringResource(R.string.lbl_amnezia),
                    hopSource = stringResource(R.string.lbl_hopping),
                    alreadyHop = stringResource(R.string.cd_dns_crypt_relay_heading),
                ),
                onToggle = { item, checked ->
                    val config = hopables.firstOrNull { it.getId().toString() == item.id }
                    if (config == null) {
                        com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardHopToggleResult(false)
                    } else {
                        updateWireguardHop(context, srcId, config, checked)
                    }
                },
                onSelectedIdChange = { selectedHopId = it },
                onDone = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

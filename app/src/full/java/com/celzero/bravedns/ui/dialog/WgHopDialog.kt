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
package com.celzero.bravedns.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.HopRow
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.wireguard.Config
import io.github.aakira.napier.Napier

@Composable
fun WgHopDialog(
    srcId: Int,
    hopables: List<Config>,
    selectedId: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val selectedHopId = remember(selectedId) { mutableStateOf(selectedId) }
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.hop_add_remove_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hopables) { config ->
                    val mapping = WireguardManager.getConfigFilesById(config.getId()) ?: return@items
                    HopRow(
                        context = context,
                        srcId = srcId,
                        config = config,
                        isActive = mapping.isActive,
                        selectedId = selectedHopId.value,
                        onSelectedIdChange = { selectedHopId.value = it }
                    )
                }
            }
            Button(
                onClick = {
                    Napier.d("Dismiss hop dialog")
                    onDismiss()
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
            ) {
                Text(text = stringResource(R.string.ada_noapp_dialog_positive))
            }
        }
    }
}

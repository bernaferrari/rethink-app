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
package com.celzero.bravedns.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.RelayRow
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DnsCryptRelayEndpoint

@Composable
fun DnsCryptRelaysDialog(
    appConfig: AppConfig,
    relays: LiveData<PagingData<DnsCryptRelayEndpoint>>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DnsCryptRelaysContent(appConfig = appConfig, relays = relays, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun DnsCryptRelaysContent(
    appConfig: AppConfig,
    relays: LiveData<PagingData<DnsCryptRelayEndpoint>>,
    onDismiss: () -> Unit
) {
    val items = relays.asFlow().collectAsLazyPagingItems()
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.cd_dnscrypt_relay_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(items.itemCount) { index ->
                val item = items[index] ?: return@items
                RelayRow(item, appConfig)
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
            Text(text = stringResource(R.string.lbl_dismiss))
        }
    }
}

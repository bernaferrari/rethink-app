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
package com.bernaferrari.bravedns.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.RelayRow
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointFeed
import com.bernaferrari.bravedns.ui.compose.dns.RethinkEndpointPicker

@Composable
fun DnsCryptRelaysDialog(
    appConfig: AppConfig,
    relays: Flow<PagingData<DnsCryptRelayEndpoint>>,
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
    relays: Flow<PagingData<DnsCryptRelayEndpoint>>,
    onDismiss: () -> Unit
) {
    val items = relays.collectAsLazyPagingItems()
    RethinkEndpointPicker(
        title = stringResource(R.string.cd_dnscrypt_relay_heading),
        feed = AndroidRelayFeed(items),
        onBackClick = onDismiss,
        itemContent = { RelayRow(it, appConfig) },
    )
}

private class AndroidRelayFeed(private val items: androidx.paging.compose.LazyPagingItems<DnsCryptRelayEndpoint>) : RethinkEndpointFeed<DnsCryptRelayEndpoint> {
    override val itemCount: Int get() = items.itemCount
    override fun get(index: Int): DnsCryptRelayEndpoint? = items[index]
}

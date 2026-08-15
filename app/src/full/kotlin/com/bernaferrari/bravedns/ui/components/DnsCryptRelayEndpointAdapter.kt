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

package com.bernaferrari.bravedns.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint

private const val TAG = "DnsCryptRelayEndpointAdapter"

@Composable
fun RelayRow(endpoint: DnsCryptRelayEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failingToast = stringResource(R.string.status_failing)
    val deleteSuccessToast = stringResource(R.string.dns_crypt_relay_remove_success)
    val resolvedExplanation = resolveDnsDescriptionText(endpoint.dnsCryptRelayExplanation)
    var isSelected by remember(endpoint.id) { mutableStateOf(endpoint.isSelected) }
    val explanation =
        rememberDnsStatusExplanation(
            key = endpoint.id,
            isSelected = isSelected,
            smartDnsEnabled = appConfig.isSmartDnsEnabled(),
            tag = TAG,
            pollIntervalMs = 1500L,
            requireTunnel = false,
            selectedFallbackText = null
        )

    DnsEndpointManagedRow(
        key = endpoint.id,
        title = endpoint.dnsCryptRelayName,
        supporting = explanation.ifEmpty { null },
        selected = isSelected,
        action = if (endpoint.isDeletable()) DnsRowAction.Delete else DnsRowAction.Info,
        selection = DnsRowSelection.Checkbox,
        onSelectionChange = { checked ->
            isSelected = checked
            launchDnsEndpointSelectionUpdate(scope, context, TAG, failingToast) {
                endpoint.isSelected = checked
                appConfig.handleDnsrelayChanges(endpoint)
            }
        },
        onInfo = { show ->
            show(
                DnsInfoDialogModel(
                    title = endpoint.dnsCryptRelayName,
                    message = dnsInfoDescription(endpoint.dnsCryptRelayURL, endpoint.dnsCryptRelayExplanation, resolvedExplanation),
                    copyValue = endpoint.dnsCryptRelayURL
                )
            )
        },
        delete =
            DnsDeleteDialogModel(
                id = endpoint.id,
                titleRes = R.string.dns_crypt_relay_remove_dialog_title,
                messageRes = R.string.dns_crypt_relay_remove_dialog_message,
                successToast = deleteSuccessToast
            ),
        onDelete = { id ->
            launchDnsEndpointDelete(scope, context, deleteSuccessToast, failingToast) {
                appConfig.deleteDnscryptRelayEndpoint(id)
            }
        }
    )
}

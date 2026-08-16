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

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.getExplanationText
import com.bernaferrari.bravedns.service.FirewallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bernaferrari.bravedns.ui.components.DnsRowAction

private const val TAG = "DnsProxyEndpointRow"

@Composable
fun DnsProxyEndpointRow(endpoint: DnsProxyEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failingToast = stringResource(R.string.status_failing)
    val deleteSuccessToast = stringResource(R.string.dns_proxy_remove_success)
    val defaultName = stringResource(R.string.cd_custom_dns_proxy_default_app)
    val dialogMessageTemplate = stringResource(R.string.dns_proxy_dialog_message)
    val dialogMessageNoAppTemplate = stringResource(R.string.dns_proxy_dialog_message_no_app)
    var explanation by remember(endpoint.id) { mutableStateOf("") }

    LaunchedEffect(endpoint.id, endpoint.proxyName, endpoint.proxyAppName) {
        val appName =
            withContext(Dispatchers.IO) {
                FirewallManager.getAppInfoByPackage(endpoint.proxyAppName)?.appName
            }
        val resolvedAppName =
            if (endpoint.proxyName != defaultName) {
                appName ?: defaultName
            } else {
                endpoint.proxyAppName ?: defaultName
            }
        explanation = endpoint.getExplanationText(context, resolvedAppName)
    }

    DnsEndpointManagedRow(
        key = endpoint.id,
        title = endpoint.proxyName,
        supporting = explanation.ifEmpty { null },
        selected = endpoint.isSelected,
        action = if (endpoint.isDeletable()) DnsRowAction.Delete else DnsRowAction.Info,
        onSelectionChange = {
            launchDnsEndpointSelectionUpdate(scope, context, TAG, failingToast) {
                endpoint.isSelected = true
                appConfig.handleDnsProxyChanges(endpoint)
            }
        },
        onInfo = { show ->
            scope.launch(Dispatchers.IO) {
                val app = FirewallManager.getAppInfoByPackage(endpoint.getPackageName())?.appName
                val message =
                    if (!app.isNullOrEmpty()) {
                        dialogMessageTemplate.format(app, endpoint.proxyIP, endpoint.proxyPort.toString())
                    } else {
                        dialogMessageNoAppTemplate.format(endpoint.proxyIP, endpoint.proxyPort.toString())
                    }
                withContext(Dispatchers.Main) {
                    show(
                        DnsInfoDialogModel(
                            title = endpoint.proxyName,
                            message = message,
                            copyValue = endpoint.proxyIP,
                            copyToastRes = R.string.info_dialog_copy_toast_msg
                        )
                    )
                }
            }
        },
        delete =
            DnsDeleteDialogModel(
                id = endpoint.id,
                titleRes = R.string.dns_proxy_remove_dialog_title,
                messageRes = R.string.dns_proxy_remove_dialog_message,
                successToast = deleteSuccessToast
            ),
        onDelete = { id ->
            launchDnsEndpointDelete(scope, context, deleteSuccessToast, failingToast) {
                appConfig.deleteDnsProxyEndpoint(id)
            }
        }
    )
}

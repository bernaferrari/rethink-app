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

package com.celzero.bravedns.adapter

import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DnsProxyEndpoint
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.util.UIUtils.clipboardCopy
import com.celzero.bravedns.util.Utilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DnsProxyEndpointRow(endpoint: DnsProxyEndpoint, appConfig: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var explanation by remember(endpoint.id) { mutableStateOf("") }

    LaunchedEffect(endpoint.id, endpoint.proxyName, endpoint.proxyAppName) {
        val appName =
            withContext(Dispatchers.IO) {
                FirewallManager.getAppInfoByPackage(endpoint.proxyAppName)?.appName
            }
        val defaultName = context.getString(R.string.cd_custom_dns_proxy_default_app)
        val resolvedAppName =
            if (endpoint.proxyName != defaultName) {
                appName ?: defaultName
            } else {
                endpoint.proxyAppName ?: defaultName
            }
        explanation = endpoint.getExplanationText(context, resolvedAppName)
    }

    val infoIcon =
        if (endpoint.isDeletable()) {
            R.drawable.ic_fab_uninstall
        } else {
            R.drawable.ic_info
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable { updateDnsProxyDetails(scope, endpoint, appConfig) },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = endpoint.proxyName, style = MaterialTheme.typography.bodyLarge)
            if (explanation.isNotEmpty()) {
                Text(text = explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { promptUser(context, scope, endpoint, appConfig) }) {
            Icon(painter = painterResource(id = infoIcon), contentDescription = null)
        }
        Checkbox(
            checked = endpoint.isSelected,
            onCheckedChange = { updateDnsProxyDetails(scope, endpoint, appConfig) }
        )
    }
}

private fun promptUser(
    context: Context,
    scope: CoroutineScope,
    endpoint: DnsProxyEndpoint,
    appConfig: AppConfig
) {
    if (endpoint.isDeletable()) showDeleteDialog(context, scope, endpoint, appConfig)
    else {
        scope.launch(Dispatchers.IO) {
            val app = FirewallManager.getAppInfoByPackage(endpoint.getPackageName())?.appName
            withContext(Dispatchers.Main) {
                showDetailsDialog(
                    context,
                    endpoint.proxyName,
                    endpoint.proxyIP,
                    endpoint.proxyPort.toString(),
                    app
                )
            }
        }
    }
}

private fun showDetailsDialog(context: Context, title: String, ip: String?, port: String, app: String?) {
    val builder = MaterialAlertDialogBuilder(context)
    builder.setTitle(title)

    if (!app.isNullOrEmpty()) {
        builder.setMessage(context.getString(R.string.dns_proxy_dialog_message, app, ip, port))
    } else {
        builder.setMessage(
            context.getString(R.string.dns_proxy_dialog_message_no_app, ip, port)
        )
    }
    builder.setCancelable(true)
    builder.setPositiveButton(context.getString(R.string.dns_info_positive)) { dialogInterface, _ ->
        dialogInterface.dismiss()
    }
    builder.setNeutralButton(context.getString(R.string.dns_info_neutral)) { _: DialogInterface, _: Int ->
        if (ip != null) {
            clipboardCopy(context, ip, context.getString(R.string.copy_clipboard_label))
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.info_dialog_copy_toast_msg),
                Toast.LENGTH_SHORT
            )
        }
    }
    builder.create().show()
}

private fun showDeleteDialog(
    context: Context,
    scope: CoroutineScope,
    dnsProxyEndpoint: DnsProxyEndpoint,
    appConfig: AppConfig
) {
    val builder = MaterialAlertDialogBuilder(context)
    builder.setTitle(R.string.dns_proxy_remove_dialog_title)
    builder.setMessage(R.string.dns_proxy_remove_dialog_message)
    builder.setCancelable(true)
    builder.setPositiveButton(context.getString(R.string.lbl_delete)) { _, _ ->
        deleteProxyEndpoint(context, scope, appConfig, dnsProxyEndpoint.id)
    }
    builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ -> }
    builder.create().show()
}

private fun updateDnsProxyDetails(
    scope: CoroutineScope,
    endpoint: DnsProxyEndpoint,
    appConfig: AppConfig
) {
    scope.launch(Dispatchers.IO) {
        endpoint.isSelected = true
        appConfig.handleDnsProxyChanges(endpoint)
    }
}

private fun deleteProxyEndpoint(
    context: Context,
    scope: CoroutineScope,
    appConfig: AppConfig,
    id: Int
) {
    scope.launch(Dispatchers.IO) {
        appConfig.deleteDnsProxyEndpoint(id)
        withContext(Dispatchers.Main) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.dns_proxy_remove_success),
                Toast.LENGTH_SHORT
            )
        }
    }
}

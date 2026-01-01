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
package com.celzero.bravedns.adapter

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ProxyApplicationMapping
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WgIncludeAppsAdapter(
    private val context: Context,
    private val proxyId: String,
    private val proxyName: String
) {
    private data class IncludeDialogState(
        val packageList: List<String>,
        val mapping: ProxyApplicationMapping,
        val included: Boolean
    )

    private val packageManager: PackageManager = context.packageManager
    private val pendingDialog = mutableStateOf<IncludeDialogState?>(null)

    @Composable
    fun IncludeDialogHost() {
        val state = pendingDialog.value ?: return
        val (title, positiveTxt) =
            if (state.included) {
                context.getString(
                    R.string.wg_apps_dialog_title_include,
                    state.packageList.size.toString()
                ) to context.getString(R.string.lbl_include)
            } else {
                context.getString(
                    R.string.wg_apps_dialog_title_exclude,
                    state.packageList.size.toString()
                ) to context.getString(R.string.lbl_remove)
            }

        AlertDialog(
            onDismissRequest = { pendingDialog.value = null },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_firewall_exclude_on),
                    contentDescription = null
                )
            },
            title = { Text(text = title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.packageList.forEach { name ->
                        Text(text = name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateProxyIdForApp(state.mapping, state.included)
                        pendingDialog.value = null
                    }
                ) {
                    Text(text = positiveTxt)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDialog.value = null }) {
                    Text(text = context.getString(R.string.ctbs_dialog_negative_btn))
                }
            }
        )
    }

    @Composable
    fun IncludeAppRow(mapping: ProxyApplicationMapping) {
        var isProxyExcluded by remember(mapping.uid) { mutableStateOf(false) }
        var hasInternetPerm by remember(mapping.uid) { mutableStateOf(true) }
        var iconDrawable by remember(mapping.uid) { mutableStateOf<Drawable?>(null) }
        var descText by remember(mapping.uid, mapping.proxyId, mapping.proxyName) { mutableStateOf("") }
        var isIncluded by remember(mapping.uid, mapping.proxyId) { mutableStateOf(false) }

        LaunchedEffect(mapping.uid, mapping.proxyId, mapping.proxyName, mapping.packageName) {
            isProxyExcluded = withContext(Dispatchers.IO) {
                FirewallManager.isAppExcludedFromProxy(mapping.uid)
            }
            hasInternetPerm = mapping.hasInternetPermission(packageManager)
            iconDrawable = withContext(Dispatchers.IO) {
                getIcon(context, mapping.packageName, mapping.appName)
            }

            descText =
                when {
                    mapping.proxyId.isEmpty() -> ""
                    mapping.proxyId != proxyId -> {
                        if (isProxyExcluded) {
                            context.getString(R.string.exclude_apps_from_proxy)
                        } else {
                            context.getString(
                                R.string.wireguard_apps_proxy_map_desc,
                                mapping.proxyName
                            )
                        }
                    }
                    else -> ""
                }

            isIncluded = mapping.proxyId == proxyId && mapping.proxyId.isNotEmpty() && !isProxyExcluded
        }

        val isClickable = !isProxyExcluded
        val backgroundColor =
            if (isIncluded) {
                Color(UIUtils.fetchColor(context, R.attr.selectedCardBg))
            } else {
                Color(UIUtils.fetchColor(context, R.attr.background))
            }
        val contentAlpha = if (hasInternetPerm) 1f else 0.4f

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isClickable) {
                        updateInterfaceDetails(mapping, !isIncluded)
                    },
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val iconPainter =
                    rememberDrawablePainter(iconDrawable)
                        ?: rememberDrawablePainter(getDefaultIcon(context))
                iconPainter?.let { painter ->
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(45.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mapping.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )
                    if (descText.isNotEmpty()) {
                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = if (isProxyExcluded) 1f else 0.6f
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(0.1f))
                Checkbox(
                    checked = isIncluded,
                    enabled = isClickable,
                    onCheckedChange = {
                        if (isClickable) updateInterfaceDetails(mapping, !isIncluded)
                    }
                )
            }
        }
    }

    private fun updateInterfaceDetails(mapping: ProxyApplicationMapping, include: Boolean) {
        io {
            val appUidList = FirewallManager.getAppNamesByUid(mapping.uid)
            if (FirewallManager.isAppExcludedFromProxy(mapping.uid)) {
                uiCtx {
                    showToastUiCentered(
                        context,
                        context.getString(R.string.exclude_apps_from_proxy_failure_toast),
                        Toast.LENGTH_LONG
                    )
                }
                return@io
            }
            uiCtx {
                if (appUidList.count() > 1) {
                    pendingDialog.value = IncludeDialogState(appUidList, mapping, include)
                } else {
                    updateProxyIdForApp(mapping, include)
                }
            }
        }
    }

    private fun updateProxyIdForApp(mapping: ProxyApplicationMapping, include: Boolean) {
        io {
            if (include) {
                ProxyManager.updateProxyIdForApp(mapping.uid, proxyId, proxyName)
                Napier.i("Included apps: ${mapping.uid}, $proxyId, $proxyName")
            } else {
                ProxyManager.setNoProxyForApp(mapping.uid)
                Napier.i("Removed apps: ${mapping.uid}, $proxyId, $proxyName")
            }
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { withContext(Dispatchers.IO) { f() } }
    }
}

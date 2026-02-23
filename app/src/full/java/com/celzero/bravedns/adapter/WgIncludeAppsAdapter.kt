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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ProxyApplicationMapping
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import androidx.compose.ui.platform.LocalContext
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IncludeDialogState(
    val packageList: List<String>,
    val mapping: ProxyApplicationMapping,
    val included: Boolean
)

@Composable
fun IncludeDialogHost(
    state: IncludeDialogState?,
    onDismiss: () -> Unit,
    onConfirm: (ProxyApplicationMapping, Boolean) -> Unit
) {
    val context = LocalContext.current
    if (state == null) return
    val (title, positiveTxt) =
        if (state.included) {
            context.resources.getString(
                R.string.wg_apps_dialog_title_include,
                state.packageList.size.toString()
            ) to context.resources.getString(R.string.lbl_include)
        } else {
            context.resources.getString(
                R.string.wg_apps_dialog_title_exclude,
                state.packageList.size.toString()
            ) to context.resources.getString(R.string.lbl_remove)
        }

    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.packageList.forEach { name ->
                    Text(text = name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmText = positiveTxt,
        dismissText = context.resources.getString(R.string.ctbs_dialog_negative_btn),
        onConfirm = {
            onConfirm(state.mapping, state.included)
            onDismiss()
        },
        onDismiss = onDismiss,
        isConfirmDestructive = !state.included
    )
}

@Composable
fun IncludeAppRow(
    mapping: ProxyApplicationMapping,
    proxyId: String,
    proxyName: String,
    onInterfaceUpdate: (ProxyApplicationMapping, Boolean) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
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
                        context.resources.getString(R.string.exclude_apps_from_proxy)
                    } else {
                        context.resources.getString(
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
    val containerColor =
        if (isIncluded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val borderColor =
        when {
            isProxyExcluded -> MaterialTheme.colorScheme.error.copy(alpha = 0.34f)
            isIncluded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        }
    val contentAlpha = if (hasInternetPerm) 1f else 0.4f

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = isClickable) {
                    onInterfaceUpdate(mapping, !isIncluded)
                },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val supportingText = if (descText.isNotEmpty()) descText else mapping.packageName
                val supportingColor =
                    when {
                        isProxyExcluded -> MaterialTheme.colorScheme.error
                        descText.isNotEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
                    }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = isIncluded,
                enabled = isClickable,
                onCheckedChange = {
                    if (isClickable) onInterfaceUpdate(mapping, !isIncluded)
                }
            )
        }
    }
}

fun updateProxyIdForApp(uid: Int, proxyId: String, proxyName: String, include: Boolean) {
    CoroutineScope(Dispatchers.IO).launch {
        if (include) {
            ProxyManager.updateProxyIdForApp(uid, proxyId, proxyName)
            Napier.i("Included apps: $uid, $proxyId, $proxyName")
        } else {
            ProxyManager.setNoProxyForApp(uid)
            Napier.i("Removed apps: $uid, $proxyId, $proxyName")
        }
    }
}

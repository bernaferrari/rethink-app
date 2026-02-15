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
package com.celzero.bravedns.ui.bottomsheet


import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.database.WgConfigFilesImmutable
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

private const val TAG = "AppDomainBtmSht"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDomainRulesSheet(
    uid: Int,
    domain: String,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    val context = LocalContext.current
    val configAddSuccessToast = stringResource(R.string.config_add_success_toast)
    val scope = rememberCoroutineScope()

    var domainRule by remember { mutableStateOf(DomainRulesManager.Status.NONE) }
    var customDomain by remember { mutableStateOf<CustomDomain?>(null) }
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var showWgSheet by remember { mutableStateOf(false) }
    var wgConfigs by remember { mutableStateOf<List<WgConfigFilesImmutable?>>(emptyList()) }

    LaunchedEffect(uid, domain) {
        if (uid == INVALID_UID) {
            onDismiss()
            return@LaunchedEffect
        }
        val loadedAppNames = withContext(Dispatchers.IO) { FirewallManager.getAppNamesByUid(uid) }
        val appCount = loadedAppNames.count()
        val pkgName =
            if (loadedAppNames.isNotEmpty()) {
                FirewallManager.getPackageNameByAppName(loadedAppNames[0])
            } else {
                null
            }
        appNames = loadedAppNames
        appIcon =
            if (pkgName != null) {
                Utilities.getIcon(context, pkgName)
            } else {
                null
            }
        domainRule = withContext(Dispatchers.IO) { DomainRulesManager.status(domain, uid) }
        customDomain =
            withContext(Dispatchers.IO) {
                DomainRulesManager.getObj(uid, domain) ?: DomainRulesManager.makeCustomDomain(uid, domain)
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val appName =
            when {
                appNames.isEmpty() -> null
                appNames.size >= 2 ->
                    stringResource(
                        R.string.ctbs_app_other_apps,
                        appNames[0],
                        appNames.size.minus(1).toString()
                    )
                else -> appNames[0]
            }
        val borderColor = MaterialTheme.colorScheme.outline
        val trustIcon =
            if (domainRule == DomainRulesManager.Status.TRUST) {
                R.drawable.ic_trust_accent
            } else {
                R.drawable.ic_trust
            }
        val blockIcon =
            if (domainRule == DomainRulesManager.Status.BLOCK) {
                R.drawable.ic_block_accent
            } else {
                R.drawable.ic_block
            }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            if (appName != null) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    appIcon?.let { icon ->
                        val painter = rememberDrawablePainter(icon)
                        painter?.let {
                            Image(
                                painter = it,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                    }
                    Text(
                        text = appName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = stringResource(R.string.bsct_block_domain),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    painter = painterResource(id = trustIcon),
                    contentDescription = null,
                    modifier =
                        Modifier.size(28.dp).clickable {
                            val target =
                                if (domainRule == DomainRulesManager.Status.TRUST) {
                                    DomainRulesManager.Status.NONE
                                } else {
                                    DomainRulesManager.Status.TRUST
                                }
                            applyDomainRule(
                                domain,
                                uid,
                                target,
                                scope,
                                eventLogger,
                                onUpdated
                            ) { domainRule = it }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    painter = painterResource(id = blockIcon),
                    contentDescription = null,
                    modifier =
                        Modifier.size(28.dp).clickable {
                            val target =
                                if (domainRule == DomainRulesManager.Status.BLOCK) {
                                    DomainRulesManager.Status.NONE
                                } else {
                                    DomainRulesManager.Status.BLOCK
                                }
                            applyDomainRule(
                                domain,
                                uid,
                                target,
                                scope,
                                eventLogger,
                                onUpdated
                            ) { domainRule = it }
                        }
                )
            }

            Text(
                text = stringResource(R.string.bsac_title_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )
        }

        if (showWgSheet) {
            WireguardListSheet(
                inputLabel = customDomain?.domain,
                selectedProxyId = customDomain?.proxyId.orEmpty(),
                wgConfigs = wgConfigs,
                onDismiss = { showWgSheet = false },
                onSelected = { conf ->
                    scope.launch(Dispatchers.IO) {
                        val current = customDomain
                        if (current == null) {
                            Napier.w("$TAG: Custom domain is null")
                            return@launch
                        }
                        val id =
                            if (conf == null) {
                                ""
                            } else {
                                ID_WG_BASE + conf.id
                            }
                        DomainRulesManager.setProxyId(current, id)
                        current.proxyId = id
                        withContext(Dispatchers.Main) {
                            Utilities.showToastUiCentered(
                                context,
                                configAddSuccessToast,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun applyDomainRule(
    domain: String,
    uid: Int,
    status: DomainRulesManager.Status,
    scope: CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: () -> Unit,
    onSetStatus: (DomainRulesManager.Status) -> Unit
) {
    onSetStatus(status)
    val details = "Domain rule applied: $domain, $uid, ${status.name}"
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        "App domain rule",
        EventSource.UI,
        false,
        details
    )
    scope.launch(Dispatchers.IO) {
        DomainRulesManager.changeStatus(
            domain,
            uid,
            "",
            DomainRulesManager.DomainType.DOMAIN,
            status
        )
        withContext(Dispatchers.Main) { onUpdated() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WireguardListSheet(
    inputLabel: String?,
    selectedProxyId: String,
    wgConfigs: List<WgConfigFilesImmutable?>,
    onDismiss: () -> Unit,
    onSelected: (WgConfigFilesImmutable?) -> Unit
) {
    val context = LocalContext.current
    var currentProxyId by remember(inputLabel, selectedProxyId) { mutableStateOf(selectedProxyId) }
    val borderColor = MaterialTheme.colorScheme.outline

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            inputLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            LazyColumn {
                items(wgConfigs, key = { it?.id ?: -1 }) { conf ->
                    val proxyId = conf?.let { ID_WG_BASE + it.id } ?: ""
                    val isSelected = currentProxyId == proxyId
                    val name =
                        conf?.name ?: stringResource(R.string.settings_app_list_default_app)
                    val idSuffix = conf?.id?.toString()?.padStart(3, '0')
                    val desc =
                        if (conf == null) {
                            stringResource(R.string.settings_app_list_default_app)
                        } else {
                            stringResource(R.string.settings_app_list_default_app) + " $idSuffix"
                        }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    currentProxyId = proxyId
                                    onSelected(conf)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = ID_WG_BASE.uppercase(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = isSelected, onClick = null)
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

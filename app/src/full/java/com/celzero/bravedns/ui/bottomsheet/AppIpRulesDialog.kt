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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppIpBtmSht"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIpRulesSheet(
    uid: Int,
    ipAddress: String,
    domains: String,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ipRule by remember { mutableStateOf(IpRulesManager.IpRuleStatus.NONE) }
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    val domainList = remember(domains) { domains.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
    val domainRules = remember { mutableStateMapOf<String, DomainRulesManager.Status>() }

    LaunchedEffect(uid, ipAddress, domains) {
        val loadedAppNames = withContext(Dispatchers.IO) { FirewallManager.getAppNamesByUid(uid) }
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
        ipRule = withContext(Dispatchers.IO) {
            IpRulesManager.getMostSpecificRuleMatch(uid, ipAddress)
        }
        val statuses =
            withContext(Dispatchers.IO) {
                domainList.associateWith { DomainRulesManager.getDomainRule(it, uid) }
            }
        domainRules.clear()
        domainRules.putAll(statuses)
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
            if (ipRule == IpRulesManager.IpRuleStatus.TRUST) {
                R.drawable.ic_trust_accent
            } else {
                R.drawable.ic_trust
            }
        val blockIcon =
            if (ipRule == IpRulesManager.IpRuleStatus.BLOCK) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                text = stringResource(R.string.bsct_block_ip),
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
                        text = ipAddress,
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
                                if (ipRule == IpRulesManager.IpRuleStatus.TRUST) {
                                    IpRulesManager.IpRuleStatus.NONE
                                } else {
                                    IpRulesManager.IpRuleStatus.TRUST
                                }
                            applyIpRule(
                                uid,
                                ipAddress,
                                target,
                                scope,
                                eventLogger,
                                onUpdated
                            ) { ipRule = it }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    painter = painterResource(id = blockIcon),
                    contentDescription = null,
                    modifier =
                        Modifier.size(28.dp).clickable {
                            val target =
                                if (ipRule == IpRulesManager.IpRuleStatus.BLOCK) {
                                    IpRulesManager.IpRuleStatus.NONE
                                } else {
                                    IpRulesManager.IpRuleStatus.BLOCK
                                }
                            applyIpRule(
                                uid,
                                ipAddress,
                                target,
                                scope,
                                eventLogger,
                                onUpdated
                            ) { ipRule = it }
                        }
                )
            }

            if (domainList.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bsct_block_domain),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    items(domainList, key = { it }) { domain ->
                        val status = domainRules[domain] ?: DomainRulesManager.Status.NONE
                        DomainRuleRow(
                            domain = domain,
                            status = status,
                            onUpdate = { newStatus ->
                                domainRules[domain] = newStatus
                                applyDomainRule(domain, uid, newStatus, scope)
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.bsac_title_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun DomainRuleRow(
    domain: String,
    status: DomainRulesManager.Status,
    onUpdate: (DomainRulesManager.Status) -> Unit
) {
    val trustIcon =
        if (status == DomainRulesManager.Status.TRUST) {
            R.drawable.ic_trust_accent
        } else {
            R.drawable.ic_trust
        }
    val blockIcon =
        if (status == DomainRulesManager.Status.BLOCK) {
            R.drawable.ic_block_accent
        } else {
            R.drawable.ic_block
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = trustIcon),
            contentDescription = null,
            modifier =
                Modifier.size(24.dp).clickable {
                    if (status == DomainRulesManager.Status.TRUST) {
                        onUpdate(DomainRulesManager.Status.NONE)
                    } else {
                        onUpdate(DomainRulesManager.Status.TRUST)
                    }
                }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            painter = painterResource(id = blockIcon),
            contentDescription = null,
            modifier =
                Modifier.size(24.dp).clickable {
                    if (status == DomainRulesManager.Status.BLOCK) {
                        onUpdate(DomainRulesManager.Status.NONE)
                    } else {
                        onUpdate(DomainRulesManager.Status.BLOCK)
                    }
                }
        )
    }
}

private fun applyDomainRule(
    domain: String,
    uid: Int,
    status: DomainRulesManager.Status,
    scope: CoroutineScope
) {
    scope.launch(Dispatchers.IO) {
        DomainRulesManager.addDomainRule(
            domain.trim(),
            status,
            DomainRulesManager.DomainType.DOMAIN,
            uid
        )
    }
}

private fun applyIpRule(
    uid: Int,
    ipAddress: String,
    status: IpRulesManager.IpRuleStatus,
    scope: CoroutineScope,
    eventLogger: EventLogger,
    onUpdated: () -> Unit,
    onSetStatus: (IpRulesManager.IpRuleStatus) -> Unit
) {
    onSetStatus(status)
    val details = "IP Rule set to ${status.name} for IP: $ipAddress, UID: $uid"
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        "Custom IP",
        EventSource.UI,
        false,
        details
    )
    scope.launch(Dispatchers.IO) {
        val ipPair = IpRulesManager.getIpNetPort(ipAddress)
        val ip = ipPair.first ?: run {
            Napier.w("$TAG invalid ip for $ipAddress")
            return@launch
        }
        IpRulesManager.addIpRule(uid, ip, null, status, proxyId = "", proxyCC = "")
        withContext(Dispatchers.Main) { onUpdated() }
    }
}

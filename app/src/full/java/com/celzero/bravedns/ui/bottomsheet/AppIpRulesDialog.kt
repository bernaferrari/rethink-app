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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.firewall.RethinkDomainRuleRow
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleAction
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleEditorHeader
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleEditorStrings
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSheetBottomPaddingWithActions
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSheetLayout
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSectionTitle
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSupportingText
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleTrustBlockRow
import Logger
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
        val (names, icon) = withContext(Dispatchers.IO) { fetchRuleSheetAppIdentity(context, uid) }
        appNames = names
        appIcon = icon
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

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        val appName = formatRuleSheetAppName(context, appNames)
        val ruleStrings = RethinkRuleEditorStrings(
            trust = stringResource(R.string.ci_trust_rule),
            block = stringResource(R.string.ci_block),
        )
        RethinkRuleSheetLayout(bottomPadding = RethinkRuleSheetBottomPaddingWithActions) {
            RethinkRuleEditorHeader(
                appName = appName,
                appIcon = {
                    appIcon?.let { icon ->
                        rememberDrawablePainter(icon)?.let { painter ->
                            Image(painter = painter, contentDescription = null, modifier = Modifier.size(Dimensions.iconSizeSm))
                        }
                    }
                },
            )

            RethinkRuleSectionTitle(
                text = stringResource(R.string.bsct_block_ip),
            )

            RethinkRuleTrustBlockRow(
                value = ipAddress,
                action = ipRule.toRethinkRuleAction(),
                strings = ruleStrings,
                onActionChange = { action ->
                    val target = action.toIpRuleStatus()
                    applyIpRule(
                        uid,
                        ipAddress,
                        target,
                        scope,
                        eventLogger,
                        onUpdated
                    ) { ipRule = it }
                },
            )

            if (domainList.isNotEmpty()) {
                RethinkRuleSectionTitle(
                    text = stringResource(R.string.bsct_block_domain),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal)
                ) {
                    items(domainList, key = { it }) { domain ->
                        val status = domainRules[domain] ?: DomainRulesManager.Status.NONE
                        RethinkDomainRuleRow(
                            domain = domain,
                            action = status.toRethinkRuleAction(),
                            strings = ruleStrings,
                            onActionChange = { action ->
                                val newStatus = action.toDomainRuleStatus()
                                domainRules[domain] = newStatus
                                applyDomainRule(domain, uid, newStatus, scope)
                            }
                        )
                    }
                }
            }

            RethinkRuleSupportingText(
                text = stringResource(R.string.bsac_title_desc),
            )
        }
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

private fun DomainRulesManager.Status.toRethinkRuleAction() = when (this) {
    DomainRulesManager.Status.TRUST -> RethinkRuleAction.Trust
    DomainRulesManager.Status.BLOCK -> RethinkRuleAction.Block
    DomainRulesManager.Status.NONE -> RethinkRuleAction.None
}

private fun RethinkRuleAction.toDomainRuleStatus() = when (this) {
    RethinkRuleAction.Trust -> DomainRulesManager.Status.TRUST
    RethinkRuleAction.Block -> DomainRulesManager.Status.BLOCK
    RethinkRuleAction.None, RethinkRuleAction.Bypass -> DomainRulesManager.Status.NONE
}

private fun IpRulesManager.IpRuleStatus.toRethinkRuleAction() = when (this) {
    IpRulesManager.IpRuleStatus.TRUST -> RethinkRuleAction.Trust
    IpRulesManager.IpRuleStatus.BLOCK -> RethinkRuleAction.Block
    IpRulesManager.IpRuleStatus.NONE, IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL -> RethinkRuleAction.None
}

private fun RethinkRuleAction.toIpRuleStatus() = when (this) {
    RethinkRuleAction.Trust -> IpRulesManager.IpRuleStatus.TRUST
    RethinkRuleAction.Block -> IpRulesManager.IpRuleStatus.BLOCK
    RethinkRuleAction.None, RethinkRuleAction.Bypass -> IpRulesManager.IpRuleStatus.NONE
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
    logFirewallRuleChange(eventLogger, "Custom IP", details)
    scope.launch(Dispatchers.IO) {
        val ipPair = IpRulesManager.getIpNetPort(ipAddress)
        val ip = ipPair.first ?: run {
            Logger.w(TAG, "$TAG invalid ip for $ipAddress")
            return@launch
        }
        IpRulesManager.addIpRule(uid, ip, null, status, proxyId = "", proxyCC = "")
        withContext(Dispatchers.Main) { onUpdated() }
    }
}

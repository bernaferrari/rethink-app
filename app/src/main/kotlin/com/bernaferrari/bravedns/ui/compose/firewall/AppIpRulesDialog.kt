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
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import Logger
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkDomainRuleRow
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleEditorHeader
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleEditorStrings
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetBottomPaddingCompact
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetLayout
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSectionTitle
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSupportingText
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleTrustBlockRow
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AppIpBtmSht"

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
    val snackbarHostState = remember { SnackbarHostState() }
    val editedMessage = stringResource(R.string.cd_toast_edit)
    val undoLabel = stringResource(R.string.brbs_restore_dialog_positive)
    val invalidIpMessage = stringResource(R.string.ci_dialog_error_invalid_ip)

    var persistedIpRule by remember { mutableStateOf(IpRulesManager.IpRuleStatus.NONE) }
    var selectedIpRule by remember { mutableStateOf(IpRulesManager.IpRuleStatus.NONE) }
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appPackage by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    var undoEpoch by remember { mutableIntStateOf(0) }
    var ipError by remember { mutableStateOf<String?>(null) }
    val domainList = remember(domains) { domains.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
    val persistedDomainRules = remember { mutableStateMapOf<String, DomainRulesManager.Status>() }
    val selectedDomainRules = remember { mutableStateMapOf<String, DomainRulesManager.Status>() }

    LaunchedEffect(uid, ipAddress, domains) {
        val (names, packageName) = withContext(Dispatchers.IO) { fetchRuleSheetAppIdentity(uid) }
        appNames = names
        appPackage = packageName
        val status = withContext(Dispatchers.IO) {
            IpRulesManager.getMostSpecificRuleMatch(uid, ipAddress)
        }
        persistedIpRule = status
        selectedIpRule = status
        val statuses =
            withContext(Dispatchers.IO) {
                domainList.associateWith { DomainRulesManager.getDomainRule(it, uid) }
            }
        persistedDomainRules.clear()
        persistedDomainRules.putAll(statuses)
        selectedDomainRules.clear()
        selectedDomainRules.putAll(statuses)
        ipError = null
    }

    val domainsDirty = domainList.any { selectedDomainRules[it] != persistedDomainRules[it] }
    val hasPendingChange = selectedIpRule != persistedIpRule || domainsDirty

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        val appName = formatRuleSheetAppName(context, appNames)
        val ruleStrings = RethinkRuleEditorStrings(
            trust = stringResource(R.string.ci_trust_rule),
            block = stringResource(R.string.ci_block),
        )
        RethinkRuleSheetLayout(bottomPadding = RethinkRuleSheetBottomPaddingCompact) {
            RethinkRuleEditorHeader(
                appName = appName,
                // Inline beside the bodyMedium app name, so text-sized rather than a list-row icon.
                appIcon = appPackage?.let { pkg -> { AppIcon(pkg, size = SharedDimensions.iconSizeSm) } },
            )

            RethinkRuleSectionTitle(
                text = stringResource(R.string.bsct_block_ip),
            )

            RethinkRuleTrustBlockRow(
                value = ipAddress,
                action = selectedIpRule.toRethinkRuleAction(),
                strings = ruleStrings,
                onActionChange = {
                    selectedIpRule = it.toIpRuleStatus()
                    ipError = null
                },
            )

            if (ipError != null) {
                Text(
                    text = ipError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
                )
            }

            if (domainList.isNotEmpty()) {
                RethinkRuleSectionTitle(
                    text = stringResource(R.string.bsct_block_ip_domains),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal)
                ) {
                    items(domainList, key = { it }) { domain ->
                        val status = selectedDomainRules[domain] ?: DomainRulesManager.Status.NONE
                        RethinkDomainRuleRow(
                            domain = domain,
                            action = status.toRethinkRuleAction(),
                            strings = ruleStrings,
                            onActionChange = { action ->
                                selectedDomainRules[domain] = action.toDomainRuleStatus()
                            }
                        )
                    }
                }
            }

            RethinkRuleSupportingText(
                text = stringResource(R.string.bsac_title_desc),
            )

            RuleSheetApplyBar(
                enabled = hasPendingChange && !applying,
                onApply = {
                    val previousIp = persistedIpRule
                    val nextIp = selectedIpRule
                    val previousDomains = persistedDomainRules.toMap()
                    val nextDomains = selectedDomainRules.toMap()
                    val epoch = undoEpoch + 1
                    undoEpoch = epoch
                    scope.launchReversibleRuleMutation(
                        snackbarHostState = snackbarHostState,
                        editedMessage = editedMessage,
                        undoLabel = undoLabel,
                        epoch = epoch,
                        currentEpoch = { undoEpoch },
                        setApplying = { applying = it },
                        apply = apply@{
                            val ipOk = persistIpRule(uid, ipAddress, nextIp, previousIp, eventLogger)
                            if (!ipOk) {
                                ipError = invalidIpMessage
                                return@apply false
                            }
                            persistDomainRules(uid, previousDomains, nextDomains)
                            persistedIpRule = nextIp
                            selectedIpRule = nextIp
                            persistedDomainRules.clear()
                            persistedDomainRules.putAll(nextDomains)
                            onUpdated()
                            true
                        },
                        undo = {
                            persistIpRule(uid, ipAddress, previousIp, nextIp, eventLogger)
                            persistDomainRules(uid, nextDomains, previousDomains)
                            persistedIpRule = previousIp
                            selectedIpRule = previousIp
                            persistedDomainRules.clear()
                            persistedDomainRules.putAll(previousDomains)
                            selectedDomainRules.clear()
                            selectedDomainRules.putAll(previousDomains)
                            onUpdated()
                        },
                    )
                },
                onDismiss = onDismiss,
            )
        }
        RuleSheetSnackbarHost(snackbarHostState)
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

private suspend fun persistIpRule(
    uid: Int,
    ipAddress: String,
    status: IpRulesManager.IpRuleStatus,
    previous: IpRulesManager.IpRuleStatus,
    eventLogger: EventLogger,
): Boolean {
    if (status == previous) return true
    val ipPair = IpRulesManager.getIpNetPort(ipAddress)
    val ip = ipPair.first ?: run {
        Logger.w(TAG, "$TAG invalid ip for $ipAddress")
        return false
    }
    logFirewallRuleChange(eventLogger, "Custom IP", "IP Rule set to ${status.name} for IP: $ipAddress, UID: $uid")
    withContext(Dispatchers.IO) {
        IpRulesManager.addIpRule(uid, ip, null, status, proxyId = "", proxyCC = "")
    }
    return true
}

private suspend fun persistDomainRules(
    uid: Int,
    previous: Map<String, DomainRulesManager.Status>,
    next: Map<String, DomainRulesManager.Status>,
) {
    val changed = next.filter { previous[it.key] != it.value }
    if (changed.isEmpty()) return
    withContext(Dispatchers.IO) {
        changed.forEach { (domain, status) ->
            DomainRulesManager.addDomainRule(
                domain.trim(),
                status,
                DomainRulesManager.DomainType.DOMAIN,
                uid
            )
        }
    }
}

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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleAction
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleEditorHeader
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleEditorStrings
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetBottomPaddingCompact
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetLayout
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSectionTitle
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSupportingText
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleTrustBlockRow
import com.bernaferrari.bravedns.ui.compose.rememberDrawablePainter
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDomainRulesSheet(
    uid: Int,
    domain: String,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val editedMessage = stringResource(R.string.cd_toast_edit)
    val undoLabel = stringResource(R.string.brbs_restore_dialog_positive)

    var persistedRule by remember { mutableStateOf(DomainRulesManager.Status.NONE) }
    var selectedRule by remember { mutableStateOf(DomainRulesManager.Status.NONE) }
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var applying by remember { mutableStateOf(false) }
    var undoEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(uid, domain) {
        if (uid == INVALID_UID) {
            onDismiss()
            return@LaunchedEffect
        }
        val (names, icon) = withContext(Dispatchers.IO) { fetchRuleSheetAppIdentity(context, uid) }
        appNames = names
        appIcon = icon
        val status = withContext(Dispatchers.IO) { DomainRulesManager.status(domain, uid) }
        persistedRule = status
        selectedRule = status
    }

    val hasPendingChange = selectedRule != persistedRule

    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        val appName = formatRuleSheetAppName(context, appNames)
        val ruleStrings = RethinkRuleEditorStrings(
            trust = stringResource(R.string.ci_trust_rule),
            block = stringResource(R.string.ci_block),
        )
        RethinkRuleSheetLayout(bottomPadding = RethinkRuleSheetBottomPaddingCompact) {
            RethinkRuleEditorHeader(
                appName = appName,
                appIcon = {
                    appIcon?.let { icon ->
                        rememberDrawablePainter(icon)?.let { painter ->
                            Image(painter = painter, contentDescription = null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                        }
                    }
                },
            )

            RethinkRuleSectionTitle(
                text = stringResource(R.string.bsct_block_domain),
            )

            RethinkRuleTrustBlockRow(
                value = domain,
                action = selectedRule.toRethinkRuleAction(),
                strings = ruleStrings,
                onActionChange = { selectedRule = it.toDomainRuleStatus() },
            )

            RethinkRuleSupportingText(
                text = stringResource(R.string.bsac_title_desc),
            )

            RuleSheetApplyBar(
                enabled = hasPendingChange && !applying,
                onApply = {
                    val previous = persistedRule
                    val next = selectedRule
                    val epoch = undoEpoch + 1
                    undoEpoch = epoch
                    scope.launchReversibleRuleMutation(
                        snackbarHostState = snackbarHostState,
                        editedMessage = editedMessage,
                        undoLabel = undoLabel,
                        epoch = epoch,
                        currentEpoch = { undoEpoch },
                        setApplying = { applying = it },
                        apply = {
                            persistDomainRule(domain, uid, next, eventLogger)
                            persistedRule = next
                            selectedRule = next
                            onUpdated()
                            true
                        },
                        undo = {
                            persistDomainRule(domain, uid, previous, eventLogger)
                            persistedRule = previous
                            selectedRule = previous
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

private suspend fun persistDomainRule(
    domain: String,
    uid: Int,
    status: DomainRulesManager.Status,
    eventLogger: EventLogger,
) {
    logFirewallRuleChange(eventLogger, "App domain rule", "Domain rule applied: $domain, $uid, ${status.name}")
    withContext(Dispatchers.IO) {
        DomainRulesManager.changeStatus(
            domain,
            uid,
            "",
            DomainRulesManager.DomainType.DOMAIN,
            status
        )
    }
}

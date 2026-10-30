/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.rules.DomainRuleInput
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android copy for [RethinkAddRuleSheet]. */
@Composable
fun androidAddRuleStrings(): RethinkAddRuleStrings {
    val blockDomain = stringResource(R.string.add_rule_block_domain)
    val trustDomain = stringResource(R.string.add_rule_trust_domain)
    val blockIp = stringResource(R.string.add_rule_block_ip)
    val trustIp = stringResource(R.string.add_rule_trust_ip)
    return RethinkAddRuleStrings(
        title = stringResource(R.string.add_rule_title),
        domainLabel = stringResource(R.string.lbl_domain),
        domainHint = stringResource(R.string.add_rule_domain_hint),
        ipLabel = stringResource(R.string.add_rule_ip_label),
        ipHint = stringResource(R.string.add_rule_ip_hint),
        action = stringResource(R.string.lbl_action),
        block = stringResource(R.string.ci_block),
        trust = stringResource(R.string.ci_trust_rule),
        appliesTo = stringResource(R.string.add_rule_applies_to),
        allApps = stringResource(R.string.lbl_all_apps),
        chooseApp = stringResource(R.string.add_rule_choose_app),
        confirm = { target, action ->
            val trust = action == RethinkRuleAction.Trust
            when (target) {
                RethinkRulesTab.DOMAIN -> if (trust) trustDomain else blockDomain
                RethinkRulesTab.IP -> if (trust) trustIp else blockIp
            }
        },
        cancel = stringResource(R.string.lbl_cancel),
    )
}

/**
 * Android adapter for [RethinkAddRuleSheet]: validates with [DomainRuleInput] / `IpRuleInput`
 * (the same checks the Rules screen's Add uses) and writes through [DomainRulesManager] /
 * [IpRulesManager]. Block stays preselected, so typing and confirming does what Add always did.
 *
 * [onChooseApp] opens the host's app picker and reports the pick through its callback; null keeps
 * the scope to [initialScope] and All apps. [onAdded] runs after the write lands.
 */
@Composable
fun AddRuleSheetHost(
    target: RethinkRulesTab,
    initialScope: RethinkAddRuleScope,
    eventLogger: EventLogger,
    onDismiss: () -> Unit,
    onAdded: () -> Unit = {},
    initialValue: String = "",
    onChooseApp: ((onPicked: (RethinkAddRuleScope.App) -> Unit) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var ruleScope by remember(initialScope) { mutableStateOf(initialScope) }
    val invalidIp = stringResource(R.string.ci_dialog_error_invalid_ip)
    val invalidDomain = stringResource(R.string.custom_url_error_invalid_url)
    RethinkAddRuleSheet(
        target = target,
        scope = ruleScope,
        strings = androidAddRuleStrings(),
        onScopeChange = { ruleScope = it },
        onDismiss = onDismiss,
        initialValue = initialValue,
        onChooseApp = onChooseApp?.let { choose -> { choose { picked -> ruleScope = picked } } },
        onAdd = { value, action, ruleScopeAtAdd ->
            val uid = (ruleScopeAtAdd as? RethinkAddRuleScope.App)?.uid ?: UID_EVERYBODY
            val trust = action == RethinkRuleAction.Trust
            when (target) {
                RethinkRulesTab.IP -> ipRuleAddResult(value, invalidIp) { address, port ->
                    // An all-apps Trust IP row is ignored by the firewall; its "trust" is bypass.
                    val status = when {
                        !trust -> IpRulesManager.IpRuleStatus.BLOCK
                        uid == UID_EVERYBODY -> IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL
                        else -> IpRulesManager.IpRuleStatus.TRUST
                    }
                    persist(scope, onAdded) {
                        IpRulesManager.addIpRule(uid, address, port, status, proxyId = "", proxyCC = "")
                        logFirewallRuleChange(eventLogger, "Added IP rule", "IP: $value, $uid, ${status.name}")
                    }
                }
                RethinkRulesTab.DOMAIN -> {
                    val domain = DomainRuleInput.normalize(value) ?: return@RethinkAddRuleSheet RethinkRulesAddResult(false, invalidDomain)
                    val status = if (trust) DomainRulesManager.Status.TRUST else DomainRulesManager.Status.BLOCK
                    val type = if (domain.startsWith("*.")) DomainRulesManager.DomainType.WILDCARD else DomainRulesManager.DomainType.DOMAIN
                    persist(scope, onAdded) {
                        DomainRulesManager.addDomainRule(domain, status, type, uid)
                        logFirewallRuleChange(eventLogger, "Added domain rule", "Domain: $domain, $uid, ${status.name}")
                    }
                    RethinkRulesAddResult.Success
                }
            }
        },
    )
}

/** The sheet leaves composition as it closes; the write must still land (DB row and trie together). */
private fun persist(scope: CoroutineScope, onAdded: () -> Unit, write: suspend () -> Unit) {
    scope.launch {
        withContext(Dispatchers.IO + NonCancellable) { write() }
        onAdded()
    }
}

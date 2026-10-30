package com.bernaferrari.bravedns.ui.compose.logs

import Logger
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DnsLogRuleActions(log: DnsLog) {
    val scope = rememberCoroutineScope()
    val appUid = log.uid.takeIf { it >= 0 }
    val editor = remember(log.queryStr, appUid) {
        DnsQueryRuleEditor(
            query = log.queryStr,
            scopes = setOfNotNull(appUid, Constants.UID_EVERYBODY),
            read = DomainRulesManager::getDomainRule,
            write = { domain, uid, status ->
                withContext(Dispatchers.IO) {
                    DomainRulesManager.changeStatus(domain, uid, log.responseIps, DomainRulesManager.DomainType.DOMAIN, status)
                }
            },
            onError = { Logger.e(Logger.LOG_TAG_UI, "DNS log rule save failed", it) },
        )
    }
    val state by editor.state.collectAsState()
    if (editor.domain == null) {
        Text(stringResource(R.string.dns_rule_invalid_query), style = MaterialTheme.typography.bodySmall)
        return
    }
    var packageCount by remember(appUid) { mutableIntStateOf(0) }
    LaunchedEffect(appUid) {
        if (appUid != null) packageCount = withContext(Dispatchers.IO) { FirewallManager.getPackageNamesByUid(appUid).size }
    }
    val options = listOf(
        RethinkChoiceOption(DomainRulesManager.Status.NONE, stringResource(R.string.ci_no_rule)),
        RethinkChoiceOption(DomainRulesManager.Status.BLOCK, stringResource(R.string.ci_block)),
        RethinkChoiceOption(DomainRulesManager.Status.TRUST, stringResource(R.string.ci_trust_rule)),
    )
    val appLabel = when {
        packageCount > 1 -> pluralStringResource(R.plurals.dns_rule_scope_shared, packageCount, appUid ?: 0, packageCount)
        packageCount == 1 -> stringResource(R.string.dns_rule_scope_app, appUid ?: 0)
        else -> stringResource(R.string.dns_rule_scope_uid, appUid ?: 0)
    }
    val scopes = listOfNotNull(appUid?.let { it to appLabel }, Constants.UID_EVERYBODY to stringResource(R.string.dns_rule_scope_global))
    DnsLogRuleControls(state, scopes, options) { uid, status -> scope.launch { editor.select(uid, status) } }
}

@Composable
internal fun DnsLogRuleControls(
    state: DnsRuleEditorState,
    scopes: List<Pair<Int, String>>,
    options: List<RethinkChoiceOption<DomainRulesManager.Status>>,
    onSelect: (Int, DomainRulesManager.Status) -> Unit,
) {
    val errorFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(state.error) { if (state.error != null) errorFocus.bringIntoView() }
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        Text(stringResource(R.string.lbl_domain_rules), style = MaterialTheme.typography.titleMedium)
        scopes.forEach { (uid, label) ->
            Text(label, style = MaterialTheme.typography.labelLarge)
            RethinkListGroup {
                options.forEachIndexed { index, option ->
                    RethinkRadioListItem(
                        headline = option.label,
                        selected = state.statuses[uid] == option.value,
                        onSelect = { onSelect(uid, option.value) },
                        position = cardPositionFor(index, options.lastIndex),
                        enabled = !state.saving,
                    )
                }
            }
        }
        if (state.saving) Text(stringResource(R.string.dns_rule_saving), style = MaterialTheme.typography.bodySmall)
        if (state.error != null) Text(
            stringResource(R.string.dns_rule_save_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.bringIntoViewRequester(errorFocus).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

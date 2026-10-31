/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.accent
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** What tapping a rule can do to it: switch it between Block and Trust, or delete it. */
@Immutable
sealed interface RethinkRuleEdit {
    /** [action] is [RethinkRuleAction.Block] or [RethinkRuleAction.Trust]. */
    data class SetAction(val action: RethinkRuleAction) : RethinkRuleEdit

    data object Delete : RethinkRuleEdit
}

/**
 * The Block · Trust option a stored rule reads as, or null when it is neither. For IPs the firewall
 * honours an all-apps "trust" only as the bypass status (that's what Add writes for an all-apps
 * Trust IP), so all-apps bypass reads as Trust, while an all-apps TRUST row (ignored by the
 * firewall) and a per-app bypass read as neither.
 */
fun RethinkFirewallRule.editAction(): RethinkRuleAction? {
    val allAppsIp = tab == RethinkRulesTab.IP && uid == RETHINK_RULES_UID_EVERYBODY
    return when (status) {
        RethinkFirewallRuleStatus.Block -> RethinkRuleAction.Block
        RethinkFirewallRuleStatus.Trust -> RethinkRuleAction.Trust.takeUnless { allAppsIp }
        RethinkFirewallRuleStatus.Bypass -> RethinkRuleAction.Trust.takeIf { allAppsIp }
        RethinkFirewallRuleStatus.None -> null
    }
}

/**
 * Tapping a rule: the target (with its port), who it applies to ([scopeLabel], read-only: moving a
 * rule to another app could overwrite that app's own rule), a **Block · Trust** choice and Delete.
 *
 * Picking the other action calls [onEdit] and closes the sheet; the host writes it and offers Undo.
 * Delete calls [onEdit] with [RethinkRuleEdit.Delete] when [onEdit] is set (Undo covers it),
 * otherwise [onDelete] (the host confirms first). A null [onEdit] hides the choice.
 */
@Composable
fun RethinkRuleEditSheet(
    rule: RethinkFirewallRule,
    scopeLabel: String,
    strings: RethinkCustomRulesStrings,
    onDismiss: () -> Unit,
    onEdit: ((RethinkRuleEdit) -> Unit)?,
    onDelete: () -> Unit,
) {
    val accent = rule.status.accent()
    var chosen by remember(rule.id, rule.status) { mutableStateOf(rule.editAction()) }
    RethinkModalBottomSheet(onDismissRequest = onDismiss) { dismiss ->
        RethinkSheetHeader(
            title = rule.value,
            subtitle = listOfNotNull(rule.port?.let(strings.port), scopeLabel).joinToString(" · "),
            leading = {
                RethinkIconWell(
                    icon = if (rule.tab == RethinkRulesTab.IP) MaterialSymbols.Filled.Pin else MaterialSymbols.Filled.Link,
                    tint = accent,
                    containerColor = RethinkIconWellDefaults.tinted(accent),
                )
            },
            // The choice below says Block or Trust; the pill stays only for what it can't show.
            trailing = if (onEdit == null || rule.editAction() == null) {
                { RethinkPill(rule.status.label(strings), colors = RethinkPillDefaults.status(accent)) }
            } else {
                null
            },
        )
        if (onEdit != null) {
            RethinkFormSection(title = strings.action) {
                RethinkConnectedChoiceGroup(
                    options = listOf(
                        RethinkChoiceOption(RethinkRuleAction.Block, strings.block, MaterialSymbols.Filled.Block),
                        RethinkChoiceOption(RethinkRuleAction.Trust, strings.trust, MaterialSymbols.Filled.Verified),
                    ),
                    selected = chosen,
                    onSelect = { action ->
                        if (action != chosen) {
                            chosen = action
                            onEdit(RethinkRuleEdit.SetAction(action))
                            dismiss()
                        }
                    },
                )
            }
        }
        RethinkListGroup {
            RethinkListItem(
                headline = strings.deleteRule,
                leadingIcon = MaterialSymbols.Filled.DeleteOutline,
                destructive = true,
                position = CardPosition.Single,
                onClick = {
                    if (onEdit != null) onEdit(RethinkRuleEdit.Delete) else onDelete()
                    dismiss()
                },
            )
        }
    }
}

internal fun RethinkFirewallRuleStatus.label(strings: RethinkCustomRulesStrings): String = when (this) {
    RethinkFirewallRuleStatus.Block -> strings.block
    RethinkFirewallRuleStatus.Trust -> strings.trust
    RethinkFirewallRuleStatus.Bypass -> strings.bypass
    RethinkFirewallRuleStatus.None -> strings.noRule
}

/** commonMain has no dependency on Android's constants module in the standalone WASM build. */
internal const val RETHINK_RULES_UID_EVERYBODY = -1000

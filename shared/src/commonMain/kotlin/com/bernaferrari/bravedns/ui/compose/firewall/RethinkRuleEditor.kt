/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Target-neutral state of a firewall rule. Platform adapters map their manager-specific enums here. */
enum class RethinkRuleAction { None, Trust, Block, Bypass }

data class RethinkRuleActionOption(
    val action: RethinkRuleAction,
    val label: String,
)

data class RethinkRuleEditorStrings(
    val trust: String,
    val block: String,
)

/** Reusable heading for app/domain/IP rule sheets; artwork remains a host-owned slot. */
@Composable
fun RethinkRuleEditorHeader(
    appName: String?,
    modifier: Modifier = Modifier,
    appIcon: (@Composable () -> Unit)? = null,
) {
    if (appName.isNullOrBlank()) return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        appIcon?.invoke()
        if (appIcon != null) androidx.compose.foundation.layout.Spacer(Modifier.width(SharedDimensions.spacingSmMd))
        Text(appName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RethinkRuleSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SectionHeader(
        title = text,
        modifier = modifier,
        horizontalPadding = SharedDimensions.screenPaddingHorizontal,
    )
}

@Composable
fun RethinkRuleSupportingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    )
}

@Composable
fun RethinkRuleValue(
    value: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    SelectionContainer(modifier.fillMaxWidth()) {
        Text(
            value,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        )
    }
}

/** Full-width rule target with labeled trust/block toggles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkRuleTrustBlockRow(
    value: String,
    action: RethinkRuleAction,
    strings: RethinkRuleEditorStrings,
    onActionChange: (RethinkRuleAction) -> Unit,
    modifier: Modifier = Modifier,
    valueTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = valueTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        RethinkRuleTrustBlockToggles(action = action, strings = strings, onActionChange = onActionChange, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RethinkRuleTrustBlockToggles(
    action: RethinkRuleAction,
    strings: RethinkRuleEditorStrings,
    onActionChange: (RethinkRuleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkConnectedChoiceGroup(
        options = listOf(
            RethinkChoiceOption(RethinkRuleAction.Trust, strings.trust),
            RethinkChoiceOption(RethinkRuleAction.Block, strings.block),
        ),
        selected = action,
        onSelect = onActionChange,
        modifier = modifier,
        onDeselect = { onActionChange(RethinkRuleAction.None) },
    )
}

/** Portable selector for persisted custom IP/domain rules. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkRuleActionSelector(
    options: List<RethinkRuleActionOption>,
    selectedAction: RethinkRuleAction,
    onActionChange: (RethinkRuleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkConnectedChoiceGroup(
        options = options.map { RethinkChoiceOption(it.action, it.label) },
        selected = selectedAction,
        onSelect = onActionChange,
        modifier = modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    )
}

/** Compact list row for domain rules attached to an IP rule. */
@Composable
fun RethinkDomainRuleRow(
    domain: String,
    action: RethinkRuleAction,
    strings: RethinkRuleEditorStrings,
    onActionChange: (RethinkRuleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkListItem(
        modifier = modifier,
        headline = domain,
        position = CardPosition.Single,
        trailing = {
            RethinkRuleTrustBlockToggles(
                action = action,
                strings = strings,
                onActionChange = onActionChange,
                modifier = Modifier.width(SharedDimensions.touchTargetMin * 4),
            )
        },
    )
}

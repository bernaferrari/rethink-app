/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
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
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
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
    RethinkConnectedChoiceRow(
        options = listOf(RethinkRuleAction.Trust, RethinkRuleAction.Block),
        selected = action,
        onSelected = { next -> onActionChange(if (next == action) RethinkRuleAction.None else next) },
        label = { option -> if (option == RethinkRuleAction.Trust) strings.trust else strings.block },
        modifier = modifier,
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
    RethinkConnectedChoiceRow(
        options = options,
        selected = options.firstOrNull { it.action == selectedAction },
        onSelected = { onActionChange(it.action) },
        label = { it.label },
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> RethinkConnectedChoiceRow(
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { checked -> if (checked || isSelected) onSelected(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = null,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(
                    label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
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

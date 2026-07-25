/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.firewall

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Full-width rule target with expressive trust/block toggles. */
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
        RethinkRuleActionIcon(
            icon = MaterialSymbols.Filled.Security,
            label = strings.trust,
            selected = action == RethinkRuleAction.Trust,
            selectedColor = MaterialTheme.colorScheme.tertiary,
            onClick = { onActionChange(if (action == RethinkRuleAction.Trust) RethinkRuleAction.None else RethinkRuleAction.Trust) },
        )
        RethinkRuleActionIcon(
            icon = MaterialSymbols.Filled.Block,
            label = strings.block,
            selected = action == RethinkRuleAction.Block,
            selectedColor = MaterialTheme.colorScheme.error,
            onClick = { onActionChange(if (action == RethinkRuleAction.Block) RethinkRuleAction.None else RethinkRuleAction.Block) },
        )
    }
}

@Composable
private fun RethinkRuleActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (selected) selectedColor.copy(alpha = 0.18f) else Color.Transparent,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Portable selector for persisted custom IP/domain rules. */
@Composable
fun RethinkRuleActionSelector(
    options: List<RethinkRuleActionOption>,
    selectedAction: RethinkRuleAction,
    onActionChange: (RethinkRuleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        options.forEach { option ->
            val selected = option.action == selectedAction
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selected,
                onClick = { onActionChange(option.action) },
                label = { Text(option.label, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ruleActionContainer(option.action),
                    selectedLabelColor = ruleActionContent(option.action),
                ),
                border = if (selected) BorderStroke(SharedDimensions.dividerThicknessBold, ruleActionContent(option.action).copy(alpha = 0.28f)) else null,
            )
        }
    }
}

@Composable
private fun ruleActionContainer(action: RethinkRuleAction): Color = when (action) {
    RethinkRuleAction.Block -> MaterialTheme.colorScheme.errorContainer
    RethinkRuleAction.Trust, RethinkRuleAction.Bypass -> MaterialTheme.colorScheme.tertiaryContainer
    RethinkRuleAction.None -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun ruleActionContent(action: RethinkRuleAction): Color = when (action) {
    RethinkRuleAction.Block -> MaterialTheme.colorScheme.error
    RethinkRuleAction.Trust, RethinkRuleAction.Bypass -> MaterialTheme.colorScheme.tertiary
    RethinkRuleAction.None -> MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(SharedDimensions.dividerThickness, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
    ) {
        RethinkRuleTrustBlockRow(
            value = domain,
            action = action,
            strings = strings,
            onActionChange = onActionChange,
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs),
            valueTextStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

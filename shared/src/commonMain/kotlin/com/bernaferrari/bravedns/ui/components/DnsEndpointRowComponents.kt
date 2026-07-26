/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.components

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** The host chooses the action behavior; commonMain owns the expressive endpoint row itself. */
enum class DnsRowAction { Info, Edit, Delete }

enum class DnsRowSelection { Radio, Checkbox }

@Composable
fun DnsEndpointRow(
    title: String,
    supporting: String?,
    selected: Boolean,
    action: DnsRowAction,
    selection: DnsRowSelection = DnsRowSelection.Radio,
    onActionClick: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SharedDimensions.cardCornerRadius)
    Surface(
        onClick = { onSelectionChange(if (selection == DnsRowSelection.Radio) true else !selected) },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.cardPaddingSm, vertical = SharedDimensions.spacingSmMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!supporting.isNullOrEmpty()) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DnsEndpointActionButton(action, onActionClick)
            when (selection) {
                DnsRowSelection.Radio -> RadioButton(selected, onClick = { onSelectionChange(true) })
                DnsRowSelection.Checkbox -> Checkbox(selected, onCheckedChange = onSelectionChange)
            }
        }
    }
}

@Composable
private fun DnsEndpointActionButton(action: DnsRowAction, onClick: () -> Unit) {
    val containerColor = when (action) {
        DnsRowAction.Delete -> MaterialTheme.colorScheme.errorContainer
        DnsRowAction.Edit -> MaterialTheme.colorScheme.tertiaryContainer
        DnsRowAction.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (action) {
        DnsRowAction.Delete -> MaterialTheme.colorScheme.onErrorContainer
        DnsRowAction.Edit -> MaterialTheme.colorScheme.onTertiaryContainer
        DnsRowAction.Info -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when (action) {
        DnsRowAction.Delete -> MaterialSymbols.Filled.DeleteOutline
        DnsRowAction.Edit -> MaterialSymbols.Filled.Edit
        DnsRowAction.Info -> MaterialSymbols.Filled.MoreHoriz
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.size(SharedDimensions.touchTargetSm),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(SharedDimensions.iconSizeSm))
    }
}

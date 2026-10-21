/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * One toolbar strip (connected group or search field + [RethinkToolbarIconToggle]s). Fixes
 * the gap between controls and their vertical centering so every log-like screen (network, DNS,
 * console, events, database) lines up the same way.
 */
@Composable
fun RethinkToolbarRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Toolbar action beside the log filter group. Same height and tonal family as the connected
 * segments so the row reads as one control strip; [selected] (an active filter) uses the checked
 * container, and [count] shows as a badge. Toggle semantics, since it reflects filter state.
 */
@Composable
fun RethinkToolbarIconToggle(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    count: Int = 0,
    modifier: Modifier = Modifier,
    /** Match the neighbouring control's height (48dp groups; 56dp text fields). */
    size: Dp = SharedDimensions.touchTargetMin,
    /** Shows a dot badge while [selected] when there is no [count] to show. */
    showSelectedDot: Boolean = false,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = modifier,
        badge = {
            when {
                count > 0 -> Badge { Text(count.toString()) }
                showSelectedDot && selected -> Badge()
            }
        },
    ) {
        FilledTonalIconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            enabled = enabled,
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(SharedDimensions.iconSizeSm),
            )
        }
    }
}

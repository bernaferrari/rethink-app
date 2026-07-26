/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

data class RethinkIncludeAppState(
    val id: String,
    val title: String,
    val isIncluded: Boolean,
    val isProxyExcluded: Boolean,
    val hasInternetPermission: Boolean,
)

/** Shared selectable app row used by proxy and WireGuard app pickers. */
@Composable
fun RethinkIncludeAppRow(
    state: RethinkIncludeAppState,
    position: CardPosition,
    onIncludedChange: (Boolean) -> Unit,
    appIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isProxyExcluded
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "includeAppRowScale",
    )
    val shape = rethinkGroupedListShape(position)
    val containerColor = when {
        state.isProxyExcluded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        state.isIncluded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentAlpha = if (state.hasInternetPermission && enabled) 1f else 0.5f
    val titleColor = if (state.isIncluded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.fillMaxWidth().scale(scale).clip(shape).padding(
            top = if (position == CardPosition.Middle || position == CardPosition.Last) 2.dp else 0.dp,
        ),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = state.isIncluded,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = ripple(),
            ) { checked -> if (checked != state.isIncluded && enabled) onIncludedChange(checked) }
                .padding(horizontal = SharedDimensions.spacingMd, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            appIcon()
            Text(
                state.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Checkbox(checked = state.isIncluded, enabled = enabled, onCheckedChange = null)
        }
    }
}

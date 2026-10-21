/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkWireguardConfigControl { Switch, Checkbox }

/** Render-only state for a WireGuard configuration card. Polling and mutations are host concerns. */
data class RethinkWireguardConfigCardState(
    val name: String,
    val identifier: String,
    val chips: List<String>,
    val isChecked: Boolean,
    val statusText: String,
    val appsText: String? = null,
    val uptimeText: String? = null,
    val rxTxText: String? = null,
    /** Status accent (connected / failing / idle). Rendered as a status dot, never as a border. */
    val accentBorderColor: Color? = null,
    /** Retained for host compatibility; a positive width marks the accent as active. */
    val accentBorderWidth: Dp = SharedDimensions.spacingNone,
)

/** Shared configuration card for both simple and advanced WireGuard modes. */
@Composable
fun RethinkWireguardConfigCard(
    state: RethinkWireguardConfigCardState,
    control: RethinkWireguardConfigControl,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection reads through container tone (as in grouped lists); status through a dot.
    val accent = state.accentBorderColor?.takeIf { state.accentBorderWidth > SharedDimensions.spacingNone }
    val containerColor = if (state.isChecked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingXs),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                        Text(state.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.identifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.chips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                        ) {
                            state.chips.forEach { chip ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                ) {
                                    Text(
                                        chip,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                                    )
                                }
                            }
                        }
                    }
                }
                when (control) {
                    RethinkWireguardConfigControl.Switch -> Switch(
                        state.isChecked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.semantics { contentDescription = state.name },
                    )
                    RethinkWireguardConfigControl.Checkbox -> Checkbox(
                        state.isChecked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.semantics { contentDescription = state.name },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                if (accent != null) {
                    Box(Modifier.size(SharedDimensions.spacingSm).background(accent, CircleShape))
                }
                Text(
                    state.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.appsText?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!state.uptimeText.isNullOrBlank() || !state.rxTxText.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.uptimeText.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(state.rxTxText.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

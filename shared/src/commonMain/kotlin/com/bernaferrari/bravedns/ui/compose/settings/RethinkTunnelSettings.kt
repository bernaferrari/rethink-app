/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedRow

enum class RethinkTunnelSettingKind { Action, Toggle }

enum class RethinkTunnelSettingIcon { Settings, Tune, NetworkCheck, Security }

data class RethinkTunnelSettingRow(
    val id: String,
    val title: String,
    val description: String? = null,
    val kind: RethinkTunnelSettingKind,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val icon: RethinkTunnelSettingIcon = RethinkTunnelSettingIcon.Settings,
)

data class RethinkTunnelSettingsStrings(
    val title: String,
    val subtitle: String,
    val lockdownDescription: String,
    val advanced: String,
    val dialTimeout: String,
)

/** Target-neutral tunnel settings layout. Hosts supply policy state and perform every system action. */
@Composable
fun RethinkTunnelSettingsScreen(
    listState: LazyListState,
    strings: RethinkTunnelSettingsStrings,
    showLockdown: Boolean,
    coreRows: List<RethinkTunnelSettingRow>,
    advancedRows: List<RethinkTunnelSettingRow>,
    dialTimeoutMinutes: Int,
    dialTimeoutDescription: String,
    focusedRowId: String? = null,
    onBackClick: (() -> Unit)? = null,
    onLockdownClick: () -> Unit,
    onActionClick: (String) -> Unit,
    onToggleChange: (String, Boolean) -> Unit,
    onDialTimeoutChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = strings.subtitle,
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
    ) {
        if (showLockdown) {
            item {
                RethinkListGroup {
                    RethinkListItem(
                        headline = strings.lockdownDescription,
                        leadingIcon = MaterialSymbols.Filled.Lock,
                        position = CardPosition.Single,
                        onClick = onLockdownClick,
                    )
                }
            }
        }
        item { RethinkTunnelSettingGroup(coreRows, focusedRowId, onActionClick, onToggleChange) }
        rethinkSection(strings.advanced) {
            RethinkTunnelSettingGroup(
                rows = advancedRows,
                focusedRowId = focusedRowId,
                onActionClick = onActionClick,
                onToggleChange = onToggleChange,
                lastIndexOverride = advancedRows.lastIndex + 1,
            ) {
                val timeoutPosition = cardPositionFor(advancedRows.lastIndex + 1, advancedRows.lastIndex + 1)
                val timeoutHighlighted = focusedRowId == "network_dial_timeout"
                Surface(
                    modifier = Modifier.rethinkGroupedRow(timeoutPosition),
                    color = if (timeoutHighlighted) {
                        // Same tonal highlight as RethinkListItem's focus state.
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    shape = rethinkGroupedListShape(timeoutPosition),
                ) {
                    Column(Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding)) {
                        Text(strings.dialTimeout, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(dialTimeoutDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(SharedDimensions.spacingSm))
                        Slider(
                            value = dialTimeoutMinutes.toFloat(),
                            onValueChange = { onDialTimeoutChange(it.toInt()) },
                            valueRange = 0f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RethinkTunnelSettingGroup(
    rows: List<RethinkTunnelSettingRow>,
    focusedRowId: String?,
    onActionClick: (String) -> Unit,
    onToggleChange: (String, Boolean) -> Unit,
    lastIndexOverride: Int? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    // A leading icon on only some rows staggers the text column; icons appear only when every
    // row in the group can carry one.
    val showIcons = rows.isNotEmpty() && rows.all {
        it.kind != RethinkTunnelSettingKind.Toggle &&
            (it.icon == RethinkTunnelSettingIcon.NetworkCheck || it.icon == RethinkTunnelSettingIcon.Security)
    }
    RethinkListGroup {
        rows.forEachIndexed { index, row ->
            val position = cardPositionFor(index, lastIndexOverride ?: rows.lastIndex)
            if (row.kind == RethinkTunnelSettingKind.Toggle) {
                RethinkSwitchListItem(
                    headline = row.title,
                    checked = row.checked,
                    onCheckedChange = { onToggleChange(row.id, it) },
                    supporting = row.description,
                    position = position,
                    highlighted = focusedRowId == row.id,
                    enabled = row.enabled,
                )
            } else {
                RethinkListItem(
                    headline = row.title,
                    supporting = row.description,
                    leadingIcon = row.icon.toImageVector().takeIf { showIcons },
                    position = position,
                    highlighted = focusedRowId == row.id,
                    enabled = row.enabled,
                    onClick = { onActionClick(row.id) },
                )
            }
        }
        footer?.invoke()
    }
}

private fun RethinkTunnelSettingIcon.toImageVector(): ImageVector = when (this) {
    RethinkTunnelSettingIcon.Settings -> MaterialSymbols.Filled.Settings
    RethinkTunnelSettingIcon.Tune -> MaterialSymbols.Filled.Tune
    RethinkTunnelSettingIcon.NetworkCheck -> MaterialSymbols.Filled.NetworkCheck
    RethinkTunnelSettingIcon.Security -> MaterialSymbols.Filled.Shield
}

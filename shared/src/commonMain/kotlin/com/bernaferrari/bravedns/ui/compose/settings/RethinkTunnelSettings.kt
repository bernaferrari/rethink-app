/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = strings.subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            if (showLockdown) {
                item {
                    RethinkListGroup {
                        RethinkListItem(
                            headline = strings.lockdownDescription,
                            leadingIcon = MaterialSymbols.Filled.Security,
                            position = CardPosition.Single,
                            onClick = onLockdownClick,
                        )
                    }
                }
            }
            item { RethinkTunnelSettingGroup(coreRows, focusedRowId, onActionClick, onToggleChange) }
            item {
                SectionHeader(strings.advanced)
                RethinkTunnelSettingGroup(advancedRows, focusedRowId, onActionClick, onToggleChange)
            }
            item {
                RethinkListGroup {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (focusedRowId == "network_dial_timeout") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding)) {
                            Text(strings.dialTimeout, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun RethinkTunnelSettingGroup(
    rows: List<RethinkTunnelSettingRow>,
    focusedRowId: String?,
    onActionClick: (String) -> Unit,
    onToggleChange: (String, Boolean) -> Unit,
) {
    RethinkListGroup {
        rows.forEachIndexed { index, row ->
            val position = cardPositionFor(index, rows.lastIndex)
            val icon = row.icon.toImageVector()
            if (row.kind == RethinkTunnelSettingKind.Toggle) {
                RethinkListItem(
                    headline = row.title,
                    supporting = row.description,
                    leadingIcon = icon,
                    position = position,
                    highlighted = focusedRowId == row.id,
                    enabled = row.enabled,
                    trailing = { Switch(checked = row.checked, onCheckedChange = { onToggleChange(row.id, it) }, enabled = row.enabled) },
                    onClick = { onToggleChange(row.id, !row.checked) },
                )
            } else {
                RethinkListItem(
                    headline = row.title,
                    supporting = row.description,
                    leadingIcon = icon,
                    position = position,
                    highlighted = focusedRowId == row.id,
                    enabled = row.enabled,
                    onClick = { onActionClick(row.id) },
                )
            }
        }
    }
}

private fun RethinkTunnelSettingIcon.toImageVector(): ImageVector = when (this) {
    RethinkTunnelSettingIcon.Settings -> MaterialSymbols.Filled.Settings
    RethinkTunnelSettingIcon.Tune -> MaterialSymbols.Filled.Tune
    RethinkTunnelSettingIcon.NetworkCheck -> MaterialSymbols.Filled.NetworkCheck
    RethinkTunnelSettingIcon.Security -> MaterialSymbols.Filled.Security
}

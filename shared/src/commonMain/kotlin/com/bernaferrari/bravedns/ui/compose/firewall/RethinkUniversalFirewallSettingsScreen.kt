/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkUniversalFirewallIcon { DeviceLock, Background, Unknown, Udp, Dns, NewApp, Metered, Http, Lockdown }
enum class RethinkUniversalFirewallChange { Applied, RequiresAccessibility }

data class RethinkUniversalFirewallSetting(
    val id: String,
    val label: String,
    val checked: Boolean,
    val icon: RethinkUniversalFirewallIcon,
    val blockedCount: Int = 0,
)

data class RethinkUniversalFirewallStrings(
    val title: String,
    val explanation: String,
    val blocked: @Composable (Int) -> String,
    val loading: String,
    val logs: String,
    val accessibilityTitle: String,
    val accessibilityDescription: String,
    val accessibilityConfirm: String,
    val accessibilityDismiss: String,
)

/** Shared universal-firewall settings list. Hosts own persistence, stats queries, and permissions. */
@Composable
fun RethinkUniversalFirewallSettingsScreen(
    settings: List<RethinkUniversalFirewallSetting>,
    strings: RethinkUniversalFirewallStrings,
    isLoadingStats: Boolean,
    onSettingChange: (String, Boolean) -> RethinkUniversalFirewallChange,
    onLogsClick: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val total = settings.sumOf { it.blockedCount }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = if (isLoadingStats) strings.explanation else strings.blocked(total),
                onBackClick = onBackClick,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
        ) {
            item {
                SectionHeader(strings.title)
                RethinkListGroup {
                    settings.forEachIndexed { index, setting ->
                        val countText = if (isLoadingStats) strings.loading else strings.blocked(setting.blockedCount)
                        RethinkListItem(
                            headline = setting.label,
                            supporting = countText,
                            leadingIcon = setting.icon.imageVector(),
                            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            position = cardPositionFor(index, settings.lastIndex),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isLoadingStats && setting.blockedCount > 0) {
                                        IconButton(
                                            onClick = { onLogsClick(setting.id) },
                                            modifier = Modifier.size(SharedDimensions.touchTargetSm),
                                        ) {
                                            Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, strings.logs, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Switch(
                                        checked = setting.checked,
                                        onCheckedChange = null,
                                    )
                                }
                            },
                            onClick = {
                                if (onSettingChange(setting.id, !setting.checked) == RethinkUniversalFirewallChange.RequiresAccessibility) {
                                    showAccessibilityDialog = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (showAccessibilityDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = strings.accessibilityTitle,
            message = strings.accessibilityDescription,
            confirmText = strings.accessibilityConfirm,
            dismissText = strings.accessibilityDismiss,
            onConfirm = { showAccessibilityDialog = false; onOpenAccessibilitySettings() },
            onDismiss = { showAccessibilityDialog = false },
        )
    }
}

private fun RethinkUniversalFirewallIcon.imageVector(): ImageVector = when (this) {
    RethinkUniversalFirewallIcon.DeviceLock -> MaterialSymbols.Filled.Lock
    RethinkUniversalFirewallIcon.Background -> MaterialSymbols.Filled.Shield
    RethinkUniversalFirewallIcon.Unknown -> MaterialSymbols.Filled.Security
    RethinkUniversalFirewallIcon.Udp -> MaterialSymbols.Filled.Wifi
    RethinkUniversalFirewallIcon.Dns -> MaterialSymbols.Filled.Dns
    RethinkUniversalFirewallIcon.NewApp -> MaterialSymbols.Filled.Block
    RethinkUniversalFirewallIcon.Metered -> MaterialSymbols.Filled.CellTower
    RethinkUniversalFirewallIcon.Http -> MaterialSymbols.Filled.Http
    RethinkUniversalFirewallIcon.Lockdown -> MaterialSymbols.Filled.Lock
}

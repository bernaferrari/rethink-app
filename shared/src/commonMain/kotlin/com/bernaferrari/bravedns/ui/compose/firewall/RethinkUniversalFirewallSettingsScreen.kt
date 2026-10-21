/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSplitSwitch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

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
@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                // The explanation lives once, above the list; the subtitle only carries the total.
                subtitle = if (isLoadingStats) null else strings.blocked(total),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
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
                Text(
                    strings.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs, vertical = SharedDimensions.spacingSm),
                )
            }
            item {
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
                            // Split row: the row opens this rule's blocked-connection logs; the
                            // switch toggles the rule.
                            trailing = {
                                RethinkSplitSwitch(
                                    checked = setting.checked,
                                    contentDescription = setting.label,
                                    onCheckedChange = { checked ->
                                        if (onSettingChange(setting.id, checked) == RethinkUniversalFirewallChange.RequiresAccessibility) {
                                            showAccessibilityDialog = true
                                        }
                                    },
                                )
                            },
                            onClick = { onLogsClick(setting.id) },
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

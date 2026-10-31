/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSplitSwitch
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

enum class RethinkUniversalFirewallIcon { DeviceLock, Background, Unknown, Udp, Dns, NewApp, Metered, Http, Lockdown }
enum class RethinkUniversalFirewallChange { Applied, RequiresAccessibility }

/**
 * One universal firewall toggle. [id] is the rule's `FirewallRuleset` id, which is also the focus
 * key the Connection sheet and Settings search open the Firewall screen with.
 */
data class RethinkUniversalFirewallSetting(
    val id: String,
    val label: String,
    val checked: Boolean,
    val icon: RethinkUniversalFirewallIcon,
    val blockedCount: Int = 0,
    /** One line saying what the rule does to apps. */
    val description: String? = null,
)

/**
 * A universal rule row. Split: the row opens the connections this rule blocked; the switch toggles
 * the rule. The supporting line is the rule's explanation, plus a count only when it blocked any.
 */
@Composable
internal fun RethinkUniversalFirewallRow(
    setting: RethinkUniversalFirewallSetting,
    position: CardPosition,
    highlighted: Boolean,
    blockedText: String?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    RethinkListItem(
        headline = setting.label,
        supporting = listOfNotNull(setting.description, blockedText).joinToString(" · ").ifEmpty { null },
        leadingIcon = setting.icon.imageVector(),
        position = position,
        highlighted = highlighted,
        trailing = {
            RethinkSplitSwitch(
                checked = setting.checked,
                contentDescription = setting.label,
                onCheckedChange = onCheckedChange,
            )
        },
        onClick = onClick,
    )
}

internal fun RethinkUniversalFirewallIcon.imageVector(): ImageVector = when (this) {
    RethinkUniversalFirewallIcon.DeviceLock -> MaterialSymbols.Filled.ScreenLockPortrait
    RethinkUniversalFirewallIcon.Background -> MaterialSymbols.Filled.VisibilityOff
    RethinkUniversalFirewallIcon.Unknown -> MaterialSymbols.AutoMirrored.Filled.HelpOutline
    RethinkUniversalFirewallIcon.Udp -> MaterialSymbols.Filled.SettingsEthernet
    RethinkUniversalFirewallIcon.Dns -> MaterialSymbols.Filled.Dns
    RethinkUniversalFirewallIcon.NewApp -> MaterialSymbols.Filled.NewReleases
    RethinkUniversalFirewallIcon.Metered -> MaterialSymbols.Filled.CellTower
    RethinkUniversalFirewallIcon.Http -> MaterialSymbols.Filled.Http
    RethinkUniversalFirewallIcon.Lockdown -> MaterialSymbols.Filled.Lock
}

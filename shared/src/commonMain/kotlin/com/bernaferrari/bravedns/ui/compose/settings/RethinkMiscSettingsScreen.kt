/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection

enum class RethinkMiscSettingIcon {
    Logs, AutoStart, Tombstone, FirewallBubble, IpInfo, AppUpdates, CrashReports, Downloader,
}

data class RethinkMiscToggle(
    val id: String,
    val title: String,
    val description: String,
    val checked: Boolean,
    val icon: RethinkMiscSettingIcon,
)

data class RethinkMiscSettingsStrings(
    val title: String,
    val backupSection: String,
    val backupTitle: String,
    val backupDescription: String,
    val generalSection: String,
    val aboutSection: String,
    val websiteTitle: String,
    val websiteDescription: String,
    val aboutTitle: String,
    val aboutDescription: String,
)

/** Shared general-settings screen. Hosts retain preference persistence and platform actions only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkMiscSettingsScreen(
    strings: RethinkMiscSettingsStrings,
    toggles: List<RethinkMiscToggle>,
    onToggleChange: (id: String, checked: Boolean) -> Unit,
    onBackupRestore: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenAbout: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    focusedSettingId: String? = null,
    modifier: Modifier = Modifier,
    additionalSettings: (@Composable () -> Unit)? = null,
) {
    val motion = LocalRethinkMotion.current
    val listState = rememberLazyListState()
    // Appearance lives on root Settings; sections here are backup / general / about.
    val focusedSection = when (focusedSettingId) {
        "general_backup", "general_backup_restore" -> 0
        "general_about", "general_website" -> 2
        else -> if (focusedSettingId != null) 1 else null
    }
    LaunchedEffect(focusedSection) {
        focusedSection?.let {
            if (motion.reducedMotion) listState.scrollToItem(it) else listState.animateScrollToItem(it)
        }
    }
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
    ) {
        rethinkSection(strings.backupSection) {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.backupTitle,
                    supporting = strings.backupDescription,
                    leadingIcon = MaterialSymbols.Filled.Backup,
                    position = CardPosition.Single,
                    highlighted = focusedSettingId == "general_backup" || focusedSettingId == "general_backup_restore",
                    // Opens a sheet in place; chevrons are reserved for navigation.
                    onClick = onBackupRestore,
                )
            }
        }
        rethinkSection(strings.generalSection) {
            RethinkListGroup {
                toggles.forEachIndexed { index, toggle ->
                    RethinkMiscToggleRow(
                        toggle = toggle,
                        position = cardPositionFor(index, toggles.lastIndex),
                        highlighted = focusedSettingId == toggle.id,
                        onCheckedChange = { onToggleChange(toggle.id, it) },
                    )
                }
            }
        }
        if (additionalSettings != null) item { additionalSettings() }
        rethinkSection(strings.aboutSection) {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.websiteTitle,
                    supporting = strings.websiteDescription,
                    leadingIcon = MaterialSymbols.Filled.Public,
                    position = CardPosition.First,
                    highlighted = focusedSettingId == "general_website",
                    onClick = onOpenWebsite,
                )
                RethinkListItem(
                    headline = strings.aboutTitle,
                    supporting = strings.aboutDescription,
                    leadingIcon = MaterialSymbols.Filled.Info,
                    position = CardPosition.Last,
                    highlighted = focusedSettingId == "general_about",
                    showTrailingChevron = true,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun RethinkMiscToggleRow(
    toggle: RethinkMiscToggle,
    position: CardPosition,
    highlighted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RethinkSwitchListItem(
        headline = toggle.title,
        checked = toggle.checked,
        onCheckedChange = onCheckedChange,
        supporting = toggle.description,
        leadingIcon = toggle.icon.imageVector(),
        position = position,
        highlighted = highlighted,
    )
}

private fun RethinkMiscSettingIcon.imageVector(): ImageVector = when (this) {
    RethinkMiscSettingIcon.Logs -> MaterialSymbols.Filled.FormatListBulleted
    RethinkMiscSettingIcon.AutoStart -> MaterialSymbols.Filled.PlayArrow
    RethinkMiscSettingIcon.Tombstone -> MaterialSymbols.Filled.DeleteOutline
    RethinkMiscSettingIcon.FirewallBubble -> MaterialSymbols.Filled.ChatBubble
    RethinkMiscSettingIcon.IpInfo -> MaterialSymbols.Filled.Public
    RethinkMiscSettingIcon.AppUpdates -> MaterialSymbols.Filled.SystemUpdate
    RethinkMiscSettingIcon.CrashReports -> MaterialSymbols.Filled.BugReport
    RethinkMiscSettingIcon.Downloader -> MaterialSymbols.Filled.Download
}

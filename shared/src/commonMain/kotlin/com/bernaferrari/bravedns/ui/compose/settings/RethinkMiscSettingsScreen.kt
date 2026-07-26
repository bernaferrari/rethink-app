/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
@Composable
fun RethinkMiscSettingsScreen(
    strings: RethinkMiscSettingsStrings,
    toggles: List<RethinkMiscToggle>,
    onToggleChange: (id: String, checked: Boolean) -> Unit,
    onBackupRestore: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenAbout: () -> Unit,
    appearanceContent: @Composable () -> Unit,
    onBackClick: (() -> Unit)? = null,
    focusedSettingId: String? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusedSection = when (focusedSettingId) {
        "general_appearance", "general_theme_mode", "general_theme_color" -> 0
        "general_backup", "general_backup_restore" -> 1
        "general_about", "general_website" -> 3
        else -> if (focusedSettingId != null) 2 else null
    }
    LaunchedEffect(focusedSection) {
        focusedSection?.let { listState.animateScrollToItem(it) }
    }
    RethinkLazyColumnScreenScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkLargeTopBar(strings.title, onBackClick = onBackClick) },
        listState = listState,
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingSm,
            bottom = SharedDimensions.spacing3xl,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
            item {
                appearanceContent()
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    SectionHeader(strings.backupSection)
                    RethinkListGroup {
                        RethinkListItem(
                            headline = strings.backupTitle,
                            supporting = strings.backupDescription,
                            leadingIcon = MaterialSymbols.Filled.Backup,
                            leadingIconTint = MaterialTheme.colorScheme.secondary,
                            leadingIconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .7f),
                            position = CardPosition.Single,
                            highlighted = focusedSettingId == "general_backup" || focusedSettingId == "general_backup_restore",
                            onClick = onBackupRestore,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    SectionHeader(strings.generalSection)
                    RethinkListGroup {
                        toggles.forEachIndexed { index, toggle ->
                            RethinkMiscToggleRow(
                                toggle = toggle,
                                position = when {
                                    toggles.size == 1 -> CardPosition.Single
                                    index == 0 -> CardPosition.First
                                    index == toggles.lastIndex -> CardPosition.Last
                                    else -> CardPosition.Middle
                                },
                                highlighted = focusedSettingId == toggle.id,
                                onCheckedChange = { onToggleChange(toggle.id, it) },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    SectionHeader(strings.aboutSection)
                    RethinkListGroup {
                        RethinkListItem(
                            headline = strings.websiteTitle,
                            supporting = strings.websiteDescription,
                            leadingIcon = MaterialSymbols.Filled.Public,
                            leadingIconTint = MaterialTheme.colorScheme.tertiary,
                            leadingIconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .7f),
                            position = CardPosition.First,
                            highlighted = focusedSettingId == "general_website",
                            onClick = onOpenWebsite,
                        )
                        RethinkListItem(
                            headline = strings.aboutTitle,
                            supporting = strings.aboutDescription,
                            leadingIcon = MaterialSymbols.Filled.Info,
                            leadingIconTint = MaterialTheme.colorScheme.tertiary,
                            leadingIconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .7f),
                            position = CardPosition.Last,
                            highlighted = focusedSettingId == "general_about",
                            onClick = onOpenAbout,
                        )
                    }
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
    RethinkListItem(
        headline = toggle.title,
        supporting = toggle.description,
        leadingIcon = toggle.icon.imageVector(),
        leadingIconTint = MaterialTheme.colorScheme.primary,
        leadingIconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f),
        position = position,
        highlighted = highlighted,
        trailing = { Switch(checked = toggle.checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!toggle.checked) },
    )
}

private fun RethinkMiscSettingIcon.imageVector(): ImageVector = when (this) {
    RethinkMiscSettingIcon.Logs -> MaterialSymbols.Filled.Subject
    RethinkMiscSettingIcon.AutoStart -> MaterialSymbols.Filled.PlayArrow
    RethinkMiscSettingIcon.Tombstone -> MaterialSymbols.Filled.Delete
    RethinkMiscSettingIcon.FirewallBubble -> MaterialSymbols.Filled.ChatBubble
    RethinkMiscSettingIcon.IpInfo -> MaterialSymbols.Filled.Public
    RethinkMiscSettingIcon.AppUpdates -> MaterialSymbols.Filled.SystemUpdate
    RethinkMiscSettingIcon.CrashReports -> MaterialSymbols.Filled.BugReport
    RethinkMiscSettingIcon.Downloader -> MaterialSymbols.Filled.Settings
}

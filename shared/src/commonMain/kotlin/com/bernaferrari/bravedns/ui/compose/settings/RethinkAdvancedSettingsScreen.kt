/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkAdvancedSettingsStrings(
    val title: String,
    val subtitle: String,
    val experimentalTitle: String,
    val autoDialTitle: String,
    val autoDialDescription: String,
    val panicTitle: String,
    val panicDescription: String,
)

/** Shared debug/advanced toggle screen. Hosts retain feature persistence and debug gating. */
@Composable
fun RethinkAdvancedSettingsScreen(
    strings: RethinkAdvancedSettingsStrings,
    experimentalEnabled: Boolean,
    autoDialEnabled: Boolean,
    panicEnabled: Boolean,
    onExperimentalChange: (Boolean) -> Unit,
    onAutoDialChange: (Boolean) -> Unit,
    onPanicChange: (Boolean) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = strings.subtitle,
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
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item {
                AdvancedToggleRow(strings.experimentalTitle, null, MaterialSymbols.Filled.Build, experimentalEnabled, CardPosition.First, onExperimentalChange)
                AdvancedToggleRow(strings.autoDialTitle, strings.autoDialDescription, MaterialSymbols.Filled.Tune, autoDialEnabled, CardPosition.Middle, onAutoDialChange)
                AdvancedToggleRow(strings.panicTitle, strings.panicDescription, MaterialSymbols.Filled.Warning, panicEnabled, CardPosition.Last, onPanicChange, destructive = true)
            }
        }
    }
}

@Composable
private fun AdvancedToggleRow(
    title: String,
    description: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    position: CardPosition,
    onCheckedChange: (Boolean) -> Unit,
    destructive: Boolean = false,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    RethinkListItem(
        headline = title,
        supporting = description,
        position = position,
        onClick = { onCheckedChange(!checked) },
        leadingContent = { RethinkSharedIconContainer(accent) { Icon(icon, null, tint = accent) } },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

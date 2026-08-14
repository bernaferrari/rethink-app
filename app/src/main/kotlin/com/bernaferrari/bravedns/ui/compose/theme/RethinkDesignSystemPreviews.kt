/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallNetworkToggle
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogDetail
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRowModel
import com.bernaferrari.bravedns.ui.compose.settings.RethinkAppearanceMode
import com.bernaferrari.bravedns.ui.compose.settings.RethinkAppearanceSettingsCard
import com.bernaferrari.bravedns.ui.compose.settings.rethinkAppearancePresets
import com.bernaferrari.bravedns.ui.compose.settings.rethinkAppearanceStrings
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

@Preview(name = "Empty state · light", showBackground = true)
@Preview(name = "Empty state · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RethinkEmptyStatePreview() {
    RethinkTheme {
        RethinkEmptyState(
            icon = MaterialSymbols.Filled.Search,
            title = "No results",
            message = "Try a different name or clear the filter.",
            ctaLabel = "Clear search",
            onCtaClick = {},
        )
    }
}

@Preview(name = "Log row · light", showBackground = true)
@Preview(name = "Log row · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RethinkLogRowPreview() {
    RethinkTheme {
        Column(
            modifier = Modifier.padding(SharedDimensions.spacingMd),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            RethinkLogRow(
                model = RethinkLogRowModel(
                    id = "allowed",
                    destination = "cdn.example.com",
                    appLabel = "Browser",
                    typeLabel = "DoH",
                    timeLabel = "12:04",
                    isBlocked = false,
                    allowedLabel = "Allowed",
                    blockedLabel = "Blocked",
                    details = listOf(RethinkLogDetail("Resolver", "10.0.0.1", monospace = true)),
                    latencyMs = 18,
                ),
                index = 0,
                itemCount = 2,
            )
            RethinkLogRow(
                model = RethinkLogRowModel(
                    id = "blocked",
                    destination = "tracker.ads",
                    appLabel = "Mail",
                    typeLabel = "A",
                    timeLabel = "12:05",
                    isBlocked = true,
                    allowedLabel = "Allowed",
                    blockedLabel = "Blocked",
                    details = listOf(RethinkLogDetail("Rule", "ads", isError = true)),
                ),
                index = 1,
                itemCount = 2,
            )
        }
    }
}

@Preview(name = "Appearance · light", showBackground = true)
@Preview(name = "Appearance · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RethinkAppearanceCardPreview() {
    RethinkTheme {
        RethinkAppearanceSettingsCard(
            selectedMode = RethinkAppearanceMode.System,
            selectedPresetId = 2,
            presets = rethinkAppearancePresets,
            strings = rethinkAppearanceStrings,
            dynamicColor = RethinkColorPreset.CORAL.seedColor ?: Color(0xFFFF5D73),
            dynamicSupported = true,
            onModeSelected = {},
            onPresetSelected = {},
            modifier = Modifier.padding(SharedDimensions.spacingMd),
        )
    }
}

@Preview(name = "Firewall toggles · light", showBackground = true)
@Preview(name = "Firewall toggles · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RethinkFirewallTogglesPreview() {
    RethinkTheme {
        Column(
            modifier = Modifier.padding(SharedDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            RethinkFirewallNetworkToggle(
                blocked = false,
                allowedIcon = MaterialSymbols.Filled.Wifi,
                blockedIcon = MaterialSymbols.Filled.WifiOff,
                contentDescription = "Allow Wi-Fi",
                onClick = {},
            )
            RethinkFirewallNetworkToggle(
                blocked = true,
                allowedIcon = MaterialSymbols.Filled.Wifi,
                blockedIcon = MaterialSymbols.Filled.WifiOff,
                contentDescription = "Block Wi-Fi",
                onClick = {},
            )
        }
    }
}

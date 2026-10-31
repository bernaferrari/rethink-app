/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** When Keepout asks for the screen lock. Mirrors the app's stored `BioMetricType`. */
enum class RethinkAppLockTimeout { Off, EveryTime, FiveMinutes, FifteenMinutes }

data class RethinkAppLockOption(
    val timeout: RethinkAppLockTimeout,
    val title: String,
    val description: String? = null,
)

data class RethinkAppLockSettingsStrings(
    val title: String,
    /** What the lock does, and that notification actions hide while it's on. */
    val explanation: String,
    /** Section header over the options: "Ask for the screen lock". */
    val section: String,
    /** Shown instead of the options when the device has no screen lock to ask for. */
    val unavailable: String,
)

/**
 * Settings › App › App lock: Off · Every time · After 5 minutes · After 15 minutes. The host checks
 * the screen lock before saving anything other than Off ([onSelect] may refuse), so a mis-set lock
 * can't shut the user out.
 */
@Composable
fun RethinkAppLockSettingsScreen(
    selected: RethinkAppLockTimeout,
    options: List<RethinkAppLockOption>,
    strings: RethinkAppLockSettingsStrings,
    available: Boolean,
    onSelect: (RethinkAppLockTimeout) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item {
            Text(
                strings.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs, vertical = SharedDimensions.spacingSm),
            )
        }
        if (!available) {
            item {
                RethinkContainedCard(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) { Text(strings.unavailable, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        rethinkSection(strings.section) {
            RethinkListGroup(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    RethinkRadioListItem(
                        headline = option.title,
                        supporting = option.description,
                        selected = option.timeout == selected,
                        onSelect = { onSelect(option.timeout) },
                        leadingIcon = when (option.timeout) {
                            RethinkAppLockTimeout.Off -> MaterialSymbols.Filled.Block
                            RethinkAppLockTimeout.EveryTime -> MaterialSymbols.Filled.Lock
                            else -> MaterialSymbols.Filled.Timer
                        },
                        position = cardPositionFor(index, options.lastIndex),
                        enabled = available || option.timeout == RethinkAppLockTimeout.Off || option.timeout == selected,
                    )
                }
            }
        }
    }
}

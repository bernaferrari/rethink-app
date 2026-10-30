/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection

data class RethinkAntiCensorshipOption(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
)

data class RethinkAntiCensorshipStrings(
    val title: String,
    val splitHeading: String,
    val splitDescription: String,
    val retryHeading: String,
    val retryDescription: String,
)

/** Shared dial and retry strategy picker; platform adapters persist the selected ids. */
@Composable
fun RethinkAntiCensorshipScreen(
    dialOptions: List<RethinkAntiCensorshipOption>,
    retryOptions: List<RethinkAntiCensorshipOption>,
    selectedDialId: String,
    selectedRetryId: String,
    strings: RethinkAntiCensorshipStrings,
    onDialSelected: (String) -> Unit,
    onRetrySelected: (String) -> Unit,
    onRetryDisabled: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // No "Split: … · Retry: …" subtitle: it truncated at phone widths and only repeated the
    // radio selections visible directly below.
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        rethinkSection(strings.splitHeading, strings.splitDescription) {
            RethinkListGroup(Modifier.selectableGroup()) {
                dialOptions.forEachIndexed { index, option ->
                    RethinkStrategyOption(
                        option = option,
                        selected = selectedDialId == option.id,
                        position = cardPositionFor(index, dialOptions.lastIndex),
                        onClick = { onDialSelected(option.id) },
                    )
                }
            }
        }
        rethinkSection(strings.retryHeading, strings.retryDescription) {
            RethinkListGroup(Modifier.selectableGroup()) {
                retryOptions.forEachIndexed { index, option ->
                    RethinkStrategyOption(
                        option = option,
                        selected = selectedRetryId == option.id,
                        position = cardPositionFor(index, retryOptions.lastIndex),
                        onClick = {
                            if (option.enabled) onRetrySelected(option.id) else onRetryDisabled()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RethinkStrategyOption(
    option: RethinkAntiCensorshipOption,
    selected: Boolean,
    position: CardPosition,
    onClick: () -> Unit,
) {
    RethinkListItem(
        headline = option.title,
        supporting = option.description,
        position = position,
        enabled = option.enabled,
        selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onClick() },
        trailing = { RadioButton(selected = selected, onClick = null, enabled = option.enabled) },
    )
}

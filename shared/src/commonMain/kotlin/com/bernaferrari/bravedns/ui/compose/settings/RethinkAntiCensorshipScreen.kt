/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

data class RethinkAntiCensorshipOption(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
)

data class RethinkAntiCensorshipStrings(
    val title: String,
    val split: String,
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
    val selectedDialLabel = dialOptions.firstOrNull { it.id == selectedDialId }?.title.orEmpty()
    val selectedRetryLabel = retryOptions.firstOrNull { it.id == selectedRetryId }?.title.orEmpty()
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = "${strings.split}: $selectedDialLabel · ${strings.retryHeading}: $selectedRetryLabel",
        onBackClick = onBackClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBarContainerColor = MaterialTheme.colorScheme.surface,
        topBarScrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        item {
            SectionHeaderWithSubtitle(title = strings.splitHeading, subtitle = strings.splitDescription)
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
        item {
            SectionHeaderWithSubtitle(title = strings.retryHeading, subtitle = strings.retryDescription)
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

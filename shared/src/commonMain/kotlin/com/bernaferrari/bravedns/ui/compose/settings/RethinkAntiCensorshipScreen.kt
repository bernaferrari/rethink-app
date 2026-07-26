/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
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
        containerColor = MaterialTheme.colorScheme.background,
        topBarContainerColor = Color.Transparent,
        topBarScrolledContainerColor = Color.Transparent,
    ) {
        item {
            RethinkListGroup {
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
            SectionHeader(strings.retryHeading)
            Text(
                strings.retryDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = SharedDimensions.spacingMd,
                    vertical = SharedDimensions.spacingXs,
                ),
            )
            RethinkListGroup {
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
        contentOffset = Modifier.offset(x = -SharedDimensions.spacingXs),
        onClick = onClick,
        trailing = { RadioButton(selected = selected, onClick = null, enabled = option.enabled) },
    )
}

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection

/** One row of [RethinkSettingsLinksScreen]. [opensScreen] adds the chevron; actions in place don't. */
data class RethinkSettingsLink(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val description: String? = null,
    val opensScreen: Boolean = true,
)

data class RethinkSettingsLinkSection(val title: String, val links: List<RethinkSettingsLink>)

/**
 * A settings screen that is only a list of destinations and one-tap actions, in titled groups
 * (Settings › Advanced › Diagnostics). Empty sections are skipped.
 */
@Composable
fun RethinkSettingsLinksScreen(
    title: String,
    sections: List<RethinkSettingsLinkSection>,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    RethinkTopBarLazyColumnScreen(title = title, onBackClick = onBackClick, modifier = modifier) {
        sections.filter { it.links.isNotEmpty() }.forEach { section ->
            rethinkSection(section.title, key = section.title) {
                RethinkListGroup {
                    section.links.forEachIndexed { index, link ->
                        RethinkListItem(
                            headline = link.title,
                            supporting = link.description,
                            leadingIcon = link.icon,
                            position = cardPositionFor(index, section.links.lastIndex),
                            showTrailingChevron = link.opensScreen,
                            onClick = link.onClick,
                        )
                    }
                }
            }
        }
    }
}

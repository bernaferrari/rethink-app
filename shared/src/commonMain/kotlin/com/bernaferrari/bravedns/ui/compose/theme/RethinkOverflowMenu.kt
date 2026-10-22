/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** One entry of a [RethinkOverflowMenu]. */
@Immutable
data class RethinkOverflowItem(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The top-bar "more" menu. Secondary and destructive screen actions (refresh, clear) live here
 * on every list screen, so the bar itself only carries the screen's primary controls.
 * Renders nothing when [items] is empty.
 */
@Composable
fun RethinkOverflowMenu(moreDescription: String, items: List<RethinkOverflowItem>) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    // Trigger and popup share one Box so Compose Web anchors the menu to the button, not the
    // whole action row.
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(MaterialSymbols.Filled.MoreVert, moreDescription)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = item.icon?.let { icon -> { Icon(icon, null) } },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

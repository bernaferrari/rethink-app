package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** A sort icon with its menu: the old "Sort apps" text button was the widest thing in the bar. */
@Composable
internal fun RethinkAppSortMenu(title: String, labels: List<String>, sorting: RethinkAppSorting) {
    if (labels.size != RethinkAppSort.entries.size) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MaterialSymbols.Filled.Sort, title) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RethinkAppSort.entries.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = sorting.order == option },
                    text = { Text(labels[option.ordinal]) },
                    leadingIcon = { if (sorting.order == option) Icon(MaterialSymbols.Filled.Check, null) },
                    onClick = { sorting.onChange(option); open = false },
                )
            }
        }
    }
}

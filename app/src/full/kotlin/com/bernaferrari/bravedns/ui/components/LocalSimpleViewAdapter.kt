/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMap
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistGroup
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistPack
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistPackRow
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistRowStrings
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState

/** Android data/resource adapter for the common blocklist pack row. */
@Composable
fun LocalSimpleBlocklistRow(map: LocalBlocklistPacksMap, showHeader: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val group = map.toRethinkGroup(context)
    RethinkBlocklistPackRow(
        pack = RethinkBlocklistPack("${map.pack}:${map.level}", group, map.pack, map.blocklistIds.size, RethinkBlocklistState.getSelectedFileTags().containsAll(map.blocklistIds), map.blocklistIds.toSet()),
        showGroupHeader = showHeader,
        strings = RethinkBlocklistRowStrings(
            blocklistCount = { context.getString(R.string.rsv_blocklist_count_text, it.toString()) },
            entries = { context.getString(R.string.dc_entries, it.toString()) },
        ),
        onToggle = onToggle,
    )
}

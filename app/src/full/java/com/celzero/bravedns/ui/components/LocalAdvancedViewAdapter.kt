/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.celzero.bravedns.R
import com.celzero.bravedns.database.RethinkLocalFileTag
import com.celzero.bravedns.ui.compose.dns.RethinkBlocklistFileTag
import com.celzero.bravedns.ui.compose.dns.RethinkBlocklistFileTagRow
import com.celzero.bravedns.ui.compose.dns.RethinkBlocklistRowStrings
import com.celzero.bravedns.util.UIUtils.openUrl

/** Android data/resource/link adapter for the common granular blocklist row. */
@Composable
fun LocalAdvancedBlocklistRow(filetag: RethinkLocalFileTag, showHeader: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    RethinkBlocklistFileTagRow(
        tag = RethinkBlocklistFileTag(filetag.value.toString(), filetag.toRethinkGroup(context), filetag.subg, filetag.vname, filetag.entries, filetag.level?.firstOrNull(), filetag.url.firstOrNull(), filetag.isSelected, setOf(filetag.value)),
        showGroupHeader = showHeader,
        strings = RethinkBlocklistRowStrings({ context.getString(R.string.rsv_blocklist_count_text, it.toString()) }, { context.getString(R.string.dc_entries, it.toString()) }),
        onToggle = onToggle,
        onOpenUrl = { openUrl(context, it) },
    )
}

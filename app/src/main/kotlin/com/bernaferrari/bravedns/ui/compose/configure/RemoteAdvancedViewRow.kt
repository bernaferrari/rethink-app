/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.configure

import com.bernaferrari.bravedns.ui.compose.dns.toRethinkGroup

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.RethinkRemoteFileTag
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistFileTag
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistFileTagRow
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistRowStrings
import com.bernaferrari.bravedns.util.UIUtils.openUrl

/** Android data/resource/link adapter for the common granular blocklist row. */
@Composable
fun RemoteAdvancedBlocklistRow(filetag: RethinkRemoteFileTag, showHeader: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val blocklistCountText = stringResource(R.string.rsv_blocklist_count_text)
    val entriesText = stringResource(R.string.dc_entries)
    RethinkBlocklistFileTagRow(
        tag = RethinkBlocklistFileTag(filetag.value.toString(), filetag.toRethinkGroup(context), filetag.subg, filetag.vname, filetag.entries, filetag.level?.firstOrNull(), filetag.url.firstOrNull(), filetag.isSelected, setOf(filetag.value)),
        showGroupHeader = showHeader,
        strings = RethinkBlocklistRowStrings({ blocklistCountText.format(it.toString()) }, { entriesText.format(it.toString()) }),
        onToggle = onToggle,
        onOpenUrl = { openUrl(context, it) },
    )
}

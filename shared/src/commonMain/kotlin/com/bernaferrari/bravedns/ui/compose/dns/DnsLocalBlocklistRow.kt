@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

@Composable
internal fun DnsLocalBlocklistRow(
    state: RethinkDnsSettingsState, strings: RethinkDnsSettingsStrings,
    highlighted: Boolean, onClick: () -> Unit,
) {
    RethinkListItem(
        headline = strings.localBlocklists,
        supporting = if (state.blocklistEnabled) strings.localBlocklistsDescription(state.numberOfLocalBlocklists) else strings.localBlocklistsDisabledDescription,
        leadingIcon = KeepoutIcons.Blocklists,
        position = CardPosition.First,
        highlighted = highlighted,
        trailing = { RethinkDnsStatusText(if (state.blocklistEnabled) strings.enabled else strings.disabled, state.blocklistEnabled) },
        onClick = onClick,
    )
}

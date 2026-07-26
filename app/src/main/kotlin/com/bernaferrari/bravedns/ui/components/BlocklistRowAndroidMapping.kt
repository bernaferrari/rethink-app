/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.components

import android.content.Context
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMap
import com.bernaferrari.bravedns.database.RemoteBlocklistPacksMap
import com.bernaferrari.bravedns.database.RethinkLocalFileTag
import com.bernaferrari.bravedns.database.RethinkRemoteFileTag
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.ui.compose.dns.RethinkBlocklistGroup

private fun rethinkGroup(context: Context, id: String) = RethinkBlocklistGroup(
    id = id,
    title = RethinkBlocklistManager.getGroupName(context, id),
    description = RethinkBlocklistManager.getTitleDesc(context, id),
)

internal fun LocalBlocklistPacksMap.toRethinkGroup(context: Context) = rethinkGroup(context, group)
internal fun RemoteBlocklistPacksMap.toRethinkGroup(context: Context) = rethinkGroup(context, group)
internal fun RethinkLocalFileTag.toRethinkGroup(context: Context) = rethinkGroup(context, group)
internal fun RethinkRemoteFileTag.toRethinkGroup(context: Context) = rethinkGroup(context, group)

internal fun RethinkBlocklistManager.getGroupName(context: Context, group: String): String = when {
    group.equals(RethinkBlocklistManager.PARENTAL_CONTROL.name, true) -> context.getString(RethinkBlocklistManager.PARENTAL_CONTROL.label)
    group.equals(RethinkBlocklistManager.SECURITY.name, true) -> context.getString(RethinkBlocklistManager.SECURITY.label)
    group.equals(RethinkBlocklistManager.PRIVACY.name, true) -> context.getString(RethinkBlocklistManager.PRIVACY.label)
    else -> group
}

internal fun RethinkBlocklistManager.getTitleDesc(context: Context, group: String): String = when {
    group.equals(RethinkBlocklistManager.PARENTAL_CONTROL.name, true) -> context.getString(RethinkBlocklistManager.PARENTAL_CONTROL.desc)
    group.equals(RethinkBlocklistManager.SECURITY.name, true) -> context.getString(RethinkBlocklistManager.SECURITY.desc)
    group.equals(RethinkBlocklistManager.PRIVACY.name, true) -> context.getString(RethinkBlocklistManager.PRIVACY.desc)
    else -> ""
}

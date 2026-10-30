package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable

fun orderFirewallApps(apps: List<RethinkFirewallApp>, sortOrder: RethinkAppSort): List<RethinkFirewallApp> =
    when (sortOrder) {
        RethinkAppSort.Name -> apps.sortedWith(compareBy(
            { it.appName.ifBlank { it.packageName }.trim().lowercase() }, { it.packageName.lowercase() }, { it.id }))
        RethinkAppSort.Package -> apps.sortedWith(compareBy({ it.packageName.lowercase() }, { it.id }))
        RethinkAppSort.Uid -> apps.sortedWith(compareBy({ it.uid }, { it.packageName.lowercase() }, { it.id }))
    }

@Composable
internal fun firewallAppWithSortIdentity(
    app: RethinkFirewallApp,
    order: RethinkAppSort,
    strings: RethinkFirewallAppListStrings,
): RethinkFirewallApp = app.copy(technicalIdentity = when (order) {
    RethinkAppSort.Name -> null
    RethinkAppSort.Package -> app.packageName
    RethinkAppSort.Uid -> strings.uidLabel(app.uid)
})

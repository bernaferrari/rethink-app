package com.bernaferrari.bravedns.ui.compose.firewall

fun orderFirewallApps(apps: List<RethinkFirewallApp>, sortOrder: RethinkAppSort): List<RethinkFirewallApp> =
    when (sortOrder) {
        RethinkAppSort.Name -> apps.sortedWith(compareBy(
            { it.appName.ifBlank { it.packageName }.trim().lowercase() }, { it.packageName.lowercase() }, { it.id }))
        RethinkAppSort.Package -> apps.sortedWith(compareBy({ it.packageName.lowercase() }, { it.id }))
        RethinkAppSort.Uid -> apps.sortedWith(compareBy({ it.uid }, { it.packageName.lowercase() }, { it.id }))
    }

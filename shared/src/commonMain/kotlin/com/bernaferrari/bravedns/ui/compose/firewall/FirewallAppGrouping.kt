package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.compose.theme.CardPosition

internal data class FirewallAppGrouping(val initial: String, val startsSection: Boolean, val position: CardPosition)

/** Group name-sorted rows per letter; other sort orders form one connected list without headers. */
internal fun firewallAppGrouping(initials: List<String>, index: Int, alphabetical: Boolean): FirewallAppGrouping {
    val initial = initials[index]
    val previous = initials.getOrNull(index - 1)
    val next = initials.getOrNull(index + 1)
    val position = when {
        previous != initial && next != initial -> CardPosition.Single
        previous != initial -> CardPosition.First
        next != initial -> CardPosition.Last
        else -> CardPosition.Middle
    }
    return FirewallAppGrouping(initial, alphabetical && (index == 0 || initial != previous), position)
}

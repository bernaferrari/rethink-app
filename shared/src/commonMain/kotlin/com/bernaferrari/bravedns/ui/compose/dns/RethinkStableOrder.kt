/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * [keys] in the order they first appeared while this screen is open (see [stableOrder]). Sources
 * that sort the selected item first (the endpoint tables do) can't move a row the user just
 * picked; it sorts first the next time the screen opens.
 */
@Composable
fun <K> rememberStableOrder(keys: List<K>): List<K> {
    val holder = remember { StableOrderHolder<K>() }
    val order = stableOrder(holder.order, keys)
    SideEffect { holder.order = order }
    return order
}

private class StableOrderHolder<K> {
    var order: List<K> = emptyList()
}

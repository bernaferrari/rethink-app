/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.util

class UidMultimap<V> {
    private val map = mutableMapOf<Int, MutableList<V>>()

    fun put(key: Int, value: V) {
        map.getOrPut(key) { mutableListOf() }.add(value)
    }

    operator fun get(key: Int): Collection<V> = map[key] ?: emptyList()

    fun values(): Collection<V> = map.values.flatten()

    fun remove(key: Int, value: V): Boolean {
        val list = map[key] ?: return false
        val removed = list.remove(value)
        if (list.isEmpty()) map.remove(key)
        return removed
    }

    fun clear() {
        map.clear()
    }

    fun isEmpty(): Boolean = map.isEmpty()

    fun keySet(): Set<Int> = map.keys
}

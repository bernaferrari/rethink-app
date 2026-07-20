/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.util

class ValueMultimap<K, V> {
    private val map = mutableMapOf<K, MutableList<V>>()

    fun put(key: K, value: V) {
        map.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun get(key: K): Collection<V> = map[key] ?: emptyList()

    fun keySet(): Set<K> = map.keys

    fun clear() {
        map.clear()
    }

    fun isEmpty(): Boolean = map.isEmpty()
}

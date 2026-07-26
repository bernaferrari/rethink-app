/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.util

import com.bernaferrari.bravedns.platform.currentTimeMillis

class ExpiringCache<K, V>(
    private val maxSize: Long,
    private val expireAfterWriteMs: Long,
    private val onRemoval: ((key: K, value: V, cause: RemovalCause) -> Unit)? = null,
) {
    enum class RemovalCause {
        EXPIRED,
        SIZE,
        MANUAL,
        REPLACED,
    }

    private data class Entry<V>(val value: V, val expiresAtMs: Long)

    private val map = LinkedHashMap<K, Entry<V>>()

    fun getIfPresent(key: K): V? {
        purgeExpired()
        val entry = map[key] ?: return null
        if (entry.expiresAtMs <= currentTimeMillis()) {
            removeEntry(key, RemovalCause.EXPIRED)
            return null
        }
        // refresh LRU order
        map.remove(key)
        map[key] = entry
        return entry.value
    }

    fun put(key: K, value: V) {
        purgeExpired()
        map[key]?.let { onRemoval?.invoke(key, it.value, RemovalCause.REPLACED) }
        map.remove(key)
        map[key] = Entry(value, currentTimeMillis() + expireAfterWriteMs)
        trimToMaxSize()
    }

    fun invalidate(key: K) {
        removeEntry(key, RemovalCause.MANUAL)
    }

    fun invalidateAll() {
        map.keys.toList().forEach { removeEntry(it, RemovalCause.MANUAL) }
    }

    fun size(): Int = map.size

    private fun trimToMaxSize() {
        while (map.size > maxSize) {
            val eldest = map.entries.firstOrNull() ?: break
            removeEntry(eldest.key, RemovalCause.SIZE)
        }
    }

    private fun purgeExpired() {
        val now = currentTimeMillis()
        map.entries
            .filter { it.value.expiresAtMs <= now }
            .map { it.key }
            .forEach { removeEntry(it, RemovalCause.EXPIRED) }
    }

    private fun removeEntry(key: K, cause: RemovalCause) {
        val entry = map.remove(key) ?: return
        onRemoval?.invoke(key, entry.value, cause)
    }
}

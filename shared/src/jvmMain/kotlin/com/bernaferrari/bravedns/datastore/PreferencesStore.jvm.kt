/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.datastore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

actual fun createPreferencesStore(platformContext: Any?): PreferencesStore = InMemoryPreferencesStore()

/** In-memory preferences for JVM tests/tooling. */
class InMemoryPreferencesStore : PreferencesStore {
    private val cache = mutableMapOf<String, Any?>()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = _changes.asSharedFlow()

    override fun boolean(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            (cache[key] as? Boolean) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            cache[key] = value
        }
    }

    override fun int(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            (cache[key] as? Int) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            cache[key] = value
        }
    }

    override fun long(key: String, default: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long =
            (cache[key] as? Long) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            cache[key] = value
        }
    }

    override fun string(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            (cache[key] as? String) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            cache[key] = value
        }
    }

    override fun snapshot(): Map<String, Any?> = cache.toMap()
    override fun restore(snapshot: Map<String, Any?>) {
        cache.clear()
        cache.putAll(snapshot)
    }
}

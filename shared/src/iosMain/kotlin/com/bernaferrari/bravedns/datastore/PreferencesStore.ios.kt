/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.datastore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSUserDefaults
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

actual fun createPreferencesStore(platformContext: Any?): PreferencesStore = IosUserDefaultsPreferencesStore()

/** iOS preferences backed by [NSUserDefaults]. */
class IosUserDefaultsPreferencesStore : PreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = _changes.asSharedFlow()

    override fun boolean(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            if (defaults.objectForKey(key) == null) return default
            return defaults.boolForKey(key)
        }
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            defaults.setBool(value, key)
        }
    }

    override fun int(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            if (defaults.objectForKey(key) == null) return default
            return defaults.integerForKey(key).toInt()
        }
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            defaults.setInteger(value.toLong(), key)
        }
    }

    override fun long(key: String, default: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long {
            if (defaults.objectForKey(key) == null) return default
            return defaults.integerForKey(key)
        }
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            defaults.setInteger(value, key)
        }
    }

    override fun string(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            defaults.stringForKey(key) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            defaults.setObject(value, key)
        }
    }

    override fun snapshot(): Map<String, Any?> {
        val dict = defaults.dictionaryRepresentation()
        val out = mutableMapOf<String, Any?>()
        for ((k, v) in dict) {
            out[k as String] = v
        }
        return out
    }

    override fun restore(snapshot: Map<String, Any?>) {
        snapshot.forEach { (key, value) ->
            when (value) {
                is Boolean -> defaults.setBool(value, key)
                is Int -> defaults.setInteger(value.toLong(), key)
                is Long -> defaults.setInteger(value, key)
                is String -> defaults.setObject(value, key)
            }
        }
    }
}

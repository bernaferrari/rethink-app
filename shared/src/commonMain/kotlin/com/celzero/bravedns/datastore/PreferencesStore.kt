/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.datastore

import kotlinx.coroutines.flow.SharedFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Multiplatform preferences façade.
 * Implementations: Android/JVM/iOS via expect factory [createPreferencesStore].
 */
interface PreferencesStore {
    fun boolean(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean>
    fun int(key: String, default: Int): ReadWriteProperty<Any?, Int>
    fun long(key: String, default: Long): ReadWriteProperty<Any?, Long>
    fun string(key: String, default: String): ReadWriteProperty<Any?, String>
    fun snapshot(): Map<String, Any?>
    fun restore(snapshot: Map<String, Any?>)
    val preferenceKeyChanges: SharedFlow<String>
}

/**
 * Platform factory. On Android pass a [PlatformContext] (Context); elsewhere pass null/path.
 */
expect fun createPreferencesStore(platformContext: Any?): PreferencesStore

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.datastore

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import kotlin.properties.ReadWriteProperty

/**
 * Android-facing wrapper kept for call-site compatibility.
 * Delegates to KMP [PreferencesStore] from the :shared module.
 */
class SyncPreferencesStore(context: Context) {
    private val delegate: PreferencesStore = createPreferencesStore(context)

    val preferenceKeyChanges: SharedFlow<String> = delegate.preferenceKeyChanges

    fun boolean(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean> =
        delegate.boolean(key, default)

    fun int(key: String, default: Int): ReadWriteProperty<Any?, Int> =
        delegate.int(key, default)

    fun long(key: String, default: Long): ReadWriteProperty<Any?, Long> =
        delegate.long(key, default)

    fun string(key: String, default: String): ReadWriteProperty<Any?, String> =
        delegate.string(key, default)

    fun snapshot(): Map<String, Any?> = delegate.snapshot()

    fun restore(snapshot: Map<String, Any?>) = delegate.restore(snapshot)
}

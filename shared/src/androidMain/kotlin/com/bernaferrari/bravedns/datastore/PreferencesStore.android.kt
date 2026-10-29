/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

actual fun createPreferencesStore(platformContext: Any?): PreferencesStore {
    val ctx = platformContext as? Context
        ?: error("Android PreferencesStore requires android.content.Context")
    return AndroidPreferencesStore(ctx.applicationContext)
}

class AndroidPreferencesStore(context: Context) : PreferencesStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, PreferencesKeys.LEGACY_PREFS_NAME)),
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(PreferencesKeys.DATASTORE_NAME) },
        )

    private val cache = ConcurrentHashMap<String, Any>()
    private val loaded = CompletableDeferred<Unit>()
    private val _preferenceKeyChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = _preferenceKeyChanges.asSharedFlow()

    // Read the store in the background: the process can keep starting while the file (and, once,
    // the legacy SharedPreferences migration) is read. Only a caller that needs a value before the
    // load completes waits, and it waits for the real value, never a default.
    init {
        scope.launch {
            try {
                dataStore.data.first().asMap().forEach { (key, value) ->
                    cache.putIfAbsent(key.name, value)
                }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /** Suspends until the stored preferences are in memory. */
    suspend fun awaitLoaded() = loaded.await()

    private fun ensureLoaded() {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
    }

    override fun boolean(key: String, default: Boolean) = BooleanProperty(key, default)
    override fun int(key: String, default: Int) = IntProperty(key, default)
    override fun long(key: String, default: Long) = LongProperty(key, default)
    override fun string(key: String, default: String) = StringProperty(key, default)
    override fun snapshot(): Map<String, Any?> {
        ensureLoaded()
        return cache.toMap()
    }

    override fun restore(snapshot: Map<String, Any?>) {
        snapshot.forEach { (key, value) ->
            when (value) {
                is Boolean -> setBoolean(key, value)
                is Int -> setInt(key, value)
                is Long -> setLong(key, value)
                is String -> setString(key, value)
            }
        }
    }

    override suspend fun restoreAndAwait(snapshot: Map<String, Any?>) {
        loaded.await()
        // Finish writes queued before the restore, so they cannot overwrite the replacement.
        scope.coroutineContext[Job]?.children?.toList()?.joinAll()
        val values = snapshot.filterValues { it is Boolean || it is Int || it is Long || it is String }
        dataStore.edit { preferences ->
            preferences.clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> preferences[booleanPreferencesKey(key)] = value
                    is Int -> preferences[intPreferencesKey(key)] = value
                    is Long -> preferences[longPreferencesKey(key)] = value
                    is String -> preferences[stringPreferencesKey(key)] = value
                }
            }
        }
        val changedKeys = cache.keys.toSet() + values.keys
        cache.clear()
        values.forEach { (key, value) -> if (value != null) cache[key] = value }
        changedKeys.forEach { _preferenceKeyChanges.emit(it) }
    }

    private fun setBoolean(key: String, value: Boolean) {
        ensureLoaded()
        cache[key] = value
        scope.launch {
            dataStore.edit { it[booleanPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setInt(key: String, value: Int) {
        ensureLoaded()
        cache[key] = value
        scope.launch {
            dataStore.edit { it[intPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setLong(key: String, value: Long) {
        ensureLoaded()
        cache[key] = value
        scope.launch {
            dataStore.edit { it[longPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setString(key: String, value: String) {
        ensureLoaded()
        cache[key] = value
        scope.launch {
            dataStore.edit { it[stringPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun read(key: String): Any? {
        ensureLoaded()
        return cache[key]
    }

    inner class BooleanProperty(private val key: String, private val default: Boolean) :
        ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            (read(key) as? Boolean) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            setBoolean(key, value)
    }

    inner class IntProperty(private val key: String, private val default: Int) :
        ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            (read(key) as? Int) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            setInt(key, value)
    }

    inner class LongProperty(private val key: String, private val default: Long) :
        ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long =
            (read(key) as? Long) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) =
            setLong(key, value)
    }

    inner class StringProperty(private val key: String, private val default: String) :
        ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            (read(key) as? String) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            setString(key, value)
    }
}

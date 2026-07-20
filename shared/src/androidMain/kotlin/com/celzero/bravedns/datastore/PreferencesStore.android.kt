/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.datastore

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private val cache = mutableMapOf<String, Any?>()
    private val _preferenceKeyChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = _preferenceKeyChanges.asSharedFlow()

    init {
        runBlocking(Dispatchers.IO) {
            dataStore.data.first().asMap().forEach { (key, value) ->
                cache[key.name] = value
            }
        }
    }

    override fun boolean(key: String, default: Boolean) = BooleanProperty(key, default)
    override fun int(key: String, default: Int) = IntProperty(key, default)
    override fun long(key: String, default: Long) = LongProperty(key, default)
    override fun string(key: String, default: String) = StringProperty(key, default)
    override fun snapshot(): Map<String, Any?> = cache.toMap()

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

    private fun setBoolean(key: String, value: Boolean) {
        cache[key] = value
        scope.launch {
            dataStore.edit { it[booleanPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setInt(key: String, value: Int) {
        cache[key] = value
        scope.launch {
            dataStore.edit { it[intPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setLong(key: String, value: Long) {
        cache[key] = value
        scope.launch {
            dataStore.edit { it[longPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    private fun setString(key: String, value: String) {
        cache[key] = value
        scope.launch {
            dataStore.edit { it[stringPreferencesKey(key)] = value }
            _preferenceKeyChanges.emit(key)
        }
    }

    inner class BooleanProperty(private val key: String, private val default: Boolean) :
        ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            (cache[key] as? Boolean) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            setBoolean(key, value)
    }

    inner class IntProperty(private val key: String, private val default: Int) :
        ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            (cache[key] as? Int) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            setInt(key, value)
    }

    inner class LongProperty(private val key: String, private val default: Long) :
        ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long =
            (cache[key] as? Long) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) =
            setLong(key, value)
    }

    inner class StringProperty(private val key: String, private val default: String) :
        ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            (cache[key] as? String) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            setString(key, value)
    }
}

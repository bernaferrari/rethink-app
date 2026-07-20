package com.celzero.bravedns.datastore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val memory = mutableMapOf<String, Any?>()

actual fun createPreferencesStore(platformContext: Any?): PreferencesStore = JsMemoryPreferencesStore()

class JsMemoryPreferencesStore : PreferencesStore {
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = _changes.asSharedFlow()

    override fun boolean(key: String, default: Boolean) = prop(key, default) { it as? Boolean ?: default }
    override fun int(key: String, default: Int) = prop(key, default) { (it as? Number)?.toInt() ?: default }
    override fun long(key: String, default: Long) = prop(key, default) { (it as? Number)?.toLong() ?: default }
    override fun string(key: String, default: String) = prop(key, default) { it as? String ?: default }

    private fun <T> prop(key: String, default: T, read: (Any?) -> T): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T =
                if (memory.containsKey(key)) read(memory[key]) else default
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                memory[key] = value
                _changes.tryEmit(key)
            }
        }

    override fun snapshot(): Map<String, Any?> = memory.toMap()
    override fun restore(snapshot: Map<String, Any?>) {
        memory.clear(); memory.putAll(snapshot)
    }
}

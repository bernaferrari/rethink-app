package com.celzero.bravedns.datastore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val memory = mutableMapOf<String, Any?>()

/** Browser demo preferences deliberately stay local to the WASM session. */
actual fun createPreferencesStore(platformContext: Any?): PreferencesStore = WasmMemoryPreferencesStore()

private class WasmMemoryPreferencesStore : PreferencesStore {
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val preferenceKeyChanges: SharedFlow<String> = changes.asSharedFlow()

    override fun boolean(key: String, default: Boolean) = property(key, default) { it as? Boolean ?: default }
    override fun int(key: String, default: Int) = property(key, default) { (it as? Number)?.toInt() ?: default }
    override fun long(key: String, default: Long) = property(key, default) { (it as? Number)?.toLong() ?: default }
    override fun string(key: String, default: String) = property(key, default) { it as? String ?: default }

    private fun <T> property(
        key: String,
        default: T,
        read: (Any?) -> T,
    ): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T =
                if (memory.containsKey(key)) read(memory[key]) else default

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                memory[key] = value
                changes.tryEmit(key)
            }
        }

    override fun snapshot(): Map<String, Any?> = memory.toMap()

    override fun restore(snapshot: Map<String, Any?>) {
        memory.clear()
        memory.putAll(snapshot)
    }
}

package com.bernaferrari.bravedns.rpnproxy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface WinRegistrationMaterialResult
internal data class WinRegistrationMaterial(
    val entitlement: ByteArray, val deviceId: String, val serverVerified: Boolean,
) : WinRegistrationMaterialResult
internal data class WinRegistrationExpired(val purchaseToken: String) : WinRegistrationMaterialResult

internal interface WinRegistrationBackend {
    suspend fun isRegistered(): Boolean
    suspend fun isStale(): Boolean
    suspend fun material(forceServer: Boolean): WinRegistrationMaterialResult?
    suspend fun savedState(): ByteArray?
    suspend fun unregister(): Boolean
    suspend fun register(material: WinRegistrationMaterial, state: ByteArray?): ByteArray?
    suspend fun persistState(state: ByteArray): Boolean
    suspend fun expired(purchaseToken: String)
}

/** One owner serializes registration and never replaces a working tunnel speculatively. */
internal class WinRegistrationCoordinator(
    private val backend: WinRegistrationBackend,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun register(forceRefresh: Boolean = false): Boolean {
        var expiredToken: String? = null
        val result = mutex.withLock {
            val registered = backend.isRegistered()
            val stale = registered && backend.isStale()
            if (registered && !stale && !forceRefresh) return@withLock true
            val material = backend.material(forceServer = stale || forceRefresh) ?: return@withLock false
            if (material is WinRegistrationExpired) {
                expiredToken = material.purchaseToken
                return@withLock false
            }
            replaceRegistration(registered, material as WinRegistrationMaterial)
        }
        // State-machine publication may acquire stateLock: never do it while holding
        // registration mutex, since activation can acquire these locks in reverse order.
        expiredToken?.let { backend.expired(it) }
        return result
    }

    private suspend fun replaceRegistration(registered: Boolean, material: WinRegistrationMaterial): Boolean {
        if (registered && !material.serverVerified) return false
        if (registered && !backend.unregister()) return false
        val saved = if (material.serverVerified) null else backend.savedState()
        val state = backend.register(material, saved) ?: return false
        if (!backend.isRegistered() || backend.isStale()) return false
        return backend.persistState(state)
    }
}

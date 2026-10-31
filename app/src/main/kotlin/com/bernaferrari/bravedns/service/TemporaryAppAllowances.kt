package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.database.AppInfoRepository
import com.bernaferrari.bravedns.util.ExpiringCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Serializes allowance reads/edits so a delayed lookup cannot restore a revoked cache entry. */
internal class TemporaryAppAllowances(
    private val repository: AppInfoRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val closeConnections: (Int) -> Unit,
) {
    private val mutex = Mutex()
    // Zero is a cached denial. Normal connections must not query Room on every Go callback.
    private val cache = ExpiringCache<Int, Long>(10_000, TimeUnit.HOURS.toMillis(1))

    suspend fun isAllowed(uid: Int): Boolean = expiry(uid) > 0L

    /** The stored expiry while the allowance is live, else 0. UI countdowns read this. */
    suspend fun expiry(uid: Int): Long = mutex.withLock {
        val timestamp = now()
        val expiry = cache.getIfPresent(uid) ?: repository.getAppInfoByUid(uid).let { app ->
            (if (app?.tempAllowEnabled == true) app.tempAllowExpiryTime else 0L)
                .also { cache.put(uid, it) }
        }
        if (expiry > timestamp) expiry else 0L
    }

    suspend fun grant(uid: Int, minutes: Int) = mutex.withLock {
        require(minutes > 0)
        val expiry = now() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        repository.updateTempAllowByUid(uid, true, expiry)
        cache.put(uid, expiry)
    }

    suspend fun revoke(uid: Int) = mutex.withLock {
        repository.clearTempAllowByUid(uid)
        cache.put(uid, 0L)
        closeConnections(uid)
    }

    suspend fun expire(timestamp: Long): Int = mutex.withLock {
        var cleared = 0
        repository.getTempAllowedApps()
            .filter { it.tempAllowExpiryTime <= timestamp }
            .distinctBy { it.uid }
            .forEach { app ->
                val count = repository.clearTempAllowByUidIfExpiry(app.uid, app.tempAllowExpiryTime)
                if (count > 0) {
                    cleared += count
                    cache.put(app.uid, 0L)
                    closeConnections(app.uid)
                }
            }
        cleared
    }

    suspend fun invalidate() = mutex.withLock { cache.invalidateAll() }
}

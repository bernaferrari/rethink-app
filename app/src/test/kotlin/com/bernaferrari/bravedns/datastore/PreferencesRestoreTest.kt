package com.bernaferrari.bravedns.datastore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class PreferencesRestoreTest {
    @Test
    fun `awaited restore survives reopening and removes stale settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = AndroidPreferencesStore(context)
        var stale by store.string("stale", "")
        stale = "old"
        val expected = mapOf("enabled" to true, "count" to 7, "time" to 42L, "name" to "restored")
        store.restoreAndAwait(expected)
        close(store)
        val reopened = AndroidPreferencesStore(context)
        try {
            reopened.awaitLoaded()
            assertEquals(expected, reopened.snapshot())
        } finally {
            close(reopened)
        }
    }

    private suspend fun close(store: AndroidPreferencesStore) {
        val field = AndroidPreferencesStore::class.java.getDeclaredField("scope").apply { isAccessible = true }
        val scope = field.get(store) as CoroutineScope
        val job = scope.coroutineContext[Job]!!
        scope.cancel()
        job.join()
    }
}

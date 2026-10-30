package com.bernaferrari.bravedns.net.go

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProxyRecoveryCoordinatorTest {
    @Test fun missingProxyIsInstalledOnceAcrossDuplicateRequests() = runTest {
        val owner = ProxyRecoveryCoordinator()
        val token = owner.invalidate()
        var present = false
        var additions = 0
        suspend fun recover() = owner.recover(token, { true }, { present }, { present = true; additions++ }, { present = false })
        val first = async { recover() }; val second = async { recover() }
        first.await(); second.await()
        assertEquals(1, additions)
    }
    @Test fun disabledAndReplacedOwnersCannotBeResurrected() = runTest {
        val owner = ProxyRecoveryCoordinator()
        val token = owner.invalidate()
        var additions = 0
        owner.recover(token, { false }, { false }, { additions++ }, {})
        owner.invalidate()
        owner.recover(token, { true }, { false }, { additions++ }, {})
        owner.close {}
        owner.recover(owner.invalidate(), { true }, { false }, { additions++ }, {})
        assertEquals(0, additions)
    }
    @Test fun disableWhileInstallingDiscardsRecoveryBeforeQueuedUserMutation() = runTest {
        val owner = ProxyRecoveryCoordinator()
        val token = owner.invalidate()
        var wanted = true
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val actions = mutableListOf<String>()
        val recovery = async { owner.recover(token, { wanted }, { false }, {
            actions += "install"; entered.complete(Unit); release.await()
        }, { actions += "discard" }) }
        entered.await(); wanted = false
        val user = async { owner.mutate { actions += "disable" } }
        release.complete(Unit); recovery.await(); user.await()
        assertEquals(listOf("install", "discard", "disable"), actions)
    }
    @Test fun cancellationEscapesAndDiscardsPartiallyInstalledProxy() = runTest {
        val owner = ProxyRecoveryCoordinator()
        var discarded = false
        var escaped = false
        try { owner.recover(owner.invalidate(), { true }, { false }, { throw CancellationException("stop") }, { discarded = true }) }
        catch (_: CancellationException) { escaped = true }
        assertTrue(escaped)
        assertTrue(discarded)
    }
    @Test fun failedNativeRollbackCannotMaskCancellation() = runTest {
        val reported = mutableListOf<Throwable>()
        val owner = ProxyRecoveryCoordinator(reported::add)
        val cancelled = CancellationException("cancel recovery")
        val cleanup = IllegalStateException("native cleanup failed")
        try {
            owner.recover(owner.invalidate(), { true }, { false }, { throw cancelled }, { throw cleanup })
            fail("Cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
            assertEquals(cleanup.message, actual.suppressed.single().message)
            assertEquals(cleanup.javaClass, actual.suppressed.single().javaClass)
            assertEquals(cleanup.message, reported.single().message)
        }
    }

    @Test fun failedRollbackReportsErrorWithoutMaskingOriginalInstallFailure() = runTest {
        val reported = mutableListOf<Throwable>()
        val owner = ProxyRecoveryCoordinator(reported::add)
        try {
            owner.recover(owner.invalidate(), { true }, { false }, { error("install failed") }, { error("cleanup failed") })
            fail("The install failure must escape")
        } catch (actual: IllegalStateException) {
            assertEquals("install failed", actual.message)
            assertEquals("cleanup failed", actual.suppressed.single().message)
            assertEquals("cleanup failed", reported.single().message)
        }
    }

}

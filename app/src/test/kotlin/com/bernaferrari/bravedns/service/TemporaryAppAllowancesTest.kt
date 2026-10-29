package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.AppInfoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TemporaryAppAllowancesTest {
    private val repository = mockk<AppInfoRepository>(relaxed = true)
    private val closed = mutableListOf<Int>()
    private var timestamp = 1_000L
    private val allowances = TemporaryAppAllowances(repository, { timestamp }, closed::add)

    @Test fun `ordinary connections cache denial and grant invalidates it`() = runTest {
        coEvery { repository.getAppInfoByUid(42) } returns AppInfo()
        assertFalse(allowances.isAllowed(42))
        assertFalse(allowances.isAllowed(42))
        coVerify(exactly = 1) { repository.getAppInfoByUid(42) }
        allowances.grant(42, 1)
        assertTrue(allowances.isAllowed(42))
        allowances.revoke(42)
        assertFalse(allowances.isAllowed(42))
        assertEquals(listOf(42), closed)
    }

    @Test fun `expiry closes only allowances successfully cleared`() = runTest {
        val expired = AppInfo().apply {
            uid = 42
            tempAllowEnabled = true
            tempAllowExpiryTime = 500L
        }
        val renewed = expired.snapshot().apply { uid = 43 }
        coEvery { repository.getTempAllowedApps() } returns listOf(expired, renewed)
        coEvery { repository.clearTempAllowByUidIfExpiry(42, 500L) } returns 1
        coEvery { repository.clearTempAllowByUidIfExpiry(43, 500L) } returns 0
        assertEquals(1, allowances.expire(timestamp))
        assertEquals(listOf(42), closed)
        assertFalse(allowances.isAllowed(42))
        coVerify(exactly = 0) { repository.getAppInfoByUid(42) }
    }

    @Test fun `reload invalidates cached denial`() = runTest {
        coEvery { repository.getAppInfoByUid(42) } returns AppInfo()
        assertFalse(allowances.isAllowed(42))
        coEvery { repository.getAppInfoByUid(42) } returns AppInfo().apply {
            tempAllowEnabled = true
            tempAllowExpiryTime = 10_000L
        }
        allowances.invalidate()
        assertTrue(allowances.isAllowed(42))
    }
}

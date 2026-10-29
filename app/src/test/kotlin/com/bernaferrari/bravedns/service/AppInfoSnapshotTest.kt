package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.database.AppInfo
import org.junit.Assert.*
import org.junit.Test

class AppInfoSnapshotTest {
    @Test fun `mutating cache entry never changes a published snapshot`() {
        val app = AppInfo().apply { uid = 42; firewallStatus = 3; connectionStatus = 3 }
        val snapshot = app.snapshot()
        app.firewallStatus = 5
        app.connectionStatus = 0
        assertEquals(3, snapshot.firewallStatus)
        assertEquals(3, snapshot.connectionStatus)
        assertNotEquals(app, snapshot)
    }

    @Test fun `changes outside firewall status also change snapshot equality`() {
        val app = AppInfo().apply { uid = 42; packageName = "example.app" }
        assertNotEquals(app, app.copy(isProxyExcluded = true))
        assertNotEquals(app, app.copy(uid = -42, tombstoneTs = 1L))
        assertNotEquals(app, app.copy(tempAllowEnabled = true, tempAllowExpiryTime = 1L))
        assertEquals(app, app.snapshot())
        assertEquals(app.hashCode(), app.snapshot().hashCode())
    }
}

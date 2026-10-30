package com.bernaferrari.bravedns.database

import android.app.Application
import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import java.net.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppDatabaseCatalogTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun resetDatabase() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    @After fun removeDatabase() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }

    @Test fun `production factory provides connected and fallback DNS on a fresh install`() = runTest {
        val db = buildConfiguredAppDatabase(context, AndroidSQLiteDriver())
        try {
            val selected = db.rethinkEndpointDao().getConnectedEndpoint()
            val fallback = db.rethinkEndpointDao().getDefaultRethinkEndpoint()
            assertNotNull("Fresh install has no selected DNS transport", selected)
            assertNotNull("Fresh install has no fallback DNS transport", fallback)
            assertEquals(RethinkDnsEndpoint.RETHINK_DEFAULT, selected!!.name)
            assertEquals("https://sky.rethinkdns.com/rec", selected.url)
            val fallbackUri = URI(fallback!!.url)
            assertEquals("https", fallbackUri.scheme)
            assertEquals("sky.rethinkdns.com", fallbackUri.host)
            assertEquals(selected.url, fallback.url)
            assertEquals(7, db.rethinkEndpointDao().getCount())
            assertEquals(7, db.dohEndpointsDAO().getCount())
            assertEquals(5, db.dnsCryptEndpointDAO().getCount())
            assertEquals(5, db.dnsCryptRelayEndpointDAO().getCount())
            assertEquals(7, db.dnsProxyEndpointDAO().getCount())
            assertEquals(5, db.dotEndpointDao().getCount())
            assertEquals(3, db.odohEndpointDao().getCount())
            assertEquals(4, db.proxyEndpointDAO().getCount())
            assertEquals(0, db.wgConfigFilesDAO().getConfigCount().first())
            assertTrue(db.tcpProxyEndpointDao().getTcpProxies().isEmpty())
            val proxies = listOf(
                db.proxyEndpointDAO().getCustomSocks5Endpoint(),
                db.proxyEndpointDAO().getHttpProxyDetails(),
                db.proxyEndpointDAO().getOrbotSocks5Endpoint(),
                db.proxyEndpointDAO().getOrbotHttpEndpoint(),
            )
            assertTrue(proxies.all { it != null && !it.isSelected })
            assertTrue(proxies.all { it!!.userName.isNullOrEmpty() && it.password.isNullOrEmpty() })
        } finally { db.close() }
    }

    @Test fun `reopening preserves custom selection and does not duplicate defaults`() = runTest {
        val db = buildConfiguredAppDatabase(context, AndroidSQLiteDriver())
        val customUrl = "https://dns.example.test/custom"
        try {
            val dao = db.rethinkEndpointDao()
            val uid = dao.getConnectedEndpoint()!!.uid
            dao.removeConnectionStatus()
            dao.insert(RethinkDnsEndpoint(
                name = "My DNS", url = customUrl, uid = uid, desc = "", isActive = true,
                isCustom = true, latency = 0, blocklistCount = 0, modifiedDataTime = 0,
            ))
        } finally { db.close() }

        repeat(2) {
            val reopened = buildConfiguredAppDatabase(context, AndroidSQLiteDriver())
            try {
                assertEquals(customUrl, reopened.rethinkEndpointDao().getConnectedEndpoint()!!.url)
                assertEquals(8, reopened.rethinkEndpointDao().getCount())
                assertEquals(7, reopened.dohEndpointsDAO().getCount())
            } finally { reopened.close() }
        }
    }
}

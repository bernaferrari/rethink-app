package com.bernaferrari.bravedns.database

import android.app.Application
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Activity log's merged timeline: one order across both tables, and filters that mean the same in each. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ActivityLogDAOTest {
    private lateinit var db: LogDatabase
    private val dao get() = db.activityLogDAO()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>()) {
            LogDatabaseConstructor.initialize()
        }.setDriver(AndroidSQLiteDriver()).build()
    }

    @After fun close() = db.close()

    @Test fun allInterleavesConnectionsAndLookupsNewestFirst() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1")
        lookup(time = 150, app = CLOCK, domain = "time.example")
        connection(time = 200, app = MAPS, ip = "192.0.2.2", blocked = true, rule = "Rule #1")
        lookup(time = 250, app = MAPS, domain = "ads.example", blocked = true)

        val rows = load(ActivityLogQuery())
        assertEquals(listOf("d250", "c200", "d150", "c100"), rows.map { it.label() })
        // Each row carries exactly its own table's entry, intact.
        assertEquals("ads.example", rows[0].lookup?.queryStr)
        assertNull(rows[0].connection)
        assertEquals("192.0.2.2", rows[1].connection?.ipAddress)
        assertEquals("Rule #1", rows[1].connection?.blockedByRule)
        assertNull(rows[1].lookup)
    }

    @Test fun withinOneMillisecondNewerRowsGoFirstAndAFullTieListsTheConnectionFirst() = runBlocking {
        lookup(time = 100, app = CLOCK, domain = "a.example") // lookup id 1
        connection(time = 100, app = CLOCK, ip = "192.0.2.1") // connection id 1
        connection(time = 100, app = CLOCK, ip = "192.0.2.2") // connection id 2
        lookup(time = 100, app = CLOCK, domain = "b.example") // lookup id 2

        val rows = load(ActivityLogQuery())
        assertEquals(listOf("c100", "d100", "c100", "d100"), rows.map { it.label() })
        assertEquals(
            listOf("192.0.2.2", "b.example", "192.0.2.1", "a.example"),
            rows.map { it.connection?.ipAddress ?: it.lookup?.queryStr },
        )
    }

    @Test fun pagesContinueTheSameOrderAcrossBothTables() = runBlocking {
        for (t in 1..30) if (t % 3 == 0) lookup(time = t * 10L, app = CLOCK, domain = "d$t.example") else connection(time = t * 10L, app = CLOCK, ip = "192.0.2.$t")
        val source = dao.timeline(ActivityLogQuery())
        val first = source.load(PagingSource.LoadParams.Refresh(null, 12, false)) as PagingSource.LoadResult.Page
        val second = source.load(PagingSource.LoadParams.Append(checkNotNull(first.nextKey), 12, false)) as PagingSource.LoadResult.Page
        val times = (first.data + second.data).map { it.ts }
        assertEquals((30 downTo 7).map { it * 10L }, times)
    }

    @Test fun kindsListOneTableOnly() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1")
        lookup(time = 200, app = CLOCK, domain = "a.example")

        assertEquals(listOf("c100"), load(ActivityLogQuery(kinds = ActivityLogKinds.Connections)).map { it.label() })
        assertEquals(listOf("d200"), load(ActivityLogQuery(kinds = ActivityLogKinds.Lookups)).map { it.label() })
    }

    @Test fun verdictMeansWhatEachPerTypeLogMeans() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1")
        connection(time = 200, app = CLOCK, ip = "192.0.2.2", blocked = true)
        lookup(time = 300, app = CLOCK, domain = "ok.example")
        lookup(time = 400, app = CLOCK, domain = "ads.example", blocked = true)
        // Matched a blocklist but answered: "maybe blocked", which the DNS log keeps out of Allowed.
        lookup(time = 500, app = CLOCK, domain = "maybe.example", blockLists = "pack")

        assertEquals(listOf("d400", "c200"), load(ActivityLogQuery(verdict = ActivityVerdict.Blocked)).map { it.label() })
        assertEquals(listOf("d300", "c100"), load(ActivityLogQuery(verdict = ActivityVerdict.Allowed)).map { it.label() })
        assertEquals(5, load(ActivityLogQuery()).size)
    }

    @Test fun aReasonMatchesConnectionsOnlyExceptInTheLookupsLogWhereItIsIgnored() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1", blocked = true, rule = "Rule #1")
        connection(time = 200, app = CLOCK, ip = "192.0.2.2", blocked = true, rule = "Rule #2")
        lookup(time = 300, app = CLOCK, domain = "a.example", blocked = true)

        assertEquals(listOf("c100"), load(ActivityLogQuery(rules = setOf("Rule #1"))).map { it.label() })
        assertEquals(
            listOf("d300"),
            load(ActivityLogQuery(kinds = ActivityLogKinds.Lookups, rules = setOf("Rule #1"))).map { it.label() },
        )
    }

    @Test fun searchMatchesEachTablesOwnFields() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1", domain = "time.example")
        connection(time = 200, app = MAPS, ip = "198.51.100.7")
        lookup(time = 300, app = MAPS, domain = "time.example")
        lookup(time = 400, app = MAPS, domain = "tiles.example", responseIps = "198.51.100.7")

        assertEquals(listOf("d300", "c100"), load(ActivityLogQuery(search = " time.example ")).map { it.label() })
        assertEquals(listOf("d400", "c200"), load(ActivityLogQuery(search = "198.51.100.7")).map { it.label() })
    }

    @Test fun anAppIsItsUidNotItsName() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1")
        lookup(time = 200, app = CLOCK, domain = "a.example")
        // Another app whose name contains "Clock": a name search would list it too.
        connection(time = 300, app = CLOCK_WIDGET, ip = "192.0.2.3")
        lookup(time = 400, app = CLOCK_WIDGET, domain = "b.example")

        assertEquals(listOf("d200", "c100"), load(ActivityLogQuery(uid = CLOCK.uid)).map { it.label() })
        assertEquals(
            listOf("c300"),
            load(ActivityLogQuery(uid = CLOCK_WIDGET.uid, kinds = ActivityLogKinds.Connections)).map { it.label() },
        )
    }

    @Test fun loggedAppsCountBothTablesPerUidUnderTheSameFilters() = runBlocking {
        connection(time = 100, app = CLOCK, ip = "192.0.2.1")
        lookup(time = 200, app = CLOCK, domain = "a.example", blocked = true)
        lookup(time = 300, app = CLOCK_WIDGET, domain = "b.example")

        val all = dao.loggedApps(ActivityLogQuery())
        assertEquals(listOf(CLOCK.uid to 2, CLOCK_WIDGET.uid to 1), all.map { it.uid to it.count })
        val blocked = dao.loggedApps(ActivityLogQuery(verdict = ActivityVerdict.Blocked))
        assertEquals(listOf(CLOCK.uid to 1), blocked.map { it.uid to it.count })
        val connections = dao.loggedApps(ActivityLogQuery(kinds = ActivityLogKinds.Connections))
        assertEquals(listOf(CLOCK.uid to 1), connections.map { it.uid to it.count })
        assertNotNull(all.first().appName)
    }

    private data class App(val uid: Int, val name: String)

    private suspend fun load(query: ActivityLogQuery): List<ActivityLogEntry> {
        val result = dao.timeline(query).load(PagingSource.LoadParams.Refresh(null, 100, false))
        return (result as PagingSource.LoadResult.Page).data
    }

    private fun ActivityLogEntry.label() = (if (connection != null) "c" else "d") + ts

    private suspend fun connection(
        time: Long,
        app: App,
        ip: String,
        blocked: Boolean = false,
        rule: String = "",
        domain: String? = null,
    ) = db.connectionTrackerDAO().insert(
        ConnectionTracker().apply {
            uid = app.uid
            appName = app.name
            ipAddress = ip
            isBlocked = blocked
            blockedByRule = rule
            dnsQuery = domain
            timeStamp = time
        }
    )

    private suspend fun lookup(
        time: Long,
        app: App,
        domain: String,
        blocked: Boolean = false,
        blockLists: String = "",
        responseIps: String = "",
    ) = db.dnsLogDAO().insert(
        DnsLog().apply {
            uid = app.uid
            appName = app.name
            queryStr = domain
            isBlocked = blocked
            this.blockLists = blockLists
            this.responseIps = responseIps
            this.time = time
        }
    )

    private companion object {
        val CLOCK = App(10_001, "Clock")
        val CLOCK_WIDGET = App(10_002, "Clock Widget")
        val MAPS = App(10_003, "Maps")
    }
}

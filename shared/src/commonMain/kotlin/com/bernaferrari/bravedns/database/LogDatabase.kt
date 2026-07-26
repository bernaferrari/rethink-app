/*
 * Copyright 2023 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.room3.migration.Migration
import androidx.room3.RoomRawQuery
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.SQLiteConnection
import com.bernaferrari.bravedns.util.Constants.Companion.EMPTY_PACKAGE_NAME

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LogDatabaseConstructor : RoomDatabaseConstructor<LogDatabase> {
    override fun initialize(): LogDatabase
}

@Database(
    entities = [ConnectionTracker::class, DnsLog::class, RethinkLog::class, IpInfo::class, Event::class],
    version = 13,
    exportSchema = false
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(LogDatabaseConstructor::class)
abstract class LogDatabase : RoomDatabase() {

    companion object {
        const val LOGS_DATABASE_NAME = "rethink_logs.db"
        private const val PRAGMA = "pragma wal_checkpoint(full)"
        private const val TABLE_NAME_DNS_LOGS = "DnsLogs"
        // previous table name for dns logs
        private const val TABLE_NAME_PREVIOUS_DNS = "DNSLogs"
        private const val TABLE_NAME_CONN_TRACKER = "ConnectionTracker"
        internal var rethinkDnsDbPath = ""
        internal var isFreshInstall = true

        // setJournalMode() is added as part of issue #344
        // modified the journal mode from TRUNCATE to AUTOMATIC.
        // The actual value will be TRUNCATE when the it is a low-RAM device.
        // Otherwise, WRITE_AHEAD_LOGGING will be used.
        // https://developer.android.com/reference/android/arch/persistence/room/RoomDatabase.JournalMode#automatic

        internal val roomCallback: Callback =
            object : Callback() {
                override suspend fun onCreate(connection: SQLiteConnection) {
                    super.onCreate(connection)
                    if (isFreshInstall) return
                    // need to call populateDatabase() only if the app is not a fresh install
                    // and the version is less than 6, as older versions had logs in the main db
                    if (connection.userVersion() > 5) return
                    populateDatabase(connection)
                }
            }

        internal suspend fun populateDatabase(connection: SQLiteConnection) {
            try {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS 'ConnectionTracker' ('id' INTEGER NOT NULL,'appName' TEXT DEFAULT '' NOT NULL, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT DEFAULT ''  NOT NULL, 'port' INTEGER NOT NULL, 'protocol' INTEGER NOT NULL,'isBlocked' INTEGER NOT NULL, 'blockedByRule' TEXT DEFAULT '' NOT NULL, 'flag' TEXT  DEFAULT '' NOT NULL, 'dnsQuery' TEXT DEFAULT '', 'timeStamp' INTEGER NOT NULL,PRIMARY KEY (id)  )"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS 'DnsLogs' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'queryStr' TEXT NOT NULL, 'time' INTEGER NOT NULL, 'flag' TEXT NOT NULL, 'resolver' TEXT NOT NULL, 'latency' INTEGER NOT NULL, 'typeName' TEXT NOT NULL, 'isBlocked' INTEGER NOT NULL, 'blockLists' LONGTEXT NOT NULL,  'serverIP' TEXT NOT NULL, 'relayIP' TEXT NOT NULL, 'responseTime' INTEGER NOT NULL, 'response' TEXT NOT NULL, 'status' TEXT NOT NULL,'dnsType' INTEGER NOT NULL, 'responseIps' TEXT NOT NULL) "
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dnslogs_querystr ON  DnsLogs(queryStr)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_connectiontracker_ipaddress ON  ConnectionTracker(ipAddress)"
                )

                // to avoid the exception, the transaction should be ended before the
                // "attach database" is called.
                // here mainDB is the database which is LogDatabase
                // attach the rethinkDB to the LogDatabase
                connection.setTransactionSuccessful()
                connection.endTransaction()
                // disable WAL option before attaching the database
                connection.disableWriteAheadLogging()
                connection.beginTransaction()
                connection.execSQL("ATTACH DATABASE '$rethinkDnsDbPath' AS tempDb")
                // delete logs from main database
                connection.execSQL("delete from main.$TABLE_NAME_DNS_LOGS")
                connection.execSQL("delete from main.$TABLE_NAME_CONN_TRACKER")
                // no need to proceed if the table does not exist
                if (!connection.tableExists("tempDb.$TABLE_NAME_PREVIOUS_DNS")) {
                    connection.execSQL("DETACH DATABASE tempDb")
                    connection.enableWriteAheadLogging()
                    return
                }

                // insert Dns and network logs to the new database tables
                connection.execSQL(
                    "INSERT INTO main.$TABLE_NAME_DNS_LOGS SELECT * FROM tempDb.$TABLE_NAME_PREVIOUS_DNS"
                )
                if (connection.tableExists("tempDb.$TABLE_NAME_CONN_TRACKER")) {
                    connection.execSQL(
                        "INSERT INTO main.$TABLE_NAME_CONN_TRACKER SELECT * FROM tempDb.$TABLE_NAME_CONN_TRACKER"
                    )
                }
                connection.enableWriteAheadLogging()
            } catch (ex: Exception) {
                println("LogDatabase")
            }
        }



        internal val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE DnsLogs add column resolverId TEXT DEFAULT '' NOT NULL")
                }
            }

        internal val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column blocklists TEXT DEFAULT '' NOT NULL"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_DnsLogs_queryStr ON DnsLogs(queryStr)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_DnsLogs_responseIps ON DnsLogs(responseIps)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_DnsLogs_isBlocked ON DnsLogs(isBlocked)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_DnsLogs_blockLists ON DnsLogs(blockLists)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ConnectionTracker_ipAddress ON ConnectionTracker(ipAddress)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ConnectionTracker_appName ON ConnectionTracker(appName)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ConnectionTracker_dnsQuery ON ConnectionTracker(dnsQuery)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ConnectionTracker_blockedByRule ON ConnectionTracker(blockedByRule)"
                    )
                }
            }

        internal val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column connId TEXT DEFAULT '' NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column downloadBytes INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column uploadBytes INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column duration INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column synack INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker add column message TEXT DEFAULT '' NOT NULL"
                    )
                }
            }

        internal val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker ADD COLUMN proxyDetails TEXT DEFAULT '' NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker ADD COLUMN connType TEXT DEFAULT '' NOT NULL"
                    )
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS 'RethinkLog' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'appName' TEXT DEFAULT '' NOT NULL, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT DEFAULT ''  NOT NULL, 'port' INTEGER NOT NULL, 'protocol' INTEGER NOT NULL,'isBlocked' INTEGER NOT NULL, 'proxyDetails' TEXT DEFAULT '' NOT NULL, 'flag' TEXT  DEFAULT '' NOT NULL, 'dnsQuery' TEXT DEFAULT '', 'timeStamp' INTEGER NOT NULL,  'connId' TEXT DEFAULT '' NOT NULL, 'downloadBytes' INTEGER DEFAULT 0 NOT NULL, 'uploadBytes' INTEGER DEFAULT 0 NOT NULL, 'duration' INTEGER DEFAULT 0 NOT NULL, 'synack' INTEGER DEFAULT 0 NOT NULL, 'message' TEXT DEFAULT '' NOT NULL, 'connType' TEXT DEFAULT '' NOT NULL)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_RethinkLog_ipAddress ON RethinkLog(ipAddress)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_RethinkLog_appName ON RethinkLog(appName)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_RethinkLog_dnsQuery ON RethinkLog(dnsQuery)"
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_DnsLogs_time ON DnsLogs(time)")
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ConnectionTracker_isBlocked_timeStamp ON ConnectionTracker(isBlocked, timeStamp)"
                    )
                    connection.execSQL(
                        "ALTER TABLE ConnectionTracker ADD COLUMN usrId INT DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "CREATE TABLE 'AlertRegistry' ('id' INTEGER NOT NULL, 'alertTitle' TEXT NOT NULL, 'alertType' TEXT NOT NULL, 'alertCount' INTEGER NOT NULL, 'alertTime' INTEGER NOT NULL, 'alertMessage' TEXT NOT NULL, 'alertCategory' TEXT NOT NULL, 'alertSeverity' TEXT NOT NULL, 'alertActions' TEXT NOT NULL, 'alertStatus' TEXT NOT NULL, 'alertSolution' TEXT NOT NULL, 'isRead' INTEGER NOT NULL, isDeleted INTEGER NOT NULL, isCustom INTEGER NOT NULL, isNotified INTEGER NOT NULL, PRIMARY KEY (id))"
                    )
                    connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN msg TEXT DEFAULT '' NOT NULL")
                }
            }

        internal val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // add a new column upstreamBlock to DNS log table with default as false
                    connection.execSQL(
                        "ALTER TABLE DnsLogs ADD COLUMN upstreamBlock INTEGER DEFAULT 0 NOT NULL"
                    )
                }
            }

        internal val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // add a new column region to DNS log table with default as empty string
                    connection.execSQL(
                        "ALTER TABLE DnsLogs ADD COLUMN region TEXT DEFAULT '' NOT NULL"
                    )
                }
            }

        internal val Migration_8_9: Migration = object : Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_ConnectionTracker_connId ON ConnectionTracker(connId)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_RethinkLog_connId ON RethinkLog(connId)")
            }
        }

        internal val Migration_9_10: Migration = object : Migration(9, 10) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_ConnectionTracker_proxyDetails ON ConnectionTracker(proxyDetails)")
            }
        }

        internal val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE ConnectionTracker ADD COLUMN rpid TEXT DEFAULT '' NOT NULL")
                connection.execSQL("ALTER TABLE RethinkLog ADD COLUMN rpid TEXT DEFAULT '' NOT NULL")

                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN uid INTEGER DEFAULT -1 NOT NULL")

                // add packageName to ConnectionTracker table
                connection.execSQL("ALTER TABLE ConnectionTracker ADD COLUMN packageName TEXT DEFAULT $EMPTY_PACKAGE_NAME NOT NULL")
                // add package name and appName to DnsLogs table
                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN packageName TEXT DEFAULT $EMPTY_PACKAGE_NAME NOT NULL")
                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN appName TEXT DEFAULT '' NOT NULL")
                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN proxyId TEXT DEFAULT '' NOT NULL")
                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN ttl INTEGER DEFAULT 0 NOT NULL")
                connection.execSQL("CREATE TABLE IF NOT EXISTS IpInfo (ip TEXT PRIMARY KEY NOT NULL, asn TEXT NOT NULL, asName TEXT NOT NULL, asDomain TEXT NOT NULL, countryCode TEXT NOT NULL, country TEXT NOT NULL, continentCode TEXT NOT NULL, continent TEXT NOT NULL, createdTs INTEGER NOT NULL)".trimIndent())
                connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN isCached INTEGER DEFAULT 0 NOT NULL")
            }
        }

        internal val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // add column dnssecOk, dnssecValid to DnsLogs
                try {
                    connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN dnssecOk INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN dnssecValid INTEGER NOT NULL DEFAULT 0")
                    println("LogDatabase")
                } catch (_: Exception) {
                    println("LogDatabase")
                }
            }
        }

        internal val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // add missing columns to RethinkLog to align with ConnectionTracker
                try {
                    connection.execSQL("ALTER TABLE RethinkLog ADD COLUMN usrId INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("ALTER TABLE RethinkLog ADD COLUMN blockedByRule TEXT NOT NULL DEFAULT ''")
                    connection.execSQL("ALTER TABLE RethinkLog ADD COLUMN blocklists TEXT NOT NULL DEFAULT ''")
                    println("LogDatabase")
                } catch (e: Exception) {
                    println("LogDatabase")
                }
                try {
                    // Create Events table with all required columns
                    connection.execSQL(
                        """CREATE TABLE IF NOT EXISTS Events (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            eventType TEXT NOT NULL,
                            severity TEXT NOT NULL,
                            message TEXT NOT NULL,
                            details TEXT,
                            source TEXT NOT NULL,
                            userAction INTEGER NOT NULL DEFAULT 0
                        )""".trimIndent()
                    )

                    // Create indices for efficient querying
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_Events_timestamp ON Events(timestamp)")
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_Events_eventType ON Events(eventType)")
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_Events_severity ON Events(severity)")
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_Events_source ON Events(source)")
                    connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN blockedTarget TEXT NOT NULL DEFAULT ''")

                    println("LogDatabase")
                } catch (e: Exception) {
                    println("LogDatabase")
                }
                try {
                    // Add blockedTarget column to DnsLogs table
                    connection.execSQL("ALTER TABLE DnsLogs ADD COLUMN blockedTarget TEXT NOT NULL DEFAULT ''")
                    println("LogDatabase")
                } catch (e: Exception) {
                    println("LogDatabase")
                }
            }
        }

    }

    suspend fun checkPoint() {
        logsDao().checkpoint(RoomRawQuery(PRAGMA))
        logsDao().vacuum(RoomRawQuery("VACUUM"))
    }

    abstract fun connectionTrackerDAO(): ConnectionTrackerDAO

    abstract fun rethinkConnectionLogDAO(): RethinkLogDao

    abstract fun dnsLogDAO(): DnsLogDAO

    abstract fun logsDao(): LogDatabaseRawQueryDao

    abstract fun statsSummaryDAO(): StatsSummaryDao

    abstract fun ipInfoDao(): IpInfoDAO

    abstract fun eventDao(): EventDao

    fun connectionTrackerRepository() = ConnectionTrackerRepository(connectionTrackerDAO())

    fun rethinkConnectionLogRepository() = RethinkLogRepository(rethinkConnectionLogDAO())

    fun rethinkLogRepository() = rethinkConnectionLogRepository()

    fun dnsLogRepository() = DnsLogRepository(dnsLogDAO())

    fun ipInfoRepository() = IpInfoRepository(ipInfoDao())

    fun eventRepository() = EventRepository(eventDao())
}

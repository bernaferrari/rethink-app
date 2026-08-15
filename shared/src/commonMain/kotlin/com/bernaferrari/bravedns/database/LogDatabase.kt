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
import androidx.room3.RoomRawQuery
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.SQLiteConnection

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LogDatabaseConstructor : RoomDatabaseConstructor<LogDatabase> {
    override fun initialize(): LogDatabase
}

@Database(
    entities = [
        ConnectionTracker::class,
        DnsLog::class,
        RethinkLog::class,
        IpInfo::class,
        Event::class,
        DailyAppStats::class,
        DailyCountryStats::class,
        DailyAsnStats::class,
        DailyDomainStats::class,
    ],
    version = 15,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(LogDatabaseConstructor::class)
abstract class LogDatabase : RoomDatabase() {

    companion object {
        const val LOGS_DATABASE_NAME = "rethink_logs.db"
        const val SCHEMA_VERSION = 15
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
                // Shared has no Logger (app-module only). Keep the failure visible.
                println("LogDatabase populateDatabase failed: ${ex.message}")
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

    abstract fun dailyStatsDAO(): DailyStatsDao

    abstract fun ipInfoDao(): IpInfoDAO

    abstract fun eventDao(): EventDao

    fun connectionTrackerRepository() = ConnectionTrackerRepository(connectionTrackerDAO())

    fun rethinkConnectionLogRepository() = RethinkLogRepository(rethinkConnectionLogDAO())

    fun rethinkLogRepository() = rethinkConnectionLogRepository()

    fun dnsLogRepository() = DnsLogRepository(dnsLogDAO())

    fun ipInfoRepository() = IpInfoRepository(ipInfoDao())

    fun eventRepository() = EventRepository(eventDao())
}

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
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter

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
        DailyIpStats::class,
    ],
    version = 1,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(LogDatabaseConstructor::class)
abstract class LogDatabase : RoomDatabase() {

    companion object {
        const val LOGS_DATABASE_NAME = "rethink_logs.db"
        const val SCHEMA_VERSION = 1
    }

    suspend fun checkPoint() = compactAndCheckpoint()

    abstract fun connectionTrackerDAO(): ConnectionTrackerDAO

    abstract fun rethinkConnectionLogDAO(): RethinkLogDao

    abstract fun dnsLogDAO(): DnsLogDAO

    abstract fun appStatsDAO(): AppStatsDao

    abstract fun asnStatsDAO(): AsnStatsDao

    abstract fun domainStatsDAO(): DomainStatsDao

    abstract fun countryStatsDAO(): CountryStatsDao

    abstract fun ipStatsDAO(): IpStatsDao

    abstract fun dailyStatsDAO(): DailyStatsDao

    abstract fun ipInfoDao(): IpInfoDAO

    abstract fun eventDao(): EventDao

    fun connectionTrackerRepository() = ConnectionTrackerRepository(connectionTrackerDAO())

    fun rethinkConnectionLogRepository() = RethinkLogRepository(rethinkConnectionLogDAO())

    fun rethinkLogRepository() = rethinkConnectionLogRepository()

    fun dnsLogRepository() = DnsLogRepository(dnsLogDAO())

    fun ipInfoRepository() = IpInfoRepository(this)

    fun eventRepository() = EventRepository(eventDao())
}

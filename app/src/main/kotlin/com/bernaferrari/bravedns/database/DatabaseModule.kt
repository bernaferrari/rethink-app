/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import android.content.Context
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** Compiler-generated definitions for the Android Room graph and its repositories. */
@Module
class DatabaseModule {
    @Single
    fun appDatabase(@Provided context: Context): AppDatabase = buildAppDatabase(context)

    @Single
    fun logDatabase(@Provided context: Context): LogDatabase =
        buildLogDatabase(
            context,
            rethinkDnsDbPath = context.getDatabasePath(AppDatabase.DATABASE_NAME).toString(),
            isFreshInstall = Utilities.isFreshInstall(context),
        )

    @Single
    fun consoleLogDatabase(@Provided context: Context): ConsoleLogDatabase = buildConsoleLogDatabase(context)

    @Single fun appInfoDao(database: AppDatabase) = database.appInfoDAO()
    @Single fun dnsCryptEndpointDao(database: AppDatabase) = database.dnsCryptEndpointDAO()
    @Single fun dnsCryptRelayEndpointDao(database: AppDatabase) = database.dnsCryptRelayEndpointDAO()
    @Single fun dnsProxyEndpointDao(database: AppDatabase) = database.dnsProxyEndpointDAO()
    @Single fun dohEndpointDao(database: AppDatabase) = database.dohEndpointsDAO()
    @Single fun proxyEndpointDao(database: AppDatabase) = database.proxyEndpointDAO()
    @Single fun customDomainDao(database: AppDatabase) = database.customDomainEndpointDAO()
    @Single fun customIpDao(database: AppDatabase) = database.customIpEndpointDao()
    @Single fun rethinkEndpointDao(database: AppDatabase) = database.rethinkEndpointDao()
    @Single fun rethinkLocalFileTagDao(database: AppDatabase) = database.rethinkLocalFileTagDao()
    @Single fun rethinkRemoteFileTagDao(database: AppDatabase) = database.rethinkRemoteFileTagDao()
    @Single fun remoteBlocklistPacksMapDao(database: AppDatabase) = database.remoteBlocklistPacksMapDao()
    @Single fun localBlocklistPacksMapDao(database: AppDatabase) = database.localBlocklistPacksMapDao()
    @Single fun wgConfigFilesDao(database: AppDatabase) = database.wgConfigFilesDAO()
    @Single fun wgApplicationMappingDao(database: AppDatabase) = database.wgApplicationMappingDao()
    @Single fun tcpProxyDao(database: AppDatabase) = database.tcpProxyEndpointDao()
    @Single fun dotEndpointDao(database: AppDatabase) = database.dotEndpointDao()
    @Single fun odohEndpointDao(database: AppDatabase) = database.odohEndpointDao()
    @Single fun rpnProxyDao(database: AppDatabase) = database.rpnProxyDao()
    @Single fun wgHopMapDao(database: AppDatabase) = database.wgHopMapDao()
    @Single fun countryConfigDao(database: AppDatabase) = database.countryConfigDao()
    @Single fun subscriptionStatusDao(database: AppDatabase) = database.subscriptionStatusDao()
    @Single fun subscriptionStateHistoryDao(database: AppDatabase) = database.subscriptionStateHistoryDao()
    @Single fun connectionTrackerDao(database: LogDatabase) = database.connectionTrackerDAO()
    @Single fun dnsLogDao(database: LogDatabase) = database.dnsLogDAO()
    @Single fun rethinkLogDao(database: LogDatabase) = database.rethinkConnectionLogDAO()
    @Single fun statsSummaryDao(database: LogDatabase) = database.statsSummaryDAO()
    @Single fun ipInfoDao(database: LogDatabase) = database.ipInfoDao()
    @Single fun eventDao(database: LogDatabase) = database.eventDao()
    @Single fun consoleLogDao(database: ConsoleLogDatabase) = database.consoleLogDAO()

    @Single fun appInfoRepository(database: AppDatabase) = database.appInfoRepository()
    @Single fun dohEndpointRepository(database: AppDatabase) = database.dohEndpointRepository()
    @Single fun dnsCryptEndpointRepository(database: AppDatabase) = database.dnsCryptEndpointRepository()
    @Single fun dnsCryptRelayEndpointRepository(database: AppDatabase) = database.dnsCryptRelayEndpointRepository()
    @Single fun dnsProxyEndpointRepository(database: AppDatabase) = database.dnsProxyEndpointRepository()
    @Single fun proxyEndpointRepository(database: AppDatabase) = database.proxyEndpointRepository()
    @Single fun customDomainRepository(database: AppDatabase) = database.customDomainRepository()
    @Single fun customIpRepository(database: AppDatabase) = database.customIpRepository()
    @Single fun rethinkEndpointRepository(database: AppDatabase) = database.rethinkEndpointRepository()
    @Single fun rethinkRemoteFileTagRepository(database: AppDatabase) = database.rethinkRemoteFileTagRepository()
    @Single fun rethinkLocalFileTagRepository(database: AppDatabase) = database.rethinkLocalFileTagRepository()
    @Single fun remoteBlocklistPacksMapRepository(database: AppDatabase) = database.remoteBlocklistPacksMapRepository()
    @Single fun localBlocklistPacksMapRepository(database: AppDatabase) = database.localBlocklistPacksMapRepository()
    @Single fun wgConfigFilesRepository(database: AppDatabase) = database.wgConfigFilesRepository()
    @Single fun wgApplicationMappingRepository(database: AppDatabase) = database.wgApplicationMappingRepository()
    @Single fun tcpProxyRepository(database: AppDatabase) = database.tcpProxyEndpointRepository()
    @Single fun dotEndpointRepository(database: AppDatabase) = database.dotEndpointRepository()
    @Single fun odohEndpointRepository(database: AppDatabase) = database.odohEndpointRepository()
    @Single fun rpnProxyRepository(database: AppDatabase) = database.rpnProxyRepository()
    @Single fun wgHopMapRepository(database: AppDatabase) = database.wgHopMapRepository()
    @Single fun countryConfigRepository(database: AppDatabase) = database.countryConfigRepository()
    @Single fun subscriptionStatusRepository(database: AppDatabase) = database.subscriptionStatusRepository()
    @Single fun connectionTrackerRepository(database: LogDatabase) = database.connectionTrackerRepository()
    @Single fun dnsLogRepository(database: LogDatabase) = database.dnsLogRepository()
    @Single fun rethinkLogRepository(database: LogDatabase) = database.rethinkLogRepository()
    @Single fun ipInfoRepository(database: LogDatabase) = database.ipInfoRepository()
    @Single fun eventRepository(database: LogDatabase) = database.eventRepository()
    @Single fun consoleLogRepository(database: ConsoleLogDatabase) = database.consoleLogRepository()

    @Single
    fun refreshDatabase(
        @Provided context: Context,
        connectionTrackerRepository: ConnectionTrackerRepository,
        dnsLogRepository: DnsLogRepository,
        rethinkLogRepository: RethinkLogRepository,
        persistentState: PersistentState,
        eventLogger: EventLogger,
    ) = RefreshDatabase(
        context,
        connectionTrackerRepository,
        dnsLogRepository,
        rethinkLogRepository,
        persistentState,
        eventLogger,
    )
}

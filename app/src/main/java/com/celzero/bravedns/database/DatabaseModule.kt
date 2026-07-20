/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import com.celzero.bravedns.util.Utilities
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

object DatabaseModule {
    private val databaseModule = module {
        single { buildAppDatabase(androidContext()) }
        single {
            val ctx = androidContext()
            buildLogDatabase(
                ctx,
                rethinkDnsDbPath = ctx.getDatabasePath(AppDatabase.DATABASE_NAME).toString(),
                isFreshInstall = Utilities.isFreshInstall(ctx)
            )
        }
        single { buildConsoleLogDatabase(androidContext()) }
    }
    private val daoModule = module {
        single { get<AppDatabase>().appInfoDAO() }
        single { get<AppDatabase>().dnsCryptEndpointDAO() }
        single { get<AppDatabase>().dnsCryptRelayEndpointDAO() }
        single { get<AppDatabase>().dnsProxyEndpointDAO() }
        single { get<AppDatabase>().dohEndpointsDAO() }
        single { get<AppDatabase>().proxyEndpointDAO() }
        single { get<AppDatabase>().customDomainEndpointDAO() }
        single { get<AppDatabase>().customIpEndpointDao() }
        single { get<AppDatabase>().rethinkEndpointDao() }
        single { get<AppDatabase>().rethinkLocalFileTagDao() }
        single { get<AppDatabase>().rethinkRemoteFileTagDao() }
        single { get<AppDatabase>().remoteBlocklistPacksMapDao() }
        single { get<AppDatabase>().localBlocklistPacksMapDao() }
        single { get<AppDatabase>().wgConfigFilesDAO() }
        single { get<AppDatabase>().wgApplicationMappingDao() }
        single { get<AppDatabase>().tcpProxyEndpointDao() }
        single { get<AppDatabase>().dotEndpointDao() }
        single { get<AppDatabase>().odohEndpointDao() }
        single { get<AppDatabase>().rpnProxyDao() }
        single { get<AppDatabase>().wgHopMapDao() }
        single { get<AppDatabase>().subscriptionStatusDao() }
        single { get<AppDatabase>().subscriptionStateHistoryDao()}

        single { get<LogDatabase>().connectionTrackerDAO() }
        single { get<LogDatabase>().dnsLogDAO() }
        single { get<LogDatabase>().rethinkConnectionLogDAO() }
        single { get<LogDatabase>().statsSummaryDAO() }
        single { get<LogDatabase>().ipInfoDao() }
        single { get<LogDatabase>().eventDao() }

        single { get<ConsoleLogDatabase>().consoleLogDAO() }

    }
    private val repositoryModule = module {
        single { get<AppDatabase>().appInfoRepository() }
        single { get<AppDatabase>().dohEndpointRepository() }
        single { get<AppDatabase>().dnsCryptEndpointRepository() }
        single { get<AppDatabase>().dnsCryptRelayEndpointRepository() }
        single { get<AppDatabase>().dnsProxyEndpointRepository() }
        single { get<AppDatabase>().proxyEndpointRepository() }
        single { get<AppDatabase>().customDomainRepository() }
        single { get<AppDatabase>().customIpRepository() }
        single { get<AppDatabase>().rethinkEndpointRepository() }
        single { get<AppDatabase>().rethinkRemoteFileTagRepository() }
        single { get<AppDatabase>().rethinkLocalFileTagRepository() }
        single { get<AppDatabase>().remoteBlocklistPacksMapRepository() }
        single { get<AppDatabase>().localBlocklistPacksMapRepository() }
        single { get<AppDatabase>().wgConfigFilesRepository() }
        single { get<AppDatabase>().wgApplicationMappingRepository() }
        single { get<AppDatabase>().tcpProxyEndpointRepository() }
        single { get<AppDatabase>().dotEndpointRepository() }
        single { get<AppDatabase>().odohEndpointRepository() }
        single { get<AppDatabase>().rpnProxyRepository() }
        single { get<AppDatabase>().wgHopMapRepository() }
        single { get<AppDatabase>().subscriptionStatusRepository() }

        single { get<LogDatabase>().connectionTrackerRepository() }
        single { get<LogDatabase>().dnsLogRepository() }
        single { get<LogDatabase>().rethinkConnectionLogRepository() }
        single { get<LogDatabase>().ipInfoRepository() }
        single { get<LogDatabase>().eventRepository() }

        single { get<ConsoleLogDatabase>().consoleLogRepository() }

        single {
            RefreshDatabase(
                androidContext(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }
    }

    val modules = listOf(databaseModule, daoModule, repositoryModule)
}

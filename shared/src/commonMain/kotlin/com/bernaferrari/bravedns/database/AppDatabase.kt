/*
 * Copyright 2020 RethinkDNS and its authors
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
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Database(
    entities =
    [
        AppInfo::class,
        CustomIp::class,
        DoHEndpoint::class,
        DnsCryptEndpoint::class,
        DnsProxyEndpoint::class,
        DnsCryptRelayEndpoint::class,
        ProxyEndpoint::class,
        CustomDomain::class,
        RethinkDnsEndpoint::class,
        RethinkRemoteFileTag::class,
        RethinkLocalFileTag::class,
        LocalBlocklistPacksMap::class,
        RemoteBlocklistPacksMap::class,
        WgConfigFiles::class,
        ProxyApplicationMapping::class,
        TcpProxyEndpoint::class,
        DoTEndpoint::class,
        ODoHEndpoint::class,
        RpnProxy::class,
        WgHopMap::class,
        CountryConfig::class,
        SubscriptionStatus::class,
        SubscriptionStateHistory::class
    ],
    version = 1,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME = "bravedns.db"
        const val SCHEMA_VERSION = 1

    }

    suspend fun checkPoint() = compactAndCheckpoint()

    abstract fun appInfoDAO(): AppInfoDAO

    abstract fun dohEndpointsDAO(): DoHEndpointDAO

    abstract fun dnsCryptEndpointDAO(): DnsCryptEndpointDAO

    abstract fun dnsCryptRelayEndpointDAO(): DnsCryptRelayEndpointDAO

    abstract fun dnsProxyEndpointDAO(): DnsProxyEndpointDAO

    abstract fun proxyEndpointDAO(): ProxyEndpointDAO

    abstract fun customDomainEndpointDAO(): CustomDomainDAO

    abstract fun customIpEndpointDao(): CustomIpDao

    abstract fun rethinkEndpointDao(): RethinkDnsEndpointDao

    abstract fun rethinkRemoteFileTagDao(): RethinkRemoteFileTagDao

    abstract fun rethinkLocalFileTagDao(): RethinkLocalFileTagDao

    abstract fun localBlocklistPacksMapDao(): LocalBlocklistPacksMapDao

    abstract fun remoteBlocklistPacksMapDao(): RemoteBlocklistPacksMapDao

    abstract fun wgConfigFilesDAO(): WgConfigFilesDAO

    abstract fun wgApplicationMappingDao(): ProxyApplicationMappingDAO

    abstract fun tcpProxyEndpointDao(): TcpProxyDAO

    abstract fun dotEndpointDao(): DoTEndpointDAO

    abstract fun odohEndpointDao(): ODoHEndpointDAO

    abstract fun rpnProxyDao(): RpnProxyDao

    abstract fun wgHopMapDao(): WgHopMapDao

    abstract fun countryConfigDao(): CountryConfigDAO

    abstract fun subscriptionStatusDao(): SubscriptionStatusDao

    abstract fun subscriptionStateHistoryDao(): SubscriptionStateHistoryDao

    fun appInfoRepository() = AppInfoRepository(appInfoDAO())

    fun dohEndpointRepository() = DoHEndpointRepository(dohEndpointsDAO())

    fun dnsCryptEndpointRepository() = DnsCryptEndpointRepository(dnsCryptEndpointDAO())

    fun dnsCryptRelayEndpointRepository() =
        DnsCryptRelayEndpointRepository(dnsCryptRelayEndpointDAO())

    fun dnsProxyEndpointRepository() = DnsProxyEndpointRepository(dnsProxyEndpointDAO())

    fun proxyEndpointRepository() = ProxyEndpointRepository(proxyEndpointDAO())

    fun customDomainRepository() = CustomDomainRepository(customDomainEndpointDAO())

    fun customIpRepository() = CustomIpRepository(customIpEndpointDao())

    fun rethinkEndpointRepository() = RethinkDnsEndpointRepository(rethinkEndpointDao())

    fun rethinkRemoteFileTagRepository() = RethinkRemoteFileTagRepository(rethinkRemoteFileTagDao())

    fun rethinkLocalFileTagRepository() = RethinkLocalFileTagRepository(rethinkLocalFileTagDao())

    fun localBlocklistPacksMapRepository() =
        LocalBlocklistPacksMapRepository(localBlocklistPacksMapDao())

    fun remoteBlocklistPacksMapRepository() =
        RemoteBlocklistPacksMapRepository(remoteBlocklistPacksMapDao())

    fun wgConfigFilesRepository() = WgConfigFilesRepository(wgConfigFilesDAO())

    fun wgApplicationMappingRepository() = ProxyAppMappingRepository(wgApplicationMappingDao())

    fun tcpProxyEndpointRepository() = TcpProxyRepository(tcpProxyEndpointDao())

    fun dotEndpointRepository() = DoTEndpointRepository(dotEndpointDao())

    fun odohEndpointRepository() = ODoHEndpointRepository(odohEndpointDao())

    fun rpnProxyRepository() = RpnProxyRepository(rpnProxyDao())

    fun wgHopMapRepository() = WgHopMapRepository(wgHopMapDao())

    fun countryConfigRepository() = CountryConfigRepository(countryConfigDao())

    fun subscriptionStatusRepository() = SubscriptionStatusRepository(subscriptionStatusDao())

}

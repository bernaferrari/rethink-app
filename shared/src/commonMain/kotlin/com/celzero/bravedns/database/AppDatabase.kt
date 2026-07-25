/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

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
import com.celzero.bravedns.util.Constants

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
    version = 30,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME = "bravedns.db"
        internal const val DATABASE_PATH = "database/rethink_v22.db"
        private const val PRAGMA = "pragma wal_checkpoint(full)"

        // setJournalMode() is added as part of issue #344
        // modified the journal mode from TRUNCATE to AUTOMATIC.
        // The actual value will be TRUNCATE when the it is a low-RAM device.
        // Otherwise, WRITE_AHEAD_LOGGING will be used.
        // Ref:
        // https://developer.android.com/reference/android/arch/persistence/room/RoomDatabase.JournalMode#automatic

        internal val roomCallback: Callback =
            object : Callback() {
                override suspend fun onCreate(connection: SQLiteConnection) {
                    super.onCreate(connection)
                    println("AppDatabase")
                }

                override suspend fun onDestructiveMigration(connection: SQLiteConnection) {
                    super.onDestructiveMigration(connection)
                    println("AppDatabase")
                }

                override suspend fun onOpen(connection: SQLiteConnection) {
                    super.onOpen(connection)
                    println("AppDatabase")
                }
            }

        internal val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DELETE from AppInfo")
                    connection.execSQL("DELETE from CategoryInfo")
                    connection.execSQL(
                        "CREATE TABLE 'CategoryInfo' ( 'categoryName' TEXT NOT NULL, 'numberOFApps' INTEGER NOT NULL,'numOfAppsBlocked' INTEGER NOT NULL, 'isInternetBlocked' INTEGER NOT NULL, PRIMARY KEY (categoryName)) "
                    )
                }
            }

        internal val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DELETE from AppInfo ")
                    connection.execSQL("DELETE from CategoryInfo")
                    connection.execSQL("DROP TABLE if exists ConnectionTracker")
                    connection.execSQL(
                        "CREATE TABLE 'ConnectionTracker' ('id' INTEGER NOT NULL,'appName' TEXT, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT, 'port' INTEGER NOT NULL, 'protocol' INTEGER NOT NULL,'isBlocked' INTEGER NOT NULL, 'flag' TEXT, 'timeStamp' INTEGER NOT NULL,PRIMARY KEY (id)  )"
                    )
                    connection.execSQL(
                        "CREATE TABLE 'BlockedConnections' ( 'id' INTEGER NOT NULL, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT, 'port' INTEGER NOT NULL, 'protocol' TEXT, PRIMARY KEY (id)) "
                    )
                }
            }

        internal val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE BlockedConnections ADD COLUMN isActive INTEGER DEFAULT 1 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE BlockedConnections ADD COLUMN ruleType TEXT DEFAULT 'RULE4' NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE BlockedConnections ADD COLUMN modifiedDateTime INTEGER DEFAULT 0  NOT NULL"
                    )
                    connection.execSQL("UPDATE BlockedConnections set ruleType = 'RULE5' where uid = -1000")
                    connection.execSQL("ALTER TABLE ConnectionTracker ADD COLUMN blockedByRule TEXT")
                    connection.execSQL(
                        "UPDATE ConnectionTracker set blockedByRule = 'RULE4' where uid <> -1000 and isBlocked = 1"
                    )
                    connection.execSQL(
                        "UPDATE ConnectionTracker set blockedByRule = 'RULE5' where uid = -1000  and isBlocked = 1"
                    )
                    connection.execSQL(
                        "ALTER TABLE AppInfo add column whiteListUniv1 INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE AppInfo add column whiteListUniv2 INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE AppInfo add column isExcluded INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "CREATE TABLE 'DoHEndpoint' ( 'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'dohName' TEXT NOT NULL, 'dohURL' TEXT NOT NULL,'dohExplanation' TEXT, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL,'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL) "
                    )
                    connection.execSQL(
                        "CREATE TABLE 'DNSCryptEndpoint' ( 'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'dnsCryptName' TEXT NOT NULL, 'dnsCryptURL' TEXT NOT NULL,'dnsCryptExplanation' TEXT, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL,'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL) "
                    )
                    connection.execSQL(
                        "CREATE TABLE 'DNSCryptRelayEndpoint' ( 'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'dnsCryptRelayName' TEXT NOT NULL, 'dnsCryptRelayURL' TEXT NOT NULL,'dnsCryptRelayExplanation' TEXT, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL,'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL) "
                    )
                    connection.execSQL(
                        "CREATE TABLE 'DNSProxyEndpoint' ( 'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'proxyName' TEXT NOT NULL, 'proxyType' TEXT NOT NULL,'proxyAppName' TEXT , 'proxyIP' TEXT, 'proxyPort' INTEGER NOT NULL, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL,'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL) "
                    )
                    connection.execSQL(
                        "CREATE TABLE 'ProxyEndpoint' ( 'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'proxyName' TEXT NOT NULL,'proxyMode' INTEGER NOT NULL, 'proxyType' TEXT NOT NULL,'proxyAppName' TEXT , 'proxyIP' TEXT, 'userName' TEXT , 'password' TEXT, 'proxyPort' INTEGER NOT NULL, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL , 'isUDP' INTEGER NOT NULL,'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL) "
                    )
                    // Perform insert of endpoints
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DoHEndpoint(id,dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values(1,'Cloudflare','https://cloudflare-dns.com/dns-query','Does not block any DNS requests. Uses Cloudflare''s 1.1.1.1 DNS endpoint.',0,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DoHEndpoint(id,dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values(2,'Cloudflare Family','https://family.cloudflare-dns.com/dns-query','Blocks malware and adult content. Uses Cloudflare''s 1.1.1.3 DNS endpoint.',0,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DoHEndpoint(id,dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values(3,'Cloudflare Security','https://security.cloudflare-dns.com/dns-query','Blocks malicious content. Uses Cloudflare''s 1.1.1.2 DNS endpoint.',0,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DoHEndpoint(id,dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values(4,'RethinkDNS Basic (default)','https://basic.bravedns.com/1:YBcgAIAQIAAIAABgIAA=','Blocks malware and more. Uses RethinkDNS''s non-configurable basic endpoint.',1,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DoHEndpoint(id,dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values(5,'RethinkDNS Plus','https://basic.bravedns.com/','Configurable DNS endpoint: Provides in-depth analytics of your Internet traffic, allows you to set custom rules and more.',0,0,0,0)"
                    )
                }
            }

        internal val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DELETE from DNSProxyEndpoint")
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://basic.bravedns.com/1:wBdgAIoBoB02kIAA5HI=' where id = 4"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptEndpoint set dnsCryptName='Quad9', dnsCryptURL='sdns://AQYAAAAAAAAAEzE0OS4xMTIuMTEyLjEwOjg0NDMgZ8hHuMh1jNEgJFVDvnVnRt803x2EwAuMRwNo34Idhj4ZMi5kbnNjcnlwdC1jZXJ0LnF1YWQ5Lm5ldA',dnsCryptExplanation='Quad9 (anycast) no-dnssec/no-log/no-filter 9.9.9.10 / 149.112.112.10' where id=5"
                    )
                    connection.execSQL(
                        "INSERT into DNSProxyEndpoint values (1,'Google','External','Nobody','8.8.8.8',53,0,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT into DNSProxyEndpoint values (2,'Cloudflare','External','Nobody','1.1.1.1',53,0,0,0,0)"
                    )
                    connection.execSQL(
                        "INSERT into DNSProxyEndpoint values (3,'Quad9','External','Nobody','9.9.9.9',53,0,0,0,0)"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptEndpoint set dnsCryptName ='Cleanbrowsing Family' where id = 1"
                    )
                    connection.execSQL("UPDATE DNSCryptEndpoint set dnsCryptName ='Adguard' where id = 2")
                    connection.execSQL(
                        "UPDATE DNSCryptEndpoint set dnsCryptName ='Adguard Family' where id = 3"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptEndpoint set dnsCryptName ='Cleanbrowsing Security' where id = 4"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Anon-AMS-NL' where id = 1"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Anon-CS-FR' where id = 2"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Anon-CS-SE' where id = 3"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Anon-CS-USCA' where id = 4"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Anon-Tiarap' where id = 5"
                    )
                }
            }

        internal val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE 'DNSLogs' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'queryStr' TEXT NOT NULL, 'time' INTEGER NOT NULL, 'flag' TEXT NOT NULL, 'resolver' TEXT NOT NULL, 'latency' INTEGER NOT NULL, 'typeName' TEXT NOT NULL, 'isBlocked' INTEGER NOT NULL, 'blockLists' LONGTEXT NOT NULL,  'serverIP' TEXT NOT NULL, 'relayIP' TEXT NOT NULL, 'responseTime' INTEGER NOT NULL, 'response' TEXT NOT NULL, 'status' TEXT NOT NULL,'dnsType' INTEGER NOT NULL) "
                    )
                    // https://basic.bravedns.com/1:YBIgACABAHAgAA== - New block list configured
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://basic.bravedns.com/1:YBcgAIAQIAAIAABgIAA=' where id = 4"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptEndpoint set dnsCryptName='Quad9', dnsCryptURL='sdns://AQMAAAAAAAAADDkuOS45Ljk6ODQ0MyBnyEe4yHWM0SAkVUO-dWdG3zTfHYTAC4xHA2jfgh2GPhkyLmRuc2NyeXB0LWNlcnQucXVhZDkubmV0',dnsCryptExplanation='Quad9 (anycast) dnssec/no-log/filter 9.9.9.9 / 149.112.112.9' where id=5"
                    )
                    connection.execSQL(
                        "ALTER TABLE CategoryInfo add column numOfAppWhitelisted INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "ALTER TABLE CategoryInfo add column numOfAppsExcluded INTEGER DEFAULT 0 NOT NULL"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Netherlands' where id = 1"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='France' where id = 2"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Sweden' where id = 3"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='US - Los Angeles, CA' where id = 4"
                    )
                    connection.execSQL(
                        "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayName ='Singapore' where id = 5"
                    )
                }
            }

        internal val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://security.cloudflare-dns.com/dns-query' where id = 3"
                    )
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://basic.bravedns.com/1:YBcgAIAQIAAIAABgIAA=' where id = 4"
                    )
                }
            }

        /**
         * For the version 053-1. Created a view for the AppInfo table so that the read will be
         * minimized. Also deleting the uid=0 row from AppInfo table. In earlier version the UID=0
         * is added as default and not used. Now the UID=0(ANDROID) is added to the non-app
         * category.
         */
        internal val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE VIEW `AppInfoView` AS select appName, appCategory, isInternetAllowed, whiteListUniv1, isExcluded from AppInfo"
                    )
                    connection.execSQL("UPDATE AppInfo set appCategory = 'System Components' where uid = 0")
                    connection.execSQL(
                        "DELETE from AppInfo where appName = 'ANDROID' and appCategory = 'System Components'"
                    )
                }
            }

        internal val MIGRATION_8_9: Migration =
            object : Migration(8, 9) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://basic.bravedns.com/1:YASAAQBwIAA=' where id = 4"
                    )
                }
            }

        internal val MIGRATION_9_10: Migration =
            object : Migration(9, 10) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL  = 'https://basic.bravedns.com/1:IAAgAA==' where id = 4"
                    )
                }
            }

        internal val MIGRATION_10_11: Migration =
            object : Migration(10, 11) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE DNSLogs add column responseIps TEXT DEFAULT '' NOT NULL"
                    )
                    connection.execSQL(
                        "CREATE TABLE 'CustomDomain' ( 'domain' TEXT NOT NULL, 'ips' TEXT NOT NULL, 'status' INTEGER NOT NULL, 'type' INTEGER NOT NULL, 'createdTs' INTEGER NOT NULL, 'deletedTs' INTEGER NOT NULL, 'version' INTEGER NOT NULL, PRIMARY KEY (domain)) "
                    )
                }
            }

        internal val MIGRATION_11_12: Migration =
            object : Migration(11, 12) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    addMoreDohToList(connection)
                    modifyAppInfoTableSchema(connection)
                    modifyBlockedConnectionsTable(connection)
                    connection.execSQL("DROP VIEW AppInfoView")
                    connection.execSQL("DROP TABLE if exists CategoryInfo")
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohURL = `replace`(dohURL,'bravedns','rethinkdns')"
                    )
                    modifyConnectionTrackerTable(connection)
                    createRethinkDnsTable(connection)
                    removeRethinkFromDohList(connection)
                    updateDnscryptStamps(connection)
                    createRethinkFileTagTables(connection)
                    insertIpv6DnsProxyEndpoint(connection)
                }

                private suspend fun updateDnscryptStamps(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptURL='sdns://AQMAAAAAAAAAEjE0OS4xMTIuMTEyLjk6ODQ0MyBnyEe4yHWM0SAkVUO-dWdG3zTfHYTAC4xHA2jfgh2GPhkyLmRuc2NyeXB0LWNlcnQucXVhZDkubmV0' where id=5"
                        )
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptURL='sdns://AQMAAAAAAAAAETk0LjE0MC4xNC4xNTo1NDQzILgxXdexS27jIKRw3C7Wsao5jMnlhvhdRUXWuMm1AFq6ITIuZG5zY3J5cHQuZmFtaWx5Lm5zMS5hZGd1YXJkLmNvbQ' where id=3"
                        )
                    }
                }

                // add more doh options as default
                private suspend fun addMoreDohToList(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "INSERT OR REPLACE INTO DoHEndpoint(dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values('Google','https://dns.google/dns-query','Traditional DNS queries and replies are sent over UDP or TCP without encryption, making them subject to surveillance, spoofing, and DNS-based Internet filtering.',0,0,0,0)"
                        )
                        execSQL(
                            "INSERT OR REPLACE INTO DoHEndpoint(dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values('CleanBrowsing Family','https://doh.cleanbrowsing.org/doh/family-filter/','Family filter blocks access to all adult, pornographic and explicit sites. It also blocks proxy and VPN domains that could be used to bypass our filters. Mixed content sites (like Reddit) are also blocked. Google, Bing and Youtube are set to the Safe Mode.',0,0,0,0)"
                        )
                        execSQL(
                            "INSERT OR REPLACE INTO DoHEndpoint(dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values('CleanBrowsing Adult','https://doh.cleanbrowsing.org/doh/adult-filter/','Adult filter blocks access to all adult, pornographic and explicit sites. It does not block proxy or VPNs, nor mixed-content sites. Sites like Reddit are allowed. Google and Bing are set to the Safe Mode.',0,0,0,0)"
                        )
                        execSQL(
                            "INSERT OR REPLACE INTO DoHEndpoint(dohName,dohURL,dohExplanation, isSelected,isCustom,modifiedDataTime,latency) values('Quad9 Secure','https://dns.quad9.net/dns-query','Quad9 routes your DNS queries through a secure network of servers around the globe.',0,0,0,0)"
                        )
                    }
                }

                // rename blockedConnections table to CustomIp
                private suspend fun modifyBlockedConnectionsTable(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'CustomIp' ('uid' INTEGER NOT NULL, 'ipAddress' TEXT DEFAULT '' NOT NULL, 'port' INTEGER DEFAULT '' NOT NULL, 'protocol' TEXT DEFAULT '' NOT NULL, 'isActive' INTEGER DEFAULT 1 NOT NULL, 'status' INTEGER DEFAULT 1 NOT NULL,'ruleType' INTEGER DEFAULT 0 NOT NULL, 'wildcard' INTEGER DEFAULT 0 NOT NULL, 'modifiedDateTime' INTEGER DEFAULT 0 NOT NULL, PRIMARY KEY(uid, ipAddress, port, protocol))"
                        )
                        execSQL(
                            "INSERT INTO 'CustomIp' SELECT uid, ipAddress, port, protocol, isActive, 1, 0, 0, modifiedDateTime from BlockedConnections"
                        )
                        execSQL("DROP TABLE if exists BlockedConnections")
                    }
                }

                private suspend fun modifyAppInfoTableSchema(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'AppInfo_backup' ('packageInfo' TEXT PRIMARY KEY NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 0, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'metered' INTEGER NOT NULL DEFAULT 0, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0,  'isInternetAllowed' INTEGER NOT NULL, 'whiteListUniv1' INTEGER NOT NULL, 'isExcluded' INTEGER NOT NULL)"
                        )
                        execSQL(
                            "INSERT INTO AppInfo_backup SELECT packageInfo, appName, uid, isSystemApp, 0, appCategory, wifiDataUsed, mobileDataUsed, 0, isScreenOff, isBackgroundEnabled, isInternetAllowed, whiteListUniv1, isExcluded FROM AppInfo"
                        )
                        execSQL(
                            "UPDATE AppInfo_backup set firewallStatus = 0 where isInternetAllowed = 1"
                        )
                        execSQL(
                            "UPDATE AppInfo_backup set firewallStatus = 1 where isInternetAllowed = 0"
                        )
                        execSQL(
                            "UPDATE AppInfo_backup set firewallStatus = 2 where whiteListUniv1 = 1"
                        )
                        execSQL("UPDATE AppInfo_backup set firewallStatus = 3 where isExcluded = 1")
                        execSQL(" DROP TABLE if exists AppInfo")
                        execSQL(
                            "CREATE TABLE 'AppInfo' ('packageInfo' TEXT PRIMARY KEY NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 0, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'metered' INTEGER NOT NULL DEFAULT 0, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0)"
                        )
                        execSQL(
                            "INSERT INTO AppInfo SELECT packageInfo, appName, uid, isSystemApp, firewallStatus, appCategory, wifiDataUsed, mobileDataUsed, metered, screenOffAllowed, backgroundAllowed FROM AppInfo_backup"
                        )

                        execSQL("DROP TABLE AppInfo_backup")
                    }
                }

                // introduce NOT NULL property for columns in the schema, alter table query cannot
                // add the not-null to the schema, so creating a backup and recreating the table
                // during migration.
                private suspend fun modifyConnectionTrackerTable(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'ConnectionTracker_backup' ('id' INTEGER NOT NULL,'appName' TEXT DEFAULT '' NOT NULL, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT DEFAULT ''  NOT NULL, 'port' INTEGER NOT NULL, 'protocol' INTEGER NOT NULL,'isBlocked' INTEGER NOT NULL, 'blockedByRule' TEXT DEFAULT '' NOT NULL, 'flag' TEXT  DEFAULT '' NOT NULL, 'dnsQuery' TEXT DEFAULT '', 'timeStamp' INTEGER NOT NULL,PRIMARY KEY (id)  )"
                        )
                        execSQL(
                            "INSERT INTO ConnectionTracker_backup SELECT id, appName, uid, ipAddress, port, protocol, isBlocked, blockedByRule, flag, '', timeStamp from ConnectionTracker"
                        )
                        execSQL("DROP TABLE if exists ConnectionTracker")
                        execSQL(
                            "CREATE TABLE 'ConnectionTracker' ('id' INTEGER NOT NULL,'appName' TEXT DEFAULT '' NOT NULL, 'uid' INTEGER NOT NULL, 'ipAddress' TEXT DEFAULT ''  NOT NULL, 'port' INTEGER NOT NULL, 'protocol' INTEGER NOT NULL,'isBlocked' INTEGER NOT NULL, 'blockedByRule' TEXT DEFAULT '' NOT NULL, 'flag' TEXT  DEFAULT '' NOT NULL, 'dnsQuery' TEXT DEFAULT '', 'timeStamp' INTEGER NOT NULL,PRIMARY KEY (id)  )"
                        )
                        execSQL(
                            "INSERT INTO ConnectionTracker SELECT id, appName, uid, ipAddress, port, protocol, isBlocked, blockedByRule, flag, '',  timeStamp from ConnectionTracker_backup"
                        )
                        execSQL("DROP TABLE if exists ConnectionTracker_backup")
                    }
                }

                // create new table to store Rethink dns endpoint
                // contains both the global and app specific dns endpoints
                private suspend fun createRethinkDnsTable(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'RethinkDnsEndpoint' ('name' TEXT NOT NULL, 'url' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'desc' TEXT NOT NULL, 'isActive' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL, 'latency' INTEGER NOT NULL, 'blocklistCount' INTEGER NOT NULL DEFAULT 0,'modifiedDataTime' INTEGER NOT NULL,  PRIMARY KEY (name, url, uid))"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Default', 'https://basic.rethinkdns.com/1:IAAQAA==',  ${Constants.MISSING_UID}, 'Blocks over 100,000+ phishing, malvertising, malware, spyware, ransomware, cryptojacking and other threats.', '0', '0', '0', '1','1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Adult', 'https://basic.rethinkdns.com/1:EMABAADgIAA=', ${Constants.MISSING_UID}, 'Blocks over 30,000 adult websites.', '0', '0', '0','5', '1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Piracy', 'https://basic.rethinkdns.com/1:EID-BwCB', ${Constants.MISSING_UID}, 'Blocks torrent, dubious video streaming and file sharing websites.', '0', '0', '0','12', '1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Social Media', 'https://basic.rethinkdns.com/1:AEAAEA==', ${Constants.MISSING_UID}, 'Blocks popular social media including Facebook, Instagram, and WhatsApp.', '0', '0', '0','1', '1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Security', 'https://basic.rethinkdns.com/1:4AIAgAABAHAgAA==', ${Constants.MISSING_UID}, 'Blocks over 150,000 malware, ransomware, phishing and other threats.', '0', '0', '0','37', '1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Privacy', 'https://basic.rethinkdns.com/1:QAcCAIAcAhCkAg==', ${Constants.MISSING_UID}, 'Blocks over 100,000+ adware, spyware, and trackers through some of the most extensive blocklists.', '0', '0', '0','11', '1633624616715')"
                        )
                        execSQL(
                            "INSERT INTO 'RethinkDnsEndpoint' ( 'name', 'url', 'uid', 'desc', 'isActive', 'isCustom', 'latency', 'blocklistCount', 'modifiedDataTime' ) VALUES ( 'RDNS Plus', (Select dohurl from DoHEndpoint where id = 5), ${Constants.MISSING_UID}, 'User Configured', (select isSelected from DoHEndpoint where id = 5), '0', '1', '0', '1633624616715')"
                        )
                    }
                }

                private suspend fun createRethinkFileTagTables(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE RethinkRemoteFileTag ('value' INTEGER NOT NULL, 'uname' TEXT NOT NULL, 'vname' TEXT NOT NULL, 'group' TEXT NOT NULL, 'subg' TEXT NOT NULL, 'url' TEXT NOT NULL, 'show' INTEGER NOT NULL, 'entries' INTEGER NOT NULL, 'simpleTagId' INTEGER NOT NULL, 'isSelected' INTEGER NOT NULL,  PRIMARY KEY (value))"
                        )
                        execSQL(
                            "CREATE TABLE RethinkLocalFileTag ('value' INTEGER NOT NULL, 'uname' TEXT NOT NULL, 'vname' TEXT NOT NULL, 'group' TEXT NOT NULL, 'subg' TEXT NOT NULL, 'url' TEXT NOT NULL, 'show' INTEGER NOT NULL, 'entries' INTEGER NOT NULL,  'simpleTagId' INTEGER NOT NULL, 'isSelected' INTEGER NOT NULL, PRIMARY KEY (value))"
                        )
                    }
                }

                // remove the rethink doh from the list
                private suspend fun removeRethinkFromDohList(connection: SQLiteConnection) {
                    with(connection) { execSQL("DELETE from DoHEndpoint where id in (4,5)") }
                }

                private suspend fun insertIpv6DnsProxyEndpoint(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "INSERT OR REPLACE INTO DNSProxyEndpoint(proxyName, proxyType, proxyAppName, proxyIP, proxyPort, isSelected, isCustom, modifiedDataTime,latency) values ('Google IPv6','External','Nobody','2001:4860:4860::8888',53,0,0,0,0)"
                        )
                        execSQL(
                            "INSERT OR REPLACE INTO DNSProxyEndpoint(proxyName, proxyType, proxyAppName, proxyIP, proxyPort, isSelected, isCustom, modifiedDataTime,latency) values ('Cloudflare IPv6','External','Nobody','2606:4700:4700::1111',53,0,0,0,0)"
                        )
                        execSQL(
                            "INSERT OR REPLACE INTO DNSProxyEndpoint(proxyName, proxyType, proxyAppName, proxyIP, proxyPort, isSelected, isCustom, modifiedDataTime,latency) values ('Quad9 IPv6','External','Nobody','2620:fe::fe',53,0,0,0,0)"
                        )
                    }
                }
            }

        internal val MIGRATION_12_13: Migration =
            object : Migration(12, 13) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "INSERT OR REPLACE INTO DNSProxyEndpoint(proxyName, proxyType, proxyAppName, proxyIP, proxyPort, isSelected, isCustom, modifiedDataTime,latency) values ('Orbot','External','org.torproject.android','127.0.0.1',5400,0,0,0,0)"
                    )
                }
            }

        // migration part of v053k
        internal val MIGRATION_13_14: Migration =
            object : Migration(13, 14) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // modify the default blocklist to OISD
                    connection.execSQL(
                        "UPDATE RethinkDnsEndpoint set url  = 'https://basic.rethinkdns.com/1:IAAgAA==' where name = 'RDNS Default' and isCustom = 0"
                    )
                    connection.execSQL(
                        "Update AppInfo set appCategory = 'System Services' where appCategory = 'Non-App System' and isSystemApp = 1"
                    )
                    connection.execSQL("Update RethinkDnsEndpoint set url = REPLACE(url, 'basic', 'sky')")
                }
            }

        // migration part of v053l
        internal val MIGRATION_14_15: Migration =
            object : Migration(14, 15) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE RethinkLocalFileTag add column pack TEXT DEFAULT ''")
                    connection.execSQL("ALTER TABLE RethinkRemoteFileTag add column pack TEXT DEFAULT ''")
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohExplanation = 'Family filter blocks access to all adult, graphic and explicit sites. It also blocks proxy and VPN domains that could be used to bypass our filters. Mixed content sites (like Reddit) are also blocked. Google, Bing and Youtube are set to the Safe Mode.' where dohName = 'CleanBrowsing Family'"
                    )
                    connection.execSQL(
                        "UPDATE DoHEndpoint set dohExplanation = 'Adult filter blocks access to all adult, graphic and explicit sites. It does not block proxy or VPNs, nor mixed-content sites. Sites like Reddit are allowed. Google and Bing are set to the Safe Mode.'  where dohName = 'CleanBrowsing Adult'"
                    )
                }
            }

        // migration part of v053m
        internal val MIGRATION_15_16: Migration =
            object : Migration(15, 16) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    modifyAppInfo(connection)
                    connection.execSQL("ALTER TABLE RethinkLocalFileTag add column level TEXT")
                    connection.execSQL("ALTER TABLE RethinkRemoteFileTag add column level TEXT")
                    connection.execSQL(
                        "CREATE TABLE 'LocalBlocklistPacksMap' ( 'pack' TEXT NOT NULL, 'level' INTEGER NOT NULL DEFAULT 0, 'blocklistIds' TEXT NOT NULL, 'group' TEXT NOT NULL, PRIMARY KEY (pack, level)) "
                    )
                    connection.execSQL(
                        "CREATE TABLE 'RemoteBlocklistPacksMap' ( 'pack' TEXT NOT NULL, 'level' INTEGER NOT NULL DEFAULT 0, 'blocklistIds' TEXT NOT NULL, 'group' TEXT NOT NULL, PRIMARY KEY (pack, level)) "
                    )
                    connection.execSQL(
                        "UPDATE RethinkDnsEndpoint set url = case when url = 'https://max.rethinkdns.com/1:IAAgAA=='  then 'https://max.rethinkdns.com/rec' else 'https://sky.rethinkdns.com/rec' end where name = 'RDNS Default' and isCustom = 0"
                    )
                }

                private suspend fun modifyAppInfo(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'AppInfo_backup' ('packageName' TEXT NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 0, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'metered' INTEGER NOT NULL DEFAULT 0, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0,  PRIMARY KEY(uid, packageName))"
                        )
                        execSQL(
                            "INSERT INTO AppInfo_backup SELECT packageInfo, appName, uid, isSystemApp, firewallStatus, appCategory, wifiDataUsed, mobileDataUsed, metered, screenOffAllowed, backgroundAllowed FROM AppInfo"
                        )
                        execSQL(" DROP TABLE if exists AppInfo")
                        execSQL(
                            "CREATE TABLE 'AppInfo' ('packageName' TEXT NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 0, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'metered' INTEGER NOT NULL DEFAULT 0, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0,  PRIMARY KEY(uid, packageName))"
                        )
                        execSQL(
                            "INSERT INTO AppInfo SELECT packageName, appName, uid, isSystemApp, firewallStatus, appCategory, wifiDataUsed, mobileDataUsed, metered, screenOffAllowed, backgroundAllowed FROM AppInfo_backup"
                        )
                        execSQL("DROP TABLE AppInfo_backup")
                    }
                }
            }

        // migration part of v054
        internal val MIGRATION_16_17: Migration =
            object : Migration(16, 17) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DROP table if exists CustomDomain")
                    connection.execSQL(
                        "CREATE TABLE 'CustomDomain' ( 'domain' TEXT NOT NULL, 'uid' INT NOT NULL,  'ips' TEXT NOT NULL, 'status' INTEGER NOT NULL, 'type' INTEGER NOT NULL, 'modifiedTs' INTEGER NOT NULL, 'deletedTs' INTEGER NOT NULL, 'version' INTEGER NOT NULL, PRIMARY KEY (domain, uid)) "
                    )
                    modifyAppInfo(connection)
                    modifyRethinkDnsUrls(connection)
                    updateDnscryptStamps(connection)
                }

                private suspend fun updateDnscryptStamps(connection: SQLiteConnection) {
                    connection.execSQL(
                        "update DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAFDE4NS4yMjguMTY4LjE2ODo4NDQzILysMvrVQ2kXHwgy1gdQJ8MgjO7w6OmflBjcd2Bl1I8pEWNsZWFuYnJvd3Npbmcub3Jn' where dnsCryptName = 'Cleanbrowsing Family' and id = 1"
                    )
                    connection.execSQL(
                        "update DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAETk0LjE0MC4xNC4xNDo1NDQzINErR_JS3PLCu_iZEIbq95zkSV2LFsigxDIuUso_OQhzIjIuZG5zY3J5cHQuZGVmYXVsdC5uczEuYWRndWFyZC5jb20' where dnsCryptName = 'Adguard'  and id = 2"
                    )
                    connection.execSQL(
                        "update DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAETk0LjE0MC4xNC4xNTo1NDQzILgxXdexS27jIKRw3C7Wsao5jMnlhvhdRUXWuMm1AFq6ITIuZG5zY3J5cHQuZmFtaWx5Lm5zMS5hZGd1YXJkLmNvbQ' where dnsCryptName = 'Adguard Family'  and id = 3"
                    )
                    connection.execSQL(
                        "update DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAFDE0OS4xMTIuMTEyLjExMjo4NDQzIGfIR7jIdYzRICRVQ751Z0bfNN8dhMALjEcDaN-CHYY-GTIuZG5zY3J5cHQtY2VydC5xdWFkOS5uZXQ', dnsCryptName = 'Quad9 Security', dnsCryptExplanation = 'Quad9 (anycast) dnssec/no-log/filter 9.9.9.9 - 149.112.112.9 - 149.112.112.112' where dnsCryptName = 'Cleanbrowsing Security'  and id = 4"
                    )
                    connection.execSQL(
                        "update DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAEzE0OS4xMTIuMTEyLjExOjg0NDMgZ8hHuMh1jNEgJFVDvnVnRt803x2EwAuMRwNo34Idhj4ZMi5kbnNjcnlwdC1jZXJ0LnF1YWQ5Lm5ldA', dnsCryptExplanation = 'Quad9 (anycast) no-dnssec/no-log/no-filter/ecs 9.9.9.12 - 149.112.112.12' where dnsCryptName = 'Quad9' and id = 5"
                    )
                }

                private suspend fun modifyRethinkDnsUrls(connection: SQLiteConnection) {
                    connection.execSQL(
                        "UPDATE RethinkDnsEndpoint set url = case when url = 'https://max.rethinkdns.com/1:EMABAADgIAA='  then 'https://max.rethinkdns.com/pec' else 'https://sky.rethinkdns.com/pec' end where name = 'RDNS Adult' and isCustom = 0"
                    )
                    connection.execSQL(
                        "UPDATE RethinkDnsEndpoint set url = case when url = 'https://max.rethinkdns.com/1:4AIAgAABAHAgAA=='  then 'https://max.rethinkdns.com/sec' else 'https://sky.rethinkdns.com/sec' end where name = 'RDNS Security' and isCustom = 0"
                    )
                    connection.execSQL(
                        "UPDATE RethinkDnsEndpoint set blocklistCount = 0 where isCustom = 0 and name != 'RDNS Plus'"
                    )
                }

                private suspend fun modifyAppInfo(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "CREATE TABLE 'AppInfo_backup' ('packageName' TEXT NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 5, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'connectionStatus' INTEGER NOT NULL DEFAULT 3, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0,  PRIMARY KEY(uid, packageName))"
                        )
                        execSQL(
                            "INSERT INTO AppInfo_backup SELECT packageName, appName, uid, isSystemApp, firewallStatus, appCategory, wifiDataUsed, mobileDataUsed, metered, screenOffAllowed, backgroundAllowed FROM AppInfo"
                        )
                        execSQL(" DROP TABLE if exists AppInfo")
                        execSQL(
                            "CREATE TABLE 'AppInfo' ('packageName' TEXT NOT NULL, 'appName' TEXT NOT NULL, 'uid' INTEGER NOT NULL, 'isSystemApp' INTEGER NOT NULL, 'firewallStatus' INTEGER NOT NULL DEFAULT 5, 'appCategory' TEXT NOT NULL, 'wifiDataUsed' INTEGER NOT NULL, 'mobileDataUsed' INTEGER NOT NULL, 'connectionStatus' INTEGER NOT NULL DEFAULT 3, 'screenOffAllowed' INTEGER NOT NULL DEFAULT 0, 'backgroundAllowed' INTEGER NOT NULL DEFAULT 0,  PRIMARY KEY(uid, packageName))"
                        )
                        execSQL(
                            "INSERT INTO AppInfo SELECT packageName, appName, uid, isSystemApp, firewallStatus, appCategory, wifiDataUsed, mobileDataUsed, connectionStatus, screenOffAllowed, backgroundAllowed FROM AppInfo_backup"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 5, connectionStatus = 3 where firewallStatus = 0"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 2, connectionStatus = 3 where firewallStatus = 2"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 3, connectionStatus = 3 where firewallStatus = 3"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 4, connectionStatus = 3 where firewallStatus = 4"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 7, connectionStatus = 3 where firewallStatus = 7"
                        )
                        execSQL("UPDATE AppInfo set firewallStatus = 5 where firewallStatus = 1")
                        execSQL("DROP TABLE AppInfo_backup")
                    }
                }
            }

        internal val MIGRATION_17_18: Migration =
            object : Migration(17, 18) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 2, connectionStatus = 3 where firewallStatus = 2"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 3, connectionStatus = 3 where firewallStatus = 3"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 4, connectionStatus = 3 where firewallStatus = 4"
                        )
                        execSQL(
                            "UPDATE AppInfo set firewallStatus = 7, connectionStatus = 3 where firewallStatus = 7"
                        )
                    }
                }
            }

        internal fun migration1819(): Migration =
            object : Migration(18, 19) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    with(connection) {
                        execSQL("DROP TABLE IF EXISTS WgConfigFiles")
                        execSQL("DROP TABLE IF EXISTS ProxyApplicationMapping")
                        execSQL(
                            "CREATE TABLE WgConfigFiles('id' INTEGER NOT NULL, 'name' TEXT NOT NULL, 'configPath' TEXT NOT NULL, 'serverResponse' TEXT NOT NULL, 'isActive' INTEGER NOT NULL, 'isDeletable' INTEGER NOT NULL, PRIMARY KEY (id))"
                        )
                        execSQL(
                            "CREATE TABLE ProxyApplicationMapping('uid' INTEGER NOT NULL, 'packageName' TEXT NOT NULL, 'appName' TEXT NOT NULL, 'proxyName' TEXT NOT NULL, 'isActive' INTEGER NOT NULL, 'proxyId' TEXT NOT NULL ,PRIMARY KEY (uid, packageName, proxyId))"
                        )
                        execSQL(
                            "INSERT INTO ProxyApplicationMapping SELECT uid, packageName, appName, '', 1, '' FROM AppInfo order by lower(appName)"
                        )
                        execSQL("DROP TABLE IF EXISTS TcpProxyEndpoint")
                        execSQL(
                            "CREATE TABLE TcpProxyEndpoint ('id' INTEGER NOT NULL, 'name' TEXT NOT NULL, 'token' TEXT NOT NULL, 'url' TEXT NOT NULL, 'paymentStatus' INTEGER NOT NULL, 'isActive' INTEGER NOT NULL, PRIMARY KEY (id))"
                        )
                        execSQL(
                            "INSERT INTO TcpProxyEndpoint(id, name, token, url, paymentStatus, isActive) VALUES(0, 'Default', '', 'proxy.nile.workers.dev/ws/', 0, 0)"
                        )
                        execSQL(
                            "ALTER TABLE AppInfo add column downloadBytes INTEGER DEFAULT 0 NOT NULL"
                        )
                        execSQL(
                            "ALTER TABLE AppInfo add column uploadBytes INTEGER DEFAULT 0 NOT NULL"
                        )
                        // doh
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.cloudflare_dns_desc' where dohName = 'Cloudflare'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.cloudflare_family_dns_desc' where dohName = 'Cloudflare Family'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.cloudflare_security_dns_desc' where dohName = 'Cloudflare Security'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.google_dns_desc' where dohName = 'Google'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.cleanbrowsing_family_dns_desc' where dohName = 'CleanBrowsing Family'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.cleanbrowsing_adult_dns_desc' where dohName = 'CleanBrowsing Adult'"
                        )
                        execSQL(
                            "UPDATE DoHEndpoint set dohExplanation = 'R.string.quad9_dns_desc' where dohName = 'Quad9 Secure'"
                        )
                        // dns crypt
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptExplanation = 'R.string.crypt_cleanbrowsing_family_desc' where dnsCryptName = 'Cleanbrowsing Family'"
                        )
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptExplanation = 'R.string.crypt_adguard_desc' where dnsCryptName = 'Adguard'"
                        )
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptExplanation = 'R.string.crypt_adguard_family_desc' where dnsCryptName = 'Adguard Family'"
                        )
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptExplanation = 'R.string.crypt_quad9_security_desc' where dnsCryptName = 'Quad9 Security'"
                        )
                        execSQL(
                            "UPDATE DNSCryptEndpoint set dnsCryptExplanation = 'R.string.crypt_quad9_desc' where dnsCryptName = 'Quad9'"
                        )
                        // dns crypt relay
                        execSQL(
                            "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayExplanation = 'R.string.crypt_relay_netherlands' where dnsCryptRelayName = 'Netherlands'"
                        )
                        execSQL(
                            "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayExplanation = 'R.string.crypt_relay_france' where dnsCryptRelayName = 'France'"
                        )
                        execSQL(
                            "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayExplanation = 'R.string.crypt_relay_sweden' where dnsCryptRelayName = 'Sweden'"
                        )
                        execSQL(
                            "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayExplanation = 'R.string.crypt_relay_us' where dnsCryptRelayName = 'US - Los Angeles, CA'"
                        )
                        execSQL(
                            "UPDATE DNSCryptRelayEndpoint set dnsCryptRelayExplanation = 'R.string.crypt_relay_singapore' where dnsCryptRelayName = 'Singapore'"
                        )
                        execSQL(
                            "ALTER TABLE DoHEndpoint ADD COLUMN isSecure INTEGER NOT NULL DEFAULT 1"
                        )
                        execSQL(
                            "UPDATE DNSProxyEndpoint set proxyAppName = 'None' where proxyAppName = 'Nobody'"
                        )
                    }
                }
            }

        internal val MIGRATION_19_20: Migration =
            object : Migration(19, 20) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // quad9
                    connection.execSQL(
                        "UPDATE DnsCryptEndpoint set dnsCryptURL = 'sdns://AQYAAAAAAAAADTkuOS45LjEyOjg0NDMgZ8hHuMh1jNEgJFVDvnVnRt803x2EwAuMRwNo34Idhj4ZMi5kbnNjcnlwdC1jZXJ0LnF1YWQ5Lm5ldA' where id = 5"
                    )
                    // quad9 security
                    connection.execSQL(
                        "UPDATE DnsCryptEndpoint set dnsCryptURL = 'sdns://AQMAAAAAAAAAEjE0OS4xMTIuMTEyLjk6ODQ0MyBnyEe4yHWM0SAkVUO-dWdG3zTfHYTAC4xHA2jfgh2GPhkyLmRuc2NyeXB0LWNlcnQucXVhZDkubmV0' where id = 4"
                    )
                    println("AppDatabase")
                }
            }

        internal val MIGRATION_20_21: Migration =
            object : Migration(20, 21) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE 'DoTEndpoint' ('id' INTEGER NOT NULL, 'name' TEXT NOT NULL, 'url' TEXT NOT NULL, 'desc' TEXT, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL, 'isSecure' INTEGER NOT NULL, 'latency' INTEGER NOT NULL, 'modifiedDataTime' INTEGER NOT NULL, PRIMARY KEY (id))"
                    )
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS 'ODoHEndpoint' ('id' INTEGER NOT NULL, 'name' TEXT NOT NULL, 'proxy' TEXT NOT NULL, 'resolver' TEXT NOT NULL, 'proxyIps' TEXT NOT NULL, 'desc' TEXT, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL, 'latency' INTEGER NOT NULL, 'modifiedDataTime' INTEGER NOT NULL, PRIMARY KEY (id))"
                    )
                    connection.execSQL("delete from ODoHEndpoint")
                    connection.execSQL("delete from DoTEndpoint")
                    // insert default odoh endpoints
                    connection.execSQL(
                        "INSERT INTO ODoHEndpoint(id, name, proxy, resolver, proxyIps, desc, isSelected, isCustom, latency, modifiedDataTime) VALUES(0, 'Cloudflare', '', 'https://odoh.cloudflare-dns.com/dns-query', '', 'Cloudflare ODoH server', 0, 0, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO ODoHEndpoint(id, name, proxy, resolver, proxyIps, desc, isSelected, isCustom, latency, modifiedDataTime) VALUES(1, 'ODoH Crypto', '', 'https://odoh.crypto.sx/dns-query', '', 'ODoH target server. Anycast, no logs. Backend hosted by Scaleway. Maintained by Frank Denis.', 0, 0, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO ODoHEndpoint(id, name, proxy, resolver, proxyIps, desc, isSelected, isCustom, latency, modifiedDataTime) VALUES(2, 'Ibksturm', '', 'https://ibksturm.synology.me/dns-query', '', 'ODoH target server hosted by Ibksturm. No logs, No Filter, DNSSEC.', 0, 0, 0, 0)"
                    )
                    // insert default DoT endpoints
                    connection.execSQL(
                        "INSERT INTO DoTEndpoint(id, name, url, desc, isSelected, isCustom, isSecure, latency, modifiedDataTime) VALUES(0, 'Cloudflare', 'tls://1dot1dot1dot1.cloudflare-dns.com', 'Cloudflare’s DNS over TLS. No blocking.', 0, 0, 1, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO DoTEndpoint(id, name, url, desc, isSelected, isCustom, isSecure, latency, modifiedDataTime) VALUES(1, 'Cloudflare family', 'tls://family.cloudflare-dns.com', 'Cloudflare’s DNS over TLS. Blocks Malware and Adult content.', 0, 0, 1, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO DoTEndpoint(id, name, url, desc, isSelected, isCustom, isSecure, latency, modifiedDataTime) VALUES(2, 'Adguard', 'tls://dns.adguard-dns.com', 'Cloudflare’s DNS over TLS. Block ads, tracking, and phishing.', 0, 0, 1, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO DoTEndpoint(id, name, url, desc, isSelected, isCustom, isSecure, latency, modifiedDataTime) VALUES(3, 'Mullvad Ad-block', 'tls://adblock.dns.mullvad.net', 'Mullvad’s DNS over TLS. Includes ad-blocking and tracker blocking.', 0, 0, 1, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO DoTEndpoint(id, name, url, desc, isSelected, isCustom, isSecure, latency, modifiedDataTime) VALUES(4, 'Mullvad Extended', 'tls://extended.dns.mullvad.net', 'Mullvad’s DNS over TLS. Includes ad-blocking, tracker, malware and social media blocking.', 0, 0, 1, 0, 0)"
                    )
                    connection.execSQL(
                        "ALTER TABLE WgConfigFiles ADD COLUMN isLockdown INTEGER NOT NULL DEFAULT 0"
                    )
                    connection.execSQL(
                        "ALTER TABLE WgConfigFiles ADD COLUMN isCatchAll INTEGER NOT NULL DEFAULT 0"
                    )
                    connection.execSQL(
                        "ALTER TABLE WgConfigFiles ADD COLUMN oneWireGuard INTEGER NOT NULL DEFAULT 0"
                    )
                    // socks5
                    val pappSocks5 =
                        "CASE WHEN EXISTS (select proxyName from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select proxyName from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE '' END"
                    val pipSocks5 =
                        "CASE WHEN EXISTS (select proxyIP from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select proxyIP from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE '127.0.0.1' END"
                    val portSocks5 =
                        "CASE WHEN EXISTS (select proxyPort from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select proxyPort from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE 9050 END"
                    val unameSocks5 =
                        "CASE WHEN EXISTS (select userName from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select userName from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE '' END"
                    val pwdSocks5 =
                        "CASE WHEN EXISTS (select password from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select password from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE '' END"
                    val isSelectedSocks5 =
                        "CASE WHEN EXISTS (select isSelected from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select isSelected from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE 0 END"
                    val isUDPSocks5 =
                        "CASE WHEN EXISTS (select isUDP from ProxyEndpoint_backup where proxyName = 'Socks5') THEN (select isUDP from ProxyEndpoint_backup where proxyName = 'Socks5') ELSE 0 END"
                    // orbot
                    val pipOrbot =
                        "CASE WHEN EXISTS (select proxyIP from ProxyEndpoint_backup where proxyName = 'ORBOT') THEN (select proxyIP from ProxyEndpoint_backup where proxyName = 'ORBOT') ELSE '127.0.0.1' END"
                    val portOrbot =
                        "CASE WHEN EXISTS (select proxyPort from ProxyEndpoint_backup where proxyName = 'ORBOT') THEN (select proxyPort from ProxyEndpoint_backup where proxyName = 'ORBOT') ELSE 9050 END"
                    val isSelectedOrbot =
                        "CASE WHEN EXISTS (select isSelected from ProxyEndpoint_backup where proxyName = 'ORBOT') THEN (select isSelected from ProxyEndpoint_backup where proxyName = 'ORBOT') ELSE 0 END"

                    // backup the table ProxyEndpoint
                    connection.execSQL("DROP TABLE IF EXISTS ProxyEndpoint_backup")
                    connection.execSQL(
                        "CREATE TABLE 'ProxyEndpoint_backup' ('id' INTEGER NOT NULL, 'proxyName' TEXT NOT NULL, 'proxyMode' INTEGER NOT NULL, 'proxyType' TEXT NOT NULL, 'proxyAppName' TEXT NOT NULL, 'proxyIP' TEXT NOT NULL, 'userName' TEXT NOT NULL, 'password' TEXT NOT NULL, 'proxyPort' INTEGER NOT NULL, 'isSelected' INTEGER NOT NULL, 'isCustom' INTEGER NOT NULL, 'isUDP' INTEGER NOT NULL, 'modifiedDataTime' INTEGER NOT NULL, 'latency' INTEGER NOT NULL, PRIMARY KEY (id))"
                    )
                    connection.execSQL(
                        "INSERT INTO ProxyEndpoint_backup SELECT id, proxyName, proxyMode, proxyType, proxyAppName, proxyIP, userName, password, proxyPort, isSelected, isCustom, isUDP, modifiedDataTime, latency FROM ProxyEndpoint"
                    )
                    connection.execSQL("DELETE FROM ProxyEndpoint")
                    connection.execSQL(
                        "INSERT INTO ProxyEndpoint (proxyName, proxyMode, proxyType, proxyAppName, proxyIP, userName, password, proxyPort, isSelected, isCustom, isUDP, modifiedDataTime, latency) VALUES('SOCKS5', 0, 'NONE', ($pappSocks5), ($pipSocks5), ($unameSocks5), ($pwdSocks5), ($portSocks5), ($isSelectedSocks5), 0, ($isUDPSocks5), 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO ProxyEndpoint (proxyName, proxyMode, proxyType, proxyAppName, proxyIP, userName, password, proxyPort, isSelected, isCustom, isUDP, modifiedDataTime, latency) VALUES('HTTP', 1, 'NONE', '', '', '', '', 0, 0, 0, 0, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO ProxyEndpoint (proxyName, proxyMode, proxyType, proxyAppName, proxyIP, userName, password, proxyPort, isSelected, isCustom, isUDP, modifiedDataTime, latency) VALUES('SOCKS5 Orbot', 2, 'NONE', 'org.torproject.android', ($pipOrbot), '', '', ($portOrbot), ($isSelectedOrbot), 0, 0, 0, 0)"
                    )
                    connection.execSQL(
                        "INSERT INTO ProxyEndpoint (proxyName, proxyMode, proxyType, proxyAppName, proxyIP, userName, password, proxyPort, isSelected, isCustom, isUDP, modifiedDataTime, latency) VALUES('HTTP Orbot', 3, 'NONE', 'org.torproject.android', '', '', '', 0, 0, 0, 0, 0, 0)"
                    )
                    connection.execSQL("DROP TABLE IF EXISTS ProxyEndpoint_backup")
                    println("AppDatabase")
                }
            }

        internal val MIGRATION_21_22: Migration =
            object : Migration(21, 22) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // fix: migration with the WgConfigFiles seen in play store crash
                    try {
                        if (!doesColumnExistInTable(connection, "WgConfigFiles", "isLockdown")) {
                            connection.execSQL(
                                "ALTER TABLE WgConfigFiles ADD COLUMN isLockdown INTEGER NOT NULL DEFAULT 0"
                            )
                        }
                        println("AppDatabase")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }
                    try {
                        if (!doesColumnExistInTable(connection, "WgConfigFiles", "isCatchAll")) {
                            connection.execSQL(
                                "ALTER TABLE WgConfigFiles ADD COLUMN isCatchAll INTEGER NOT NULL DEFAULT 0"
                            )
                        }
                        println("AppDatabase")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }
                    try {
                        if (!doesColumnExistInTable(connection, "WgConfigFiles", "oneWireGuard")) {
                            connection.execSQL(
                                "ALTER TABLE WgConfigFiles ADD COLUMN oneWireGuard INTEGER NOT NULL DEFAULT 0"
                            )
                        }
                        println("AppDatabase")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }
                }
            }

        internal val MIGRATION_22_23: Migration =
            object : Migration(22, 23) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE AppInfo ADD COLUMN isProxyExcluded INTEGER NOT NULL DEFAULT 0"
                    )
                    println("AppDatabase")
                }
            }

        internal val MIGRATION_23_24: Migration =
            object : Migration(23, 24) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "UPDATE DoTEndpoint set desc = 'Adguard DNS over TLS. Blocks ads, tracking, and phishing.' where name = 'Adguard' and id = 2"
                    )
                }
            }

        // migration part of v055o
        internal val MIGRATION_24_25: Migration =
            object : Migration(24, 25) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        """
                        CREATE TABLE 'RpnProxy' (
                            'id' INTEGER NOT NULL,
                            'name' TEXT NOT NULL,
                            'configPath' TEXT NOT NULL,
                            'serverResPath' TEXT NOT NULL,
                            'isActive' INTEGER NOT NULL,
                            'isLockdown' INTEGER NOT NULL,
                            'createdTs' INTEGER NOT NULL,
                            'modifiedTs' INTEGER NOT NULL,
                            'lastRefreshTime' INTEGER NOT NULL DEFAULT 0,
                            'misc' TEXT NOT NULL,
                            'tunId' TEXT NOT NULL,
                            'latency' INTEGER NOT NULL,
                            PRIMARY KEY (id)
                        )
                        """.trimIndent()
                    )
                    connection.execSQL(
                        """
                        CREATE TABLE 'WgHopMap' (
                            'id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            'src' TEXT NOT NULL,
                            'hop' TEXT NOT NULL,
                            'isActive' INTEGER NOT NULL,
                            'status' TEXT NOT NULL
                        )
                        """.trimIndent()
                    )

                    try {
                        connection.execSQL("ALTER TABLE CustomDomain ADD COLUMN proxyId TEXT NOT NULL DEFAULT ''")
                        connection.execSQL("ALTER TABLE CustomDomain ADD COLUMN proxyCC TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }

                    try {
                        connection.execSQL("ALTER TABLE CustomIp ADD COLUMN proxyId TEXT NOT NULL DEFAULT ''")
                        connection.execSQL("ALTER TABLE CustomIp ADD COLUMN proxyCC TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }

                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN tombstoneTs INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }

                    try {
                        connection.execSQL("ALTER TABLE WgConfigFiles ADD COLUMN useOnlyOnMetered INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }

                    connection.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS SubscriptionStatus (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            accountId TEXT NOT NULL,
                            purchaseToken TEXT NOT NULL,
                            productId TEXT NOT NULL,
                            planId TEXT NOT NULL,
                            sessionToken TEXT NOT NULL,
                            productTitle TEXT NOT NULL,
                            state INTEGER NOT NULL DEFAULT 0,
                            status INTEGER NOT NULL DEFAULT -1,
                            lastUpdatedTs INTEGER NOT NULL DEFAULT 0,
                            purchaseTime INTEGER NOT NULL DEFAULT 0,
                            accountExpiry INTEGER NOT NULL DEFAULT 0,
                            billingExpiry INTEGER NOT NULL DEFAULT 0,
                            developerPayload TEXT NOT NULL
                            )""".trimIndent()
                    )

                    connection.execSQL(
                    """
                            CREATE TABLE IF NOT EXISTS SubscriptionStateHistory (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            subscriptionId INTEGER NOT NULL,
                            fromState INTEGER NOT NULL,
                            toState INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL DEFAULT 0,
                            reason TEXT)
                         """.trimIndent()
                    )
                }
            }

        internal val MIGRATION_25_26: Migration =
            object : Migration(25, 26) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    try {
                        connection.execSQL("ALTER TABLE WgConfigFiles ADD COLUMN ssidEnabled INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }
                    try {
                        connection.execSQL("ALTER TABLE WgConfigFiles ADD COLUMN ssids TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {
                        println("AppDatabase")
                    }
                    println("AppDatabase")
                }
            }

        internal val MIGRATION_26_27: Migration =
            object : Migration(26, 27) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // delete the column isLockdown from WgConfigFiles
                    connection.execSQL("CREATE TABLE 'WgConfigFiles_new' ('id' INTEGER NOT NULL, 'name' TEXT NOT NULL, 'configPath' TEXT NOT NULL, 'serverResponse' TEXT NOT NULL, 'isActive' INTEGER NOT NULL, 'isDeletable' INTEGER NOT NULL, 'isCatchAll' INTEGER NOT NULL, 'oneWireGuard' INTEGER NOT NULL, 'useOnlyOnMetered' INTEGER NOT NULL, 'ssidEnabled' INTEGER NOT NULL, 'ssids' TEXT NOT NULL, PRIMARY KEY (id))")
                    connection.execSQL("INSERT INTO WgConfigFiles_new SELECT id, name, configPath, serverResponse, isActive, isDeletable, isCatchAll, oneWireGuard, useOnlyOnMetered, ssidEnabled, ssids FROM WgConfigFiles")
                    connection.execSQL("DROP TABLE WgConfigFiles")
                    connection.execSQL("ALTER TABLE WgConfigFiles_new RENAME TO WgConfigFiles")
                    // insert new columns with default values (modifiedTs)
                    connection.execSQL("ALTER TABLE WgConfigFiles ADD COLUMN modifiedTs INTEGER NOT NULL DEFAULT 0")
                    println("AppDatabase")
                    // Add modifiedTs column to AppInfo table to track when firewall/proxy rules change
                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN modifiedTs INTEGER NOT NULL DEFAULT 0")
                        // Backfill all existing rows with 0 (already done by DEFAULT 0)
                        println("AppDatabase")
                    } catch (e: Exception) {
                        println("AppDatabase")
                    }
                    // Add tempAllowEnabled and tempAllowExpiryTime columns to AppInfo table for temporary allow feature
                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN tempAllowEnabled INTEGER NOT NULL DEFAULT 0")
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN tempAllowExpiryTime INTEGER NOT NULL DEFAULT 0")
                        println("AppDatabase")
                    } catch (e: Exception) {
                        println("AppDatabase")
                    }
                }
            }

        internal val MIGRATION_27_28: Migration =
            object : Migration(27, 28) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN modifiedTs INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                        // The KMP branch already added this column in migration 26 -> 27.
                    }
                    connection.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS CountryConfig (
                            cc TEXT PRIMARY KEY NOT NULL,
                            catchAll INTEGER NOT NULL DEFAULT 0,
                            lockdown INTEGER NOT NULL DEFAULT 0,
                            mobileOnly INTEGER NOT NULL DEFAULT 0,
                            ssidBased INTEGER NOT NULL DEFAULT 0,
                            lastModified INTEGER NOT NULL DEFAULT 0,
                            enabled INTEGER NOT NULL DEFAULT 1,
                            priority INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                }
            }

        internal val MIGRATION_28_29: Migration =
            object : Migration(28, 29) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN tempAllowEnabled INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                    try {
                        connection.execSQL("ALTER TABLE AppInfo ADD COLUMN tempAllowExpiryTime INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}

                    connection.execSQL("DROP INDEX IF EXISTS index_RpnWinServers_countryCode")
                    connection.execSQL("DROP INDEX IF EXISTS index_RpnWinServers_isActive")
                    connection.execSQL("DROP TABLE IF EXISTS RpnWinServers")
                    connection.execSQL("DROP TABLE IF EXISTS CountryConfig")
                    connection.execSQL(
                        """
                        CREATE TABLE CountryConfig (
                            id TEXT PRIMARY KEY NOT NULL,
                            cc TEXT NOT NULL,
                            name TEXT NOT NULL DEFAULT '',
                            address TEXT NOT NULL DEFAULT '',
                            city TEXT NOT NULL DEFAULT '',
                            key TEXT NOT NULL DEFAULT '',
                            load INTEGER NOT NULL DEFAULT 0,
                            link INTEGER NOT NULL DEFAULT 0,
                            count INTEGER NOT NULL DEFAULT 0,
                            isActive INTEGER NOT NULL DEFAULT 1,
                            catchAll INTEGER NOT NULL DEFAULT 0,
                            lockdown INTEGER NOT NULL DEFAULT 0,
                            mobileOnly INTEGER NOT NULL DEFAULT 0,
                            ssidBased INTEGER NOT NULL DEFAULT 0,
                            priority INTEGER NOT NULL DEFAULT 0,
                            ssids TEXT NOT NULL DEFAULT '',
                            lastModified INTEGER NOT NULL DEFAULT 0,
                            isEnabled INTEGER NOT NULL DEFAULT 1,
                            premium INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_CountryConfig_cc ON CountryConfig(cc)")
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_CountryConfig_isActive ON CountryConfig(isActive)")

                    val subscriptionColumns = listOf(
                        "previousProductId TEXT NOT NULL DEFAULT ''",
                        "previousPurchaseToken TEXT NOT NULL DEFAULT ''",
                        "replacedAt INTEGER NOT NULL DEFAULT 0",
                        "windowDays INTEGER NOT NULL DEFAULT 0",
                        "orderId TEXT NOT NULL DEFAULT ''",
                        "deviceId TEXT NOT NULL DEFAULT ''"
                    )
                    subscriptionColumns.forEach { column ->
                        try {
                            connection.execSQL("ALTER TABLE SubscriptionStatus ADD COLUMN $column")
                        } catch (_: Exception) {}
                    }
                    connection.execSQL(
                        "UPDATE SubscriptionStatus SET deviceId = 'pip/identity.json' " +
                            "WHERE deviceId != '' AND deviceId != 'pip/identity.json'"
                    )
                    try {
                        connection.execSQL("ALTER TABLE WgConfigFiles ADD COLUMN isLockdown INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                }
            }

        internal val MIGRATION_29_30: Migration =
            object : Migration(29, 30) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    try {
                        connection.execSQL("ALTER TABLE DNSLogs ADD COLUMN isEch INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                    try {
                        connection.execSQL("ALTER TABLE CountryConfig ADD COLUMN selectionCount INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                    try {
                        connection.execSQL("ALTER TABLE CountryConfig ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_CountryConfig_isFavourite ON CountryConfig(isFavourite)")
                    try {
                        connection.execSQL("ALTER TABLE CountryConfig ADD COLUMN hopEnabled INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                }
            }

        // ref: stackoverflow.com/a/57204285
        internal suspend fun doesColumnExistInTable(
            connection: SQLiteConnection,
            tableName: String,
            columnToCheck: String
        ): Boolean = connection.doesColumnExistInTable(tableName, columnToCheck)
    }

    // fixme: revisit the links to remove the pragma for each table
    // https://stackoverflow.com/questions/49030258/how-to-vacuum-roomdatabase
    // https://stackoverflow.com/questions/50987119/backup-room-databas
    suspend fun checkPoint() {
        appDatabaseRawQueries().checkpoint(RoomRawQuery(PRAGMA))
        appDatabaseRawQueries().vacuum(RoomRawQuery("VACUUM"))
    }

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

    abstract fun appDatabaseRawQueries(): AppDatabaseRawQueryDao

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

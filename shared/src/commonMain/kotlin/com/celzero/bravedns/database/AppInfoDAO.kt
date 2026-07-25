/*
Copyright 2020 RethinkDNS developers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.celzero.bravedns.data.DataUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface AppInfoDAO {

    @Update suspend fun update(appInfo: AppInfo): Int

    @Query(
        "update AppInfo set firewallStatus = :firewallStatus, connectionStatus = :connectionStatus, modifiedTs = :modifiedTs where uid = :uid"
    )
    suspend fun updateFirewallStatusByUid(uid: Int, firewallStatus: Int, connectionStatus: Int, modifiedTs: Long)

    @Query("update AppInfo set tempAllowEnabled = :enabled, tempAllowExpiryTime = :expiryTime, modifiedTs = :modifiedTs where uid = :uid")
    suspend fun updateTempAllowByUid(uid: Int, enabled: Boolean, expiryTime: Long, modifiedTs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(appInfo: AppInfo): Long

    @Query("update AppInfo set uid = :newUid, tombstoneTs = 0, modifiedTs = :modifiedTs where uid = :oldUid and packageName = :pkg")
    suspend fun updateUid(oldUid: Int, pkg: String, newUid: Int, modifiedTs: Long): Int

    @Query("select * from AppInfo where uid = :uid and packageName = :pkg")
    suspend fun isUidPkgExist(uid: Int, pkg: String): AppInfo?

    @Query("select * from AppInfo where uid = :uid limit 1")
    suspend fun getAppInfoByUid(uid: Int): AppInfo?

    @Delete suspend fun delete(appInfo: AppInfo)

    @Query("delete from AppInfo")
    suspend fun deleteAll()

    @Query("delete from AppInfo where packageName in (:packageNames)")
    suspend fun deleteByPackageName(packageNames: List<String>)

    @Query("delete from AppInfo where uid = :uid and packageName = :packageName")
    suspend fun deletePackage(uid: Int, packageName: String)

    @Query("update AppInfo set uid = :newUid, tombstoneTs = :tombstoneTs, modifiedTs = :modifiedTs where uid = :uid and packageName = :packageName")
    suspend fun tombstoneApp(newUid: Int, uid: Int, packageName: String, tombstoneTs: Long, modifiedTs: Long)

    @Query("update AppInfo set uid = :newUid, tombstoneTs = :tombstoneTs, modifiedTs = :modifiedTs where uid = :oldUid")
    suspend fun tombstoneApp(oldUid: Int, newUid: Int, tombstoneTs: Long, modifiedTs: Long)

    @Query("select * from AppInfo order by appCategory, uid") suspend fun getAllAppDetails(): List<AppInfo>
    @Query("select * from AppInfo order by lower(appName), uid, packageName")
    fun getAllAppDetailsFlow(): Flow<List<AppInfo>>

    @Query("select count(*) from AppInfo")
    suspend fun getAppCount(): Int

    @Query(
        "select * from AppInfo where isSystemApp = 1 and (appName like :search or uid like :search or packageName like :search) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getSystemApps(
        search: String,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where isSystemApp = 1 and (appName like :search or uid like :search or packageName like :search) and appCategory in (:filter) and (firewallStatus in (:firewall)  or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getSystemApps(
        search: String,
        filter: Set<String>,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where isSystemApp = 0 and (appName like :search or uid like :search or packageName like :search) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getInstalledApps(
        search: String,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where isSystemApp = 0 and (appName like :search or uid like :search or packageName like :search) and appCategory in (:filter) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getInstalledApps(
        search: String,
        filter: Set<String>,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where (appName like :search or uid like :search or packageName like :search) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getAppInfos(
        search: String,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where (appName like :search or uid like :search or packageName like :search) and appCategory in (:filter)  and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    fun getAppInfos(
        search: String,
        filter: Set<String>,
        firewall: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): PagingSource<Int, AppInfo>

    @Query(
        "select * from AppInfo where (appName like :search or uid like :search or packageName like :search) and appCategory in (:cat) and isSystemApp in (:appType) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    suspend fun getFilteredApps(
        search: String,
        cat: Set<String>,
        firewall: Set<Int>,
        appType: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): List<AppInfo>

    @Query(
        "select * from AppInfo where (appName like :search or uid like :search or packageName like :search) and isSystemApp in (:appType) and (firewallStatus in (:firewall) or isProxyExcluded in (:isProxyExcluded)) and connectionStatus in (:connectionStatus) order by lower(appName)"
    )
    suspend fun getFilteredApps(
        search: String,
        firewall: Set<Int>,
        appType: Set<Int>,
        connectionStatus: Set<Int>,
        isProxyExcluded: Set<Int>
    ): List<AppInfo>

    @Query(
        "update AppInfo set firewallStatus = :firewall, connectionStatus = :connectionStatus where :clause"
    )
    suspend fun cpUpdate(firewall: Int, connectionStatus: Int, clause: String): Int

    @Query("select * from AppInfo order by appCategory, uid") suspend fun getAllAppDetailsCursor(): List<AppInfo>

    @Query("delete from AppInfo where uid = :uid") suspend fun deleteByUid(uid: Int): Int

    @Query(
        "select uid as uid, downloadBytes as downloadBytes, uploadBytes as uploadBytes from AppInfo where uid = :uid"
    )
    suspend fun getDataUsageByUid(uid: Int): DataUsage?

    @Query(
        "update AppInfo set  uploadBytes = :uploadBytes, downloadBytes = :downloadBytes where uid = :uid"
    )
    suspend fun updateDataUsageByUid(uid: Int, uploadBytes: Long, downloadBytes: Long)

    @Query("update AppInfo set isProxyExcluded = :isProxyExcluded, modifiedTs = :modifiedTs where uid = :uid")
    suspend fun updateProxyExcluded(uid: Int, isProxyExcluded: Boolean, modifiedTs: Long)

    @Query("select uid from AppInfo where packageName = :packageName")
    suspend fun getAppInfoUidForPackageName(packageName: String): Int

    @Query("update AppInfo set isProxyExcluded = :bypass where packageName = 'com.celzero.bravedns'")
    suspend fun setRethinkToBypassProxy(bypass: Boolean)

    @Query("update AppInfo set firewallStatus = 7, connectionStatus = 3 where packageName = 'com.celzero.bravedns'")
    suspend fun setRethinkToBypassDnsAndFirewall()

    @Query("select * from AppInfo where tempAllowEnabled = 1 and tempAllowExpiryTime > 0")
    suspend fun getTempAllowedApps(): List<AppInfo>

    @Query("select * from AppInfo where tempAllowEnabled = 1 and tempAllowExpiryTime > :now ORDER BY tempAllowExpiryTime DESC")
    fun getTempAllowedAppsPaged(now: Long): PagingSource<Int, AppInfo>

    @Query("update AppInfo set tempAllowEnabled = 0, tempAllowExpiryTime = 0, modifiedTs = :modifiedTs where uid = :uid")
    suspend fun clearTempAllowByUid(uid: Int, modifiedTs: Long)

    @Query(
        "update AppInfo set tempAllowEnabled = 0, tempAllowExpiryTime = 0, modifiedTs = :modifiedTs " +
            "where uid = :uid and tempAllowEnabled = 1 and tempAllowExpiryTime = :expectedExpiry"
    )
    suspend fun clearTempAllowByUidIfExpiry(uid: Int, expectedExpiry: Long, modifiedTs: Long): Int

    @Query("select MIN(tempAllowExpiryTime) from AppInfo where tempAllowEnabled = 1 and tempAllowExpiryTime > :now")
    suspend fun getNearestTempAllowExpiry(now: Long): Long?

    @Query(
        "update AppInfo set tempAllowEnabled = 0, tempAllowExpiryTime = 0, modifiedTs = :modifiedTs " +
            "where tempAllowEnabled = 1 and tempAllowExpiryTime > 0 and tempAllowExpiryTime <= :now"
    )
    suspend fun clearAllExpiredTempAllows(now: Long, modifiedTs: Long): Int
}

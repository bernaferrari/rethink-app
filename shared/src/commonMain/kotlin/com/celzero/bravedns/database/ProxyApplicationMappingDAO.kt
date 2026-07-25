/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.database


import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyApplicationMappingDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wgMapping: ProxyApplicationMapping): Long

    @Update suspend fun update(wgMapping: ProxyApplicationMapping)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wgMapping: List<ProxyApplicationMapping>): LongArray

    @Delete suspend fun delete(wgMapping: ProxyApplicationMapping)

    @Query("delete from ProxyApplicationMapping where uid = :uid and packageName = :packageName")
    suspend fun deleteApp(uid: Int, packageName: String)

    @Query("delete from ProxyApplicationMapping where packageName = :packageName")
    suspend fun deleteAppByPkgName(packageName: String)

    @Query("delete from ProxyApplicationMapping") suspend fun deleteAll()

    @Query("select * from ProxyApplicationMapping")
    suspend fun getWgAppMapping(): List<ProxyApplicationMapping>

    @Query("select * from ProxyApplicationMapping order by lower(appName), lower(packageName), uid")
    fun getWgAppMappingFlow(): Flow<List<ProxyApplicationMapping>>

    // query to get apps for pager adapter
    @Query(
        "select * from ProxyApplicationMapping where appName like :appName order by lower(appName)"
    )
    fun getAllAppsMapping(appName: String): PagingSource<Int, ProxyApplicationMapping>

    @Query(
        "select * from ProxyApplicationMapping where appName like :appName and proxyId = :proxyId order by lower(appName)"
    )
    fun getSelectedAppsMapping(
        appName: String,
        proxyId: String
    ): PagingSource<Int, ProxyApplicationMapping>

    @Query(
        "select * from ProxyApplicationMapping where appName like :appName and proxyId != :proxyId order by lower(appName)"
    )
    fun getUnSelectedAppsMapping(
        appName: String,
        proxyId: String
    ): PagingSource<Int, ProxyApplicationMapping>

    @Query("select count(packageName) from ProxyApplicationMapping where proxyId = :id")
    suspend fun getAppCountById(id: String): Int

    @Query("select count(packageName) from ProxyApplicationMapping where proxyId = :id")
    fun observeAppCountById(id: String): Flow<Int>

    @Query(
        "update ProxyApplicationMapping set proxyId = :cfgId, proxyName = :cfgName where uid = :uid"
    )
    suspend fun updateProxyIdForApp(uid: Int, cfgId: String, cfgName: String)

    @Query(
        "update ProxyApplicationMapping set proxyId = :cfgId, proxyName = :cfgName where uid = :uid and packageName = :packageName"
    )
    suspend fun updateProxyIdForPackage(uid: Int, packageName: String, cfgId: String, cfgName: String)

    @Query("update ProxyApplicationMapping set proxyId = '', proxyName = '' where proxyId = :cfgId")
    suspend fun removeAllAppsForProxy(cfgId: String)

    @Query("update ProxyApplicationMapping set proxyId = '', proxyName = '' where proxyId = 'wg%'")
    suspend fun removeAllWgProxies()

    @Query("update ProxyApplicationMapping set proxyId = :cfgId, proxyName = :cfgName")
    suspend fun updateProxyForAllApps(cfgId: String, cfgName: String = "")

    @Query("update ProxyApplicationMapping set proxyName = :proxyName where proxyId = :proxyId")
    suspend fun updateProxyNameForProxyId(proxyId: String, proxyName: String)

    @Query(
        "update ProxyApplicationMapping set proxyId = :cfgId, proxyName = :cfgName where proxyId = ''"
    )
    suspend fun updateProxyForUnselectedApps(cfgId: String, cfgName: String = "")

    @Query("update ProxyApplicationMapping set uid = :uid where packageName = :packageName")
    suspend fun updateUidForApp(uid: Int, packageName: String)

    @Query("update ProxyApplicationMapping set uid = :newUid where uid = :oldUid")
    suspend fun tombstoneApp(oldUid: Int, newUid: Int)

    @Query(
        """
        delete from ProxyApplicationMapping
        where rowid not in (
            select min(rowid)
            from ProxyApplicationMapping
            group by uid, packageName
        )
        """
    )
    suspend fun dedupeByUidAndPackage()

    @Query(
        """
        delete from ProxyApplicationMapping
        where uid = :uid
          and packageName = :packageName
          and rowid not in (
              select min(rowid)
              from ProxyApplicationMapping
              where uid = :uid and packageName = :packageName
          )
        """
    )
    suspend fun dedupeByUidAndPackage(uid: Int, packageName: String)
}

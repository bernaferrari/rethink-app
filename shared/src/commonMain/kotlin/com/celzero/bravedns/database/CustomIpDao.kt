/*
 * Copyright 2021 RethinkDNS and its authors
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

import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY

@Dao
interface CustomIpDao {

    @Update suspend fun update(customIp: CustomIp)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(customIp: CustomIp)

    @Delete suspend fun delete(customIp: CustomIp)

    @Delete suspend fun deleteAll(customIp: List<CustomIp>)

    @Transaction
    @Query("select * from CustomIp order by uid")
    suspend fun getCustomIpRules(): List<CustomIp>

    @Query("select * from CustomIp where ipAddress = :ipAddress and uid = :uid and port = :port")
    suspend fun getCustomIpDetail(uid: Int, ipAddress: String, port: Int): CustomIp?

    @Transaction
    @Query("select * from CustomIp where uid = :uid and isActive = 1")
    suspend fun getBlockedConnectionsByUID(uid: Int): List<CustomIp>

    @Query("delete from CustomIp where uid = :uid") suspend fun clearIpRuleByUid(uid: Int)

    @Query(
        "select * from CustomIp where isActive = 1 and uid = $UID_EVERYBODY order by modifiedDateTime desc"
    )
    fun univBlockedConnectionsPagingSource(): PagingSource<Int, CustomIp>

    @Query(
        "select * from CustomIp where ipAddress like :query and uid = $UID_EVERYBODY and  isActive = 1 order by modifiedDateTime desc"
    )
    fun getUnivBlockedConnectionsByIP(query: String): PagingSource<Int, CustomIp>

    @Query(
        "delete from CustomIp where ipAddress = :ipAddress and uid = $UID_EVERYBODY and port = :port"
    )
    suspend fun deleteIPRulesUniversal(ipAddress: String, port: Int)

    @Transaction
    @Query("delete from CustomIp where ipAddress = :ipAddress and uid = :uid and port = :port")
    suspend fun deleteRule(uid: Int, ipAddress: String, port: Int): Int

    @Query("delete from CustomIp where uid = :uid") suspend fun deleteRulesByUid(uid: Int)

    @Query("delete from CustomIp where uid = $UID_EVERYBODY") suspend fun deleteAllIPRulesUniversal()

    @Query("select * from CustomIp where uid = :uid") suspend fun getRulesByUid(uid: Int): List<CustomIp>

    @Query("select count(*) from CustomIp where uid = $UID_EVERYBODY and isActive = 1")
    suspend fun getBlockedConnectionsCount(): Int

    @Query("select count(*) from CustomIp where uid = $UID_EVERYBODY and isActive = 1")
    fun observeCustomIpsCount(): Flow<Int>

    @Query("select count(*) from CustomIp where uid = :uid and isActive = 1")
    fun getAppWiseIpRulesCount(uid: Int): Flow<Int>

    @Query("select count(*) from CustomIp where isActive = 1 and uid != $UID_EVERYBODY")
    fun getIpRulesCountInt(): Flow<Int>

    @Query(
        "select * from CustomIp where uid = :uid and isActive = 1 order by modifiedDateTime desc"
    )
    fun getAppWiseCustomIp(uid: Int): PagingSource<Int, CustomIp>

    @Query(
        "select * from CustomIp where ipAddress like :query and uid = :uid and  isActive = 1 order by modifiedDateTime desc"
    )
    fun getAppWiseCustomIp(query: String, uid: Int): PagingSource<Int, CustomIp>

    @androidx.room3.RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM (SELECT *, (SELECT COUNT(*) FROM CustomIp ci2 WHERE ci2.uid = ci1.uid AND ci2.rowid <= ci1.rowid) row_num FROM CustomIp ci1 WHERE ipAddress LIKE :query AND isActive = 1 AND uid != $UID_EVERYBODY) WHERE row_num <= 5 ORDER BY uid, row_num"
    )
    fun getAllCustomIpRules(query: String): PagingSource<Int, CustomIp>

    @Transaction
    suspend fun updateUid(uid: Int, newUid: Int) {
        // Use INSERT OR REPLACE to handle conflicts properly
        // First, insert all entries from oldUid with newUid (this will replace any existing conflicts)
        insertOrReplaceWithNewUid(uid, newUid)
        // Then delete the original entries
        deleteRulesByUid(uid)
    }

    @Query("""
        INSERT OR REPLACE INTO CustomIp (uid, ipAddress, port, protocol, isActive, status, ruleType, wildcard, proxyId, proxyCC, modifiedDateTime)
        SELECT :newUid, ipAddress, port, protocol, isActive, status, ruleType, wildcard, proxyId, proxyCC, modifiedDateTime 
        FROM CustomIp WHERE uid = :oldUid
    """)
    suspend fun insertOrReplaceWithNewUid(oldUid: Int, newUid: Int)

    @Query("delete from CustomIp where uid != $UID_EVERYBODY") suspend fun deleteAllAppsRules()

    @Query("select count(*) from CustomIp") suspend fun getRulesCount(): Int

    @Query("select count(*) from CustomIp where proxyCC = :cc") suspend fun getRulesCountByCC(cc: String): Int

    @Transaction
    suspend fun tombstoneRulesByUid(oldUid: Int, newUid: Int) {
        // Use INSERT OR REPLACE to handle conflicts properly
        // First, insert all entries from oldUid with newUid (this will replace any existing conflicts)
        insertOrReplaceWithNewUid(oldUid, newUid)
        // Then delete the original entries
        deleteRulesByUid(oldUid)
    }
}

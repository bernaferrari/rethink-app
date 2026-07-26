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
package com.bernaferrari.bravedns.database

import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.bernaferrari.bravedns.util.Constants

@Dao
interface CustomDomainDAO {

    @Update suspend fun update(customDomain: CustomDomain): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(customDomain: CustomDomain): Long

    @Delete suspend fun delete(customDomain: CustomDomain)

    @Delete suspend fun deleteAll(customDomains: List<CustomDomain>)

    @Transaction
    @Query("select * from CustomDomain order by modifiedTs desc")
    suspend fun getAllDomains(): List<CustomDomain>

    @Transaction
    @Query(
        "select * from CustomDomain where uid = :uid and domain like :query order by modifiedTs desc"
    )
    fun domainsPagingSource(
        uid: Int = Constants.UID_EVERYBODY,
        query: String
    ): PagingSource<Int, CustomDomain>

    @Query("select count(*) from CustomDomain where uid = :uid")
    fun getAppWiseDomainRulesCount(uid: Int): Flow<Int>

    @Query("select * from CustomDomain where uid = :uid order by modifiedTs desc")
    suspend fun getDomainsByUID(uid: Int): List<CustomDomain>

    @Transaction
    suspend fun updateUid(uid: Int, newUid: Int) {
        // Use INSERT OR REPLACE to handle conflicts properly
        // First, insert all entries from oldUid with newUid (this will replace any existing conflicts)
        insertOrReplaceWithNewUid(uid, newUid)
        // Then delete the original entries
        deleteRulesByUid(uid)
    }

    @Query("""
        INSERT OR REPLACE INTO CustomDomain (domain, uid, ips, status, type, proxyId, proxyCC, modifiedTs, deletedTs, version)
        SELECT domain, :newUid, ips, status, type, proxyId, proxyCC, modifiedTs, deletedTs, version 
        FROM CustomDomain WHERE uid = :oldUid
    """)
    suspend fun insertOrReplaceWithNewUid(oldUid: Int, newUid: Int)

    @Query("select count(*) from CustomDomain where uid != ${Constants.UID_EVERYBODY}")
    fun getAllDomainRulesCount(): Flow<Int>

    @Query("delete from CustomDomain where uid = :uid") suspend fun deleteRulesByUid(uid: Int)

    @Query("delete from CustomDomain") suspend fun deleteAllRules()

    @Query("select * from CustomDomain where status in (1,2) order by modifiedTs desc")
    suspend fun getRulesCursor(): List<CustomDomain>

    @Query("delete from CustomDomain where domain = :domain and uid = :uid")
    suspend fun deleteDomain(domain: String, uid: Int): Int

    @Query("update CustomDomain set status = :status where :clause")
    suspend fun cpUpdate(status: Int, clause: String): Int

    @androidx.room3.RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM (SELECT *, (SELECT COUNT(*) FROM CustomDomain cd2 WHERE cd2.uid = cd1.uid AND cd2.rowid <= cd1.rowid) row_num FROM CustomDomain cd1 WHERE uid != ${Constants.UID_EVERYBODY} AND domain LIKE :query) WHERE row_num <= 5 ORDER BY uid, row_num"
    )
    fun getAllDomainRules(query: String): PagingSource<Int, CustomDomain>

    @Query("SELECT * FROM CustomDomain WHERE uid = :uid AND domain = :domain")
    suspend fun getCustomDomain(uid: Int, domain: String): CustomDomain?

    @Query("SELECT COUNT(*) FROM CustomDomain")
    suspend fun getCustomDomainCount(): Int

    @Query("SELECT COUNT(*) FROM CustomDomain WHERE proxyCC = :cc")
    suspend fun getRulesCountByCC(cc: String): Int
}

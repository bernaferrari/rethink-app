/*
Copyright 2022 RethinkDNS and its authors

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
import androidx.room3.Transaction
import androidx.room3.Update
import com.celzero.bravedns.database.RethinkDnsEndpoint.Companion.RETHINK_DEFAULT
import com.celzero.bravedns.database.RethinkDnsEndpoint.Companion.RETHINK_PLUS
import com.celzero.bravedns.util.Constants.Companion.MISSING_UID

@Dao
interface RethinkDnsEndpointDao {

    @Update fun update(rethinkDnsEndpoint: RethinkDnsEndpoint)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rethinkDnsEndpoint: RethinkDnsEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(rethinkDnsEndpoint: RethinkDnsEndpoint)

    @Query(
        "Update RethinkDnsEndpoint set url = :url, blocklistCount = :count where name = :name and uid = $MISSING_UID"
    )
    suspend fun updateEndpoint(name: String, url: String, count: Int)

    @Delete fun delete(rethinkDnsEndpoint: RethinkDnsEndpoint)

    @Query("update RethinkDnsEndpoint set isActive = 0 where isActive = 1 and uid = $MISSING_UID")
    suspend fun removeConnectionStatus()

    @Query("update RethinkDnsEndpoint set isActive = 0 where uid = :uid")
    suspend fun removeAppWiseDns(uid: Int)

    @Transaction
    @Query("select * from RethinkDnsEndpoint where uid =  $MISSING_UID order by isActive desc")
    fun getRethinkEndpoints(): PagingSource<Int, RethinkDnsEndpoint>

    @Transaction
    @Query("select * from RethinkDnsEndpoint order by isActive desc")
    fun getAllRethinkEndpoints(): PagingSource<Int, RethinkDnsEndpoint>

    @Query("select * from RethinkDnsEndpoint where uid = $MISSING_UID order by isActive desc")
    suspend fun getAllForBlockFree(): List<RethinkDnsEndpoint>

    @Query("select isActive from RethinkDnsEndpoint where uid = :uid")
    suspend fun isAppWiseDnsEnabled(uid: Int): Boolean?

    @Transaction
    @Query(
        "select * from RethinkDnsEndpoint where name like :query or url like :query and uid = $MISSING_UID order by isActive desc"
    )
    fun getRethinkEndpointsByName(query: String): PagingSource<Int, RethinkDnsEndpoint>

    @Query("select * from RethinkDnsEndpoint where isActive = 1 and uid = $MISSING_UID LIMIT 1")
    suspend fun getConnectedEndpoint(): RethinkDnsEndpoint?

    @Query("update RethinkDnsEndpoint set isActive = 1 where uid = $MISSING_UID and name = :conn")
    suspend fun updateConnectionDefault(conn: String = RETHINK_DEFAULT)

    @Query("select count(*) from RethinkDnsEndpoint") fun getCount(): Int

    @Query("select * from RethinkDnsEndpoint where name = :plus and uid = $MISSING_UID")
    suspend fun getRethinkPlusEndpoint(plus: String = RETHINK_PLUS): RethinkDnsEndpoint?

    @Query("update RethinkDnsEndpoint set isActive = 1 where uid = $MISSING_UID and name = :plus")
    suspend fun setRethinkPlus(plus: String = RETHINK_PLUS)

    // TODO: remove this method post v054 versions
    @Query(
        "update RethinkDnsEndpoint set blocklistCount = :count where uid = $MISSING_UID and name = :plus"
    )
    suspend fun updatePlusBlocklistCount(count: Int, plus: String = RETHINK_PLUS)

    @Query("update RethinkDnsEndpoint set url = REPLACE(url, 'sky', 'max')") fun switchToMax()

    @Query("update RethinkDnsEndpoint set url = REPLACE(url, 'max', 'sky')") fun switchToSky()

    @Query("select * from RethinkDnsEndpoint where name = 'RDNS Default' and isCustom = 0 LIMIT 1")
    suspend fun getDefaultRethinkEndpoint(): RethinkDnsEndpoint?
}

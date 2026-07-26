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

package com.bernaferrari.bravedns.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update

@Dao
interface ODoHEndpointDAO {

    @Update suspend fun update(endpoint: ODoHEndpoint)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(endpoint: ODoHEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReplace(endpoint: ODoHEndpoint)

    @Delete suspend fun delete(endpoint: ODoHEndpoint)

    @Transaction
    @Query("select * from ODoHEndpoint order by isSelected desc")
    fun oDoHEndpointsPagingSource(): PagingSource<Int, ODoHEndpoint>

    @Query("select * from ODoHEndpoint order by isSelected desc")
    suspend fun getAll(): List<ODoHEndpoint>

    @Transaction
    @Query(
        "select * from ODoHEndpoint where resolver like :query or name like :query order by isSelected desc"
    )
    fun oDoHEndpointsPagingSource(query: String): PagingSource<Int, ODoHEndpoint>

    @Query("delete from ODoHEndpoint where modifiedDataTime < :date")
    suspend fun deleteOlderData(date: Long)

    @Query("delete from ODoHEndpoint") suspend fun clearAllData()

    @Query("delete from ODoHEndpoint where id = :id and isCustom = 1")
    suspend fun deleteODoHEndpoint(id: Int)

    @Query("update ODoHEndpoint set isSelected = 0 where isSelected = 1")
    suspend fun removeConnectionStatus()

    @Transaction
    @Query("select * from ODoHEndpoint where isSelected = 1")
    suspend fun getConnectedODoH(): ODoHEndpoint?

    @Query("select count(*) from ODoHEndpoint") suspend fun getCount(): Int
}

/*
Copyright 2020 RethinkDNS and its authors

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

@Dao
interface DoHEndpointDAO {

    @Update suspend fun update(doHEndpoint: DoHEndpoint)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(doHEndpoint: DoHEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReplace(doHEndpoint: DoHEndpoint)

    @Delete suspend fun delete(doHEndpoint: DoHEndpoint)

    @Transaction
    @Query("select * from DoHEndpoint order by isSelected desc")
    fun doHEndpointsPagingSource(): PagingSource<Int, DoHEndpoint>

    @Query("select * from DoHEndpoint order by isSelected desc")
    suspend fun getAll(): List<DoHEndpoint>

    @Transaction
    @Query(
        "select * from DoHEndpoint where dohURL like :query or dohName like :query order by isSelected desc"
    )
    fun doHEndpointsPagingSource(query: String): PagingSource<Int, DoHEndpoint>

    @Query("delete from DoHEndpoint where modifiedDataTime < :date") suspend fun deleteOlderData(date: Long)

    @Query("delete from DoHEndpoint") suspend fun clearAllData()

    @Query("delete from DoHEndpoint where id = :id and isCustom = 1") suspend fun deleteDoHEndpoint(id: Int)

    @Query("update DoHEndpoint set isSelected = 0 where isSelected = 1")
    suspend fun removeConnectionStatus()

    @Transaction
    @Query("select * from DoHEndpoint where isSelected = 1")
    suspend fun getConnectedDoH(): DoHEndpoint?

    @Query("select * from DoHEndpoint where isCustom = 0")
    suspend fun getAllDefaultDoHEndpoints(): List<DoHEndpoint>

    @Query("select count(*) from DoHEndpoint") suspend fun getCount(): Int
}

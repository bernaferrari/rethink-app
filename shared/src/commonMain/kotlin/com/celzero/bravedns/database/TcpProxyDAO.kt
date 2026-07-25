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

@Dao
interface TcpProxyDAO {

    @Update suspend fun update(tcpProxy: TcpProxyEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tcpProxyEndpoints: List<TcpProxyEndpoint>): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tcpProxyEndpoints: TcpProxyEndpoint): Long

    @Query("select * from TcpProxyEndpoint")
    fun tcpProxiesPagingSource(): PagingSource<Int, TcpProxyEndpoint>

    @Query("select * from TcpProxyEndpoint") suspend fun getTcpProxies(): List<TcpProxyEndpoint>

    @Delete suspend fun delete(tcpProxy: TcpProxyEndpoint)

    @Query("delete from TcpProxyEndpoint where id = :id") suspend fun deleteById(id: Int)
}

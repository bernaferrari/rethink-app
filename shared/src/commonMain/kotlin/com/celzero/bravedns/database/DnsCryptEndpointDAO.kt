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
import androidx.room3.Transaction
import androidx.room3.Update

@Dao
interface DnsCryptEndpointDAO {

    @Update fun update(dnsCryptEndpoint: DnsCryptEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insert(dnsCryptEndpoint: DnsCryptEndpoint)

    @Delete fun delete(dnsCryptEndpoint: DnsCryptEndpoint)

    @Transaction
    @Query("select * from DNSCryptEndpoint order by isSelected desc")
    fun dnsCryptEndpointsPagingSource(): PagingSource<Int, DnsCryptEndpoint>

    @Transaction
    @Query(
        "select * from DNSCryptEndpoint where dnsCryptURL like :query or dnsCryptName like :query order by isSelected desc"
    )
    fun dnsCryptEndpointsPagingSource(query: String): PagingSource<Int, DnsCryptEndpoint>

    @Query("delete from DNSCryptEndpoint where modifiedDataTime < :date")
    suspend fun deleteOlderData(date: Long)

    @Query("delete from DNSCryptEndpoint") fun clearAllData()

    @Query("delete from DNSCryptEndpoint where id = :id and isCustom = 1")
    suspend fun deleteDNSCryptEndpoint(id: Int)

    @Query("update DNSCryptEndpoint set isSelected = 0 where isSelected = 1")
    suspend fun removeConnectionStatus()

    @Transaction
    @Query("select * from DNSCryptEndpoint where isSelected = 1")
    suspend fun getConnectedDNSCrypt(): DnsCryptEndpoint?

    @Query("select count(*) from DNSCryptEndpoint where isSelected = 1")
    suspend fun getConnectedCount(): Int

    @Query("select count(*) from DNSCryptEndpoint") fun getCount(): Int

    @Transaction
    @Query("update DNSCryptEndpoint set isSelected = 1 where id = :liveServerID")
    suspend fun updateConnectionStatus(liveServerID: Int)

    @Transaction @Query("update DNSCryptEndpoint set isSelected=0") fun updateFailingConnections()
}

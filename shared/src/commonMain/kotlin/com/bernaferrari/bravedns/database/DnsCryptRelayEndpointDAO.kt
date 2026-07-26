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
interface DnsCryptRelayEndpointDAO {

    @Update suspend fun update(dnsCryptRelayEndpoint: DnsCryptRelayEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dnsCryptRelayEndpoint: DnsCryptRelayEndpoint)

    @Delete suspend fun delete(dnsCryptRelayEndpoint: DnsCryptRelayEndpoint)

    @Transaction
    @Query("select * from DNSCryptRelayEndpoint order by isSelected desc")
    fun dnsCryptRelayEndpointsPagingSource(): PagingSource<Int, DnsCryptRelayEndpoint>

    @Transaction
    @Query(
        "select * from DNSCryptRelayEndpoint where dnsCryptRelayURL like :query or dnsCryptRelayName like :query order by isSelected desc"
    )
    fun dnsCryptRelayEndpointsPagingSource(
        query: String
    ): PagingSource<Int, DnsCryptRelayEndpoint>

    @Query("delete from DNSCryptRelayEndpoint where modifiedDataTime < :date")
    suspend fun deleteOlderData(date: Long)

    @Query("delete from DNSCryptRelayEndpoint") suspend fun clearAllData()

    @Query("delete from DNSCryptRelayEndpoint where id = :id and isCustom = 1")
    suspend fun deleteDnsCryptRelayEndpoint(id: Int)

    @Query("update DNSCryptRelayEndpoint set isSelected = 0 where isSelected = 1")
    suspend fun removeConnectionStatus()

    @Query(
        "update DNSCryptRelayEndpoint set isSelected = 0 where isSelected = 1 and dnsCryptRelayURL = :stamp"
    )
    suspend fun unselectRelay(stamp: String)

    @Transaction
    @Query("select * from DNSCryptRelayEndpoint where isSelected = 1")
    suspend fun getConnectedRelays(): List<DnsCryptRelayEndpoint>

    @Transaction @Query("select count(*) from DNSCryptRelayEndpoint") suspend fun getCount(): Int
}

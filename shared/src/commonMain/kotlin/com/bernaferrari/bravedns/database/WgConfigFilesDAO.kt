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

import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface WgConfigFilesDAO {

    @Update suspend fun update(wgConfigFiles: WgConfigFiles)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wgConfigFiles: List<WgConfigFiles>): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(wgConfigFiles: WgConfigFiles): Long

    @Query(
        "select * from WgConfigFiles order by isActive desc, name collate nocase asc"
    )
    fun wgConfigsPagingSource(): PagingSource<Int, WgConfigFiles>

    @Query(
        "select * from WgConfigFiles order by isActive desc, name collate nocase asc"
    )
    suspend fun getWgConfigs(): List<WgConfigFiles>

    // TODO: should remove this query post v055o
    // sometimes the db update does not delete the entry, so adding this as precaution
    @Query("select * from WgConfigFiles where id in (0, 1)")
    suspend fun getWarpSecWarpConfig(): List<WgConfigFiles>

    @Query("select max(id) from WgConfigFiles") suspend fun getLastAddedConfigId(): Int

    @Delete suspend fun delete(wgConfigFiles: WgConfigFiles)

    @Query("delete from WgConfigFiles")
    suspend fun deleteOnAppRestore(): Int

    @Query("delete from WgConfigFiles where id = :id") suspend fun deleteConfig(id: Int)

    @Query("update WgConfigFiles set isCatchAll = :isCatchAll, oneWireGuard = 0 where id = :id")
    suspend fun updateCatchAllConfig(id: Int, isCatchAll: Boolean)

    @Query("update WgConfigFiles set isLockdown = :isLockdown where id = :id")
    suspend fun updateLockdownConfig(id: Int, isLockdown: Boolean)

    @Query("update WgConfigFiles set useOnlyOnMetered = :isMobile where id = :id")
    suspend fun updateMobileConfig(id: Int, isMobile: Boolean)

    @Query("update WgConfigFiles set oneWireGuard = :oneWireGuard where id = :id")
    suspend fun updateOneWireGuardConfig(id: Int, oneWireGuard: Boolean)

    @Query("update WgConfigFiles set ssidEnabled = :enabled where id = :id")
    suspend fun updateSsidEnabled(id: Int, enabled: Boolean)

    @Query("update WgConfigFiles set ssids = :ssids where id = :id")
    suspend fun updateSsids(id: Int, ssids: String)

    @Query("select * from WgConfigFiles where id = :id") suspend fun isConfigAdded(id: Int): WgConfigFiles?

    @Query("select count(id) from WgConfigFiles")
    fun getConfigCount(): Flow<Int>

    @Query("update WgConfigFiles set isActive = 0, oneWireGuard = 0 where id = :id")
    suspend fun disableConfig(id: Int)

}

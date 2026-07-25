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

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface WgHopMapDao {

    @Update suspend fun update(map: WgHopMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maps: List<WgHopMap>): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: WgHopMap): Long

    @Delete suspend fun delete(map: WgHopMap)

    @Query("delete from WgHopMap where id = :id") suspend fun deleteById(id: Int)

    @Query("delete from WgHopMap where src = :src and hop = :hop") suspend fun deleteBySrcAndHop(src: String, hop: String): Int

    @Query("select * from WgHopMap where src = :src") suspend fun getBySrc(src: String): WgHopMap?

    @Query("select * from WgHopMap") suspend fun getAll(): List<WgHopMap>

    @Query("select * from WgHopMap where src like :prefix || '%'")
    suspend fun getAllByPrefix(prefix: String): List<WgHopMap>

    @Query("delete from WgHopMap") suspend fun deleteAll()
}

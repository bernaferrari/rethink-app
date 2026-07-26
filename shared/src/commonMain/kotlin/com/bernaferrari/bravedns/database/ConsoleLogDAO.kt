/*
 * Copyright 2024 RethinkDNS and its authors
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
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery

@Dao
interface ConsoleLogDAO {
    @Insert
    suspend fun insert(log: ConsoleLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(log: List<ConsoleLog>)

    @RawQuery
    suspend fun getLogsCursor(query: RoomRawQuery): List<ConsoleLog>

    @Query("select * from ConsoleLog where message like :input order by id desc")
    fun getLogs(input: String): PagingSource<Int, ConsoleLog>

    @Query("DELETE FROM ConsoleLog WHERE timestamp < :to")
    suspend fun deleteOldLogs(to: Long)

    @Query("select timestamp from ConsoleLog order by id limit 1")
    suspend fun sinceTime(): Long

    @Query("select count(*) from ConsoleLog")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM ConsoleLog")
    suspend fun deleteAllLogs()
}

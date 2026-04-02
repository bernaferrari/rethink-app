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
package com.celzero.bravedns.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConsoleLogDAO {
    @Insert
    suspend fun insert(log: ConsoleLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(log: List<ConsoleLog>)

    // Chunked export: fetch LIMIT rows starting at offset — used by LogExportWorker for
    // streaming export to avoid loading all rows into a single StringBuilder (OOM risk).
    @Query("SELECT * FROM ConsoleLog ORDER BY id LIMIT :limit OFFSET :offset")
    fun getLogsChunked(limit: Int, offset: Int): List<ConsoleLog>

    // Paged query filtered by search text AND log level (level <= :maxLevel means show
    // that severity and above, e.g. maxLevel=2 shows only WARN+ERROR).
    @Query("SELECT * FROM ConsoleLog WHERE message LIKE :input AND level >= :minLevel ORDER BY id DESC")
    fun getLogs(input: String, minLevel: Int): PagingSource<Int, ConsoleLog>

    @Query("DELETE FROM ConsoleLog WHERE timestamp < :to")
    suspend fun deleteOldLogs(to: Long)

    @Query("select timestamp from ConsoleLog order by id limit 1")
    suspend fun sinceTime(): Long

    @Query("select count(*) from ConsoleLog")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM ConsoleLog")
    suspend fun deleteAllLogs()
}

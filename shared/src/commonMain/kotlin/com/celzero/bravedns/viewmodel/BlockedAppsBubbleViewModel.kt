/*
 * Copyright 2025 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.celzero.bravedns.data.BlockedAppInfo
import com.celzero.bravedns.database.AppInfoRepository
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.database.DnsLogDAO
import com.celzero.bravedns.platform.currentTimeMillis
import com.celzero.bravedns.ui.compose.apps.AppCatalog

/**
 * Paging source for the blocked-apps bubble. The source owns aggregation; the host supplies the
 * catalog so this shared state never needs PackageManager or FirewallManager.
 */
class BlockedAppsBubbleViewModel(
    private val connectionTrackerDAO: ConnectionTrackerDAO,
    private val dnsLogDAO: DnsLogDAO,
    private val appInfoRepository: AppInfoRepository,
    private val appCatalog: AppCatalog,
    private val sinceTime: Long,
    private val tempAllowedUids: Set<Int>,
) : PagingSource<Int, BlockedAppInfo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BlockedAppInfo> =
        try {
            val liveTempAllowedUids =
                runCatching {
                    appInfoRepository.getAllTempAllowedApps(currentTimeMillis()).map { it.uid }.toSet()
                }.getOrDefault(tempAllowedUids)

            val merged = LinkedHashMap<Int, Pair<Long, Int>>()
            fun merge(uid: Int, lastBlocked: Long, count: Int) {
                val previous = merged[uid]
                merged[uid] =
                    if (previous == null) lastBlocked to count
                    else maxOf(previous.first, lastBlocked) to (previous.second + count)
            }

            connectionTrackerDAO.getRecentlyBlockedApps(sinceTime)
                .forEach { merge(it.uid, it.lastBlocked, it.count) }
            runCatching { dnsLogDAO.getRecentlyBlockedDnsApps(sinceTime) }
                .getOrDefault(emptyList())
                .forEach { merge(it.uid, it.lastBlocked, it.count) }

            val blockedUids =
                merged.entries
                    .asSequence()
                    .sortedByDescending { it.value.first }
                    .take(MAX_BLOCKED_APPS)
                    .filterNot { (uid, _) -> uid in liveTempAllowedUids }
                    .toList()

            val blockedApps = blockedUids.map { (uid, metadata) ->
                val apps = appCatalog.appsForUid(uid)
                val primary = apps.firstOrNull()
                val baseName = primary?.appName ?: UNKNOWN_APP
                val otherCount = (apps.size - 1).coerceAtLeast(0)
                BlockedAppInfo(
                    packageName = primary?.packageName ?: UNKNOWN_APP,
                    appName = if (otherCount > 0) "$baseName + $otherCount other apps" else baseName,
                    uid = uid,
                    count = metadata.second,
                    lastBlocked = metadata.first,
                )
            }

            LoadResult.Page(data = blockedApps, prevKey = null, nextKey = null)
        } catch (error: Exception) {
            LoadResult.Error(error)
        }

    override fun getRefreshKey(state: PagingState<Int, BlockedAppInfo>): Int? = null

    private companion object {
        const val MAX_BLOCKED_APPS = 10
        const val UNKNOWN_APP = "Unknown"
    }
}

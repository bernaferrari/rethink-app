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
package com.bernaferrari.bravedns.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.bernaferrari.bravedns.data.AllowedAppInfo
import com.bernaferrari.bravedns.database.AppInfoRepository

/** Shared paging source for currently temporary-allowed apps. */
class AllowedAppsBubbleViewModel(
    private val appInfoRepository: AppInfoRepository,
    private val now: Long,
) : PagingSource<Int, AllowedAppInfo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AllowedAppInfo> =
        try {
            val allowedApps =
                appInfoRepository.getAllTempAllowedApps(now)
                    .groupBy { it.uid }
                    .values
                    .mapNotNull { appsForUid ->
                        val primary = appsForUid.firstOrNull() ?: return@mapNotNull null
                        val otherCount = (appsForUid.size - 1).coerceAtLeast(0)
                        val expiry = appsForUid.maxOfOrNull { it.tempAllowExpiryTime } ?: primary.tempAllowExpiryTime
                        AllowedAppInfo(
                            packageName = primary.packageName,
                            appName = if (otherCount > 0) "${primary.appName} + $otherCount other apps" else primary.appName,
                            uid = primary.uid,
                            allowedAt = expiry - TEMP_ALLOW_MS,
                            otherAppsCount = otherCount,
                        )
                    }
                    .sortedByDescending { it.allowedAt }
            LoadResult.Page(data = allowedApps, prevKey = null, nextKey = null)
        } catch (error: Exception) {
            LoadResult.Error(error)
        }

    override fun getRefreshKey(state: PagingState<Int, AllowedAppInfo>): Int? = null

    private companion object {
        const val TEMP_ALLOW_MS = 15 * 60 * 1_000L
    }
}

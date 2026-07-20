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
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.data.DataUsageSummary
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

class WgNwActivityViewModel(private val dao: ConnectionTrackerDAO) : ViewModel() {

    private var startTime: MutableStateFlow<Long> = MutableStateFlow(0L)
    private var networkActivity: MutableStateFlow<String> = MutableStateFlow("")

    private var wgId: String = ""
    private var timeCategory: TimeCategory = TimeCategory.ONE_HOUR

    companion object {
        private const val ONE_HOUR_MILLIS = 1 * 60 * 60 * 1000L
        private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }

    init {
        // set from and to time to current and 1 hr before
        startTime.value = System.currentTimeMillis() - ONE_HOUR_MILLIS
    }

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    private val pagingConfig: PagingConfig = PagingConfig(
        enablePlaceholders = true,
        prefetchDistance = 3,
        initialLoadSize = PAGING_PAGE_SIZE * 2,
        maxSize = PAGING_PAGE_SIZE * 3,
        pageSize = PAGING_PAGE_SIZE * 2,
        jumpThreshold = 5
    )

    fun timeCategoryChanged(tc: TimeCategory) {
        timeCategory = tc
        when (tc) {
            TimeCategory.ONE_HOUR -> {
                startTime.value =
                    System.currentTimeMillis() - ONE_HOUR_MILLIS
            }

            TimeCategory.TWENTY_FOUR_HOUR -> {
                startTime.value = System.currentTimeMillis() - ONE_DAY_MILLIS
            }

            TimeCategory.SEVEN_DAYS -> {
                startTime.value = System.currentTimeMillis() - ONE_WEEK_MILLIS
            }
        }
        networkActivity.value = ""
    }

    fun setWgId(wgId: String) {
        this.wgId = "%$wgId%"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wgAppNwActivity: Flow<PagingData<AppConnection>> =
        combine(networkActivity, startTime) { _, start -> start }
            .flatMapLatest { start ->
                Pager(pagingConfig) {
                    dao.getWgAppNetworkActivity(wgId, start)
                }.flow.cachedIn(viewModelScope)
            }

    suspend fun totalUsage(wgId: String): DataUsageSummary {
        val to = startTime.value
        return dao.getTotalUsagesByWgId(to, ConnectionTracker.ConnType.METERED.value, wgId)
    }
}
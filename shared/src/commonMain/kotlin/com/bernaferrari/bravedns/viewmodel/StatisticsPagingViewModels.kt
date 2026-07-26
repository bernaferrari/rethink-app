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
package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.DataUsageSummary
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.StatsSummaryDao
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.time.Clock

private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS

@KoinViewModel
class WgNwActivityViewModel(
    @Provided private val dao: ConnectionTrackerDAO,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val query = MutableStateFlow(WgActivityQuery(startTime = nowMillis() - ONE_HOUR_MILLIS))

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    fun timeCategoryChanged(category: TimeCategory) {
        query.value = query.value.copy(startTime = nowMillis() - category.durationMillis)
    }

    fun setWgId(wgId: String) {
        query.value = query.value.copy(wgId = "%$wgId%")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wgAppNwActivity: Flow<PagingData<AppConnection>> =
        query.flatMapLatest { state ->
            Pager(pagingConfig) { dao.getWgAppNetworkActivity(state.wgId, state.startTime) }
                .flow
                .cachedIn(viewModelScope)
        }

    suspend fun totalUsage(wgId: String): DataUsageSummary =
        dao.getTotalUsagesByWgId(
            query.value.startTime,
            ConnectionTracker.ConnType.METERED.value,
            wgId
        )

    private data class WgActivityQuery(val wgId: String = "", val startTime: Long)

    private val TimeCategory.durationMillis: Long
        get() =
            when (this) {
                TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
                TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
                TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
            }

    private val pagingConfig =
        PagingConfig(
            enablePlaceholders = true,
            prefetchDistance = 3,
            initialLoadSize = Constants.PAGING_PAGE_SIZE * 2,
            maxSize = Constants.PAGING_PAGE_SIZE * 3,
            pageSize = Constants.PAGING_PAGE_SIZE * 2,
            jumpThreshold = 5
        )
}

@KoinViewModel
class DomainConnectionsViewModel(
    @Provided private val statsDao: StatsSummaryDao,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val startTime = MutableStateFlow(nowMillis() - ONE_HOUR_MILLIS)
    private val domainQuery = MutableStateFlow(DetailQuery())
    private val flagQuery = MutableStateFlow("")
    private val asnQuery = MutableStateFlow(DetailQuery())
    private val ipQuery = MutableStateFlow(DetailQuery())

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    fun setDomain(domain: String, isBlocked: Boolean) {
        domainQuery.value = DetailQuery(domain, isBlocked)
    }

    fun setFlag(flag: String) {
        flagQuery.value = flag
    }

    fun setAsn(asn: String, isBlocked: Boolean) {
        asnQuery.value = DetailQuery(asn, isBlocked)
    }

    fun setIp(ip: String, isBlocked: Boolean) {
        ipQuery.value = DetailQuery(ip, isBlocked)
    }

    fun timeCategoryChanged(category: TimeCategory) {
        startTime.value = nowMillis() - category.durationMillis
        domainQuery.value = DetailQuery()
        flagQuery.value = ""
        asnQuery.value = DetailQuery()
        ipQuery.value = DetailQuery()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val domainConnectionList: Flow<PagingData<AppConnection>> =
        combine(domainQuery, startTime) { detail, start -> detail to start }
            .flatMapLatest { (detail, start) ->
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                        statsDao.getDomainDetails(detail.value, start, detail.isBlocked)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flagConnectionList: Flow<PagingData<AppConnection>> =
        combine(flagQuery, startTime) { flag, start -> flag to start }
            .flatMapLatest { (flag, start) ->
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { statsDao.getFlagDetails(flag, start) }
                    .flow
                    .cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val asnConnectionList: Flow<PagingData<AppConnection>> =
        combine(asnQuery, startTime) { detail, start -> detail to start }
            .flatMapLatest { (detail, start) ->
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                        if (detail.isBlocked) statsDao.getAsnBlockedDetails(detail.value, start)
                        else statsDao.getAsnDetails(detail.value, start)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ipConnectionList: Flow<PagingData<AppConnection>> =
        combine(ipQuery, startTime) { detail, start -> detail to start }
            .flatMapLatest { (detail, start) ->
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                        statsDao.getIpDetails(detail.value, start, detail.isBlocked)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }

    private data class DetailQuery(val value: String = "", val isBlocked: Boolean = false)

    private val TimeCategory.durationMillis: Long
        get() =
            when (this) {
                TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
                TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
                TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
            }
}

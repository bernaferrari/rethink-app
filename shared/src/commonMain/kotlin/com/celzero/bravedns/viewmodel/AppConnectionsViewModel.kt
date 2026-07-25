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
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.database.RethinkLogDao
import com.celzero.bravedns.database.StatsSummaryDao
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

@KoinViewModel
class AppConnectionsViewModel(
    @Provided private val nwlogDao: ConnectionTrackerDAO,
    @Provided private val rinrDao: RethinkLogDao,
    @Provided private val statsDao: StatsSummaryDao,
    @Provided private val uptimeProvider: NetworkUptimeProvider,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val ipFilter = MutableStateFlow("")
    private val domainFilter = MutableStateFlow("")
    private val asnFilter = MutableStateFlow("")
    private val activeConnsFilter = MutableStateFlow("")
    private val startTime = MutableStateFlow(0L)
    private var uid: Int = Constants.INVALID_UID
    private var timeCategory: TimeCategory = TimeCategory.SEVEN_DAYS

    var filterQuery: String = ""

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    enum class FilterType {
        IP,
        DOMAIN,
        ASN,
        ACTIVE_CONNECTIONS
    }

    fun timeCategoryChanged(category: TimeCategory, isDomain: Boolean) {
        timeCategory = category
        startTime.value = nowMillis() - category.durationMillis
        if (isDomain) domainFilter.value = ""
        else {
            ipFilter.value = ""
            asnFilter.value = ""
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val appIpLogs: Flow<PagingData<AppConnection>> =
        ipFilter.flatMapLatest { input -> fetchIpLogs(uid, input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val appDomainLogs: Flow<PagingData<AppConnection>> =
        domainFilter.flatMapLatest { input -> fetchAppDomainLogs(uid, input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val asnLogs: Flow<PagingData<AppConnection>> =
        asnFilter.flatMapLatest { input -> fetchAllAsnLogs(uid, input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConnections: Flow<PagingData<AppConnection>> =
        activeConnsFilter.flatMapLatest { input -> fetchAllActiveConnections(uid, input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rinrIpLogs: Flow<PagingData<AppConnection>> =
        ipFilter.flatMapLatest { input -> fetchRinrIpLogs(input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rinrDomainLogs: Flow<PagingData<AppConnection>> =
        domainFilter.flatMapLatest { input -> fetchRinrDomainLogs(input) }

    fun fetchTopActiveConnections(uid: Int, uptime: Long): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getTopActiveConns(uid, nowMillis() - uptime) }
            .flow
            .cachedIn(viewModelScope)

    fun deleteLogs(uid: Int) {
        viewModelScope.launch {
            when (timeCategory) {
                TimeCategory.ONE_HOUR -> nwlogDao.clearLogsByTime(uid, nowMillis() - ONE_HOUR_MILLIS)
                TimeCategory.TWENTY_FOUR_HOUR ->
                    nwlogDao.clearLogsByTime(uid, nowMillis() - ONE_DAY_MILLIS)
                TimeCategory.SEVEN_DAYS -> nwlogDao.clearLogsByUid(uid)
            }
        }
    }

    fun getDomainLogsLimited(uid: Int): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getMostDomainsByUid(uid, nowMillis() - ONE_WEEK_MILLIS) }
            .flow
            .cachedIn(viewModelScope)

    fun getRethinkActiveConnsLimited(uptime: Long): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getRethinkTopActiveConns(nowMillis() - uptime) }
            .flow
            .cachedIn(viewModelScope)

    fun getRethinkAllActiveConns(uptime: Long): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getRethinkAllActiveConns(nowMillis() - uptime) }
            .flow
            .cachedIn(viewModelScope)

    fun getRethinkDomainLogsLimited(): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { rinrDao.getDomainLogsLimited(nowMillis() - ONE_WEEK_MILLIS) }
            .flow
            .cachedIn(viewModelScope)

    fun getRethinkIpLogsLimited(): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { rinrDao.getIpLogsLimited(nowMillis() - ONE_WEEK_MILLIS) }
            .flow
            .cachedIn(viewModelScope)

    fun getAsnLogsLimited(uid: Int): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getAsnLogsLimited(uid, nowMillis() - ONE_WEEK_MILLIS) }
            .flow
            .cachedIn(viewModelScope)

    fun getIpLogsLimited(uid: Int): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { nwlogDao.getAppIpLogsLimited(uid, nowMillis() - ONE_WEEK_MILLIS) }
            .flow
            .cachedIn(viewModelScope)

    fun setFilter(input: String, filterType: FilterType) {
        filterQuery = input
        when (filterType) {
            FilterType.IP -> ipFilter.value = input
            FilterType.DOMAIN -> domainFilter.value = input
            FilterType.ASN -> asnFilter.value = input
            FilterType.ACTIVE_CONNECTIONS -> activeConnsFilter.value = input
        }
    }

    fun setUid(uid: Int) {
        this.uid = uid
    }

    private fun fetchRinrIpLogs(input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) rinrDao.getIpLogs(startTimestamp())
                else rinrDao.getIpLogsFiltered(startTimestamp(), "%$input%")
            }
            .flow
            .cachedIn(viewModelScope)

    private fun fetchRinrDomainLogs(input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) rinrDao.getDomainLogs(startTimestamp())
                else rinrDao.getDomainLogsFiltered(startTimestamp(), "%$input%")
            }
            .flow
            .cachedIn(viewModelScope)

    private fun fetchIpLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) nwlogDao.getAppIpLogs(uid, startTimestamp())
                else nwlogDao.getAppIpLogsFiltered(uid, startTimestamp(), "%$input%")
            }
            .flow
            .cachedIn(viewModelScope)

    private fun fetchAppDomainLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) statsDao.getAllDomainsByUid(uid, startTimestamp())
                else statsDao.getAllDomainsByUid(uid, startTimestamp(), "%$input%")
            }
            .flow
            .cachedIn(viewModelScope)

    private fun fetchAllActiveConnections(uid: Int, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                statsDao.getAllActiveConns(uid, nowMillis() - uptimeProvider.uptimeMs(), "%$input%")
            }
            .flow
            .cachedIn(viewModelScope)

    private fun fetchAllAsnLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { statsDao.getAllAsnLogs(uid, startTimestamp(), "%$input%") }
            .flow
            .cachedIn(viewModelScope)

    private fun startTimestamp(): Long =
        startTime.value.takeIf { it != 0L } ?: nowMillis() - ONE_WEEK_MILLIS

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

    private companion object {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }
}

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
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class AppConnectionsViewModel(
    private val nwlogDao: ConnectionTrackerDAO,
    private val rinrDao: RethinkLogDao,
    private val statsDao: StatsSummaryDao
) : ViewModel() {
    private var ipFilter: MutableStateFlow<String> = MutableStateFlow("")
    private var domainFilter: MutableStateFlow<String> = MutableStateFlow("")
    private var asnFilter: MutableStateFlow<String> = MutableStateFlow("")
    private var activeConnsFilter: MutableStateFlow<String> = MutableStateFlow("")

    private var uid: Int = Constants.INVALID_UID
    private val pagingConfig: PagingConfig
    private var timeCategory: TimeCategory = TimeCategory.SEVEN_DAYS
    private var startTime: MutableStateFlow<Long> = MutableStateFlow(0L)
    var filterQuery: String = ""

    companion object {
        private const val ONE_HOUR_MILLIS = 1 * 60 * 60 * 1000L
        private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    init {
        pagingConfig =
            PagingConfig(
                enablePlaceholders = true,
                prefetchDistance = 3,
                initialLoadSize = Constants.PAGING_PAGE_SIZE * 2,
                maxSize = Constants.PAGING_PAGE_SIZE * 3,
                pageSize = Constants.PAGING_PAGE_SIZE * 2,
                jumpThreshold = 5
            )
    }

    fun timeCategoryChanged(tc: TimeCategory, isDomain: Boolean) {
        timeCategory = tc
        when (tc) {
            TimeCategory.ONE_HOUR -> {
                startTime.value =
                    System.currentTimeMillis() - ONE_HOUR_MILLIS
            }

            TimeCategory.TWENTY_FOUR_HOUR -> {
                startTime.value =
                    System.currentTimeMillis() - ONE_DAY_MILLIS
            }

            TimeCategory.SEVEN_DAYS -> {
                startTime.value =
                    System.currentTimeMillis() - ONE_WEEK_MILLIS
            }
        }
        if (isDomain) {
            domainFilter.value = ""
        } else {
            ipFilter.value = ""
            asnFilter.value = ""
        }
    }

    enum class FilterType {
        IP,
        DOMAIN,
        ASN,
        ACTIVE_CONNECTIONS
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

    private fun fetchRinrIpLogs(input: String): Flow<PagingData<AppConnection>> {
        val to = getStartTime()
        return if (input.isEmpty()) {
            Pager(pagingConfig) { rinrDao.getIpLogs(to) }
        } else {
            Pager(pagingConfig) { rinrDao.getIpLogsFiltered(to, "%$input%") }
        }
            .flow
            .cachedIn(viewModelScope)
    }

    private fun fetchRinrDomainLogs(input: String): Flow<PagingData<AppConnection>> {
        val to = getStartTime()
        return if (input.isEmpty()) {
            Pager(pagingConfig) { rinrDao.getDomainLogs(to) }
        } else {
            Pager(pagingConfig) { rinrDao.getDomainLogsFiltered(to, "%$input%") }
        }
            .flow
            .cachedIn(viewModelScope)
    }

    private fun fetchIpLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> {
        val to = getStartTime()
        return if (input.isEmpty()) {
            Pager(pagingConfig) { nwlogDao.getAppIpLogs(uid, to) }
        } else {
            Pager(pagingConfig) { nwlogDao.getAppIpLogsFiltered(uid, to, "%$input%") }
        }
            .flow
            .cachedIn(viewModelScope)
    }

    private fun fetchAppDomainLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> {
        val to = getStartTime()
        return Pager(pagingConfig) {
            if (input.isEmpty()) {
                statsDao.getAllDomainsByUid(uid, to)
            } else {
                statsDao.getAllDomainsByUid(uid, to, "%$input%")
            }
        }
            .flow
            .cachedIn(viewModelScope)
    }

    fun fetchTopActiveConnections(uid: Int, uptime: Long): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - uptime
        return Pager(pagingConfig) { statsDao.getTopActiveConns(uid, to) }
            .flow
            .cachedIn(viewModelScope)
    }

    private fun fetchAllActiveConnections(uid: Int, input: String): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - VpnController.uptimeMs()
        val query = "%$input%"
        return Pager(pagingConfig) { statsDao.getAllActiveConns(uid, to, query) }
            .flow
            .cachedIn(viewModelScope)
    }

    private fun fetchAllAsnLogs(uid: Int, input: String): Flow<PagingData<AppConnection>> {
        val to = getStartTime()
        val query = "%$input%"
        return Pager(pagingConfig) { statsDao.getAllAsnLogs(uid, to, query) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun deleteLogs(uid: Int) {
        // delete based on the time category
        viewModelScope.launch {
            when (timeCategory) {
                TimeCategory.ONE_HOUR -> {
                    nwlogDao.clearLogsByTime(uid, System.currentTimeMillis() - ONE_HOUR_MILLIS)
                }

                TimeCategory.TWENTY_FOUR_HOUR -> {
                    nwlogDao.clearLogsByTime(uid, System.currentTimeMillis() - ONE_DAY_MILLIS)
                }

                TimeCategory.SEVEN_DAYS -> {
                    nwlogDao.clearLogsByUid(uid) // similar to clearing logs for uid
                }
            }
        }
    }

    private fun getStartTime(): Long {
        return startTime.value.takeIf { it != 0L }
            ?: (System.currentTimeMillis() - ONE_WEEK_MILLIS)
    }

    fun getDomainLogsLimited(uid: Int): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - ONE_WEEK_MILLIS
        return Pager(pagingConfig) {
            statsDao.getMostDomainsByUid(uid, to)
        }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getRethinkActiveConnsLimited(uptime: Long): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - uptime
        return Pager(pagingConfig) { statsDao.getRethinkTopActiveConns(to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getRethinkAllActiveConns(uptime: Long): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - uptime
        return Pager(pagingConfig) { statsDao.getRethinkAllActiveConns(to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getRethinkDomainLogsLimited(): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - ONE_WEEK_MILLIS
        return Pager(pagingConfig) { rinrDao.getDomainLogsLimited(to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getRethinkIpLogsLimited(): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - ONE_WEEK_MILLIS
        return Pager(pagingConfig) { rinrDao.getIpLogsLimited(to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getAsnLogsLimited(uid: Int): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - ONE_WEEK_MILLIS
        return Pager(pagingConfig) { statsDao.getAsnLogsLimited(uid, to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun getIpLogsLimited(uid: Int): Flow<PagingData<AppConnection>> {
        val to = System.currentTimeMillis() - ONE_WEEK_MILLIS
        return Pager(pagingConfig) { nwlogDao.getAppIpLogsLimited(uid, to) }
            .flow
            .cachedIn(viewModelScope)
    }

    fun setFilter(input: String, filterType: FilterType) {
        filterQuery = input
        when (filterType) {
            FilterType.IP -> {
                ipFilter.value = input
            }

            FilterType.DOMAIN -> {
                domainFilter.value = input
            }

            FilterType.ASN -> {
                asnFilter.value = input
            }

            FilterType.ACTIVE_CONNECTIONS -> {
                activeConnsFilter.value = input
            }
        }
    }

    fun setUid(uid: Int) {
        this.uid = uid
    }
}
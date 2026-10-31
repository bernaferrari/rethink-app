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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.database.AppStatsDao
import com.bernaferrari.bravedns.database.AsnStatsDao
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.DomainStatsDao
import com.bernaferrari.bravedns.database.RethinkLogDao
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

@KoinViewModel
class AppConnectionsViewModel(
    @Provided private val nwlogDao: ConnectionTrackerDAO,
    @Provided private val rinrDao: RethinkLogDao,
    @Provided private val appStatsDao: AppStatsDao,
    @Provided private val asnStatsDao: AsnStatsDao,
    @Provided private val domainStatsDao: DomainStatsDao,
    @Provided private val uptimeProvider: NetworkUptimeProvider,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val ipFilter = MutableStateFlow("")
    private val domainFilter = MutableStateFlow("")
    private val asnFilter = MutableStateFlow("")
    private val activeConnsFilter = MutableStateFlow("")
    private val startTime = MutableStateFlow(0L)
    private val uid = MutableStateFlow(Constants.INVALID_UID)
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

    /** This app's connections in the last 24 hours, blocked first (App detail's Recent activity). */
    fun recentConnections(uid: Int, limit: Int = RECENT_ACTIVITY_SCAN_LIMIT): Flow<List<com.bernaferrari.bravedns.database.ConnectionTracker>> =
        nwlogDao.observeRecentConnectionsForUid(uid, nowMillis() - RECENT_ACTIVITY_WINDOW_MS, limit)

    fun timeCategoryChanged(category: TimeCategory, isDomain: Boolean) {
        timeCategory = category
        startTime.value = nowMillis() - category.durationMillis
        if (isDomain) domainFilter.value = ""
        else {
            ipFilter.value = ""
            asnFilter.value = ""
        }
    }

    val appIpLogs: Flow<PagingData<AppConnection>> = logs(ipFilter, ::fetchIpLogs)
    val appDomainLogs: Flow<PagingData<AppConnection>> = logs(domainFilter, ::fetchAppDomainLogs)
    val asnLogs: Flow<PagingData<AppConnection>> = logs(asnFilter, ::fetchAllAsnLogs)
    val rinrIpLogs: Flow<PagingData<AppConnection>> =
        logs(ipFilter) { _, start, input -> fetchRinrIpLogs(start, input) }
    val rinrDomainLogs: Flow<PagingData<AppConnection>> =
        logs(domainFilter) { _, start, input -> fetchRinrDomainLogs(start, input) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConnections: Flow<PagingData<AppConnection>> =
        combine(uid, activeConnsFilter) { appUid, input -> appUid to input }
            .flatMapLatest { (appUid, input) -> fetchAllActiveConnections(appUid, input) }
            .cachedIn(viewModelScope)

    // Every parameter that changes the SQL must replace the pager. Cache the resulting stream,
    // not each superseded pager, so range/search changes do not retain old pager collectors.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun logs(
        filter: Flow<String>,
        fetch: (Int, Long, String) -> Flow<PagingData<AppConnection>>,
    ): Flow<PagingData<AppConnection>> =
        combine(uid, startTime, filter) { appUid, start, input ->
            Triple(appUid, start.takeIf { it != 0L } ?: nowMillis() - ONE_WEEK_MILLIS, input)
        }.flatMapLatest { (appUid, start, input) -> fetch(appUid, start, input) }
            .cachedIn(viewModelScope)

    fun fetchTopActiveConnections(uid: Int, uptime: Long): Flow<List<AppConnection>> =
        appStatsDao.getTopActiveConns(uid, nowMillis() - uptime)

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

    fun getDomainLogsLimited(uid: Int): Flow<List<AppConnection>> =
        domainStatsDao.getMostDomainsByUid(uid, nowMillis() - ONE_WEEK_MILLIS)

    fun getRethinkActiveConnsLimited(uptime: Long): Flow<List<AppConnection>> =
        appStatsDao.getRethinkTopActiveConns(nowMillis() - uptime)

    fun getRethinkAllActiveConns(uptime: Long): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { appStatsDao.getRethinkAllActiveConns(nowMillis() - uptime) }
            .flow
            .cachedIn(viewModelScope)

    fun getRethinkDomainLogsLimited(): Flow<List<AppConnection>> =
        rinrDao.getDomainLogsLimited(nowMillis() - ONE_WEEK_MILLIS)

    fun getRethinkIpLogsLimited(): Flow<List<AppConnection>> =
        rinrDao.getIpLogsLimited(nowMillis() - ONE_WEEK_MILLIS)

    fun getAsnLogsLimited(uid: Int): Flow<PagingData<AppConnection>> =
        asnStatsDao.getAsnLogsLimited(uid, nowMillis() - ONE_WEEK_MILLIS).toPagingData()

    fun getIpLogsLimited(uid: Int): Flow<List<AppConnection>> =
        nwlogDao.getAppIpLogsLimited(uid, nowMillis() - ONE_WEEK_MILLIS)

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
        this.uid.value = uid
    }

    private fun fetchRinrIpLogs(start: Long, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) rinrDao.getIpLogs(start)
                else rinrDao.getIpLogsFiltered(start, "%$input%")
            }
            .flow

    private fun fetchRinrDomainLogs(start: Long, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) rinrDao.getDomainLogs(start)
                else rinrDao.getDomainLogsFiltered(start, "%$input%")
            }
            .flow

    private fun fetchIpLogs(uid: Int, start: Long, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) nwlogDao.getAppIpLogs(uid, start)
                else nwlogDao.getAppIpLogsFiltered(uid, start, "%$input%")
            }
            .flow

    private fun fetchAppDomainLogs(uid: Int, start: Long, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                if (input.isEmpty()) domainStatsDao.getAllDomainsByUid(uid, start)
                else domainStatsDao.getAllDomainsByUid(uid, start, "%$input%")
            }
            .flow

    private fun fetchAllActiveConnections(uid: Int, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) {
                appStatsDao.getAllActiveConns(uid, nowMillis() - uptimeProvider.uptimeMs(), "%$input%")
            }
            .flow

    private fun fetchAllAsnLogs(uid: Int, start: Long, input: String): Flow<PagingData<AppConnection>> =
        Pager(pagingConfig) { asnStatsDao.getAllAsnLogs(uid, start, "%$input%") }
            .flow

    private fun Flow<List<AppConnection>>.toPagingData(): Flow<PagingData<AppConnection>> =
        map { PagingData.from(it) }.cachedIn(viewModelScope)

    private val TimeCategory.durationMillis: Long
        get() =
            when (this) {
                TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
                TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
                TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
            }

    private val pagingConfig =
        PagingConfig(
            enablePlaceholders = false,
            prefetchDistance = 3,
            initialLoadSize = Constants.PAGING_PAGE_SIZE * 2,
            maxSize = Constants.PAGING_PAGE_SIZE * 3,
            pageSize = Constants.PAGING_PAGE_SIZE * 2,
        )

    private companion object {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
        const val RECENT_ACTIVITY_WINDOW_MS = ONE_DAY_MILLIS
        const val RECENT_ACTIVITY_SCAN_LIMIT = 200
    }
}

/* Copyright 2022 RethinkDNS and its authors */
package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.DataUsageSummary
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.StatsSummaryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

@KoinViewModel
class SummaryStatisticsViewModel(
    @Provided private val connectionTrackerDAO: ConnectionTrackerDAO,
    @Provided private val statsDao: StatsSummaryDao,
    @Provided private val uptimeProvider: NetworkUptimeProvider,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val uiStateMutable = MutableStateFlow(SummaryStatisticsUiState())
    private val startTime = MutableStateFlow(nowMillis() - ONE_HOUR_MILLIS)
    private val topActiveConnsTick = MutableStateFlow(uptimeProvider.uptimeMs())
    private val refreshTick = MutableStateFlow(0L)

    val uiState: StateFlow<SummaryStatisticsUiState> = uiStateMutable.asStateFlow()

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    data class SummaryStatisticsUiState(
        val timeCategory: TimeCategory = TimeCategory.ONE_HOUR,
        val dataUsage: DataUsageSummary = DataUsageSummary(0, 0, 0, 0)
    )

    init {
        updateDataUsage()
    }

    fun timeCategoryChanged(category: TimeCategory) {
        startTime.value = nowMillis() - category.durationMillis
        uiStateMutable.update { it.copy(timeCategory = category) }
        refreshTick.update { it + 1 }
        updateDataUsage()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val summaryCards: Flow<SummaryCardLists> =
        combine(startTime, topActiveConnsTick, refreshTick) { start, uptime, _ ->
            start to (nowMillis() - uptime)
        }.flatMapLatest { (start, activeFrom) ->
            // Sub-day windows stay on the live tables (daily rollups are calendar-day
            // coarse); day+ windows read the materialized daily_* tables.
            val useDaily = start <= nowMillis() - ONE_DAY_MILLIS
            combine(
                listOf(
                    statsDao.getTopActiveConns(activeFrom),
                    if (useDaily) statsDao.getMostAllowedAppsDaily(start) else statsDao.getMostAllowedApps(start),
                    if (useDaily) statsDao.getMostBlockedAppsDaily(start) else statsDao.getMostBlockedApps(start),
                    if (useDaily) statsDao.getMostConnectedASNDaily(start) else statsDao.getMostConnectedASN(start),
                    if (useDaily) statsDao.getMostBlockedASNDaily(start) else statsDao.getMostBlockedASN(start),
                    if (useDaily) statsDao.getMostBlockedDomainsDaily(start) else statsDao.getMostBlockedDomains(start),
                    if (useDaily) statsDao.getMostContactedDomainsDaily(start) else statsDao.getMostContactedDomains(start),
                    if (useDaily) statsDao.getMostContactedCountriesDaily(start) else statsDao.getMostContactedCountries(start),
                    connectionTrackerDAO.getMostContactedIps(start),
                    connectionTrackerDAO.getMostBlockedIps(start),
                )
            ) { lists ->
                SummaryCardLists(
                    topActiveConns = lists[0],
                    allowedApps = lists[1],
                    blockedApps = lists[2],
                    connectedAsn = lists[3],
                    blockedAsn = lists[4],
                    blockedDomains = lists[5],
                    contactedDomains = lists[6],
                    contactedCountries = lists[7],
                    contactedIps = lists[8],
                    blockedIps = lists[9],
                )
            }
        }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val getTopActiveConns: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.topActiveConns }
    val getAllowedAppNetworkActivity: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.allowedApps }
    val getBlockedAppNetworkActivity: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.blockedApps }
    val getMostConnectedASN: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.connectedAsn }
    val getMostBlockedASN: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.blockedAsn }
    val mbd: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.blockedDomains }
    val mcd: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.contactedDomains }
    val getMostContactedIps: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.contactedIps }
    val getMostBlockedIps: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.blockedIps }
    val getMostContactedCountries: Flow<PagingData<AppConnection>> =
        summaryCards.toPagingData { it.contactedCountries }

    suspend fun getTopAppsForCountry(flag: String, limit: Int = 5): List<AppConnection> =
        if (flag.isBlank()) emptyList()
        else statsDao.getFlagDetailsLimited(flag = flag, to = startTime.value, limit = limit)

    private fun updateDataUsage() {
        viewModelScope.launch {
            val usage = connectionTrackerDAO.getTotalUsages(
                startTime.value,
                ConnectionTracker.ConnType.METERED.value
            )
            uiStateMutable.update { it.copy(dataUsage = usage) }
        }
    }

    private fun Flow<SummaryCardLists>.toPagingData(
        select: (SummaryCardLists) -> List<AppConnection>
    ): Flow<PagingData<AppConnection>> =
        map { PagingData.from(select(it)) }.cachedIn(viewModelScope)

    private val TimeCategory.durationMillis: Long
        get() = when (this) {
            TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
            TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
            TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
        }

    private data class SummaryCardLists(
        val topActiveConns: List<AppConnection>,
        val allowedApps: List<AppConnection>,
        val blockedApps: List<AppConnection>,
        val connectedAsn: List<AppConnection>,
        val blockedAsn: List<AppConnection>,
        val blockedDomains: List<AppConnection>,
        val contactedDomains: List<AppConnection>,
        val contactedCountries: List<AppConnection>,
        val contactedIps: List<AppConnection>,
        val blockedIps: List<AppConnection>,
    )

    private companion object {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }
}

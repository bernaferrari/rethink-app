/* Copyright 2022 RethinkDNS and its authors */
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
import com.celzero.bravedns.database.StatsSummaryDao
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    val getTopActiveConns: Flow<PagingData<AppConnection>> =
        topActiveConnsTick.flatMapLatest { uptime ->
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { statsDao.getTopActiveConns(nowMillis() - uptime) }
                .flow
                .cachedIn(viewModelScope)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllowedAppNetworkActivity = recentPaging { statsDao.getMostAllowedApps(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getBlockedAppNetworkActivity = recentPaging { statsDao.getMostBlockedApps(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getMostConnectedASN = recentPaging { statsDao.getMostConnectedASN(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getMostBlockedASN = recentPaging { statsDao.getMostBlockedASN(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val mbd = recentPaging { statsDao.getMostBlockedDomains(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val mcd = recentPaging { statsDao.getMostContactedDomains(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getMostContactedIps = recentPaging { connectionTrackerDAO.getMostContactedIps(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getMostBlockedIps = recentPaging { connectionTrackerDAO.getMostBlockedIps(startTime.value) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getMostContactedCountries = recentPaging { statsDao.getMostContactedCountries(startTime.value) }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun recentPaging(
        source: () -> androidx.paging.PagingSource<Int, AppConnection>
    ): Flow<PagingData<AppConnection>> =
        refreshTick.flatMapLatest {
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE), pagingSourceFactory = source)
                .flow
                .cachedIn(viewModelScope)
        }

    private val TimeCategory.durationMillis: Long
        get() = when (this) {
            TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
            TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
            TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
        }

    private companion object {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }
}

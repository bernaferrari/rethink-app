/* Copyright 2022 RethinkDNS and its authors */
package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.StatsSummaryDao
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

@KoinViewModel
class DetailedStatisticsViewModel(
    @Provided private val connectionTrackerDAO: ConnectionTrackerDAO,
    @Provided private val statsDao: StatsSummaryDao,
    @Provided private val uptimeProvider: NetworkUptimeProvider,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val activeConnections = MutableStateFlow(0L)
    private val allowedNetworkActivity = MutableStateFlow("")
    private val blockedNetworkActivity = MutableStateFlow("")
    private val allowedAsn = MutableStateFlow("")
    private val blockedAsn = MutableStateFlow("")
    private val allowedDomains = MutableStateFlow("")
    private val blockedDomains = MutableStateFlow("")
    private val allowedIps = MutableStateFlow("")
    private val blockedIps = MutableStateFlow("")
    private val allowedCountries = MutableStateFlow("")
    private val startTime = MutableStateFlow(0L)

    fun setData(type: SummaryStatisticsType) {
        when (type) {
            SummaryStatisticsType.TOP_ACTIVE_CONNS -> activeConnections.value = uptimeProvider.uptimeMs()
            SummaryStatisticsType.MOST_CONNECTED_APPS -> allowedNetworkActivity.value = ""
            SummaryStatisticsType.MOST_BLOCKED_APPS -> blockedNetworkActivity.value = ""
            SummaryStatisticsType.MOST_CONNECTED_ASN -> allowedAsn.value = ""
            SummaryStatisticsType.MOST_BLOCKED_ASN -> blockedAsn.value = ""
            SummaryStatisticsType.MOST_CONTACTED_DOMAINS -> allowedDomains.value = ""
            SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> blockedDomains.value = ""
            SummaryStatisticsType.MOST_CONTACTED_IPS -> allowedIps.value = ""
            SummaryStatisticsType.MOST_BLOCKED_IPS -> blockedIps.value = ""
            SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> allowedCountries.value = ""
        }
    }

    fun timeCategoryChanged(timeCategory: SummaryStatisticsViewModel.TimeCategory) {
        startTime.value = nowMillis() - timeCategory.durationMillis
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllActiveConns = activeConnections.flatMapLatest { uptime ->
        Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { statsDao.getAllActiveConns(nowMillis() - uptime) }
            .flow.cachedIn(viewModelScope)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllAllowedAppNetworkActivity = detailsPaging(allowedNetworkActivity) { start -> statsDao.getAllAllowedApps(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllAllowedAsn = detailsPaging(allowedAsn) { start -> statsDao.getAllConnectedASN(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllBlockedAsn = detailsPaging(blockedAsn) { start -> statsDao.getAllBlockedASN(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllBlockedAppNetworkActivity = detailsPaging(blockedNetworkActivity) { start -> statsDao.getAllBlockedApps(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllBlockedDomains = detailsPaging(blockedDomains) { start -> statsDao.getAllBlockedDomains(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllContactedDomains = detailsPaging(allowedDomains) { start -> statsDao.getAllContactedDomains(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllContactedIps = detailsPaging(allowedIps) { start -> connectionTrackerDAO.getAllContactedIps(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllBlockedIps = detailsPaging(blockedIps) { start -> connectionTrackerDAO.getAllBlockedIps(start) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllContactedCountries = detailsPaging(allowedCountries) { start -> statsDao.getAllContactedCountries(start) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detailsPaging(
        trigger: MutableStateFlow<String>,
        source: (Long) -> androidx.paging.PagingSource<Int, AppConnection>
    ): Flow<PagingData<AppConnection>> =
        combine(trigger, startTime) { _, start -> start }
            .flatMapLatest { start ->
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { source(start) }
                    .flow.cachedIn(viewModelScope)
            }

    private val SummaryStatisticsViewModel.TimeCategory.durationMillis: Long
        get() = when (this) {
            SummaryStatisticsViewModel.TimeCategory.ONE_HOUR -> ONE_HOUR_MILLIS
            SummaryStatisticsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> ONE_DAY_MILLIS
            SummaryStatisticsViewModel.TimeCategory.SEVEN_DAYS -> ONE_WEEK_MILLIS
        }

    private companion object {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }
}

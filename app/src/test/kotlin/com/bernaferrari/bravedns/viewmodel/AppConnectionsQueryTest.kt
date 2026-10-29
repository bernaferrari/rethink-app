package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.DomainStatsDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppConnectionsQueryTest {
    private val dispatcher = StandardTestDispatcher()
    private val network = mockk<ConnectionTrackerDAO>(relaxed = true)
    private val domains = mockk<DomainStatsDao>(relaxed = true)
    private lateinit var model: AppConnectionsViewModel
    private val now = 10_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { network.getAppIpLogs(any(), any()) } answers { EmptyConnections() }
        every { domains.getAllDomainsByUid(any(), any()) } answers { EmptyConnections() }
        model = AppConnectionsViewModel(network, mockk(), mockk(), mockk(), domains, mockk()) { now }
    }

    @After
    fun tearDown() {
        model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun emptySearchRangeChangeReplacesIpPager() = runTest(dispatcher) {
        model.setUid(42)
        model.timeCategoryChanged(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS, false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.appIpLogs.collect {} }
        advanceUntilIdle()
        verify { network.getAppIpLogs(42, now - 7 * DAY) }

        model.timeCategoryChanged(AppConnectionsViewModel.TimeCategory.ONE_HOUR, false)
        advanceUntilIdle()
        verify { network.getAppIpLogs(42, now - HOUR) }
    }

    @Test
    fun uidChangeReplacesIpPagerWithoutSearchChange() = runTest(dispatcher) {
        model.setUid(42)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.appIpLogs.collect {} }
        advanceUntilIdle()
        model.setUid(99)
        advanceUntilIdle()
        verify { network.getAppIpLogs(99, now - 7 * DAY) }
    }

    @Test
    fun emptySearchRangeChangeReplacesDomainPager() = runTest(dispatcher) {
        model.setUid(42)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.appDomainLogs.collect {} }
        advanceUntilIdle()
        model.timeCategoryChanged(AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR, true)
        advanceUntilIdle()
        verify { domains.getAllDomainsByUid(42, now - DAY) }
    }

    private class EmptyConnections : PagingSource<Int, AppConnection>() {
        override fun getRefreshKey(state: PagingState<Int, AppConnection>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppConnection> =
            LoadResult.Page(emptyList(), null, null)
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}

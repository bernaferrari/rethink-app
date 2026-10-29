package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.AppInfoDAO
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilter
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkFirewallFilters
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppInfoFilterTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var model: AppInfoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dao = mockk<AppInfoDAO>()
        every { dao.getAllAppDetailsFlow() } returns MutableStateFlow(
            AppConnectionStatus.entries.map { connection ->
                AppInfo().apply {
                    uid = connection.id
                    packageName = "package.${connection.name}"
                    appName = connection.name
                    firewallStatus = AppFirewallStatus.None.id
                    connectionStatus = connection.id
                }
            },
        )
        model = AppInfoViewModel(dao, mockk())
    }

    @After
    fun tearDown() {
        model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun networkFiltersIncludeFullyBlockedApps() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.appInfo.collect {} }
        model.setFilter(RethinkFirewallFilters(status = RethinkFirewallFilter.BlockedWifi))
        advanceTimeBy(301)
        runCurrent()
        assertEquals(setOf(AppConnectionStatus.Unmetered.id, AppConnectionStatus.Both.id), model.appInfo.value.map { it.uid }.toSet())

        model.setFilter(RethinkFirewallFilters(status = RethinkFirewallFilter.BlockedMobile))
        runCurrent()
        assertEquals(setOf(AppConnectionStatus.Metered.id, AppConnectionStatus.Both.id), model.appInfo.value.map { it.uid }.toSet())
    }
}

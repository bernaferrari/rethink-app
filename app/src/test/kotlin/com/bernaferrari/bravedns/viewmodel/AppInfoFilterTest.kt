package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.database.AppInfoDAO
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkAppSelectionAction
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppInfoFilterTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var model: AppInfoViewModel
    private val rules = FakeRules()

    /** In-memory firewall: uid -> (status, connection). */
    private class FakeRules : AppFirewallRuleMutator {
        val state = mutableMapOf<Int, Pair<AppFirewallStatus, AppConnectionStatus>>()
        override suspend fun firewallStatus(uid: Int) = state[uid]?.first ?: AppFirewallStatus.None
        override suspend fun connectionStatus(uid: Int) = state[uid]?.second ?: AppConnectionStatus.Allow
        override suspend fun updateFirewallStatus(uid: Int, firewallStatus: AppFirewallStatus, connectionStatus: AppConnectionStatus) {
            state[uid] = firewallStatus to connectionStatus
        }
    }

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
        model = AppInfoViewModel(dao, rules)
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
        assertEquals(setOf(AppConnectionStatus.Unmetered.id, AppConnectionStatus.Both.id), model.appInfo.value.orEmpty().map { it.uid }.toSet())

        model.setFilter(RethinkFirewallFilters(status = RethinkFirewallFilter.BlockedMobile))
        runCurrent()
        assertEquals(setOf(AppConnectionStatus.Metered.id, AppConnectionStatus.Both.id), model.appInfo.value.orEmpty().map { it.uid }.toSet())
    }

    @Test
    fun appsAreNullUntilTheFirstLoadSoNothingFlashesEmpty() {
        assertNull(model.appInfo.value)
    }

    @Test
    fun selectionChangesOnlySelectedUidsAndUndoRestoresEach() = runTest(dispatcher) {
        rules.state[10] = AppFirewallStatus.None to AppConnectionStatus.Metered
        rules.state[11] = AppFirewallStatus.Exclude to AppConnectionStatus.Allow
        rules.state[12] = AppFirewallStatus.None to AppConnectionStatus.Allow

        val before = model.applyToSelected(setOf(10, 11), RethinkAppSelectionAction.BlockWifi)

        // Wi‑Fi joins mobile data's block; a mode goes back to Normal; the unselected app is untouched.
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Both, rules.state[10])
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Unmetered, rules.state[11])
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Allow, rules.state[12])

        model.restore(before)
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Metered, rules.state[10])
        assertEquals(AppFirewallStatus.Exclude to AppConnectionStatus.Allow, rules.state[11])
    }

    @Test
    fun selectionModesClearNetworkBlocks() = runTest(dispatcher) {
        rules.state[20] = AppFirewallStatus.None to AppConnectionStatus.Both
        model.applyToSelected(setOf(20), RethinkAppSelectionAction.TrustedOnly)
        assertEquals(AppFirewallStatus.Isolate to AppConnectionStatus.Allow, rules.state[20])

        model.applyToSelected(setOf(20), RethinkAppSelectionAction.AllowMobile)
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Allow, rules.state[20])

        model.applyToSelected(setOf(20), RethinkAppSelectionAction.BlockMobile)
        model.applyToSelected(setOf(20), RethinkAppSelectionAction.AllowAll)
        assertEquals(AppFirewallStatus.None to AppConnectionStatus.Allow, rules.state[20])
    }
}

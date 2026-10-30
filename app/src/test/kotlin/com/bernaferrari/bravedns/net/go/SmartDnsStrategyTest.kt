package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.data.SmartDnsProfile
import com.celzero.firestack.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartDnsStrategyTest {
    @Test fun everyProfilePreservesSafestOrderingAndInstallsItsNativeFilter() {
        val expected = listOf(Settings.PlusFilterNone, Settings.PlusFilterSecurity, Settings.PlusFilterAdblock, Settings.PlusFilterFamily)
        SmartDnsProfile.entries.forEachIndexed { index, profile ->
            var arguments: Pair<Long, Long>? = null
            SmartDnsStrategy.apply(profile.value, install = { order, filter -> arguments = order to filter; true })
            assertEquals(Settings.PlusOrderSafest to expected[index], arguments)
        }
    }
    @Test fun unknownStoredValueRemainsUnfilteredAndExplicitOrderingIsNotOverridden() {
        var arguments: Pair<Long, Long>? = null
        SmartDnsStrategy.apply(-1, Settings.PlusOrderRobust) { order, filter -> arguments = order to filter; true }
        assertEquals(Settings.PlusOrderRobust to Settings.PlusFilterNone, arguments)
    }
    @Test(expected = IllegalStateException::class)
    fun rejectedNativeStrategyIsNotReportedAsSuccess() {
        SmartDnsStrategy.apply(SmartDnsProfile.Family.value, install = { _, _ -> false })
    }

}

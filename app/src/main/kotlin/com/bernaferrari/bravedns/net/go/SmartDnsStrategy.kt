package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.data.SmartDnsProfile
import com.celzero.firestack.settings.Settings

internal object SmartDnsStrategy {
    fun apply(
        storedProfile: Int,
        order: Long = Settings.PlusOrderSafest,
        install: (Long, Long) -> Boolean = Settings::setPlusStrategy,
    ) {
        val filter = when (SmartDnsProfile.fromValue(storedProfile)) {
            SmartDnsProfile.None -> Settings.PlusFilterNone
            SmartDnsProfile.Security -> Settings.PlusFilterSecurity
            SmartDnsProfile.Privacy -> Settings.PlusFilterAdblock
            SmartDnsProfile.Family -> Settings.PlusFilterFamily
        }
        check(install(order, filter)) { "Cannot configure Smart DNS filtering" }
    }
}

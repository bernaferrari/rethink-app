package com.bernaferrari.bravedns.data

/** Runtime acknowledgement, separate from the saved selection. Never included in backups. */
sealed interface SmartDnsApplyStatus {
    data object Saved : SmartDnsApplyStatus
    data class Queued(val profile: SmartDnsProfile) : SmartDnsApplyStatus
    data class Settling(val profile: SmartDnsProfile) : SmartDnsApplyStatus
    data class Active(val profile: SmartDnsProfile) : SmartDnsApplyStatus
    data class Failed(val profile: SmartDnsProfile) : SmartDnsApplyStatus
}

/** An earlier selection cannot advertise the current choice as active. */
fun SmartDnsApplyStatus.forSelection(selection: SmartDnsProfile): SmartDnsApplyStatus {
    val profile = when (this) {
        SmartDnsApplyStatus.Saved -> return this
        is SmartDnsApplyStatus.Queued -> profile
        is SmartDnsApplyStatus.Settling -> profile
        is SmartDnsApplyStatus.Active -> profile
        is SmartDnsApplyStatus.Failed -> profile
    }
    return if (profile == selection) this else SmartDnsApplyStatus.Queued(selection)
}

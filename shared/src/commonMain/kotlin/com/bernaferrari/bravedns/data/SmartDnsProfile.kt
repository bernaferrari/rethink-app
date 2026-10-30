package com.bernaferrari.bravedns.data

/** Stable preference values; the native unfiltered policy remains the default. */
enum class SmartDnsProfile(val value: Int) {
    None(0), Security(1), Privacy(2), Family(3);

    companion object {
        fun fromValue(value: Int): SmartDnsProfile = entries.firstOrNull { it.value == value } ?: None
    }
}

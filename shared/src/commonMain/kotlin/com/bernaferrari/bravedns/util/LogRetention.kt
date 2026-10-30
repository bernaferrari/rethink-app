package com.bernaferrari.bravedns.util

/** Storage policy; scheduler cadence is approximate, query cutoffs are exact. */
enum class LogRetention(val hours: Int) {
    OneHour(1), ThreeHours(3), SixHours(6), TwelveHours(12), OneDay(24), ThreeDays(72), SevenDays(168);

    val durationMillis: Long get() = hours * 60L * 60L * 1000L
    val purgeIntervalMinutes: Long get() = (hours * 15L).coerceIn(15L, 240L)

    companion object {
        fun fromHours(hours: Int): LogRetention = entries.firstOrNull { it.hours == hours } ?: SevenDays
    }
}

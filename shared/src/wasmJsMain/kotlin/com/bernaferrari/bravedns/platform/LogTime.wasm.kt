package com.bernaferrari.bravedns.platform

actual fun formatLogTime(epochMillis: Long): String {
    fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
    // The nonfunctional browser demo uses a deterministic UTC clock presentation.
    val millisInDay = 86_400_000L
    val localDayOffset = ((epochMillis % millisInDay) + millisInDay) % millisInDay
    val hours = (localDayOffset / 3_600_000L).toInt()
    val minutes = ((localDayOffset / 60_000L) % 60L).toInt()
    val seconds = ((localDayOffset / 1_000L) % 60L).toInt()
    return "${twoDigits(hours)}:${twoDigits(minutes)}:${twoDigits(seconds)}"
}

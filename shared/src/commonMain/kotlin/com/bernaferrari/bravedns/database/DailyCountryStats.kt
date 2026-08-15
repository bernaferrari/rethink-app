/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "daily_country_stats",
    primaryKeys = ["date", "flag"],
    indices =
        [
            Index(value = ["date"]),
            Index(value = ["date", "flag"]),
        ],
)
data class DailyCountryStats(
    val date: Long,
    val flag: String,
    val connectionCount: Int,
    val dnsQueryCount: Int,
)

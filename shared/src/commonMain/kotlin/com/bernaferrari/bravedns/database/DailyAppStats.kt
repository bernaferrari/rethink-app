/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "daily_app_stats",
    primaryKeys = ["date", "uid"],
    indices =
        [
            Index(value = ["date"]),
            Index(value = ["date", "uid"]),
        ],
)
data class DailyAppStats(
    val date: Long,
    val uid: Int,
    val appName: String,
    val connectionCount: Int,
    val blockedCount: Int,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val dnsQueryCount: Int,
    val blockedDnsCount: Int,
)

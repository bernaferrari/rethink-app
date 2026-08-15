/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "daily_ip_stats",
    primaryKeys = ["date", "ipAddress"],
    indices =
        [
            Index(value = ["date"]),
            Index(value = ["date", "ipAddress"]),
        ],
)
data class DailyIpStats(
    val date: Long,
    val ipAddress: String,
    val flag: String,
    val contactedCount: Int,
    val blockedCount: Int,
    val downloadBytes: Long,
    val uploadBytes: Long,
)

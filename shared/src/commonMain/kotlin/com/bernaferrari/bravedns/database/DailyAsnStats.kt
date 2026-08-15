/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "daily_asn_stats",
    primaryKeys = ["date", "asName"],
    indices =
        [
            Index(value = ["date"]),
            Index(value = ["date", "asName"]),
        ],
)
data class DailyAsnStats(
    val date: Long,
    val asName: String,
    val countryCode: String,
    val connectionCount: Int,
    val blockedCount: Int,
    val downloadBytes: Long,
    val uploadBytes: Long,
)

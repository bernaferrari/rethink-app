/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "daily_domain_stats",
    primaryKeys = ["date", "domain"],
    indices =
        [
            Index(value = ["date"]),
            Index(value = ["date", "domain"]),
        ],
)
data class DailyDomainStats(
    val date: Long,
    val domain: String,
    val flag: String,
    val contactedCount: Int,
    val blockedCount: Int,
)

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

/** Connections by verdict over a window (Home's last-24-hours summary). */
data class VerdictCounts(
    val allowed: Long,
    val blocked: Long,
)

/** An app and how many of its connections were blocked over a window. */
data class AppBlockCount(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val count: Long,
)

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

/**
 * One row of a log ranking: an app that looked a domain up, or a host an app reached.
 * [blocked] of [count] entries were blocked.
 */
data class LogGroupCount(
    val label: String,
    val packageName: String,
    val count: Int,
    val blocked: Int,
)

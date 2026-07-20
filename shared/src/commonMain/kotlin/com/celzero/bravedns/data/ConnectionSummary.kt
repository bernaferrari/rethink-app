/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.celzero.bravedns.data

// do not use as key in map or set, as some fields are mutable
data class ConnectionSummary(
    val uid: String,
    val pid: String,
    val rpid: String,
    val connId: String,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val duration: Int,
    val rtt: Long,
    val message: String,
    val targetIp: String?,
    var flag: String?,
)

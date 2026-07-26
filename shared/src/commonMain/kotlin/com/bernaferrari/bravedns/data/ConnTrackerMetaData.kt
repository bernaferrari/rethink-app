/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.data

// do not use it as key in the map or set, as it is mutable
data class ConnTrackerMetaData(
    val uid: Int,
    val usrId: Int,
    val sourceIP: String,
    val sourcePort: Int,
    var destIP: String,
    val destPort: Int,
    val timestamp: Long,
    var isBlocked: Boolean,
    var blockedByRule: String,
    var proxyDetails: String,
    var blocklists: String,
    val protocol: Int,
    var query: String?,
    var connId: String,
    var connType: String,
    var rpid: String = "",
    var message: String = "",
    var downloadBytes: Long = 0,
    var uploadBytes: Long = 0,
    var duration: Int = 0,
    var synack: Long = 0,
)

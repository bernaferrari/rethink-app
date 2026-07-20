/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import android.content.ContentValues
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString

fun CustomIp.getCustomIpAddress(): Pair<IPAddress, Int>? = try {
    val ip = IPAddressString(ipAddress).address
    Pair(ip, port)
} catch (_: Exception) {
    null
}

fun CustomIp.applyContentValues(values: ContentValues?) {
    values?.valueSet()?.forEach { entry ->
        when (entry.key) {
            "uid" -> uid = entry.value as? Int ?: uid
            "ipAddress" -> ipAddress = entry.value as? String ?: ipAddress
            "port" -> port = entry.value as? Int ?: port
            "protocol" -> protocol = entry.value as? String ?: protocol
            "isActive" -> isActive = (entry.value as? Int ?: 1) == 1
            "status" -> status = entry.value as? Int ?: status
            "wildcard" -> wildcard = (entry.value as? Int ?: 0) == 1
            "ruleType" -> ruleType = entry.value as? Int ?: ruleType
            "modifiedDateTime" -> modifiedDateTime = entry.value as? Long ?: modifiedDateTime
            "proxyId" -> proxyId = entry.value as? String ?: proxyId
            "proxyCC" -> proxyCC = entry.value as? String ?: proxyCC
        }
    }
}

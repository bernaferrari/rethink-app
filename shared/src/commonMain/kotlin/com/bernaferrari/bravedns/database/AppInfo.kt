/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Ignore

/** firewallStatus/connectionStatus ints mirror app FirewallManager ids. */
@Entity(primaryKeys = ["uid", "packageName"], tableName = "AppInfo")
data class AppInfo(
    var packageName: String = "",
    var appName: String = "",
    var uid: Int = 0,
    var isSystemApp: Boolean = false,
    var firewallStatus: Int = 0,
    var appCategory: String = "",
    var wifiDataUsed: Long = 0,
    var mobileDataUsed: Long = 0,
    var connectionStatus: Int = 0,
    var screenOffAllowed: Boolean = false,
    var backgroundAllowed: Boolean = false,
    var uploadBytes: Long = 0,
    var downloadBytes: Long = 0,
    var isProxyExcluded: Boolean = false,
    var tombstoneTs: Long = 0,
    var modifiedTs: Long = 0,
    var tempAllowEnabled: Boolean = false,
    var tempAllowExpiryTime: Long = 0,
    var notes: String = ""
) {
    /** Detached observer value: mutable cache entries never alias emitted snapshots. */
    fun snapshot(): AppInfo = copy()

    @Ignore constructor(_unused: Any?) : this()

    @Ignore
    constructor(
        packageName: String,
        appName: String,
        uid: Int,
        isSystemApp: Boolean,
        firewallStatus: Int,
        appCategory: String,
        wifiDataUsed: Long,
        mobileDataUsed: Long,
        connectionStatus: Int,
        isProxyExcluded: Boolean,
        screenOffAllowed: Boolean,
        backgroundAllowed: Boolean,
        tombstoneTs: Long = 0,
    ) : this(
        packageName = packageName,
        appName = appName,
        uid = uid,
        isSystemApp = isSystemApp,
        firewallStatus = firewallStatus,
        appCategory = appCategory,
        wifiDataUsed = wifiDataUsed,
        mobileDataUsed = mobileDataUsed,
        connectionStatus = connectionStatus,
        isProxyExcluded = isProxyExcluded,
        screenOffAllowed = screenOffAllowed,
        backgroundAllowed = backgroundAllowed,
        tombstoneTs = tombstoneTs,
        modifiedTs = 0,
    )
}

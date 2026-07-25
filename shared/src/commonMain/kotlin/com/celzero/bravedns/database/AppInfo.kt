/*
 * Copyright 2020 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import androidx.room3.Entity
import androidx.room3.Ignore
import com.celzero.bravedns.database.AppInfoRepository.Companion.NO_PACKAGE_PREFIX

/** firewallStatus/connectionStatus ints mirror app FirewallManager ids. */
@Entity(primaryKeys = ["uid", "packageName"], tableName = "AppInfo")
class AppInfo {
    var packageName: String = ""
    var appName: String = ""
    var uid: Int = 0
    var isSystemApp: Boolean = false
    var firewallStatus: Int = 0
    var appCategory: String = ""
    var wifiDataUsed: Long = 0
    var mobileDataUsed: Long = 0
    var connectionStatus: Int = 0
    var screenOffAllowed: Boolean = false
    var backgroundAllowed: Boolean = false
    var uploadBytes: Long = 0
    var downloadBytes: Long = 0
    var isProxyExcluded: Boolean = false
    var tombstoneTs: Long = 0
    var modifiedTs: Long = 0
    var tempAllowEnabled: Boolean = false
    var tempAllowExpiryTime: Long = 0

    override fun equals(other: Any?): Boolean {
        if (other !is AppInfo) return false
        if (packageName != other.packageName) return false
        if (firewallStatus != other.firewallStatus) return false
        if (connectionStatus != other.connectionStatus) return false
        return true
    }

    override fun hashCode(): Int {
        var result = this.packageName.hashCode()
        result += result * 31 + this.firewallStatus
        result += result * 31 + this.connectionStatus
        return result
    }

    constructor()

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
    ) {
        this.packageName = packageName
        this.appName = appName
        this.uid = uid
        this.isSystemApp = isSystemApp
        this.firewallStatus = firewallStatus
        this.appCategory = appCategory
        this.wifiDataUsed = wifiDataUsed
        this.mobileDataUsed = mobileDataUsed
        this.connectionStatus = connectionStatus
        this.isProxyExcluded = isProxyExcluded
        this.screenOffAllowed = screenOffAllowed
        this.backgroundAllowed = backgroundAllowed
        this.tombstoneTs = tombstoneTs
    }
}

/*
Copyright 2020 RethinkDNS and its authors
*/
package com.celzero.bravedns.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.celzero.bravedns.platform.currentTimeMillis
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS

@Entity(tableName = "DNSProxyEndpoint")
class DnsProxyEndpoint {
    @PrimaryKey(autoGenerate = true) var id: Int = 0
    var proxyName: String = ""
    var proxyType: String = ""
    var proxyAppName: String? = null
    var proxyIP: String? = null
    var proxyPort: Int = 0
    var isSelected: Boolean = false
    var isCustom: Boolean = true
    var isSecure: Boolean = false
    var modifiedDataTime: Long = INIT_TIME_MS
    var latency: Int = 0

    override fun equals(other: Any?): Boolean {
        if (other !is DnsProxyEndpoint) return false
        return id == other.id && isSelected == other.isSelected
    }

    override fun hashCode(): Int = id.hashCode()

    constructor()

    constructor(
        id: Int,
        proxyName: String,
        proxyType: String,
        proxyAppName: String,
        proxyIP: String?,
        proxyPort: Int,
        isSelected: Boolean,
        isCustom: Boolean,
        isSecure: Boolean = false,
        modifiedDataTime: Long,
        latency: Int
    ) {
        this.id = id
        this.proxyName = proxyName
        this.proxyType = proxyType
        this.proxyAppName = proxyAppName
        this.proxyIP = proxyIP
        this.proxyPort = proxyPort
        this.isSelected = isSelected
        this.isCustom = isCustom
        this.isSecure = isSecure
        this.modifiedDataTime = if (modifiedDataTime != 0L) modifiedDataTime else currentTimeMillis()
        this.latency = latency
    }

    // Secondary ctor matching ConfigureOtherDnsScreen call sites (no isSecure arg)
    constructor(
        id: Int,
        proxyName: String,
        proxyType: String,
        proxyAppName: String,
        proxyIP: String?,
        proxyPort: Int,
        isSelected: Boolean,
        isCustom: Boolean,
        modifiedDataTime: Long,
        latency: Int
    ) : this(id, proxyName, proxyType, proxyAppName, proxyIP, proxyPort, isSelected, isCustom, false, modifiedDataTime, latency)

    fun isDeletable(): Boolean = isCustom && !isSelected

    fun getPackageName(): String? = proxyAppName

    fun getExplanationText(appName: String): String = appName

    fun isInternal(): Boolean = false
}

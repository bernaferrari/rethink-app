/*
 * Copyright 2021 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import com.bernaferrari.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.Constants.Companion.UNSPECIFIED_PORT

@Entity(primaryKeys = ["uid", "ipAddress", "port", "protocol"], tableName = "CustomIp")
class CustomIp {
    var uid: Int = UID_EVERYBODY
    var ipAddress: String = ""
    var port: Int = UNSPECIFIED_PORT
    var protocol: String = ""
    var isActive: Boolean = true
    var proxyId: String = ""
    var proxyCC: String = ""
    var status: Int = 0
    var wildcard: Boolean = false
    var ruleType: Int = 0
    var modifiedDateTime: Long = INIT_TIME_MS

    fun isIPv6(): Boolean = ipAddress.contains(":")

    fun deepCopy(): CustomIp {
        val c = CustomIp()
        c.uid = uid; c.ipAddress = ipAddress; c.port = port; c.protocol = protocol
        c.isActive = isActive; c.proxyId = proxyId; c.proxyCC = proxyCC
        c.status = status; c.wildcard = wildcard; c.ruleType = ruleType
        c.modifiedDateTime = modifiedDateTime
        return c
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CustomIp) return false
        return ipAddress == other.ipAddress && port == other.port && uid == other.uid && status == other.status
    }

    override fun hashCode(): Int {
        var result = uid
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + status
        return result
    }
}

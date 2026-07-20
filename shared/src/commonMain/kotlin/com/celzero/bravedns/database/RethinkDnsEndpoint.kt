/*
 * Copyright 2022 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import androidx.room3.Entity
import com.celzero.bravedns.platform.currentTimeMillis

@Entity(primaryKeys = ["name", "url", "uid"], tableName = "RethinkDnsEndpoint")
class RethinkDnsEndpoint(
    var name: String,
    var url: String,
    var uid: Int,
    var desc: String,
    var isActive: Boolean,
    var isCustom: Boolean,
    var latency: Int,
    var blocklistCount: Int,
    var modifiedDataTime: Long
) {

    override fun equals(other: Any?): Boolean {
        if (other !is RethinkDnsEndpoint) return false
        if (name != other.name) return false
        if (url != other.url) return false
        return uid == other.uid
    }

    override fun hashCode(): Int {
        var result = this.name.hashCode()
        result += result * 31 + this.url.hashCode()
        result += result * 31 + this.uid.hashCode()
        return result
    }

    companion object {
        const val RETHINK_DEFAULT: String = "RDNS Default"
        const val RETHINK_PLUS: String = "RDNS Plus"
    }

    init {
        this.modifiedDataTime = currentTimeMillis()
    }

    fun isEditable(): Boolean = isCustom
}

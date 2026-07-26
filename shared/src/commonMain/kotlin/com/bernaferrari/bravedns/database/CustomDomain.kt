package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Ignore
import com.bernaferrari.bravedns.util.Constants

@Entity(primaryKeys = ["domain", "uid"], tableName = "CustomDomain")
class CustomDomain {
    var domain: String = ""
    var uid: Int = Constants.UID_EVERYBODY
    var ips: String = ""
    var status: Int = 0
    var type: Int = 0
    var proxyId: String = ""
    var proxyCC: String = ""
    var modifiedTs: Long = 0L
    var deletedTs: Long = 0L
    var version: Long = 0L

    constructor()

    @Ignore
    constructor(
        domain: String,
        uid: Int,
        ips: String,
        type: Int,
        status: Int,
        proxyId: String,
        proxyCC: String,
        modifiedTs: Long,
        deletedTs: Long,
        version: Long
    ) {
        this.domain = domain.dropLastWhile { it == '.' }
        this.uid = uid
        this.ips = ips
        this.type = type
        this.status = status
        this.proxyId = proxyId
        this.proxyCC = proxyCC
        this.modifiedTs = modifiedTs
        this.deletedTs = deletedTs
        this.version = version
    }

    companion object {
        private const val CURRENT_VERSION: Long = 1L
        fun getCurrentVersion(): Long = CURRENT_VERSION
    }

    fun isBlocked(): Boolean = status != 0
}

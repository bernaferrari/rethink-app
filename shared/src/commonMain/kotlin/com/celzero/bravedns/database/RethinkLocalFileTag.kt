/*
 * Copyright 2022 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey

@Entity(tableName = "RethinkLocalFileTag")
class RethinkLocalFileTag {
    @PrimaryKey var value: Int = 0
    var uname: String = ""
    var vname: String = ""
    var group: String = ""
    var subg: String = ""
    var url: List<String> = arrayListOf()
    var show: Int = 0
    var entries: Int = 0
    var pack: List<String>? = null
    var level: List<Int>? = null
    var simpleTagId: Int = INVALID_SIMPLE_TAG_ID
    var isSelected: Boolean = false

    companion object {
        const val INVALID_SIMPLE_TAG_ID = -1
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RethinkLocalFileTag) return false
        if (value != other.value) return false
        if (isSelected != other.isSelected) return false
        return true
    }

    override fun hashCode(): Int = this.value.hashCode()

    constructor()

    @Ignore
    constructor(
        value: Int,
        uname: String,
        vname: String,
        group: String,
        subg: String,
        pack: List<String>?,
        level: List<Int>?,
        url: List<String>,
        show: Int,
        entries: Int,
        simpleTagId: Int,
        isSelected: Boolean = false
    ) {
        this.value = value
        this.uname = uname
        this.vname = vname
        this.group = group
        this.subg = subg
        this.url = url
        this.show = show
        this.entries = entries
        this.pack = pack
        this.level = level
        this.simpleTagId = simpleTagId
        this.isSelected = isSelected
    }
}

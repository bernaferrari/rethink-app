/*
 * Copyright 2025 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.bernaferrari.bravedns.platform.currentTimeMillis

/** Persisted RPN server metadata and the user's per-server routing preferences. */
@Entity(
    tableName = "CountryConfig",
    indices = [
        Index(value = ["cc"]),
        Index(value = ["isActive"]),
        Index(value = ["isFavourite"])
    ]
)
data class CountryConfig(
    @PrimaryKey val id: String,
    val cc: String,
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val key: String = "",
    val load: Int = 0,
    val link: Int = 0,
    val count: Int = 0,
    val premium: Boolean = false,
    var isActive: Boolean = true,
    var isEnabled: Boolean = false,
    var catchAll: Boolean = false,
    var lockdown: Boolean = false,
    var mobileOnly: Boolean = false,
    var ssidBased: Boolean = false,
    val priority: Int = 0,
    val ssids: String = "",
    val lastModified: Long = currentTimeMillis(),
    val selectionCount: Int = 0,
    var isFavourite: Boolean = false,
    var hopEnabled: Boolean = false
) {
    val serverLocation: String get() = city.ifBlank { name }

    companion object {
        const val TAG = "CountryConfig"
    }
}

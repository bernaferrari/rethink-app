/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.service

/** Controls which resolver is used when a trusted DNS rule must bypass blocking. */
enum class BlockFreeDnsMode(val mode: Int) {
    FALLBACK(1),
    GLOBAL(2),
    AUTO(3);

    companion object {
        fun fromMode(mode: Int): BlockFreeDnsMode = entries.find { it.mode == mode } ?: AUTO
    }
}

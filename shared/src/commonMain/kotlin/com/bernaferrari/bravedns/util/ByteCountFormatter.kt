/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.util

import kotlin.math.roundToLong

private const val SI_UNIT = 1000.0
private const val UNIT_PREFIXES = "KMGTPE"

// A value that would round up to "1000.0" is promoted to the next unit instead.
private const val ROUNDING_CEILING = 999.95

/**
 * The single byte-count formatter for every user-visible size (statistics, logs, WireGuard,
 * memory diagnostics): SI (1000-based), one decimal, `B` / `KB` / `MB` / `GB` / `TB` / `PB` / `EB`.
 *
 * Matches what the connection logs have always shown, e.g. `999 B`, `1.0 KB`, `12.3 MB`.
 * Negative inputs are treated as zero.
 */
fun formatByteCount(bytes: Long): String {
    if (bytes < SI_UNIT) return "${bytes.coerceAtLeast(0L)} B"
    var value = bytes / SI_UNIT
    var unitIndex = 0
    while (value >= ROUNDING_CEILING && unitIndex < UNIT_PREFIXES.lastIndex) {
        value /= SI_UNIT
        unitIndex++
    }
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10} ${UNIT_PREFIXES[unitIndex]}B"
}

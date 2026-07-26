/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bernaferrari.bravedns.wireguard

/** WireGuard config line attribute (key = value). Multiplatform, no java.util. */
class Attribute private constructor(val key: String, val value: String) {

    companion object {
        private val LINE_PATTERN = Regex("""(\w+)\s*=\s*([^\s#][^#]*)""")

        fun join(values: Iterable<*>): String = values.joinToString(",")

        fun parse(line: CharSequence?): Attribute? {
            val m = LINE_PATTERN.matchEntire((line ?: "").trim()) ?: return null
            return Attribute(m.groupValues[1], m.groupValues[2].trim())
        }

        fun split(value: CharSequence?): List<String> {
            if (value == null || value.isEmpty()) return emptyList()
            return value.split(Regex("""\s*,\s*""")).filter { it.isNotEmpty() }
        }
    }
}

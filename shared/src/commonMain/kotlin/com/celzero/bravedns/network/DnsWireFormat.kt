/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.network

import kotlin.random.Random

/** DNS wire-format helpers. Returns textual IP addresses (no java.net). */
internal object DnsWireFormat {
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28
    private const val CLASS_IN = 1

    fun buildQuery(hostname: String): ByteArray {
        val labels = hostname.split('.').filter { it.isNotEmpty() }
        val qnameSize = labels.sumOf { it.length + 1 } + 1
        val buffer = ByteArray(12 + qnameSize + 4)
        val id = Random.nextInt(0, 65535)
        buffer[0] = (id shr 8).toByte()
        buffer[1] = id.toByte()
        buffer[2] = 0x01
        buffer[3] = 0x00
        buffer[4] = 0x00
        buffer[5] = 0x01
        var offset = 12
        for (label in labels) {
            buffer[offset++] = label.length.toByte()
            label.encodeToByteArray().copyInto(buffer, offset)
            offset += label.length
        }
        buffer[offset++] = 0
        buffer[offset++] = 0x00
        buffer[offset++] = 0x01.toByte()
        buffer[offset++] = 0x00
        buffer[offset] = CLASS_IN.toByte()
        return buffer
    }

    fun parseAddresses(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()
        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (answerCount == 0) return emptyList()
        var offset = 12
        offset = skipName(response, offset)
        offset += 4
        val addresses = mutableListOf<String>()
        repeat(answerCount) {
            if (offset >= response.size) return@repeat
            offset = skipName(response, offset)
            if (offset + 10 > response.size) return@repeat
            val type = readU16(response, offset)
            offset += 2
            offset += 2
            offset += 4
            val rdLength = readU16(response, offset)
            offset += 2
            if (offset + rdLength > response.size) return@repeat
            when (type) {
                TYPE_A -> {
                    if (rdLength == 4) {
                        addresses.add(formatIpv4(response, offset))
                    }
                }
                TYPE_AAAA -> {
                    if (rdLength == 16) {
                        addresses.add(formatIpv6(response, offset))
                    }
                }
            }
            offset += rdLength
        }
        return addresses
    }

    private fun formatIpv4(bytes: ByteArray, offset: Int): String =
        "${bytes[offset].toUByte()}.${bytes[offset + 1].toUByte()}.${bytes[offset + 2].toUByte()}.${bytes[offset + 3].toUByte()}"

    private fun formatIpv6(bytes: ByteArray, offset: Int): String {
        val parts = ArrayList<String>(8)
        for (i in 0 until 8) {
            val hi = bytes[offset + i * 2].toInt() and 0xFF
            val lo = bytes[offset + i * 2 + 1].toInt() and 0xFF
            parts.add(((hi shl 8) or lo).toString(16))
        }
        return parts.joinToString(":")
    }

    private fun skipName(response: ByteArray, offset: Int): Int {
        var pos = offset
        while (pos < response.size) {
            val len = response[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if (len and 0xC0 == 0xC0) return pos + 2
            pos += len + 1
        }
        return pos
    }

    private fun readU16(response: ByteArray, offset: Int): Int {
        return ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
    }
}

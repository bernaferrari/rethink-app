/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bernaferrari.bravedns.wireguard

/** Internet network as address string + mask (no java.net.InetAddress). */
class InetNetwork private constructor(val address: String, val mask: Int) {

    override fun equals(obj: Any?): Boolean {
        if (obj !is InetNetwork) return false
        return address == obj.address && mask == obj.mask
    }

    override fun hashCode(): Int = address.hashCode() xor mask

    override fun toString(): String = "$address/$mask"

    companion object {
        @Throws(ParseException::class)
        fun parse(network: String): InetNetwork {
            val slash = network.lastIndexOf('/')
            val maskString: String
            val rawMask: Int
            val rawAddress: String
            if (slash >= 0) {
                maskString = network.substring(slash + 1)
                rawMask = try {
                    maskString.toInt(10)
                } catch (_: NumberFormatException) {
                    throw ParseException("Int", maskString)
                }
                rawAddress = network.substring(0, slash)
            } else {
                maskString = ""
                rawMask = -1
                rawAddress = network
            }
            val address = InetAddresses.parse(rawAddress)
            val maxMask = if (InetAddresses.isIpv4(address)) 32 else 128
            if (rawMask > maxMask)
                throw ParseException("InetNetwork", maskString, "Invalid network mask")
            val mask = if (rawMask >= 0) rawMask else maxMask
            return InetNetwork(address, mask)
        }
    }
}

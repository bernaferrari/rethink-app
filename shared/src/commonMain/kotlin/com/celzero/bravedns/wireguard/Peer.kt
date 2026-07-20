/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.celzero.bravedns.wireguard

import com.celzero.bravedns.wireguard.BadConfigException.Location
import com.celzero.bravedns.wireguard.BadConfigException.Reason
import com.celzero.bravedns.wireguard.BadConfigException.Section

/** WireGuard peer block — multiplatform (WgKeyHandle, nullable optionals, no java.util). */
class Peer private constructor(builder: Builder) {
    val id: Int = 0
    private val allowedIps: Set<InetNetwork>
    private val endpoint: InetEndpoint?
    private val unresolvedEndpoint: String?
    val persistentKeepalive: Int?
    private val preSharedKey: WgKeyHandle?
    private val publicKey: WgKeyHandle

    init {
        allowedIps = builder.allowedIps.toSet()
        endpoint = builder.endpoint
        unresolvedEndpoint = builder.unresolvedEndpoint
        persistentKeepalive = builder.persistentKeepalive
        preSharedKey = builder.preSharedKey
        publicKey = requireNotNull(builder.publicKey) { "Peers must have a public key" }
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is Peer) return false
        return allowedIps == obj.allowedIps &&
            endpoint == obj.endpoint &&
            unresolvedEndpoint == obj.unresolvedEndpoint &&
            persistentKeepalive == obj.persistentKeepalive &&
            preSharedKey == obj.preSharedKey &&
            publicKey == obj.publicKey
    }

    fun getAllowedIps(): Set<InetNetwork> = allowedIps
    fun getEndpoint(): InetEndpoint? = endpoint
    fun getEndpointText(): String? = unresolvedEndpoint
    fun getPreSharedKey(): WgKeyHandle? = preSharedKey
    fun getPublicKey(): WgKeyHandle = publicKey

    override fun hashCode(): Int {
        var hash = 1
        hash = 31 * hash + allowedIps.hashCode()
        hash = 31 * hash + (endpoint?.hashCode() ?: 0)
        hash = 31 * hash + (unresolvedEndpoint?.hashCode() ?: 0)
        hash = 31 * hash + (persistentKeepalive ?: 0)
        hash = 31 * hash + (preSharedKey?.hashCode() ?: 0)
        hash = 31 * hash + publicKey.hashCode()
        return hash
    }

    override fun toString(): String {
        val sb = StringBuilder("(Peer ")
        sb.append(publicKey.base64())
        endpoint?.let { sb.append(" @").append(it) }
        sb.append(')')
        return sb.toString()
    }

    fun toWgQuickString(): String {
        val sb = StringBuilder()
        if (allowedIps.isNotEmpty())
            sb.append("AllowedIPs = ").append(Attribute.join(allowedIps)).append('\n')
        endpoint?.let { sb.append("Endpoint = ").append(it).append('\n') }
        unresolvedEndpoint?.let { sb.append("Endpoint = ").append(it).append('\n') }
        persistentKeepalive?.let { sb.append("PersistentKeepalive = ").append(it).append('\n') }
        preSharedKey?.let { sb.append("PreSharedKey = ").append(it.base64()).append('\n') }
        sb.append("PublicKey = ").append(publicKey.base64()).append('\n')
        return sb.toString()
    }

    fun toWgUserspaceString(isAmz: Boolean = false, amzDebugAllowedIps: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("public_key=").append(publicKey.hex()).append('\n')
        if (amzDebugAllowedIps) {
            sb.append("allowed_ip=").append("0.0.0.0/0").append('\n')
        } else {
            for (allowedIp in allowedIps) sb.append("allowed_ip=").append(allowedIp).append('\n')
        }
        endpoint?.getResolved()?.let { sb.append("endpoint=").append(it).append('\n') }
        unresolvedEndpoint?.let { sb.append("endpoint=").append(it).append('\n') }
        persistentKeepalive?.let {
            sb.append("persistent_keepalive_interval=").append(it).append('\n')
        }
        preSharedKey?.let { sb.append("preshared_key=").append(it.hex()).append('\n') }
        return sb.toString()
    }

    class Builder {
        val allowedIps: MutableSet<InetNetwork> = linkedSetOf()
        var endpoint: InetEndpoint? = null
        var unresolvedEndpoint: String? = null
        var persistentKeepalive: Int? = null
        var preSharedKey: WgKeyHandle? = null
        var publicKey: WgKeyHandle? = null

        fun addAllowedIp(allowedIp: InetNetwork): Builder { allowedIps.add(allowedIp); return this }
        fun addAllowedIps(ips: Collection<InetNetwork>?): Builder {
            ips?.let { allowedIps.addAll(it) }; return this
        }

        @Throws(BadConfigException::class)
        fun build(): Peer {
            if (publicKey == null)
                throw BadConfigException(Section.PEER, Location.PUBLIC_KEY, Reason.MISSING_ATTRIBUTE, null)
            return Peer(this)
        }

        @Throws(BadConfigException::class)
        fun parseAllowedIPs(allowedIps: CharSequence?): Builder {
            return try {
                for (allowedIp in Attribute.split(allowedIps)) addAllowedIp(InetNetwork.parse(allowedIp))
                this
            } catch (e: ParseException) {
                throw BadConfigException(Section.PEER, Location.ALLOWED_IPS, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parseEndpoint(endpoint: String): Builder {
            return try {
                setEndpoint(InetEndpoint.parse(endpoint))
                parseUnresolvedEndpoint(endpoint)
            } catch (e: ParseException) {
                throw BadConfigException(Section.PEER, Location.ENDPOINT, e)
            }
        }

        fun parseUnresolvedEndpoint(d: String): Builder {
            if (d.isEmpty()) return this
            return try {
                if (InetAddresses.isNumericAddress(d.substringBeforeLast(':'))) return this
                setUnresolvedEndpoint(d)
                this
            } catch (_: Exception) {
                setUnresolvedEndpoint(d)
                this
            }
        }

        @Throws(BadConfigException::class)
        fun parsePersistentKeepalive(persistentKeepalive: String): Builder {
            return try {
                setPersistentKeepalive(persistentKeepalive.toInt())
            } catch (e: NumberFormatException) {
                throw BadConfigException(Section.PEER, Location.PERSISTENT_KEEPALIVE, persistentKeepalive, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parsePreSharedKey(preSharedKey: String): Builder {
            return try {
                setPreSharedKey(WgCrypto.parsePrivateKey(preSharedKey))
            } catch (e: Exception) {
                throw BadConfigException(Section.PEER, Location.PRE_SHARED_KEY, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parsePublicKey(publicKey: String): Builder {
            return try {
                setPublicKey(WgCrypto.parsePublicKey(publicKey))
            } catch (e: Exception) {
                throw BadConfigException(Section.PEER, Location.PUBLIC_KEY, e)
            }
        }

        fun setEndpoint(endpoint: InetEndpoint): Builder { this.endpoint = endpoint; return this }
        fun setUnresolvedEndpoint(endpointText: String): Builder {
            this.unresolvedEndpoint = endpointText; return this
        }

        @Throws(BadConfigException::class)
        fun setPersistentKeepalive(value: Int): Builder {
            if (value < 0 || value > MAX_PERSISTENT_KEEPALIVE)
                throw BadConfigException(
                    Section.PEER, Location.PERSISTENT_KEEPALIVE, Reason.INVALID_VALUE, value.toString()
                )
            this.persistentKeepalive = if (value == 0) null else value
            return this
        }

        fun setPreSharedKey(preSharedKey: WgKeyHandle): Builder {
            this.preSharedKey = preSharedKey; return this
        }

        fun setPublicKey(publicKey: WgKeyHandle?): Builder {
            this.publicKey = publicKey; return this
        }

        companion object {
            private const val MAX_PERSISTENT_KEEPALIVE = 65535
        }
    }

    companion object {
        @Throws(BadConfigException::class)
        fun parse(lines: Iterable<CharSequence?>): Peer {
            val builder = Builder()
            for (line in lines) {
                val attribute = Attribute.parse(line)
                    ?: throw BadConfigException(Section.PEER, Location.TOP_LEVEL, Reason.SYNTAX_ERROR, line)
                when (attribute.key.lowercase()) {
                    "allowedips" -> builder.parseAllowedIPs(attribute.value)
                    "endpoint" -> builder.parseEndpoint(attribute.value)
                    "persistentkeepalive" -> builder.parsePersistentKeepalive(attribute.value)
                    "presharedkey" -> builder.parsePreSharedKey(attribute.value)
                    "publickey" -> builder.parsePublicKey(attribute.value)
                    else -> throw BadConfigException(
                        Section.PEER, Location.TOP_LEVEL, Reason.UNKNOWN_ATTRIBUTE, attribute.key
                    )
                }
            }
            return builder.build()
        }
    }
}

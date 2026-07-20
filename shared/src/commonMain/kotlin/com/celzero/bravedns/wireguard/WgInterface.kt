/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.celzero.bravedns.wireguard

import com.celzero.bravedns.wireguard.BadConfigException.Location
import com.celzero.bravedns.wireguard.BadConfigException.Reason
import com.celzero.bravedns.wireguard.BadConfigException.Section

/** WireGuard interface block — multiplatform. DNS servers are address strings. */
class WgInterface private constructor(builder: Builder) {
    private val addresses: Set<InetNetwork>
    val dnsServers: Set<String>
    val dnsSearchDomains: Set<String>
    val excludedApplications: Set<String>
    val includedApplications: Set<String>
    private val keyPair: KeyPair
    val listenPort: Int?
    private val clientId: String?
    private val jc: Int?
    private val jmin: Int?
    private val jmax: Int?
    private val s1: Int?
    private val s2: Int?
    private val h1: Long?
    private val h2: Long?
    private val h3: Long?
    private val h4: Long?
    val mtu: Int?

    init {
        addresses = builder.addresses.toSet()
        dnsServers = builder.dnsServers.toSet()
        dnsSearchDomains = builder.dnsSearchDomains.toSet()
        excludedApplications = builder.excludedApplications.toSet()
        includedApplications = builder.includedApplications.toSet()
        keyPair = requireNotNull(builder.keyPair) { "Interfaces must have a private key" }
        listenPort = builder.listenPort
        mtu = builder.mtu
        clientId = builder.clientId
        jc = builder.jc
        jmin = builder.jmin
        jmax = builder.jmax
        s1 = builder.s1
        s2 = builder.s2
        h1 = builder.h1
        h2 = builder.h2
        h3 = builder.h3
        h4 = builder.h4
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is WgInterface) return false
        return addresses == obj.addresses &&
            dnsServers == obj.dnsServers &&
            dnsSearchDomains == obj.dnsSearchDomains &&
            excludedApplications == obj.excludedApplications &&
            includedApplications == obj.includedApplications &&
            keyPair.getPrivateKey().base64() == obj.keyPair.getPrivateKey().base64() &&
            listenPort == obj.listenPort &&
            mtu == obj.mtu &&
            clientId == obj.clientId &&
            jc == obj.jc && jmin == obj.jmin && jmax == obj.jmax &&
            s1 == obj.s1 && s2 == obj.s2 &&
            h1 == obj.h1 && h2 == obj.h2 && h3 == obj.h3 && h4 == obj.h4
    }

    fun getAddresses(): Set<InetNetwork> = addresses

    fun isAmnezia(): Boolean =
        jc != null || jmin != null || jmax != null || s1 != null || s2 != null ||
            h1 != null || h2 != null || h3 != null || h4 != null

    fun getAmzProps(): String =
        "jc=${jc ?: 0}, jmin=${jmin ?: 0}, jmax=${jmax ?: 0}, s1=${s1 ?: 0}, s2=${s2 ?: 0}, h1=${h1 ?: 0}, h2=${h2 ?: 0}, h3=${h3 ?: 0}, h4=${h4 ?: 0}"

    fun getKeyPair(): KeyPair = keyPair

    override fun hashCode(): Int {
        var hash = 1
        hash = 31 * hash + addresses.hashCode()
        hash = 31 * hash + dnsServers.hashCode()
        hash = 31 * hash + excludedApplications.hashCode()
        hash = 31 * hash + includedApplications.hashCode()
        hash = 31 * hash + keyPair.getPrivateKey().base64().hashCode()
        hash = 31 * hash + (listenPort ?: 0)
        hash = 31 * hash + (mtu ?: 0)
        hash = 31 * hash + (clientId?.hashCode() ?: 0)
        return hash
    }

    override fun toString(): String {
        val sb = StringBuilder("(Interface ")
        sb.append(keyPair.getPublicKey().base64())
        listenPort?.let { sb.append(" @").append(it) }
        sb.append(')')
        return sb.toString()
    }

    fun toWgQuickString(): String {
        val sb = StringBuilder()
        if (addresses.isNotEmpty())
            sb.append("Address = ").append(Attribute.join(addresses)).append('\n')
        if (dnsServers.isNotEmpty()) {
            val dnsServerStrings = dnsServers.toMutableList()
            dnsServerStrings.addAll(dnsSearchDomains)
            sb.append("DNS = ").append(Attribute.join(dnsServerStrings)).append('\n')
        }
        if (excludedApplications.isNotEmpty())
            sb.append("ExcludedApplications = ").append(Attribute.join(excludedApplications)).append('\n')
        if (includedApplications.isNotEmpty())
            sb.append("IncludedApplications = ").append(Attribute.join(includedApplications)).append('\n')
        listenPort?.let { sb.append("ListenPort = ").append(it).append('\n') }
        mtu?.let { sb.append("MTU = ").append(it).append('\n') }
        sb.append("PrivateKey = ").append(keyPair.getPrivateKey().base64()).append('\n')
        clientId?.let { sb.append("ClientID = ").append(it).append('\n') }
        jc?.let { sb.append("JC = ").append(it).append('\n') }
        jmin?.let { sb.append("JMin = ").append(it).append('\n') }
        jmax?.let { sb.append("JMax = ").append(it).append('\n') }
        s1?.let { sb.append("S1 = ").append(it).append('\n') }
        s2?.let { sb.append("S2 = ").append(it).append('\n') }
        h1?.let { sb.append("H1 = ").append(it).append('\n') }
        h2?.let { sb.append("H2 = ").append(it).append('\n') }
        h3?.let { sb.append("H3 = ").append(it).append('\n') }
        h4?.let { sb.append("H4 = ").append(it).append('\n') }
        return sb.toString()
    }

    fun toWgUserspaceString(skipListenPort: Boolean): String {
        val dnsServerStrings = dnsServers.toMutableList()
        dnsServerStrings.addAll(dnsSearchDomains)
        val sb = StringBuilder()
        sb.append("private_key=").append(keyPair.getPrivateKey().hex()).append('\n')
        if (!skipListenPort) listenPort?.let { sb.append("listen_port=").append(it).append('\n') }
        sb.append("address=").append(Attribute.join(addresses)).append('\n')
        sb.append("dns=").append(Attribute.join(dnsServerStrings)).append('\n')
        sb.append("mtu=").append(mtu ?: DEFAULT_MTU).append('\n')
        clientId?.let { sb.append("client_id=").append(it).append('\n') }
        jc?.let { sb.append("jc=").append(it).append('\n') }
        jmin?.let { sb.append("jmin=").append(it).append('\n') }
        jmax?.let { sb.append("jmax=").append(it).append('\n') }
        s1?.let { sb.append("s1=").append(it).append('\n') }
        s2?.let { sb.append("s2=").append(it).append('\n') }
        h1?.let { sb.append("h1=").append(it).append('\n') }
        h2?.let { sb.append("h2=").append(it).append('\n') }
        h3?.let { sb.append("h3=").append(it).append('\n') }
        h4?.let { sb.append("h4=").append(it).append('\n') }
        return sb.toString()
    }

    fun getClientId(): String? = clientId
    fun getJc(): Int? = jc
    fun getJmin(): Int? = jmin
    fun getJmax(): Int? = jmax
    fun getS1(): Int? = s1
    fun getS2(): Int? = s2
    fun getH1(): Long? = h1
    fun getH2(): Long? = h2
    fun getH3(): Long? = h3
    fun getH4(): Long? = h4

    class Builder {
        val addresses: MutableSet<InetNetwork> = linkedSetOf()
        val dnsServers: MutableSet<String> = linkedSetOf()
        val dnsSearchDomains: MutableSet<String> = linkedSetOf()
        val excludedApplications: MutableSet<String> = linkedSetOf()
        val includedApplications: MutableSet<String> = linkedSetOf()
        var keyPair: KeyPair? = null
        var listenPort: Int? = null
        var clientId: String? = null
        var jc: Int? = null
        var jmin: Int? = null
        var jmax: Int? = null
        var s1: Int? = null
        var s2: Int? = null
        var h1: Long? = null
        var h2: Long? = null
        var h3: Long? = null
        var h4: Long? = null
        var mtu: Int? = null

        fun addAddress(addr: String): Builder {
            addresses.add(InetNetwork.parse(addr)); return this
        }
        fun addAddresses(addresses: Collection<InetNetwork>?): Builder {
            addresses?.let { this.addresses.addAll(it) }; return this
        }
        fun addDnsServer(dnsServer: String): Builder { dnsServers.add(dnsServer); return this }
        fun addDnsServers(dnsServers: Collection<String>?): Builder {
            dnsServers?.let { this.dnsServers.addAll(it) }; return this
        }
        fun addDnsSearchDomain(dnsSearchDomain: String): Builder {
            dnsSearchDomains.add(dnsSearchDomain); return this
        }
        fun addDnsSearchDomains(domains: Collection<String>?): Builder {
            domains?.let { dnsSearchDomains.addAll(it) }; return this
        }
        fun setClientId(clientId: String?): Builder { this.clientId = clientId; return this }
        fun setJc(jc: Int): Builder { this.jc = jc; return this }
        fun setJmin(jmin: Int): Builder { this.jmin = jmin; return this }
        fun setJmax(jmax: Int): Builder { this.jmax = jmax; return this }
        fun setS1(s1: Int): Builder { this.s1 = s1; return this }
        fun setS2(s2: Int): Builder { this.s2 = s2; return this }
        fun setH1(h1: Long): Builder { this.h1 = h1; return this }
        fun setH2(h2: Long): Builder { this.h2 = h2; return this }
        fun setH3(h3: Long): Builder { this.h3 = h3; return this }
        fun setH4(h4: Long): Builder { this.h4 = h4; return this }

        @Throws(BadConfigException::class)
        fun build(): WgInterface {
            if (keyPair == null)
                throw BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY, Reason.MISSING_ATTRIBUTE, null)
            if (includedApplications.isNotEmpty() && excludedApplications.isNotEmpty())
                throw BadConfigException(Section.INTERFACE, Location.INCLUDED_APPLICATIONS, Reason.INVALID_KEY, null)
            return WgInterface(this)
        }

        fun excludeApplication(application: String): Builder {
            excludedApplications.add(application); return this
        }
        fun excludeApplications(applications: Collection<String>?): Builder {
            applications?.let { excludedApplications.addAll(it) }; return this
        }
        fun includeApplication(application: String): Builder {
            includedApplications.add(application); return this
        }
        fun includeApplications(applications: Collection<String>?): Builder {
            applications?.let { includedApplications.addAll(it) }; return this
        }

        @Throws(BadConfigException::class)
        fun parseAddresses(addresses: CharSequence?): Builder {
            return try {
                for (address in Attribute.split(addresses)) addAddress(address)
                this
            } catch (e: ParseException) {
                throw BadConfigException(Section.INTERFACE, Location.ADDRESS, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parseDnsServers(dnsServers: CharSequence?): Builder {
            return try {
                for (dnsServer in Attribute.split(dnsServers)) {
                    try {
                        addDnsServer(InetAddresses.parse(dnsServer))
                    } catch (e: ParseException) {
                        if (!InetAddresses.isHostname(dnsServer)) throw e
                        addDnsSearchDomain(dnsServer)
                    }
                }
                this
            } catch (e: ParseException) {
                throw BadConfigException(Section.INTERFACE, Location.DNS, e)
            }
        }

        fun parseExcludedApplications(apps: CharSequence?): Builder = excludeApplications(Attribute.split(apps))
        fun parseIncludedApplications(apps: CharSequence?): Builder = includeApplications(Attribute.split(apps))

        @Throws(BadConfigException::class)
        fun parseListenPort(listenPort: String): Builder {
            return try { setListenPort(listenPort.toInt()) }
            catch (e: NumberFormatException) {
                throw BadConfigException(Section.INTERFACE, Location.LISTEN_PORT, listenPort, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parseMtu(mtu: String): Builder {
            return try { setMtu(mtu.toInt()) }
            catch (e: NumberFormatException) {
                throw BadConfigException(Section.INTERFACE, Location.MTU, mtu, e)
            }
        }

        @Throws(BadConfigException::class)
        fun parsePrivateKey(privateKey: String?): Builder {
            return try {
                val keyPair = privateKey?.let { KeyPair(WgCrypto.parsePrivateKey(it)) }
                setKeyPair(keyPair)
            } catch (e: Exception) {
                throw BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY, e)
            }
        }

        fun parseClientId(clientId: String): Builder = setClientId(clientId)

        fun parseJc(jc: String): Builder = try { setJc(jc.toInt()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, jc, e) }
        fun parseJmin(jmin: String): Builder = try { setJmin(jmin.toInt()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, jmin, e) }
        fun parseJmax(jmax: String): Builder = try { setJmax(jmax.toInt()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, jmax, e) }
        fun parseS1(s1: String): Builder = try { setS1(s1.toInt()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, s1, e) }
        fun parseS2(s2: String): Builder = try { setS2(s2.toInt()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, s2, e) }
        fun parseH1(h1: String): Builder = try { setH1(h1.toLong()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, h1, e) }
        fun parseH2(h2: String): Builder = try { setH2(h2.toLong()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, h2, e) }
        fun parseH3(h3: String): Builder = try { setH3(h3.toLong()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, h3, e) }
        fun parseH4(h4: String): Builder = try { setH4(h4.toLong()) }
        catch (e: NumberFormatException) { throw BadConfigException(Section.INTERFACE, Location.AMNEZIA, h4, e) }

        fun setKeyPair(keyPair: KeyPair?): Builder { this.keyPair = keyPair; return this }

        @Throws(BadConfigException::class)
        fun setListenPort(listenPort: Int): Builder {
            if (listenPort < MIN_UDP_PORT || listenPort > MAX_UDP_PORT)
                throw BadConfigException(Section.INTERFACE, Location.LISTEN_PORT, Reason.INVALID_VALUE, listenPort.toString())
            this.listenPort = if (listenPort == 0) null else listenPort
            return this
        }

        @Throws(BadConfigException::class)
        fun setMtu(mtu: Int): Builder {
            if (mtu < -1)
                throw BadConfigException(Section.INTERFACE, Location.MTU, Reason.INVALID_VALUE, mtu.toString())
            this.mtu = if (mtu == 0 || mtu == DEFAULT_MTU) null else mtu
            return this
        }
    }

    companion object {
        private const val MAX_UDP_PORT = 65535
        private const val MIN_UDP_PORT = 0
        private const val DEFAULT_MTU = -1

        @Throws(BadConfigException::class)
        fun parse(lines: Iterable<CharSequence?>): WgInterface {
            val builder = Builder()
            for (line in lines) {
                val attribute = Attribute.parse(line)
                    ?: throw BadConfigException(Section.INTERFACE, Location.TOP_LEVEL, Reason.SYNTAX_ERROR, line)
                when (attribute.key.lowercase()) {
                    "address" -> builder.parseAddresses(attribute.value)
                    "dns" -> builder.parseDnsServers(attribute.value)
                    "excludedapplications" -> builder.parseExcludedApplications(attribute.value)
                    "includedapplications" -> builder.parseIncludedApplications(attribute.value)
                    "listenport" -> builder.parseListenPort(attribute.value)
                    "mtu" -> builder.parseMtu(attribute.value)
                    "privatekey" -> builder.parsePrivateKey(attribute.value)
                    "publickey" -> {}
                    "clientid" -> builder.parseClientId(attribute.value)
                    "jc" -> builder.parseJc(attribute.value)
                    "jmin" -> builder.parseJmin(attribute.value)
                    "jmax" -> builder.parseJmax(attribute.value)
                    "s1" -> builder.parseS1(attribute.value)
                    "s2" -> builder.parseS2(attribute.value)
                    "h1" -> builder.parseH1(attribute.value)
                    "h2" -> builder.parseH2(attribute.value)
                    "h3" -> builder.parseH3(attribute.value)
                    "h4" -> builder.parseH4(attribute.value)
                    else -> throw BadConfigException(
                        Section.INTERFACE, Location.TOP_LEVEL, Reason.UNKNOWN_ATTRIBUTE, attribute.key
                    )
                }
            }
            return builder.build()
        }
    }
}

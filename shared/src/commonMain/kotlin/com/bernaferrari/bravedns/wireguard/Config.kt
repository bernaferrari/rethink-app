/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bernaferrari.bravedns.wireguard

import com.bernaferrari.bravedns.wireguard.BadConfigException.Location
import com.bernaferrari.bravedns.wireguard.BadConfigException.Reason
import com.bernaferrari.bravedns.wireguard.BadConfigException.Section

/** wg-quick config — parse from lines/string (no java.io in common). */
class Config private constructor(builder: Builder) {
    private val id: Int
    private val name: String
    private val wgInterface: WgInterface?
    private val peers: List<Peer>?

    init {
        id = builder.id
        name = builder.name
        wgInterface = requireNotNull(builder.wgInterface) { "An [Interface] section is required" }
        peers = builder.peers.toList()
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is Config) return false
        return wgInterface?.equals(obj.wgInterface) == true && peers?.equals(obj.peers) == true
    }

    fun getInterface(): WgInterface? = wgInterface
    fun getPeers(): List<Peer>? = peers
    fun getId(): Int = id
    fun getName(): String = name

    override fun hashCode(): Int = 31 * (wgInterface?.hashCode() ?: 0) + (peers?.hashCode() ?: 0)

    override fun toString(): String =
        "(Config $wgInterface (${peers?.size ?: 0} peers))"

    fun toWgQuickString(): String {
        val sb = StringBuilder()
        sb.append("[Interface]\n").append(wgInterface?.toWgQuickString() ?: "")
        peers?.forEach { sb.append("\n[Peer]\n").append(it.toWgQuickString()) }
        return sb.toString()
    }

    fun toWgUserspaceString(skipListenPort: Boolean = false, isAmz: Boolean = false, amzDebugAllowedIps: Boolean = false): String {
        val sb = StringBuilder()
        sb.append(wgInterface?.toWgUserspaceString(skipListenPort) ?: "")
        sb.append("replace_peers=true\n")
        peers?.forEach { sb.append(it.toWgUserspaceString(isAmz, amzDebugAllowedIps)) }
        return sb.toString()
    }

    class Builder {
        var id = -1
        var name = ""
        val peers: ArrayList<Peer> = ArrayList()
        var wgInterface: WgInterface? = null

        fun addPeer(peer: Peer): Builder { peers.add(peer); return this }
        fun addPeers(peers: Collection<Peer>?): Builder { peers?.let { this.peers.addAll(it) }; return this }
        fun setId(id: Int): Builder { this.id = id; return this }
        fun setName(name: String): Builder { this.name = name; return this }
        fun build(): Config {
            requireNotNull(wgInterface) { "An [Interface] section is required" }
            return Config(this)
        }
        @Throws(BadConfigException::class)
        fun parseInterface(lines: Iterable<CharSequence?>?): Builder =
            setInterface(WgInterface.parse(lines ?: emptyList()))
        @Throws(BadConfigException::class)
        fun parsePeer(lines: Iterable<CharSequence?>): Builder = addPeer(Peer.parse(lines))
        fun setInterface(i: WgInterface?): Builder { this.wgInterface = i; return this }
    }

    companion object {
        @Throws(BadConfigException::class)
        fun parse(text: String): Config = parse(text.lineSequence().asIterable())

        @Throws(BadConfigException::class)
        fun parse(lines: Iterable<String>): Config {
            val builder = Builder()
            val interfaceLines = ArrayList<String?>()
            val peerLines = ArrayList<String?>()
            var inInterfaceSection = false
            var inPeerSection = false
            var seenInterfaceSection = false

            for (rawLine in lines) {
                var line = rawLine
                val commentIndex = line.indexOf('#')
                if (commentIndex != -1) line = line.substring(0, commentIndex)
                line = line.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("[")) {
                    if (inPeerSection) {
                        builder.parsePeer(peerLines)
                        peerLines.clear()
                    }
                    when {
                        line.equals("[Interface]", ignoreCase = true) -> {
                            inInterfaceSection = true; inPeerSection = false; seenInterfaceSection = true
                        }
                        line.equals("[Peer]", ignoreCase = true) -> {
                            inInterfaceSection = false; inPeerSection = true
                        }
                        else -> throw BadConfigException(
                            Section.CONFIG, Location.TOP_LEVEL, Reason.UNKNOWN_SECTION, line
                        )
                    }
                } else if (inInterfaceSection) {
                    interfaceLines.add(line)
                } else if (inPeerSection) {
                    peerLines.add(line)
                } else {
                    throw BadConfigException(Section.CONFIG, Location.TOP_LEVEL, Reason.UNKNOWN_SECTION, line)
                }
            }
            if (inPeerSection) builder.parsePeer(peerLines)
            if (!seenInterfaceSection)
                throw BadConfigException(Section.CONFIG, Location.TOP_LEVEL, Reason.MISSING_SECTION, null)
            builder.parseInterface(interfaceLines)
            return builder.build()
        }
    }
}

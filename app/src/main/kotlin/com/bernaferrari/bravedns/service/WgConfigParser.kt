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
package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.wireguard.BadConfigException
import com.bernaferrari.bravedns.wireguard.Config
import com.bernaferrari.bravedns.wireguard.Peer
import com.bernaferrari.bravedns.wireguard.WgInterface

/**
 * Pure WireGuard config text parse + identity rebuild.
 *
 * [Config.parse] already owns the wg-quick grammar; this helper only attaches
 * manager identity (id/name) and rebuilds after interface/peer edits.
 */
internal object WgConfigParser {

    @Throws(BadConfigException::class)
    fun parseText(text: String): Config = Config.parse(text)

    fun withIdentity(parsed: Config, id: Int, name: String): Config {
        return rebuild(id, name, parsed.getInterface(), parsed.getPeers())
    }

    fun rebuild(
        id: Int,
        name: String,
        iface: WgInterface?,
        peers: Collection<Peer>?,
    ): Config {
        return Config.Builder()
            .setId(id)
            .setName(name)
            .setInterface(iface)
            .addPeers(peers)
            .build()
    }
}

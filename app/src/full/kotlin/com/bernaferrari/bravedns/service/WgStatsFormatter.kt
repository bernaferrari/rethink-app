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

import android.text.format.DateUtils
import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE

internal object WgStatsFormatter {

    suspend fun formatActive(
        mappings: Collection<WgConfigFilesImmutable>,
        statsFor: suspend (proxyId: String) -> WireguardManager.WgStats?,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        mappings.filter { it.isActive }.forEach {
            val stats = statsFor(ID_WG_BASE + it.id)
            val routerStats = stats?.routerStats
            sb.append("   id: ${it.id}, name: ${it.name}\n")
            sb.append("   addr: ${routerStats?.addrs}").append("\n")
            sb.append("   mtu: ${stats?.mtu}\n")
            sb.append("   status: ${routerStats?.status}\n")
            sb.append("   status-reason: ${routerStats?.statusReason}\n")
            sb.append("   ip4: ${stats?.ip4}\n")
            sb.append("   ip6: ${stats?.ip6}\n")
            sb.append("   rx: ${routerStats?.rx}\n")
            sb.append("   tx: ${routerStats?.tx}\n")
            sb.append("   lastRx: ${relativeTimeSpan(routerStats?.lastRx, nowMillis)}\n")
            sb.append("   lastTx: ${relativeTimeSpan(routerStats?.lastTx, nowMillis)}\n")
            sb.append("   lastGoodRx: ${relativeTimeSpan(routerStats?.lastGoodRx, nowMillis)}\n")
            sb.append("   lastGoodTx: ${relativeTimeSpan(routerStats?.lastGoodTx, nowMillis)}\n")
            sb.append("   lastRxErr: ${routerStats?.lastRxErr}\n")
            sb.append("   lastTxErr: ${routerStats?.lastTxErr}\n")
            sb.append("   lastOk: ${relativeTimeSpan(routerStats?.lastOK, nowMillis)}\n")
            sb.append("   lastOpen: ${relativeTimeSpan(routerStats?.lastOpen, nowMillis)}\n")
            sb.append("   hdl: ${routerStats?.hdl}\n")
            sb.append("   since: ${relativeTimeSpan(routerStats?.since, nowMillis)}\n")
            sb.append("   errRx: ${routerStats?.errRx}\n")
            sb.append("   errTx: ${routerStats?.errTx}\n")
            sb.append("   extra: ${routerStats?.extra}\n")
            sb.append("   clientV4: ${stats?.clientV4?.toString()}\n")
            sb.append("   clientV6: ${stats?.clientV6?.toString()}\n\n")
        }
        if (sb.isEmpty()) {
            sb.append("   N/A\n\n")
        }
        return sb.toString()
    }

    fun relativeTimeSpan(t: Long?, now: Long): CharSequence? {
        if (t == null || t <= 0L) return "0"
        return DateUtils.getRelativeTimeSpanString(
            t,
            now,
            DateUtils.SECOND_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }
}

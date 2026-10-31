/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.RethinkLog
import com.bernaferrari.bravedns.database.RethinkLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

/** App detail's Recent activity: one app's connections in the last 24 hours. */
@KoinViewModel
class AppConnectionsViewModel(
    @Provided private val nwlogDao: ConnectionTrackerDAO,
    @Provided private val rinrDao: RethinkLogDao,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    /** The 1h / 24h / 7d windows the statistics and log screens offer. */
    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    /** This app's connections in the last 24 hours, blocked first (App detail's Recent activity). */
    fun recentConnections(uid: Int, limit: Int = RECENT_ACTIVITY_SCAN_LIMIT): Flow<List<ConnectionTracker>> =
        nwlogDao.observeRecentConnectionsForUid(uid, nowMillis() - RECENT_ACTIVITY_WINDOW_MS, limit)

    /**
     * Keepout's own connections in the last 24 hours, blocked first. The tunnel logs them apart
     * from every other app's (RethinkLog), so the connection log never holds Keepout's uid.
     */
    fun recentKeepoutConnections(limit: Int = RECENT_ACTIVITY_SCAN_LIMIT): Flow<List<ConnectionTracker>> =
        rinrDao.observeRecentLogs(nowMillis() - RECENT_ACTIVITY_WINDOW_MS, limit)
            .map { logs -> logs.map { it.toRecentConnection() } }

    private companion object {
        const val RECENT_ACTIVITY_WINDOW_MS = 24 * 60 * 60 * 1000L
        const val RECENT_ACTIVITY_SCAN_LIMIT = 200
    }
}

/** The fields Recent activity reads; RethinkLog shares ConnectionTracker's columns. */
private fun RethinkLog.toRecentConnection(): ConnectionTracker = ConnectionTracker().also {
    it.appName = appName
    it.uid = uid
    it.usrId = usrId
    it.ipAddress = ipAddress
    it.port = port
    it.protocol = protocol
    it.isBlocked = isBlocked
    it.blockedByRule = blockedByRule
    it.blocklists = blocklists
    it.proxyDetails = proxyDetails
    it.flag = flag
    it.dnsQuery = dnsQuery
    it.timeStamp = timeStamp
    it.connId = connId
    it.downloadBytes = downloadBytes
    it.uploadBytes = uploadBytes
    it.duration = duration
    it.synack = synack
    it.rpid = rpid
    it.message = message
    it.connType = connType
}

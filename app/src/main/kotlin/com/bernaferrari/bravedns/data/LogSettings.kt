/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.data

import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import kotlinx.coroutines.flow.Flow

interface LogSettingsApi {
    val networkLogsCount: Flow<Long>
    val dnsLogsCount: Flow<Long>
    suspend fun getLeastLoggedNetworkLogs(): Long
}

class LogSettings(
    private val networkLogs: ConnectionTrackerRepository,
    private val dnsLogs: DnsLogRepository,
    private val eventLogger: EventLogger,
    private val firewallSettings: FirewallSettings,
) : LogSettingsApi {
    override val networkLogsCount: Flow<Long> = networkLogs.logsCount()

    override val dnsLogsCount: Flow<Long> = dnsLogs.logsCount()

    override suspend fun getLeastLoggedNetworkLogs(): Long {
        return if (firewallSettings.getBraveMode().isDnsMode()) {
            dnsLogs.getLeastLoggedTime()
        } else {
            networkLogs.getLeastLoggedTime()
        }
    }

    suspend fun logEvent(type: EventType, msg: String, details: String) {
        eventLogger.log(type, Severity.LOW, msg, EventSource.UI, true, details)
    }
}

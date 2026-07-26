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

import android.content.Context
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsCryptEndpointRepository
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpointRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.database.DnsProxyEndpointRepository
import com.bernaferrari.bravedns.database.DoHEndpointRepository
import com.bernaferrari.bravedns.database.DoTEndpointRepository
import com.bernaferrari.bravedns.database.ODoHEndpointRepository
import com.bernaferrari.bravedns.database.ProxyEndpointRepository
import com.bernaferrari.bravedns.database.RethinkDnsEndpointRepository
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Module
class DataModule {
    @Single
    fun appConfig(
        @Provided context: Context,
        rethinkDnsEndpointRepository: RethinkDnsEndpointRepository,
        dnsProxyEndpointRepository: DnsProxyEndpointRepository,
        doHEndpointRepository: DoHEndpointRepository,
        dnsCryptEndpointRepository: DnsCryptEndpointRepository,
        dnsCryptRelayEndpointRepository: DnsCryptRelayEndpointRepository,
        doTEndpointRepository: DoTEndpointRepository,
        oDoHEndpointRepository: ODoHEndpointRepository,
        proxyEndpointRepository: ProxyEndpointRepository,
        persistentState: PersistentState,
        networkLogs: ConnectionTrackerRepository,
        dnsLogs: DnsLogRepository,
        eventLogger: EventLogger,
    ) = AppConfig(
        context,
        rethinkDnsEndpointRepository,
        dnsProxyEndpointRepository,
        doHEndpointRepository,
        dnsCryptEndpointRepository,
        dnsCryptRelayEndpointRepository,
        doTEndpointRepository,
        oDoHEndpointRepository,
        proxyEndpointRepository,
        persistentState,
        networkLogs,
        dnsLogs,
        eventLogger,
    )
}

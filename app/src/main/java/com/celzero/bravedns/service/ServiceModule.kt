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
package com.celzero.bravedns.service

import android.content.Context
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.ConsoleLogRepository
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.iab.SecureIdentityStore
import com.celzero.bravedns.database.EventDao
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Module
class ServiceModule {
    @Single fun persistentState(@Provided context: Context) = PersistentState(context)
    @Single fun eventLogger(eventDao: EventDao) = EventLogger(eventDao)

    @Single
    fun netLogTracker(
        @Provided context: Context,
        connectionTrackerRepository: ConnectionTrackerRepository,
        rethinkLogRepository: RethinkLogRepository,
        dnsLogRepository: DnsLogRepository,
        consoleLogRepository: ConsoleLogRepository,
        persistentState: PersistentState,
    ) = NetLogTracker(
        context,
        connectionTrackerRepository,
        rethinkLogRepository,
        dnsLogRepository,
        consoleLogRepository,
        persistentState,
    )

    // SecureIdentityStore stays one shared singleton for every flavor's billing backend.
    @Single fun secureIdentityStore(@Provided context: Context) = SecureIdentityStore(context)
}

/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.database.DnsLogDAO
import kotlinx.coroutines.flow.MutableStateFlow

class AlertsViewModel(
    private val connectionTrackerDao: ConnectionTrackerDAO,
    private val dnsLogDao: DnsLogDAO
) : ViewModel() {
    private var ipLogList: MutableStateFlow<String> = MutableStateFlow("")
    private var domainLogList: MutableStateFlow<String> = MutableStateFlow("")
    private var appLogList: MutableStateFlow<String> = MutableStateFlow("")
    private var fromTime: MutableStateFlow<Long> = MutableStateFlow(0L)
    private var toTime: MutableStateFlow<Long> = MutableStateFlow(0L)

    init {
        fromTime.value = System.currentTimeMillis() - 1 * 60 * 60 * 1000L
        toTime.value = System.currentTimeMillis()
    }
}
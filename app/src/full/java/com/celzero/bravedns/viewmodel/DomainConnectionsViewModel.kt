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
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.database.StatsSummaryDao
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

class DomainConnectionsViewModel(private val statsDao: StatsSummaryDao) : ViewModel() {
    private var domains: MutableStateFlow<String> = MutableStateFlow("")
    private var asn: MutableStateFlow<String> = MutableStateFlow("")
    private var flag: MutableStateFlow<String> = MutableStateFlow("")
    private var ip: MutableStateFlow<String> = MutableStateFlow("")
    private var timeCategory: TimeCategory = TimeCategory.ONE_HOUR
    private var startTime: MutableStateFlow<Long> = MutableStateFlow(0L)
    private var isBlocked: Boolean = false

    companion object {
        private const val ONE_HOUR_MILLIS = 1 * 60 * 60 * 1000L
        private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }

    enum class TimeCategory(val value: Int) {
        ONE_HOUR(0),
        TWENTY_FOUR_HOUR(1),
        SEVEN_DAYS(2);

        companion object {
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    init {
        // set from and to time to current and 1 hr before
        startTime.value = System.currentTimeMillis() - ONE_HOUR_MILLIS
    }

    fun setDomain(domain: String, isBlocked: Boolean) {
        this.isBlocked = isBlocked
        domains.value = domain
    }

    fun setFlag(flag: String) {
        this.flag.value = flag
    }

    fun setAsn(asn: String, isBlocked: Boolean) {
        this.isBlocked = isBlocked
        this.asn.value = asn
    }

    fun setIp(ip: String, isBlocked: Boolean) {
        this.isBlocked = isBlocked
        this.ip.value = ip
    }

    fun timeCategoryChanged(tc: TimeCategory) {
        timeCategory = tc
        when (tc) {
            TimeCategory.ONE_HOUR -> {
                startTime.value = System.currentTimeMillis() - ONE_HOUR_MILLIS
            }
            TimeCategory.TWENTY_FOUR_HOUR -> {
                startTime.value = System.currentTimeMillis() - ONE_DAY_MILLIS
            }
            TimeCategory.SEVEN_DAYS -> {
                startTime.value = System.currentTimeMillis() - ONE_WEEK_MILLIS
            }
        }
        asn.value = ""
        flag.value = ""
        domains.value = ""
        ip.value = ""
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val domainConnectionList: Flow<PagingData<AppConnection>> =
        combine(domains, startTime) { input, start -> input to start }
            .flatMapLatest { (input, start) ->
                Pager(PagingConfig(pageSize = Constants.PAGING_PAGE_SIZE)) {
                    statsDao.getDomainDetails(input, start, isBlocked)
                }.flow.cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flagConnectionList: Flow<PagingData<AppConnection>> =
        combine(flag, startTime) { input, start -> input to start }
            .flatMapLatest { (input, start) ->
                Pager(PagingConfig(pageSize = Constants.PAGING_PAGE_SIZE)) {
                    statsDao.getFlagDetails(input, start)
                }.flow.cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val asnConnectionList: Flow<PagingData<AppConnection>> =
        combine(asn, startTime) { input, start -> input to start }
            .flatMapLatest { (input, start) ->
                Pager(PagingConfig(pageSize = Constants.PAGING_PAGE_SIZE)) {
                    if (isBlocked) {
                        statsDao.getAsnBlockedDetails(input, start)
                    } else {
                        statsDao.getAsnDetails(input, start)
                    }
                }.flow.cachedIn(viewModelScope)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ipConnectionList: Flow<PagingData<AppConnection>> =
        combine(ip, startTime) { input, start -> input to start }
            .flatMapLatest { (input, start) ->
                Pager(PagingConfig(pageSize = Constants.PAGING_PAGE_SIZE)) {
                    statsDao.getIpDetails(input, start, isBlocked)
                }.flow.cachedIn(viewModelScope)
            }
}
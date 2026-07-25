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
package com.celzero.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.DnsLogDAO
import com.celzero.bravedns.database.RethinkLog
import com.celzero.bravedns.database.RethinkLogDao
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import com.celzero.bravedns.util.ResourceRecordTypes.Companion.getHandledTypes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

private val logsPagingConfig =
    PagingConfig(
        enablePlaceholders = true,
        prefetchDistance = 3,
        initialLoadSize = PAGING_PAGE_SIZE * 2,
        maxSize = PAGING_PAGE_SIZE * 3,
        pageSize = PAGING_PAGE_SIZE * 2,
        jumpThreshold = 5
    )

@KoinViewModel
class DnsLogViewModel(
    @Provided private val dao: DnsLogDAO,
) : ViewModel() {
    private val query = MutableStateFlow(DnsLogQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dnsLogsList: Flow<PagingData<DnsLog>> = query.flatMapLatest(::fetchDnsLogs)

    fun setFilter(searchString: String, type: DnsLogFilter) {
        query.value = query.value.copy(search = searchString, filter = type)
    }

    fun setIsWireGuardLogs(isWgLogs: Boolean, wgId: String) {
        query.value = query.value.copy(isWireGuardLogs = isWgLogs, wgDnsId = "%$wgId%")
    }

    private fun fetchDnsLogs(state: DnsLogQuery): Flow<PagingData<DnsLog>> =
        Pager(logsPagingConfig) {
                when {
                    state.isWireGuardLogs -> dao.getDnsLogsForWireGuard(state.wgDnsId)
                    state.filter == DnsLogFilter.ALL && state.search.isBlank() -> dao.getAllDnsLogs()
                    state.filter == DnsLogFilter.ALL -> dao.getDnsLogsByName("%${state.search}%")
                    state.filter == DnsLogFilter.ALLOWED && state.search.isBlank() ->
                        dao.getAllowedDnsLogs()
                    state.filter == DnsLogFilter.ALLOWED ->
                        dao.getAllowedDnsLogsByName("%${state.search}%")
                    state.filter == DnsLogFilter.BLOCKED && state.search.isBlank() ->
                        dao.getBlockedDnsLogs()
                    state.filter == DnsLogFilter.BLOCKED ->
                        dao.getBlockedDnsLogsByName("%${state.search}%")
                    state.filter == DnsLogFilter.MAYBE_BLOCKED && state.search.isBlank() ->
                        dao.getMaybeBlockedDnsLogs()
                    state.filter == DnsLogFilter.MAYBE_BLOCKED ->
                        dao.getMaybeBlockedDnsLogsByName("%${state.search}%")
                    state.search.isBlank() -> dao.getUnknownRecordDnsLogs(getHandledTypes())
                    else -> dao.getUnknownRecordDnsLogsByName("%${state.search}%", getHandledTypes())
                }
            }
            .flow
            .cachedIn(viewModelScope)

    private data class DnsLogQuery(
        val search: String = "",
        val filter: DnsLogFilter = DnsLogFilter.ALL,
        val isWireGuardLogs: Boolean = false,
        val wgDnsId: String = ""
    )

    enum class DnsLogFilter(val id: Int) {
        ALL(0),
        ALLOWED(1),
        BLOCKED(2),
        MAYBE_BLOCKED(3),
        UNKNOWN_RECORDS(4)
    }
}

@KoinViewModel
class ConnectionTrackerViewModel(
    @Provided private val dao: ConnectionTrackerDAO,
) : ViewModel() {
    companion object {
        const val PROTOCOL_FILTER_PREFIX = "P:"
    }

    private val query = MutableStateFlow(ConnectionQuery())
    private var debounceJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionTrackerList: Flow<PagingData<ConnectionTracker>> =
        query.flatMapLatest(::fetchNetworkLogs)

    fun setFilter(searchString: String, filter: Set<String>, type: TopLevelFilter) {
        debounceJob?.cancel()
        debounceJob =
            viewModelScope.launch {
                delay(300)
                query.value = ConnectionQuery(searchString, filter.toSet(), type)
            }
    }

    private fun fetchNetworkLogs(state: ConnectionQuery): Flow<PagingData<ConnectionTracker>> {
        val normalizedSearch = state.search.trim()
        val protocolPrefix = PROTOCOL_FILTER_PREFIX.lowercase()
        if (normalizedSearch.lowercase().startsWith(protocolPrefix)) {
            val protocol = normalizedSearch.substringAfter(protocolPrefix)
            return Pager(logsPagingConfig) {
                    if (state.rules.isEmpty()) dao.getProtocolFilteredConnections(protocol)
                    else dao.getProtocolFilteredConnections(protocol, state.rules)
                }
                .flow
                .cachedIn(viewModelScope)
        }

        return Pager(logsPagingConfig) {
                val search = normalizedSearch.takeIf(String::isNotBlank)?.let { "%$it%" }
                when (state.filter) {
                    TopLevelFilter.ALL ->
                        if (state.rules.isEmpty()) {
                            if (search == null) dao.getConnectionTrackerByName()
                            else dao.getConnectionTrackerByName(search)
                        } else if (search == null) {
                            dao.getConnectionsFiltered(state.rules)
                        } else {
                            dao.getConnectionsFiltered(search, state.rules)
                        }
                    TopLevelFilter.ALLOWED ->
                        if (state.rules.isEmpty()) {
                            if (search == null) dao.getAllowedConnections()
                            else dao.getAllowedConnections(search)
                        } else if (search == null) {
                            dao.getAllowedConnectionsFiltered(state.rules)
                        } else {
                            dao.getAllowedConnectionsFiltered(search, state.rules)
                        }
                    TopLevelFilter.BLOCKED ->
                        if (state.rules.isEmpty()) {
                            if (search == null) dao.getBlockedConnections()
                            else dao.getBlockedConnections(search)
                        } else if (search == null) {
                            dao.getBlockedConnectionsFiltered(state.rules)
                        } else {
                            dao.getBlockedConnectionsFiltered(search, state.rules)
                        }
                }
            }
            .flow
            .cachedIn(viewModelScope)
    }

    private data class ConnectionQuery(
        val search: String = "",
        val rules: Set<String> = emptySet(),
        val filter: TopLevelFilter = TopLevelFilter.ALL
    )

    enum class TopLevelFilter(val id: Int) {
        ALL(0),
        ALLOWED(1),
        BLOCKED(2)
    }
}

@KoinViewModel
class RethinkLogViewModel(
    @Provided private val dao: RethinkLogDao,
) : ViewModel() {
    private val filter = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val rlogList: Flow<PagingData<RethinkLog>> =
        filter.flatMapLatest { search ->
            Pager(logsPagingConfig) {
                    if (search.isBlank()) dao.getRethinkLogByName()
                    else dao.getRethinkLogByName("%$search%")
                }
                .flow
                .cachedIn(viewModelScope)
        }

    fun setFilter(searchString: String) {
        filter.value = searchString
    }
}

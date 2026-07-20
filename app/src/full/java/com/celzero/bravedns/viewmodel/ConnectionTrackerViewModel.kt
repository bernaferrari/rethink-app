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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.ConnectionTracker
import com.celzero.bravedns.database.ConnectionTrackerDAO
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class ConnectionTrackerViewModel(private val connectionTrackerDAO: ConnectionTrackerDAO) :
    ViewModel() {

    companion object {
        const val PROTOCOL_FILTER_PREFIX = "P:"
    }

    private val _filterString = MutableStateFlow("")
    private var filterRules: MutableSet<String> = mutableSetOf()
    private var filterType: TopLevelFilter = TopLevelFilter.ALL

    enum class TopLevelFilter(val id: Int) {
        ALL(0),
        ALLOWED(1),
        BLOCKED(2)
    }

    private val pagingConfig: PagingConfig

    init {
        pagingConfig =
            PagingConfig(
                enablePlaceholders = true,
                prefetchDistance = 3,
                initialLoadSize = PAGING_PAGE_SIZE * 2,
                maxSize = PAGING_PAGE_SIZE * 3,
                pageSize = PAGING_PAGE_SIZE * 2,
                jumpThreshold = 5
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionTrackerList: Flow<PagingData<ConnectionTracker>> =
        _filterString.flatMapLatest { input -> fetchNetworkLogs(input) }

    private fun setFilterWithDebounce(searchString: String) {
        viewModelScope.launch {
            debounceFilter(searchString)
        }
    }

    private var debounceJob: Job? = null
    private fun debounceFilter(searchString: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300) // 300ms debounce delay
            _filterString.value = searchString
        }
    }

    fun setFilter(searchString: String, filter: Set<String>, type: TopLevelFilter) {
        filterRules.clear()

        filterRules.addAll(filter)
        filterType = type

        setFilterWithDebounce(searchString)
    }

    private fun fetchNetworkLogs(input: String): Flow<PagingData<ConnectionTracker>> {
        // spl case: treat input with P:UDP, P:TCP, P:ICMP as protocol filter
        val protocolPrefix = PROTOCOL_FILTER_PREFIX.lowercase()
        val s = input.trim().lowercase()
        if (s.startsWith(protocolPrefix)) {
            val protocol = s.substringAfter(protocolPrefix)
            return if (filterRules.isNotEmpty()) {
                Pager(pagingConfig) {
                        connectionTrackerDAO.getProtocolFilteredConnections(protocol, filterRules)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            } else {
                Pager(pagingConfig) {
                        connectionTrackerDAO.getProtocolFilteredConnections(protocol)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }
        }

        return when (filterType) {
            TopLevelFilter.ALL -> {
                getAllNetworkLogs(input)
            }
            TopLevelFilter.ALLOWED -> {
                getAllowedNetworkLogs(input)
            }
            TopLevelFilter.BLOCKED -> {
                getBlockedNetworkLogs(input)
            }
        }
    }

    private fun getBlockedNetworkLogs(input: String): Flow<PagingData<ConnectionTracker>> {
        return if (filterRules.isNotEmpty()) {
            Pager(pagingConfig) {
                    if (input.isBlank())
                        connectionTrackerDAO.getBlockedConnectionsFiltered(filterRules)
                    else connectionTrackerDAO.getBlockedConnectionsFiltered("%$input%", filterRules)
                }
                .flow
                .cachedIn(viewModelScope)
        } else {
            Pager(pagingConfig) {
                    if (input.isBlank()) connectionTrackerDAO.getBlockedConnections()
                    else connectionTrackerDAO.getBlockedConnections("%$input%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
    }

    private fun getAllowedNetworkLogs(input: String): Flow<PagingData<ConnectionTracker>> {
        return if (filterRules.isNotEmpty()) {
            Pager(pagingConfig) {
                    if (input.isBlank())
                        connectionTrackerDAO.getAllowedConnectionsFiltered(filterRules)
                    else connectionTrackerDAO.getAllowedConnectionsFiltered("%$input%", filterRules)
                }
                .flow
                .cachedIn(viewModelScope)
        } else {
            Pager(pagingConfig) {
                    if (input.isBlank()) connectionTrackerDAO.getAllowedConnections()
                    else connectionTrackerDAO.getAllowedConnections("%$input%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
    }

    private fun getAllNetworkLogs(input: String): Flow<PagingData<ConnectionTracker>> {
        return if (filterRules.isNotEmpty()) {
            Pager(pagingConfig) {
                    if (input.isBlank()) connectionTrackerDAO.getConnectionsFiltered(filterRules)
                    else connectionTrackerDAO.getConnectionsFiltered("%$input%", filterRules)
                }
                .flow
                .cachedIn(viewModelScope)
        } else {
            Pager(pagingConfig) {
                    if (input.isBlank()) connectionTrackerDAO.getConnectionTrackerByName()
                    else connectionTrackerDAO.getConnectionTrackerByName("%$input%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
    }
}
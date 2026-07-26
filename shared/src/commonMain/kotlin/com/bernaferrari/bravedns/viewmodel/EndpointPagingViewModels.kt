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
package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.database.DnsCryptEndpoint
import com.bernaferrari.bravedns.database.DnsCryptEndpointDAO
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpointDAO
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.DnsProxyEndpointDAO
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.bernaferrari.bravedns.database.DoHEndpointDAO
import com.bernaferrari.bravedns.database.DoTEndpoint
import com.bernaferrari.bravedns.database.DoTEndpointDAO
import com.bernaferrari.bravedns.database.ODoHEndpoint
import com.bernaferrari.bravedns.database.ODoHEndpointDAO
import com.bernaferrari.bravedns.database.RethinkDnsEndpoint
import com.bernaferrari.bravedns.database.RethinkDnsEndpointDao
import com.bernaferrari.bravedns.database.WgConfigFiles
import com.bernaferrari.bravedns.database.WgConfigFilesDAO
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@KoinViewModel
class DnsCryptEndpointViewModel(
    @Provided private val dao: DnsCryptEndpointDAO,
) : ViewModel() {
    private val filter = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val dnsCryptEndpointList: Flow<PagingData<DnsCryptEndpoint>> =
        filter.flatMapLatest { query ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    if (query.isBlank()) dao.dnsCryptEndpointsPagingSource()
                    else dao.dnsCryptEndpointsPagingSource("%$query%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
}

@KoinViewModel
class DnsCryptRelayEndpointViewModel(
    @Provided private val dao: DnsCryptRelayEndpointDAO,
) : ViewModel() {
    private val filter = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val dnsCryptRelayEndpointList: Flow<PagingData<DnsCryptRelayEndpoint>> =
        filter.flatMapLatest { query ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    if (query.isBlank()) dao.dnsCryptRelayEndpointsPagingSource()
                    else dao.dnsCryptRelayEndpointsPagingSource("%$query%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
}

@KoinViewModel
class DnsProxyEndpointViewModel(
    @Provided private val dao: DnsProxyEndpointDAO,
) : ViewModel() {
    private val filter = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val dnsProxyEndpointList: Flow<PagingData<DnsProxyEndpoint>> =
        filter.flatMapLatest { query ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    if (query.isBlank()) dao.dnsProxyEndpointsPagingSource()
                    else dao.dnsProxyEndpointsPagingSource("%$query%")
                }
                .flow
                .cachedIn(viewModelScope)
        }
}

@KoinViewModel
class DoHEndpointViewModel(
    @Provided private val dao: DoHEndpointDAO,
) : ViewModel() {
    val dohEndpointList: Flow<PagingData<DoHEndpoint>> =
        Pager(PagingConfig(PAGING_PAGE_SIZE)) { dao.doHEndpointsPagingSource() }
            .flow
            .cachedIn(viewModelScope)
}

@KoinViewModel
class DoTEndpointViewModel(
    @Provided private val dao: DoTEndpointDAO,
) : ViewModel() {
    val dohEndpointList: Flow<PagingData<DoTEndpoint>> =
        Pager(PagingConfig(PAGING_PAGE_SIZE)) { dao.doTEndpointsPagingSource() }
            .flow
            .cachedIn(viewModelScope)
}

@KoinViewModel
class ODoHEndpointViewModel(
    @Provided private val dao: ODoHEndpointDAO,
) : ViewModel() {
    val dohEndpointList: Flow<PagingData<ODoHEndpoint>> =
        Pager(PagingConfig(PAGING_PAGE_SIZE)) { dao.oDoHEndpointsPagingSource() }
            .flow
            .cachedIn(viewModelScope)
}

@KoinViewModel
class RethinkEndpointViewModel(
    @Provided private val dao: RethinkDnsEndpointDao,
) : ViewModel() {
    private val query = MutableStateFlow(EndpointQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rethinkEndpointList: Flow<PagingData<RethinkDnsEndpoint>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    when {
                        state.uid != Constants.MISSING_UID -> dao.getAllRethinkEndpoints()
                        state.search.isBlank() -> dao.getRethinkEndpoints()
                        else -> dao.getRethinkEndpointsByName("%${state.search}%")
                    }
                }
                .flow
                .cachedIn(viewModelScope)
        }

    fun setFilter(uid: Int, searchText: String = "") {
        query.value = EndpointQuery(uid = uid, search = searchText)
    }

    private data class EndpointQuery(val uid: Int = Constants.MISSING_UID, val search: String = "")
}

@KoinViewModel
class WgConfigViewModel(
    @Provided private val dao: WgConfigFilesDAO,
) : ViewModel() {
    private val refresh = MutableStateFlow(Unit)

    @OptIn(ExperimentalCoroutinesApi::class)
    val interfaces: Flow<PagingData<WgConfigFiles>> =
        refresh.flatMapLatest {
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { dao.wgConfigsPagingSource() }
                .flow
                .cachedIn(viewModelScope)
        }

    fun insert(wgConfigFiles: WgConfigFiles) {
        viewModelScope.launch { dao.insert(wgConfigFiles) }
    }

    fun configCount(): Flow<Int> = dao.getConfigCount()
}

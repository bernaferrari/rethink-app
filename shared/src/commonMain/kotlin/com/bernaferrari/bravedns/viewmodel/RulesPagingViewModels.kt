/*
 * Copyright 2021 RethinkDNS and its authors
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
import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.database.CustomDomainDAO
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.database.CustomIpDao
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@KoinViewModel
class AppCustomIpViewModel(
    @Provided private val dao: CustomIpDao,
) : ViewModel() {
    private val query = MutableStateFlow(AppIpQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customIpDetails: Flow<PagingData<CustomIp>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                    if (state.search.isBlank()) dao.getAppWiseCustomIp(state.uid)
                    else dao.getAppWiseCustomIp("%${state.search}%", state.uid)
                }
                .flow
                .cachedIn(viewModelScope)
        }

    fun appWiseIpRulesCount(uid: Int): Flow<Int> = dao.getAppWiseIpRulesCount(uid)

    fun setFilter(filter: String) {
        query.value = query.value.copy(search = filter)
    }

    fun setUid(uid: Int) {
        query.value = query.value.copy(uid = uid)
    }

    private data class AppIpQuery(val search: String = "", val uid: Int = UID_EVERYBODY)
}

@KoinViewModel
class CustomDomainViewModel(
    @Provided private val dao: CustomDomainDAO,
) : ViewModel() {
    private val query = MutableStateFlow(DomainQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customDomains: Flow<PagingData<CustomDomain>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    dao.domainsPagingSource(state.uid, "%${state.search}%")
                }
                .flow
                .cachedIn(viewModelScope)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allDomainRules: Flow<PagingData<CustomDomain>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) { dao.getAllDomainRules("%${state.search}%") }
                .flow
                .cachedIn(viewModelScope)
        }

    fun setFilter(filter: String) {
        query.value = query.value.copy(search = filter)
    }

    fun domainRulesCount(uid: Int): Flow<Int> = dao.getAppWiseDomainRulesCount(uid)

    fun allDomainRulesCount(): Flow<Int> = dao.getAllDomainRulesCount()

    fun setUid(uid: Int) {
        query.value = query.value.copy(uid = uid)
    }

    private data class DomainQuery(val search: String = "", val uid: Int = Constants.UID_EVERYBODY)
}

@KoinViewModel
class CustomIpViewModel(
    @Provided private val dao: CustomIpDao,
) : ViewModel() {
    private val query = MutableStateFlow(CustomIpQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customIpDetails: Flow<PagingData<CustomIp>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    when {
                        state.uid != UID_EVERYBODY && state.search.isBlank() ->
                            dao.getAppWiseCustomIp(state.uid)
                        state.uid != UID_EVERYBODY -> dao.getAppWiseCustomIp("%${state.search}%", state.uid)
                        state.search.isBlank() -> dao.univBlockedConnectionsPagingSource()
                        else -> dao.getUnivBlockedConnectionsByIP("%${state.search}%")
                    }
                }
                .flow
                .cachedIn(viewModelScope)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allIpRules: Flow<PagingData<CustomIp>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) { dao.getAllCustomIpRules("%${state.search}%") }
                .flow
                .cachedIn(viewModelScope)
        }

    fun ipRulesCount(uid: Int): Flow<Int> = dao.getAppWiseIpRulesCount(uid)

    fun allIpRulesCount(): Flow<Int> = dao.getIpRulesCountInt()

    fun setFilter(filter: String) {
        query.value = query.value.copy(search = filter)
    }

    fun setUid(uid: Int) {
        query.value = query.value.copy(uid = uid)
    }

    private data class CustomIpQuery(val search: String = "", val uid: Int = UID_EVERYBODY)
}

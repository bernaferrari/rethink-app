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
package com.celzero.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomDomainDAO
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class CustomDomainViewModel(private val customDomainDAO: CustomDomainDAO) : ViewModel() {

    private var filteredList: MutableStateFlow<String> = MutableStateFlow("")
    private var uid: Int = Constants.UID_EVERYBODY

    @OptIn(ExperimentalCoroutinesApi::class)
    val customDomains: Flow<PagingData<CustomDomain>> =
        filteredList.flatMapLatest { input ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    customDomainDAO.domainsPagingSource(uid, "%$input%")
                }
                .flow
                .cachedIn(viewModelScope)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allDomainRules: Flow<PagingData<CustomDomain>> =
        filteredList.flatMapLatest { input ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    customDomainDAO.getAllDomainRules("%$input%")
                }
                .flow
                .cachedIn(viewModelScope)
        }

    fun setFilter(filter: String) {
        filteredList.value = filter
    }

    fun domainRulesCount(uid: Int): Flow<Int> {
        return customDomainDAO.getAppWiseDomainRulesCount(uid)
    }

    fun allDomainRulesCount(): Flow<Int> {
        return customDomainDAO.getAllDomainRulesCount()
    }

    fun setUid(i: Int) {
        this.uid = i
    }
}
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
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.RethinkLog
import com.celzero.bravedns.database.RethinkLogDao
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class RethinkLogViewModel(private val rlogDao: RethinkLogDao) : ViewModel() {

    private var filterString: MutableStateFlow<String> = MutableStateFlow("")
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
    val rlogList: Flow<PagingData<RethinkLog>> =
        filterString.flatMapLatest { input -> fetchNetworkLogs(input) }

    fun setFilter(searchString: String) {
        if (searchString.isNotBlank()) filterString.value = searchString
        else filterString.value = ""
    }

    private fun fetchNetworkLogs(input: String): Flow<PagingData<RethinkLog>> {
        return getAllNetworkLogs(input)
    }

    private fun getAllNetworkLogs(input: String): Flow<PagingData<RethinkLog>> {
        return Pager(pagingConfig) {
                if (input.isBlank()) rlogDao.getRethinkLogByName()
                else rlogDao.getRethinkLogByName("%$input%")
            }
            .flow
            .cachedIn(viewModelScope)
    }
}
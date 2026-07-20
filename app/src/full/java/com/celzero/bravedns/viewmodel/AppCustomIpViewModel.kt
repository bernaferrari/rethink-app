/*
 * Copyright 2022 RethinkDNS and its authors
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
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.CustomIpDao
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class AppCustomIpViewModel(private val customIpDao: CustomIpDao) : ViewModel() {

    private var filteredList: MutableStateFlow<String> = MutableStateFlow("")
    private var uid: Int = UID_EVERYBODY

    @OptIn(ExperimentalCoroutinesApi::class)
    val customIpDetails: Flow<PagingData<CustomIp>> =
        filteredList.flatMapLatest { input ->
            if (input.isNullOrBlank()) {
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                        customIpDao.getAppWiseCustomIp(uid)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            } else {
                Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                        customIpDao.getAppWiseCustomIp("%$input%", uid)
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }
        }

    fun appWiseIpRulesCount(uid: Int): Flow<Int> {
        return customIpDao.getAppWiseIpRulesCount(uid)
    }

    fun setFilter(filter: String) {
        filteredList.value = filter
    }

    fun setUid(i: Int) {
        this.uid = i
    }
}
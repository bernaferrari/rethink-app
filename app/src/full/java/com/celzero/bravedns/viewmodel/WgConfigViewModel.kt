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
import com.celzero.bravedns.database.WgConfigFiles
import com.celzero.bravedns.database.WgConfigFilesDAO
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class WgConfigViewModel(private val wgConfigFilesDAO: WgConfigFilesDAO) : ViewModel() {

    private var list: MutableStateFlow<String> = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    var interfaces: Flow<PagingData<WgConfigFiles>> =
        list.flatMapLatest { _ ->
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) {
                    wgConfigFilesDAO.wgConfigsPagingSource()
                }
                .flow
                .cachedIn(viewModelScope)
        }

    fun insert(wgConfigFiles: WgConfigFiles) {
        wgConfigFilesDAO.insert(wgConfigFiles)
    }

    fun configCount(): Flow<Int> {
        return wgConfigFilesDAO.getConfigCount()
    }
}
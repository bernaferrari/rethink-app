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
import com.celzero.bravedns.database.DoTEndpoint
import com.celzero.bravedns.database.DoTEndpointDAO
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.flow.Flow

class DoTEndpointViewModel(private val endpointDao: DoTEndpointDAO) : ViewModel() {
    val dohEndpointList: Flow<PagingData<DoTEndpoint>> =
        Pager(PagingConfig(PAGING_PAGE_SIZE)) { endpointDao.doTEndpointsPagingSource() }
            .flow
            .cachedIn(viewModelScope)
}
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
package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMap
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMapDao
import com.bernaferrari.bravedns.database.RemoteBlocklistPacksMap
import com.bernaferrari.bravedns.database.RemoteBlocklistPacksMapDao
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@KoinViewModel
class LocalBlocklistPacksMapViewModel(
    @Provided private val dao: LocalBlocklistPacksMapDao,
) : ViewModel() {
    private val refresh = MutableStateFlow(Unit)

    @OptIn(ExperimentalCoroutinesApi::class)
    val simpleTags: Flow<PagingData<LocalBlocklistPacksMap>> =
        refresh.flatMapLatest {
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { dao.getTags() }
                .flow
                .cachedIn(viewModelScope)
        }
}

@KoinViewModel
class RemoteBlocklistPacksMapViewModel(
    @Provided private val dao: RemoteBlocklistPacksMapDao,
) : ViewModel() {
    private val refresh = MutableStateFlow(Unit)

    @OptIn(ExperimentalCoroutinesApi::class)
    val simpleTags: Flow<PagingData<RemoteBlocklistPacksMap>> =
        refresh.flatMapLatest {
            Pager(PagingConfig(Constants.PAGING_PAGE_SIZE)) { dao.getTags() }
                .flow
                .cachedIn(viewModelScope)
        }
}

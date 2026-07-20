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
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.database.RethinkRemoteFileTag
import com.celzero.bravedns.database.RethinkRemoteFileTagDao
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class RethinkRemoteFileTagViewModel(private val rethinkRemoteDao: RethinkRemoteFileTagDao) :
    ViewModel() {

    private var list: MutableStateFlow<String> = MutableStateFlow("")
    private var blocklistFilter: RethinkBlocklistState.Filters? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val remoteFileTags: Flow<PagingData<RethinkRemoteFileTag>> =
        list.flatMapLatest { input: String ->
            if (blocklistFilter != null) {
                val query = blocklistFilter?.query ?: "%%"
                val selected = getSelectedFilter()
                val subg = blocklistFilter?.subGroups ?: mutableSetOf()

                if (subg.isNotEmpty()) {
                    Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                            rethinkRemoteDao.getRemoteFileTagsSubg(query, selected, subg)
                        }
                        .flow
                        .cachedIn(viewModelScope)
                } else {
                    Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                            rethinkRemoteDao.getRemoteFileTagsWithFilter(query, selected)
                        }
                        .flow
                        .cachedIn(viewModelScope)
                }
            } else if (input.isBlank()) {
                Pager(PagingConfig(PAGING_PAGE_SIZE)) { rethinkRemoteDao.getRemoteFileTags() }
                    .flow
                    .cachedIn(viewModelScope)
            } else {
                Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                        rethinkRemoteDao.getRemoteFileTagsWithFilter(
                            "%$input%",
                            getSelectedFilter()
                        )
                    }
                    .flow
                    .cachedIn(viewModelScope)
            }
        }

    private fun getSelectedFilter(): MutableSet<Int> {
        if (
            blocklistFilter?.filterSelected ==
                RethinkBlocklistState.BlocklistSelectionFilter.SELECTED
        ) {
            return mutableSetOf(1)
        }
        return mutableSetOf(0, 1)
    }

    suspend fun allFileTags(): List<FileTag> {
        return rethinkRemoteDao.getAllTags()
    }

    fun setFilter(searchText: String = "") {
        list.value = searchText
    }

    fun setFilter(filter: RethinkBlocklistState.Filters) {
        this.blocklistFilter = filter
        list.value = filter.query
    }
}
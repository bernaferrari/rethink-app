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
package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.data.FileTag
import com.bernaferrari.bravedns.database.RethinkLocalFileTag
import com.bernaferrari.bravedns.database.RethinkLocalFileTagDao
import com.bernaferrari.bravedns.database.RethinkRemoteFileTag
import com.bernaferrari.bravedns.database.RethinkRemoteFileTagDao
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@KoinViewModel
class RethinkLocalFileTagViewModel(
    @Provided private val dao: RethinkLocalFileTagDao,
) : ViewModel() {
    private val query = MutableStateFlow(TagQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val localFiletags: Flow<PagingData<RethinkLocalFileTag>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    val filter = state.filter
                    when {
                        filter != null && filter.subGroups.isNotEmpty() ->
                            dao.getLocalFileTagsSubg(filter.query, filter.selected, filter.subGroups)
                        filter != null -> dao.getLocalFileTagsWithFilter(filter.query, filter.selected)
                        state.search.isBlank() -> dao.getLocalFileTags()
                        else -> dao.getLocalFileTagsWithFilter("%${state.search}%", allSelections)
                    }
                }
                .flow
                .cachedIn(viewModelScope)
        }

    suspend fun allFileTags(): List<FileTag> = dao.getAllTags()

    fun setFilter(searchText: String = "") {
        query.value = TagQuery(search = searchText)
    }

    fun setFilter(filter: RethinkBlocklistState.Filters) {
        query.value = TagQuery(filter = BlocklistFilter(filter.query, filter.filterSelected, filter.subGroups.toSet()))
    }
}

@KoinViewModel
class RethinkRemoteFileTagViewModel(
    @Provided private val dao: RethinkRemoteFileTagDao,
) : ViewModel() {
    private val query = MutableStateFlow(TagQuery())

    @OptIn(ExperimentalCoroutinesApi::class)
    val remoteFileTags: Flow<PagingData<RethinkRemoteFileTag>> =
        query.flatMapLatest { state ->
            Pager(PagingConfig(PAGING_PAGE_SIZE)) {
                    val filter = state.filter
                    when {
                        filter != null && filter.subGroups.isNotEmpty() ->
                            dao.getRemoteFileTagsSubg(filter.query, filter.selected, filter.subGroups)
                        filter != null -> dao.getRemoteFileTagsWithFilter(filter.query, filter.selected)
                        state.search.isBlank() -> dao.getRemoteFileTags()
                        else -> dao.getRemoteFileTagsWithFilter("%${state.search}%", allSelections)
                    }
                }
                .flow
                .cachedIn(viewModelScope)
        }

    suspend fun allFileTags(): List<FileTag> = dao.getAllTags()

    fun setFilter(searchText: String = "") {
        query.value = TagQuery(search = searchText)
    }

    fun setFilter(filter: RethinkBlocklistState.Filters) {
        query.value = TagQuery(filter = BlocklistFilter(filter.query, filter.filterSelected, filter.subGroups.toSet()))
    }
}

private val allSelections = setOf(0, 1)

private data class TagQuery(val search: String = "", val filter: BlocklistFilter? = null)

private data class BlocklistFilter(
    val query: String,
    val selection: RethinkBlocklistState.BlocklistSelectionFilter,
    val subGroups: Set<String>
) {
    val selected: Set<Int>
        get() =
            if (selection == RethinkBlocklistState.BlocklistSelectionFilter.SELECTED) setOf(1)
            else allSelections
}

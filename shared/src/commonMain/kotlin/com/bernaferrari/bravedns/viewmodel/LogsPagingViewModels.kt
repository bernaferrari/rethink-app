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
import com.bernaferrari.bravedns.database.ActivityAppCount
import com.bernaferrari.bravedns.database.ActivityLogDAO
import com.bernaferrari.bravedns.database.ActivityLogEntry
import com.bernaferrari.bravedns.database.ActivityLogQuery
import com.bernaferrari.bravedns.database.loggedApps
import com.bernaferrari.bravedns.database.timeline
import com.bernaferrari.bravedns.util.Constants.Companion.PAGING_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

private val logsPagingConfig =
    PagingConfig(
        enablePlaceholders = false,
        prefetchDistance = 3,
        initialLoadSize = PAGING_PAGE_SIZE * 2,
        maxSize = PAGING_PAGE_SIZE * 3,
        pageSize = PAGING_PAGE_SIZE * 2,
    )

/**
 * The Activity log's rows: connections, DNS lookups or both as one timeline ([ActivityLogDAO]).
 * Each log screen (the Activity tab, a log pushed for one app or search) owns an instance, scoped to
 * its navigation entry, so their filters never leak into each other.
 */
@KoinViewModel
class ActivityLogViewModel(
    @Provided private val dao: ActivityLogDAO,
) : ViewModel() {
    // Null until the screen applies its filters, so nothing loads unfiltered first.
    private val query = MutableStateFlow<ActivityLogQuery?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<PagingData<ActivityLogEntry>> =
        query
            .filterNotNull()
            .flatMapLatest { state -> Pager(logsPagingConfig) { dao.timeline(state) }.flow }
            .cachedIn(viewModelScope)

    fun setQuery(state: ActivityLogQuery) {
        query.value = state
    }

    /** The app filter's options for [state]'s kinds, verdict and reasons. */
    suspend fun loggedApps(state: ActivityLogQuery): List<ActivityAppCount> = dao.loggedApps(state)
}

/*
 * Copyright 2025 RethinkDNS and its authors
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.celzero.bravedns.database.RichHistoryEntry
import com.celzero.bravedns.database.SubscriptionStateHistoryDao
import com.celzero.bravedns.util.Constants.Companion.LIVEDATA_PAGE_SIZE

class PurchaseHistoryViewModel(private val historyDao: SubscriptionStateHistoryDao) : ViewModel() {

    private val pagingConfig = PagingConfig(
        enablePlaceholders = true,
        prefetchDistance = 3,
        initialLoadSize = LIVEDATA_PAGE_SIZE * 2,
        maxSize = LIVEDATA_PAGE_SIZE * 3,
        pageSize = LIVEDATA_PAGE_SIZE,
    )

    /**
     * Paged list of meaningful purchase history entries.
     * Noise transitions (Initial↔Initial, Unknown→Active, etc.) are already
     * excluded in the SQL query inside [SubscriptionStateHistoryDao.observeRichHistoryPaged],
     * so the adapter never receives them.
     */
    val historyList: LiveData<PagingData<RichHistoryEntry>> =
        Pager(pagingConfig) { historyDao.observeRichHistoryPaged() }
            .liveData
            .cachedIn(viewModelScope)

    /**
     * Total count of meaningful history entries for the badge in the toolbar.
     * Emitted once on first observation; stale after new inserts but acceptable
     * for a badge that is only populated during Activity startup.
     */
    val totalCount: LiveData<Int> = liveData {
        emit(historyDao.getMeaningfulCount())
    }
}

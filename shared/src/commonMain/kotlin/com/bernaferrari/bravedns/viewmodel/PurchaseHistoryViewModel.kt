/* Copyright 2025 RethinkDNS and its authors */
package com.bernaferrari.bravedns.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bernaferrari.bravedns.database.SubscriptionStateHistory
import com.bernaferrari.bravedns.database.SubscriptionStateHistoryDao
import com.bernaferrari.bravedns.util.Constants.Companion.LIVEDATA_PAGE_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

@KoinViewModel
class PurchaseHistoryViewModel(@Provided private val historyDao: SubscriptionStateHistoryDao) : ViewModel() {
    private val pagingConfig = PagingConfig(
        enablePlaceholders = true,
        prefetchDistance = 3,
        initialLoadSize = LIVEDATA_PAGE_SIZE * 2,
        maxSize = LIVEDATA_PAGE_SIZE * 3,
        pageSize = LIVEDATA_PAGE_SIZE * 2,
        jumpThreshold = 5
    )

    val historyFlow: Flow<PagingData<SubscriptionStateHistory>> =
        Pager(pagingConfig) { historyDao.observeHistoryPaged() }
            .flow
            .cachedIn(viewModelScope)

    val totalCount: Flow<Int> = flow { emit(historyDao.getMeaningfulCount()) }
}

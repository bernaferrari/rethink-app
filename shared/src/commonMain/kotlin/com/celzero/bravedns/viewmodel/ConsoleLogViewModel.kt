/* Copyright 2024 RethinkDNS and its authors */
package com.celzero.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.ConsoleLog
import com.celzero.bravedns.database.ConsoleLogDAO
import com.celzero.bravedns.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@KoinViewModel
class ConsoleLogViewModel(
    @Provided private val dao: ConsoleLogDAO,
) : ViewModel() {
    private val filter = MutableStateFlow("")
    private var debounceJob: Job? = null
    private var logLevel: Long = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: Flow<PagingData<ConsoleLog>> =
        filter.flatMapLatest { input ->
            Pager(pagingConfig) { dao.getLogs("%$input%") }
                .flow
                .cachedIn(viewModelScope)
        }

    val pagingConfig = PagingConfig(
        pageSize = Constants.PAGING_PAGE_SIZE,
        enablePlaceholders = false,
        prefetchDistance = 10
    )

    suspend fun sinceTime(): Long = runCatching { dao.sinceTime() }.getOrDefault(0L)

    fun setLogLevel(level: Long) {
        logLevel = level
    }

    fun setFilter(filter: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(100)
            this@ConsoleLogViewModel.filter.value = filter
        }
    }
}

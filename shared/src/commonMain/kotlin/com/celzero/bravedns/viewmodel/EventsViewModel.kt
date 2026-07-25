/* Copyright 2025 RethinkDNS and its authors */
package com.celzero.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.celzero.bravedns.database.Event
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class EventsViewModel(
    @Provided private val eventDao: EventDao,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val severity = MutableStateFlow<Severity?>(null)
    private val sources = MutableStateFlow<Set<EventSource>>(emptySet())
    private var filterType: TopLevelFilter = TopLevelFilter.ALL

    enum class TopLevelFilter(val id: Int) {
        ALL(0),
        SEVERITY(1),
        SOURCE(2)
    }

    val eventsFlow: Flow<PagingData<Event>> =
        combine(query, severity, sources) { text, level, selectedSources ->
            EventQuery(text, level, selectedSources)
        }.flatMapLatest(::eventsFor)
            .cachedIn(viewModelScope)

    fun setFilter(query: String, sources: Set<EventSource>, severity: Severity?) {
        this.query.value = query
        this.sources.value = sources.toSet()
        this.severity.value = severity
    }

    fun setFilterType(type: TopLevelFilter) {
        filterType = type
    }

    fun getFilterType(): TopLevelFilter = filterType
    fun getCurrentSeverity(): Severity? = severity.value
    fun getCurrentSources(): Set<EventSource> = sources.value
    fun getCurrentQuery(): String = query.value

    private fun eventsFor(state: EventQuery): Flow<PagingData<Event>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false, maxSize = PAGE_SIZE * 3),
            pagingSourceFactory = {
                when {
                    state.severity != null && state.query.isEmpty() ->
                        eventDao.getEventsBySeverityPaged(state.severity)
                    state.severity != null ->
                        eventDao.getEventsBySeverityAndSearchPaged(state.severity, "%${state.query}%")
                    state.sources.isNotEmpty() && state.query.isEmpty() ->
                        eventDao.getEventsBySourcesPaged(state.sources.toList())
                    state.sources.isNotEmpty() ->
                        eventDao.getEventsBySourcesAndSearchPaged(state.sources.toList(), "%${state.query}%")
                    state.query.isNotEmpty() -> eventDao.getEventsBySearchPaged("%${state.query}%")
                    else -> eventDao.getAllEventsPaged()
                }
            }
        ).flow

    private data class EventQuery(
        val query: String,
        val severity: Severity?,
        val sources: Set<EventSource>
    )

    private companion object {
        const val PAGE_SIZE = 50
    }
}

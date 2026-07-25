package com.celzero.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.database.ProxyApplicationMapping
import com.celzero.bravedns.database.ProxyApplicationMappingDAO
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

enum class ProxyAppsFilter { All, Selected, Unselected }

/** Shared app-to-proxy filtering state for WireGuard and TCP proxy screens. */
@OptIn(FlowPreview::class)
@KoinViewModel
class ProxyAppsMappingViewModel(
    @Provided private val mappingDAO: ProxyApplicationMappingDAO,
) : ViewModel() {

    private data class FilterState(
        val searchQuery: String,
        val filter: ProxyAppsFilter,
        val proxyId: String,
    )

    private val filterState = MutableStateFlow(FilterState("", ProxyAppsFilter.All, ""))

    val apps: StateFlow<List<ProxyApplicationMapping>> =
        combine(mappingDAO.getWgAppMappingFlow(), filterState.debounce(200).distinctUntilChanged()) { apps, state ->
            filterAndSortApps(apps, state)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unfiltered list for global actions such as Select all. */
    val allApps: StateFlow<List<ProxyApplicationMapping>> =
        mappingDAO.getWgAppMappingFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(query: String, filter: ProxyAppsFilter, proxyId: String) {
        filterState.value = FilterState(query.trim(), filter, proxyId)
    }

    fun getAppCountById(configId: String): Flow<Int> = mappingDAO.observeAppCountById(configId)

    private fun filterAndSortApps(
        apps: List<ProxyApplicationMapping>,
        state: FilterState,
    ): List<ProxyApplicationMapping> =
        apps.asSequence()
            .filter { app ->
                when (state.filter) {
                    ProxyAppsFilter.All -> true
                    ProxyAppsFilter.Selected -> app.proxyId == state.proxyId
                    ProxyAppsFilter.Unselected -> app.proxyId != state.proxyId
                }
            }
            .filter { app ->
                state.searchQuery.isBlank() || app.appName.contains(state.searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareBy<ProxyApplicationMapping>(
                    { it.appName.ifBlank { it.packageName }.lowercase() },
                    { it.packageName.lowercase() },
                    { it.uid },
                )
            )
            .toList()
}

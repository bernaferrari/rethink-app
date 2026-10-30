/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar

/** Portable identity for the value used to scope a domain-connections query. */
enum class DomainConnectionsInputType(val type: Int) {
    DOMAIN(0),
    FLAG(1),
    ASN(2),
    IP(3);

    companion object {
        fun fromValue(value: Int): DomainConnectionsInputType = entries.firstOrNull { it.type == value } ?: DOMAIN
    }
}

/** Target-neutral chrome for a filtered connection history. Data paging is owned by the host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDomainConnectionsScreen(
    title: String,
    subtitle: String,
    emptyMessage: String,
    loadingMessage: String,
    isEmpty: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    connectionsContent: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> RethinkLoadingState(loadingMessage, Modifier.align(Alignment.Center))
                isEmpty -> RethinkEmptyState(
                    title = emptyMessage,
                    icon = MaterialSymbols.Filled.Public,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> connectionsContent()
            }
        }
    }
}

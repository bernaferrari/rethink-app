/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionRow
import com.bernaferrari.bravedns.ui.compose.common.lastTimeWindowLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkScreenDefaults
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.util.UIUtils.getCountryNameFromFlag
import com.bernaferrari.bravedns.viewmodel.DomainConnectionsViewModel

/** Android query/paging bridge for the shared domain-connections surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainConnectionsScreen(
    viewModel: DomainConnectionsViewModel,
    type: DomainConnectionsInputType,
    flag: String,
    domain: String,
    asn: String,
    ip: String,
    isBlocked: Boolean,
    timeCategory: DomainConnectionsViewModel.TimeCategory,
    onBackClick: () -> Unit,
    onOpenApp: ((uid: Int) -> Unit)? = null,
    onOpenLogs: ((query: String) -> Unit)? = null,
) {
    val title = when (type) {
        DomainConnectionsInputType.DOMAIN -> domain
        DomainConnectionsInputType.FLAG -> stringResource(R.string.two_argument_space, flag, getCountryNameFromFlag(flag))
        DomainConnectionsInputType.ASN -> asn
        DomainConnectionsInputType.IP -> ip
    }
    LaunchedEffect(type, flag, domain, asn, ip, isBlocked) {
        when (type) {
            DomainConnectionsInputType.DOMAIN -> viewModel.setDomain(domain, isBlocked)
            DomainConnectionsInputType.FLAG -> viewModel.setFlag(flag)
            DomainConnectionsInputType.ASN -> viewModel.setAsn(asn, isBlocked)
            DomainConnectionsInputType.IP -> viewModel.setIp(ip, isBlocked)
        }
    }
    LaunchedEffect(timeCategory) { viewModel.timeCategoryChanged(timeCategory) }
    val connectionFlow = when (type) {
        DomainConnectionsInputType.DOMAIN -> viewModel.domainConnectionList
        DomainConnectionsInputType.FLAG -> viewModel.flagConnectionList
        DomainConnectionsInputType.ASN -> viewModel.asnConnectionList
        DomainConnectionsInputType.IP -> viewModel.ipConnectionList
    }
    val items = connectionFlow.collectAsLazyPagingItems()
    val refreshState = items.loadState.refresh
    val isLoading = refreshState is LoadState.Loading && items.itemCount == 0
    val isEmpty = refreshState is LoadState.NotLoading && items.itemCount == 0
    RethinkDomainConnectionsScreen(
        title = title,
        subtitle = lastTimeWindowLabel(timeCategory.value),
        emptyMessage = stringResource(R.string.ada_ip_no_connection),
        isEmpty = isEmpty,
        isLoading = isLoading,
        loadingMessage = stringResource(R.string.lbl_loading),
        onBackClick = onBackClick,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = RethinkScreenDefaults.contentPadding,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            items(count = items.itemCount) { index ->
                val connection = items[index] ?: return@items
                ConnectionRow(connection, cardPositionFor(index, items.itemCount - 1), onOpenApp, onOpenLogs)
            }
        }
    }
}

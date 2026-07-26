/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.ConnectionRow
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
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
    val isEmpty = items.itemCount == 0 && items.loadState.append.endOfPaginationReached
    RethinkDomainConnectionsScreen(
        title = title,
        subtitle = subtitleFor(timeCategory),
        emptyMessage = stringResource(R.string.ada_ip_no_connection),
        isEmpty = isEmpty,
        onBackClick = onBackClick,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SharedDimensions.screenPaddingHorizontal,
                vertical = SharedDimensions.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            items(count = items.itemCount) { index ->
                val connection = items[index] ?: return@items
                ConnectionRow(connection)
            }
        }
    }
}

@Composable
private fun subtitleFor(timeCategory: DomainConnectionsViewModel.TimeCategory): String = when (timeCategory) {
    DomainConnectionsViewModel.TimeCategory.ONE_HOUR -> stringResource(R.string.three_argument, stringResource(R.string.lbl_last), stringResource(R.string.numeric_one), stringResource(R.string.lbl_hour))
    DomainConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> stringResource(R.string.three_argument, stringResource(R.string.lbl_last), stringResource(R.string.numeric_twenty_four), stringResource(R.string.lbl_hour))
    DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS -> stringResource(R.string.three_argument, stringResource(R.string.lbl_last), stringResource(R.string.numeric_seven), stringResource(R.string.lbl_day))
}

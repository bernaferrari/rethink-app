/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.logs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.components.ConnectionRow
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.util.UIUtils.getCountryNameFromFlag
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel

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
    val title =
        when (type) {
            DomainConnectionsInputType.DOMAIN -> domain
            DomainConnectionsInputType.FLAG ->
                stringResource(R.string.two_argument_space, flag, getCountryNameFromFlag(flag))
            DomainConnectionsInputType.ASN -> asn
            DomainConnectionsInputType.IP -> ip
        }
    val subtitle = subtitleFor(timeCategory)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(type, flag, domain, asn, ip, isBlocked) {
        when (type) {
            DomainConnectionsInputType.DOMAIN -> viewModel.setDomain(domain, isBlocked)
            DomainConnectionsInputType.FLAG -> viewModel.setFlag(flag)
            DomainConnectionsInputType.ASN -> viewModel.setAsn(asn, isBlocked)
            DomainConnectionsInputType.IP -> viewModel.setIp(ip, isBlocked)
        }
    }
    LaunchedEffect(timeCategory) { viewModel.timeCategoryChanged(timeCategory) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        ConnectionsContent(
            viewModel = viewModel,
            type = type,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}

@Composable
private fun ConnectionsContent(
    viewModel: DomainConnectionsViewModel,
    type: DomainConnectionsInputType,
    modifier: Modifier = Modifier,
) {
    val connectionFlow =
        when (type) {
            DomainConnectionsInputType.DOMAIN -> viewModel.domainConnectionList
            DomainConnectionsInputType.FLAG -> viewModel.flagConnectionList
            DomainConnectionsInputType.ASN -> viewModel.asnConnectionList
            DomainConnectionsInputType.IP -> viewModel.ipConnectionList
        }
    val items = connectionFlow.collectAsLazyPagingItems()
    val isEmpty = items.itemCount == 0 && items.loadState.append.endOfPaginationReached

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
        ) {
            items(count = items.itemCount) { index ->
                val item = items[index] ?: return@items
                ConnectionRow(item)
            }
        }
        if (isEmpty) ConnectionsEmptyState()
    }
}

@Composable
private fun ConnectionsEmptyState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.illustrations_no_record),
            contentDescription = null,
            modifier = Modifier.size(168.dp),
        )
        Text(
            text = stringResource(R.string.ada_ip_no_connection),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Dimensions.spacingLg),
        )
    }
}

@Composable
private fun subtitleFor(timeCategory: DomainConnectionsViewModel.TimeCategory): String =
    when (timeCategory) {
        DomainConnectionsViewModel.TimeCategory.ONE_HOUR ->
            stringResource(
                R.string.three_argument,
                stringResource(R.string.lbl_last),
                stringResource(R.string.numeric_one),
                stringResource(R.string.lbl_hour),
            )
        DomainConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR ->
            stringResource(
                R.string.three_argument,
                stringResource(R.string.lbl_last),
                stringResource(R.string.numeric_twenty_four),
                stringResource(R.string.lbl_hour),
            )
        DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS ->
            stringResource(
                R.string.three_argument,
                stringResource(R.string.lbl_last),
                stringResource(R.string.numeric_seven),
                stringResource(R.string.lbl_day),
            )
    }

enum class DomainConnectionsInputType(val type: Int) {
    DOMAIN(0),
    FLAG(1),
    ASN(2),
    IP(3);

    companion object {
        fun fromValue(value: Int): DomainConnectionsInputType =
            entries.firstOrNull { it.type == value } ?: DOMAIN
    }
}

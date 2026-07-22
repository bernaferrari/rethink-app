package com.celzero.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celzero.bravedns.data.BlockFreeDnsItem
import com.celzero.bravedns.data.BlockFreeDnsType
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.viewmodel.BlockFreeDnsViewModel

/** Compose port of the endpoint picker used for trusted/block-free DNS. */
@Composable
fun BlockFreeDnsScreen(viewModel: BlockFreeDnsViewModel, persistentState: PersistentState, onBackClick: () -> Unit) {
    val items by viewModel.filteredItemsFlow.collectAsStateWithLifecycle()
    Scaffold(topBar = { RethinkTopBar(title = stringResource(R.string.trusted_dns_endpoint_title), onBackClick = onBackClick) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeaderWithSubtitle(
                    title = stringResource(R.string.trusted_dns_endpoint_heading),
                    subtitle = stringResource(R.string.trusted_dns_endpoint_desc),
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RethinkFilterChip(label = stringResource(R.string.trusted_dns_filter_all), selected = viewModel.activeFilter == null, onClick = { viewModel.setFilter(null) })
                    BlockFreeDnsType.entries.forEach { type ->
                        RethinkFilterChip(label = blockFreeDnsTypeLabel(type), selected = viewModel.activeFilter == type, onClick = { viewModel.setFilter(type) })
                    }
                }
            }
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                val selected = persistentState.blockFreeDns == item.key
                RethinkListItem(
                    headline = item.name,
                    supporting = item.url,
                    leadingIcon = Icons.Default.Public,
                    highlighted = selected,
                    position = cardPositionFor(index, items.lastIndex),
                    trailing = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.trusted_dns_selected)) }
                    } else null,
                    onClick = { persistentState.blockFreeDns = item.key },
                )
            }
        }
    }
}

@Composable
private fun blockFreeDnsTypeLabel(type: BlockFreeDnsType): String =
    stringResource(
        when (type) {
            BlockFreeDnsType.RETHINK -> R.string.trusted_dns_type_rethink
            BlockFreeDnsType.DOH -> R.string.trusted_dns_type_doh
            BlockFreeDnsType.DOT -> R.string.trusted_dns_type_dot
            BlockFreeDnsType.DNSCRYPT -> R.string.trusted_dns_type_dnscrypt
            BlockFreeDnsType.ODOH -> R.string.trusted_dns_type_odoh
            BlockFreeDnsType.DNS_PROXY -> R.string.trusted_dns_type_proxy
            BlockFreeDnsType.SYSTEM -> R.string.trusted_dns_type_system
        }
    )

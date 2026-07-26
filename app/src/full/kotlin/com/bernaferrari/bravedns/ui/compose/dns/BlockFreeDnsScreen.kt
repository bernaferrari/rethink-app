/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.BlockFreeDnsType
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.viewmodel.BlockFreeDnsViewModel

/** Android data and preference bridge for the target-neutral endpoint picker. */
@Composable
fun BlockFreeDnsScreen(
    viewModel: BlockFreeDnsViewModel,
    persistentState: PersistentState,
    onBackClick: () -> Unit,
) {
    val items by viewModel.filteredItemsFlow.collectAsStateWithLifecycle()
    RethinkBlockFreeDnsScreen(
        items = items.map { item ->
            RethinkBlockFreeDnsItem(
                key = item.key,
                name = item.name,
                url = item.url,
                type = item.type.prefix,
            )
        },
        filters = listOf(RethinkBlockFreeDnsFilter(null, stringResource(R.string.trusted_dns_filter_all))) +
            BlockFreeDnsType.entries.map { type ->
                RethinkBlockFreeDnsFilter(type.prefix, blockFreeDnsTypeLabel(type))
            },
        activeFilterId = viewModel.activeFilter?.prefix,
        selectedKey = persistentState.blockFreeDns,
        strings = RethinkBlockFreeDnsStrings(
            title = stringResource(R.string.trusted_dns_endpoint_title),
            heading = stringResource(R.string.trusted_dns_endpoint_heading),
            description = stringResource(R.string.trusted_dns_endpoint_desc),
            selectedDescription = stringResource(R.string.trusted_dns_selected),
        ),
        onFilterSelected = { prefix ->
            viewModel.setFilter(BlockFreeDnsType.entries.firstOrNull { it.prefix == prefix })
        },
        onItemSelected = { item -> persistentState.blockFreeDns = item.key },
        onBackClick = onBackClick,
    )
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
        },
    )

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.logs.RethinkLogRow
import com.bernaferrari.bravedns.ui.compose.logs.connectionLogModel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel

/**
 * Every connection Keepout itself made (DNS, update and blocklist checks), newest first. The
 * tunnel logs these apart from other apps', so the Activity log never shows them. Read-only:
 * Keepout's own traffic has no rules to edit here, so rows expand to their details instead of
 * opening the Connection sheet.
 */
@Composable
fun KeepoutConnectionsScreen(
    viewModel: AppConnectionsViewModel,
    onBackClick: () -> Unit,
) {
    val items = viewModel.keepoutConnections.collectAsLazyPagingItems()
    val refresh = items.loadState.refresh
    val count = items.itemCount
    RethinkTopBarLazyColumnScreen(
        title = stringResource(R.string.keepout_connections_title),
        onBackClick = onBackClick,
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingXs,
            bottom = SharedDimensions.spacing3xl,
        ),
        // Log rows sit as one grouped list, like Activity's.
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
    ) {
        item(key = "explainer") {
            Text(
                stringResource(R.string.keepout_connections_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = SharedDimensions.spacingXs,
                    end = SharedDimensions.spacingXs,
                    bottom = SharedDimensions.spacingMd,
                ),
            )
        }
        when {
            count == 0 && refresh is LoadState.Loading -> item(key = "loading") {
                RethinkLoadingState(stringResource(R.string.lbl_loading), contained = true)
            }
            count == 0 && refresh is LoadState.Error -> item(key = "error") {
                RethinkErrorState(
                    stringResource(R.string.error_loading_log_file),
                    retryLabel = stringResource(R.string.cd_refresh),
                    onRetry = { items.retry() },
                    contained = true,
                )
            }
            count == 0 -> item(key = "empty") {
                RethinkEmptyState(
                    title = stringResource(R.string.keepout_connections_empty),
                    icon = MaterialSymbols.Filled.Info,
                    contained = true,
                )
            }
            else -> items(count, key = items.itemKey { it.id }) { index ->
                val ct = items[index] ?: return@items
                RethinkLogRow(model = connectionLogModel(ct), index = index, itemCount = count)
            }
        }
    }
}

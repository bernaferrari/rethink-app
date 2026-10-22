/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.rpn

import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusIndicator
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

private val AvailabilityRingSize = 120.dp

enum class RethinkRpnAvailabilityStatus { Loading, Active, Inactive, Unavailable }

data class RethinkRpnAvailabilityItem(
    val key: String,
    val name: String,
    val status: RethinkRpnAvailabilityStatus,
)

data class RethinkRpnAvailabilityStrings(
    val title: String,
    val description: String,
    val noSelected: String,
    val loadFailed: String,
    val retry: String,
    val active: String,
    val inactive: String,
    val unavailable: String,
)

/** Shared availability dashboard; hosts provide server probes and retry behavior. */
@Composable
fun RethinkRpnAvailabilityScreen(
    items: List<RethinkRpnAvailabilityItem>,
    loadFailed: Boolean,
    strings: RethinkRpnAvailabilityStrings,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeCount = items.count { it.status == RethinkRpnAvailabilityStatus.Active }
    val progress = if (items.isNotEmpty()) activeCount.toFloat() / items.size else 0f
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The large title already names the screen; the description stands alone beneath it.
            Text(
                strings.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = SharedDimensions.screenPaddingHorizontal,
                    end = SharedDimensions.screenPaddingHorizontal,
                    bottom = SharedDimensions.spacing3xl,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingXl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(AvailabilityRingSize), strokeWidth = SharedDimensions.spacingSm)
                    Text(
                        "$activeCount/${items.size}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val failedEmpty = items.isEmpty() && loadFailed
                when {
                    failedEmpty -> RethinkErrorState(strings.loadFailed, retryLabel = strings.retry, onRetry = onRetry, contained = true)
                    items.isEmpty() -> RethinkEmptyState(title = strings.noSelected, message = "", contained = true)
                    else -> AvailabilityList(items, strings)
                }
                if (!failedEmpty) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = SharedDimensions.spacingSm),
                    ) { Text(strings.retry) }
                }
            }
        }
    }
}

@Composable
private fun AvailabilityList(items: List<RethinkRpnAvailabilityItem>, strings: RethinkRpnAvailabilityStrings) {
    RethinkListGroup {
        items.forEachIndexed { index, item ->
            RethinkListItem(
                headline = item.name,
                position = cardPositionFor(index, items.lastIndex),
                trailing = { AvailabilityStatus(item.status, strings) },
            )
        }
    }
}

@Composable
private fun AvailabilityStatus(status: RethinkRpnAvailabilityStatus, strings: RethinkRpnAvailabilityStrings) {
    when (status) {
        RethinkRpnAvailabilityStatus.Loading -> LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
        RethinkRpnAvailabilityStatus.Active -> RethinkStatusLabel(strings.active, RethinkStatusColors.allowed, indicator = RethinkStatusIndicator.None)
        RethinkRpnAvailabilityStatus.Inactive -> RethinkStatusLabel(strings.inactive, RethinkStatusColors.blocked, indicator = RethinkStatusIndicator.None)
        RethinkRpnAvailabilityStatus.Unavailable -> RethinkStatusLabel(strings.unavailable, RethinkStatusColors.idle, indicator = RethinkStatusIndicator.None)
    }
}

/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

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
            Surface(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = SharedDimensions.screenPaddingHorizontal,
                    vertical = SharedDimensions.spacingSm,
                ),
                shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(SharedDimensions.spacingLg)) {
                    Text(strings.title, style = MaterialTheme.typography.titleMedium)
                    Text(strings.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = SharedDimensions.screenPaddingHorizontal,
                    vertical = SharedDimensions.spacingLg,
                ),
            ) {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingLg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(120.dp), strokeWidth = 8.dp)
                    Text("$activeCount/${items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (items.isEmpty()) {
                    EmptyAvailabilityMessage(if (loadFailed) strings.loadFailed else strings.noSelected)
                } else {
                    AvailabilityList(items, strings)
                }
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text(strings.retry) }
            }
        }
    }
}

@Composable
private fun EmptyAvailabilityMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(SharedDimensions.spacingXl),
        )
    }
}

@Composable
private fun AvailabilityList(items: List<RethinkRpnAvailabilityItem>, strings: RethinkRpnAvailabilityStrings) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                AvailabilityRow(item, strings)
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        modifier = Modifier.padding(horizontal = SharedDimensions.spacingLg),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailabilityRow(item: RethinkRpnAvailabilityItem, strings: RethinkRpnAvailabilityStrings) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        when (item.status) {
            RethinkRpnAvailabilityStatus.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            RethinkRpnAvailabilityStatus.Active -> AvailabilityStatusText(strings.active, MaterialTheme.colorScheme.primary)
            RethinkRpnAvailabilityStatus.Inactive -> AvailabilityStatusText(strings.inactive, MaterialTheme.colorScheme.error)
            RethinkRpnAvailabilityStatus.Unavailable -> AvailabilityStatusText(strings.unavailable, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AvailabilityStatusText(label: String, color: Color) {
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
}

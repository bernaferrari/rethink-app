/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkRpnAccountEntry(val title: String, val supporting: String? = null)

data class RethinkRpnAccountOperation(
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

data class RethinkRpnAccountState(
    val entitlementLoading: Boolean,
    val entitlementError: String? = null,
    val entitlement: List<RethinkRpnAccountEntry> = emptyList(),
    val manage: RethinkRpnAccountOperation = RethinkRpnAccountOperation(),
    val historyLoading: Boolean = false,
    val history: List<RethinkRpnAccountEntry> = emptyList(),
    val ordersLoading: Boolean = false,
    val ordersError: String? = null,
    val orders: List<RethinkRpnAccountEntry> = emptyList(),
)

data class RethinkRpnAccountStrings(
    val title: String,
    val manageTab: String,
    val historyTab: String,
    val ordersTab: String,
    val entitlementTab: String,
    val manageHeading: String,
    val manageDescription: String,
    val actionsHeading: String,
    val actionsDescription: String,
    val cancel: String,
    val cancelDescription: String,
    val revoke: String,
    val revokeDescription: String,
    val cancelConfirmation: String,
    val revokeConfirmation: String,
    val proceed: String,
    val dismiss: String,
    val helpHeading: String,
    val helpDescription: String,
    val support: String,
    val supportDescription: String,
    val noHistory: String,
    val noOrders: String,
    val retry: String,
    val loading: String,
)

/** Shared RPN account surface. Billing, paging and entitlement fetches stay with the Android host. */
@Composable
fun RethinkRpnAccountScreen(
    state: RethinkRpnAccountState,
    strings: RethinkRpnAccountStrings,
    onBackClick: () -> Unit,
    onCancelSubscription: () -> Unit,
    onRevokeSubscription: () -> Unit,
    onSupport: () -> Unit,
    onRetryOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<RethinkAccountAction?>(null) }
    val tabs = listOf(strings.manageTab, strings.historyTab, strings.ordersTab, strings.entitlementTab)
    androidx.compose.material3.Scaffold(modifier = modifier, topBar = { RethinkTopBar(strings.title, onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            when (selectedTab) {
                0 -> RethinkRpnManageTab(state.manage, strings, onSupport) { pendingAction = it }
                1 -> RethinkRpnHistoryTab(state.historyLoading, state.history, strings)
                2 -> RethinkRpnOrdersTab(state.ordersLoading, state.ordersError, state.orders, strings, onRetryOrders)
                else -> RethinkRpnEntitlementTab(state.entitlementLoading, state.entitlementError, state.entitlement, strings)
            }
        }
    }
    pendingAction?.let { action ->
        val isRevoke = action == RethinkAccountAction.Revoke
        RethinkConfirmDialog(
            onDismissRequest = { pendingAction = null },
            title = if (isRevoke) strings.revoke else strings.cancel,
            message = if (isRevoke) strings.revokeConfirmation else strings.cancelConfirmation,
            confirmText = strings.proceed,
            dismissText = strings.dismiss,
            isConfirmDestructive = true,
            onConfirm = {
                pendingAction = null
                if (isRevoke) onRevokeSubscription() else onCancelSubscription()
            },
            onDismiss = { pendingAction = null },
        )
    }
}

private enum class RethinkAccountAction { Cancel, Revoke }

@Composable
private fun RethinkRpnManageTab(state: RethinkRpnAccountOperation, strings: RethinkRpnAccountStrings, onSupport: () -> Unit, onAction: (RethinkAccountAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        item { SectionHeaderWithSubtitle(title = strings.manageHeading, subtitle = strings.manageDescription) }
        state.message?.let { message ->
            item {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SharedDimensions.cornerRadiusMd),
                    color = if (state.error) {
                        androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        message,
                        color = if (state.error) {
                            androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(SharedDimensions.spacingMd),
                    )
                }
            }
        }
        if (state.busy) item { Column(Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) { CircularProgressIndicator(); Text(strings.loading) } }
        item { SectionHeaderWithSubtitle(title = strings.actionsHeading, subtitle = strings.actionsDescription) }
        item {
            RethinkListGroup {
                RethinkListItem(headline = strings.cancel, supporting = strings.cancelDescription, leadingIcon = MaterialSymbols.Filled.Close, position = CardPosition.First, enabled = !state.busy, leadingIconTint = androidx.compose.material3.MaterialTheme.colorScheme.error, onClick = { onAction(RethinkAccountAction.Cancel) })
                RethinkListItem(headline = strings.revoke, supporting = strings.revokeDescription, leadingIcon = MaterialSymbols.Filled.Block, position = CardPosition.Last, enabled = !state.busy, leadingIconTint = androidx.compose.material3.MaterialTheme.colorScheme.error, onClick = { onAction(RethinkAccountAction.Revoke) })
            }
        }
        item { SectionHeaderWithSubtitle(title = strings.helpHeading, subtitle = strings.helpDescription) }
        item { RethinkListGroup { RethinkListItem(headline = strings.support, supporting = strings.supportDescription, position = CardPosition.Single, onClick = onSupport) } }
    }
}

@Composable
private fun RethinkRpnHistoryTab(loading: Boolean, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings) {
    when {
        loading -> RethinkRpnAccountLoading(strings.loading)
        entries.isEmpty() -> RethinkEmptyState(strings.noHistory, "", Modifier.fillMaxWidth())
        else -> RethinkRpnAccountEntries(entries)
    }
}

@Composable
private fun RethinkRpnOrdersTab(loading: Boolean, error: String?, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings, onRetry: () -> Unit) {
    when {
        loading -> RethinkRpnAccountLoading(strings.loading)
        error != null -> Column(Modifier.fillMaxWidth().padding(SharedDimensions.spacingXl), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) { Text(error, color = androidx.compose.material3.MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text(strings.retry) } }
        entries.isEmpty() -> RethinkEmptyState(strings.noOrders, "", Modifier.fillMaxWidth())
        else -> RethinkRpnAccountEntries(entries)
    }
}

@Composable
private fun RethinkRpnEntitlementTab(loading: Boolean, error: String?, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings) {
    when {
        loading -> RethinkRpnAccountLoading(strings.loading)
        error != null -> RethinkEmptyState(error, "", Modifier.fillMaxWidth())
        else -> RethinkRpnAccountEntries(entries)
    }
}

@Composable
private fun RethinkRpnAccountLoading(label: String) {
    Column(Modifier.fillMaxWidth().padding(SharedDimensions.spacingXl), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) { CircularProgressIndicator(); Text(label) }
}

@Composable
private fun RethinkRpnAccountEntries(entries: List<RethinkRpnAccountEntry>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(SharedDimensions.screenPaddingHorizontal)) {
        item {
            RethinkListGroup {
                entries.forEachIndexed { index, item ->
                    RethinkListItem(headline = item.title, supporting = item.supporting, position = when {
                        entries.size == 1 -> CardPosition.Single
                        index == 0 -> CardPosition.First
                        index == entries.lastIndex -> CardPosition.Last
                        else -> CardPosition.Middle
                    })
                }
            }
        }
    }
}

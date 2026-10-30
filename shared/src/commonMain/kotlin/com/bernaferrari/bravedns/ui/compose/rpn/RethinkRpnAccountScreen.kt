@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueListItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkScreenDefaults
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

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
    /** Current-subscription summary (e.g. status and expiry) shown on the Manage tab. */
    val summary: List<RethinkRpnAccountEntry> = emptyList(),
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
    val noSubscriptionTitle: String,
    val noSubscriptionMessage: String,
    val viewPlans: String,
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
    onViewPlans: (() -> Unit)? = null,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<RethinkAccountAction?>(null) }
    val tabs = listOf(strings.manageTab, strings.historyTab, strings.ordersTab, strings.entitlementTab)
    androidx.compose.material3.Scaffold(modifier = modifier, topBar = { RethinkTopBar(strings.title, onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Four short labels fit a fixed row at 360dp, so the tabs share the width evenly
            // instead of scrolling a clipped "Entitlement" off the edge.
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            when (selectedTab) {
                0 -> RethinkRpnManageTab(state, strings, onSupport, onViewPlans) { pendingAction = it }
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
private fun RethinkRpnManageTab(
    account: RethinkRpnAccountState,
    strings: RethinkRpnAccountStrings,
    onSupport: () -> Unit,
    onViewPlans: (() -> Unit)?,
    onAction: (RethinkAccountAction) -> Unit,
) {
    val state = account.manage
    // Only a loaded, error-free, empty entitlement means "no subscription". While loading or on
    // error the account actions stay available so a user is never locked out of cancelling.
    val noSubscription = !account.entitlementLoading && account.entitlementError == null && account.entitlement.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = RethinkScreenDefaults.contentPadding,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        item { SectionHeaderWithSubtitle(title = strings.manageHeading, subtitle = strings.manageDescription) }
        when {
            account.entitlementLoading -> item { RethinkLoadingState(strings.loading) }
            noSubscription -> item {
                RethinkEmptyState(
                    title = strings.noSubscriptionTitle,
                    message = strings.noSubscriptionMessage,
                    icon = MaterialSymbols.Filled.Star,
                    ctaLabel = onViewPlans?.let { strings.viewPlans },
                    onCtaClick = onViewPlans,
                )
            }
            account.summary.isNotEmpty() -> item {
                RethinkListGroup {
                    account.summary.forEachIndexed { index, entry ->
                        RethinkKeyValueListItem(entry.title, entry.supporting.orEmpty(), cardPositionFor(index, account.summary.lastIndex))
                    }
                }
            }
        }
        state.message?.let { message ->
            item {
                Surface(
                    shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
                    color = if (state.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(SharedDimensions.spacingMd),
                    )
                }
            }
        }
        if (state.busy) item { RethinkLoadingState(strings.loading) }
        if (!noSubscription) {
            item { SectionHeaderWithSubtitle(title = strings.actionsHeading, subtitle = strings.actionsDescription) }
            item {
                RethinkListGroup {
                    RethinkListItem(headline = strings.cancel, supporting = strings.cancelDescription, leadingIcon = MaterialSymbols.Filled.Close, position = CardPosition.First, enabled = !state.busy, destructive = true, onClick = { onAction(RethinkAccountAction.Cancel) })
                    RethinkListItem(headline = strings.revoke, supporting = strings.revokeDescription, leadingIcon = MaterialSymbols.Filled.Block, position = CardPosition.Last, enabled = !state.busy, destructive = true, onClick = { onAction(RethinkAccountAction.Revoke) })
                }
            }
        }
        item { SectionHeaderWithSubtitle(title = strings.helpHeading, subtitle = strings.helpDescription) }
        item {
            RethinkListGroup {
                RethinkListItem(
                    headline = strings.support,
                    supporting = strings.supportDescription,
                    leadingIcon = MaterialSymbols.Filled.Headset,
                    position = CardPosition.Single,
                    // Opens the support sheet in place; chevrons are reserved for navigation.
                    onClick = onSupport,
                )
            }
        }
    }
}

@Composable
private fun RethinkRpnHistoryTab(loading: Boolean, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings) {
    when {
        loading -> RethinkLoadingState(strings.loading)
        entries.isEmpty() -> RethinkEmptyState(strings.noHistory, "", Modifier.fillMaxWidth(), icon = MaterialSymbols.Filled.History)
        else -> RethinkRpnAccountEntries(entries)
    }
}

@Composable
private fun RethinkRpnOrdersTab(loading: Boolean, error: String?, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings, onRetry: () -> Unit) {
    when {
        loading -> RethinkLoadingState(strings.loading)
        error != null -> RethinkErrorState(error, retryLabel = strings.retry, onRetry = onRetry)
        entries.isEmpty() -> RethinkEmptyState(strings.noOrders, "", Modifier.fillMaxWidth(), icon = MaterialSymbols.Filled.ShoppingCart)
        else -> RethinkRpnAccountEntries(entries)
    }
}

@Composable
private fun RethinkRpnEntitlementTab(loading: Boolean, error: String?, entries: List<RethinkRpnAccountEntry>, strings: RethinkRpnAccountStrings) {
    when {
        loading -> RethinkLoadingState(strings.loading)
        error != null -> RethinkErrorState(error)
        entries.isEmpty() -> RethinkEmptyState(strings.noSubscriptionTitle, strings.noSubscriptionMessage, Modifier.fillMaxWidth(), icon = MaterialSymbols.Filled.Star)
        else -> RethinkRpnAccountEntries(entries, keyValue = true)
    }
}

@Composable
private fun RethinkRpnAccountEntries(entries: List<RethinkRpnAccountEntry>, keyValue: Boolean = false) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = RethinkScreenDefaults.contentPadding,
    ) {
        itemsIndexed(entries, key = { index, item -> "${item.title}_${item.supporting}_$index" }) { index, item ->
            if (keyValue) {
                RethinkKeyValueListItem(item.title, item.supporting.orEmpty(), cardPositionFor(index, entries.lastIndex))
            } else {
                RethinkListItem(
                    headline = item.title,
                    supporting = item.supporting,
                    position = cardPositionFor(index, entries.lastIndex),
                )
            }
        }
    }
}

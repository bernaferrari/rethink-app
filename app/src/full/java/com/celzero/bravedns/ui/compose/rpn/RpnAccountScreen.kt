/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Observer
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.celzero.bravedns.R
import com.celzero.bravedns.database.SubscriptionStateHistory
import com.celzero.bravedns.iab.InAppBillingHandler
import com.celzero.bravedns.iab.ServerApiError
import com.celzero.bravedns.iab.ServerOrderEntry
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkActionListItem
import com.celzero.bravedns.ui.compose.theme.CompactEmptyState
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.viewmodel.ManagePurchaseViewModel
import com.celzero.bravedns.viewmodel.PurchaseHistoryViewModel
import com.celzero.bravedns.viewmodel.ServerOrderHistoryViewModel
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnAccountScreen(
    manageViewModel: ManagePurchaseViewModel,
    historyViewModel: PurchaseHistoryViewModel,
    ordersViewModel: ServerOrderHistoryViewModel,
    onBackClick: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSupport by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var serverError by remember { mutableStateOf(InAppBillingHandler.serverApiErrorLiveData.value) }
    DisposableEffect(lifecycleOwner) {
        val observer = Observer<ServerApiError?> { serverError = it }
        InAppBillingHandler.serverApiErrorLiveData.observe(lifecycleOwner, observer)
        onDispose { InAppBillingHandler.serverApiErrorLiveData.removeObserver(observer) }
    }
    val tabs = listOf(
        stringResource(R.string.rpn_account_manage_tab),
        stringResource(R.string.rpn_account_history_tab),
        stringResource(R.string.rpn_account_orders_tab),
        stringResource(R.string.rpn_account_entitlement_tab),
    )

    Scaffold(
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.rpn_account_title),
                onBackClick = onBackClick,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }
            when (selectedTab) {
                0 -> ManagePurchaseTab(manageViewModel, onSupportClick = { showSupport = true })
                1 -> PurchaseHistoryTab(historyViewModel)
                2 -> ServerOrdersTab(ordersViewModel)
                else -> EntitlementTab()
            }
        }
    }
    serverError?.let { error ->
        ServerErrorRecoveryDialog(
            error = error,
            onDismiss = { InAppBillingHandler.serverApiErrorLiveData.value = null },
            onGetHelp = {
                InAppBillingHandler.serverApiErrorLiveData.value = null
                showSupport = true
            },
        )
    }
    if (showSupport) {
        RpnSupportDialog(onDismiss = { showSupport = false })
    }
}

@Composable
private fun EntitlementTab() {
    val context = LocalContext.current
    val labels = listOf(
        stringResource(R.string.rpn_account_entitlement_status),
        stringResource(R.string.rpn_account_entitlement_client_id),
        stringResource(R.string.rpn_account_entitlement_device_id),
        stringResource(R.string.rpn_account_entitlement_expiry),
        stringResource(R.string.rpn_account_entitlement_provider),
        stringResource(R.string.rpn_account_entitlement_allow_restore),
    )
    var details by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                RpnProxyManager.getEntitlementDetails()?.let {
                    listOf(
                        labels[0] to it.status(), labels[1] to it.cid().take(12),
                        labels[2] to it.did().take(4), labels[3] to it.expiry().toString(),
                        labels[4] to it.providerID(), labels[5] to it.allowRestore().toString(),
                    )
                }
            }
        }.onSuccess { details = it }.onFailure { error = it.message ?: context.getString(R.string.rpn_account_entitlement_load_failed) }
    }
    when {
        error != null -> EmptyAccountState(error.orEmpty())
        details == null -> Column(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
        else -> {
            val currentDetails = details.orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    RethinkListGroup {
                        currentDetails.forEachIndexed { index, (label, value) ->
                            RethinkListItem(
                                headline = label,
                                supporting = value,
                                position = cardPositionFor(index, currentDetails.lastIndex),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagePurchaseTab(viewModel: ManagePurchaseViewModel, onSupportClick: () -> Unit) {
    val state by viewModel.operationState.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val busy = state is ManagePurchaseViewModel.OperationState.InProgress

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeaderWithSubtitle(
                title = stringResource(R.string.rpn_account_manage_heading),
                subtitle = stringResource(R.string.rpn_account_manage_desc),
            )
        }
        item {
            when (val current = state) {
                is ManagePurchaseViewModel.OperationState.InProgress -> {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(current.step.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
                is ManagePurchaseViewModel.OperationState.Success ->
                    Text(current.message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp))
                is ManagePurchaseViewModel.OperationState.Failure ->
                    Text(current.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
                ManagePurchaseViewModel.OperationState.Idle -> Unit
            }
        }
        item { SectionHeaderWithSubtitle(title = stringResource(R.string.rpn_account_actions_heading), subtitle = stringResource(R.string.rpn_account_actions_desc)) }
        item {
            RethinkListGroup {
                RethinkActionListItem(
                    title = stringResource(R.string.rpn_account_cancel),
                    description = stringResource(R.string.rpn_account_cancel_desc),
                    accentColor = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    position = CardPosition.First,
                    onClick = { confirmCancel = true },
                )
                RethinkActionListItem(
                    title = stringResource(R.string.rpn_account_revoke),
                    description = stringResource(R.string.rpn_account_revoke_desc),
                    accentColor = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    position = CardPosition.Last,
                    onClick = { confirmRevoke = true },
                )
            }
        }
        item { SectionHeaderWithSubtitle(title = stringResource(R.string.rpn_account_help_heading), subtitle = stringResource(R.string.rpn_account_help_desc)) }
        item {
            RethinkActionListItem(
                title = stringResource(R.string.rpn_account_support),
                description = stringResource(R.string.rpn_account_support_desc),
                position = CardPosition.Single,
                onClick = onSupportClick,
            )
        }
    }

    if (confirmCancel || confirmRevoke) {
        val revoke = confirmRevoke
        AlertDialog(
            onDismissRequest = { confirmCancel = false; confirmRevoke = false },
            title = { Text(stringResource(if (revoke) R.string.rpn_account_revoke else R.string.rpn_account_cancel)) },
            text = { Text(stringResource(if (revoke) R.string.rpn_account_revoke_confirm else R.string.rpn_account_cancel_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    confirmRevoke = false
                    if (revoke) viewModel.revokeSubscription() else viewModel.cancelSubscription()
                }) { Text(stringResource(R.string.lbl_proceed)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false; confirmRevoke = false }) {
                    Text(stringResource(R.string.lbl_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerErrorRecoveryDialog(
    error: ServerApiError,
    onDismiss: () -> Unit,
    onGetHelp: () -> Unit,
) {
    val details = when (error) {
        is ServerApiError.Conflict409 -> buildString {
            append(stringResource(R.string.rpn_account_conflict, error.operation.name.lowercase()))
            error.serverMessage?.takeIf(String::isNotBlank)?.let { append("\n\n$it") }
        }
        is ServerApiError.Unauthorized401 ->
            stringResource(R.string.rpn_account_unauthorized)
        is ServerApiError.DeviceNotRegistered ->
            stringResource(R.string.rpn_account_device_unregistered)
        is ServerApiError.GenericError -> error.message
        is ServerApiError.NetworkError -> error.message ?: stringResource(R.string.rpn_account_network_error)
        ServerApiError.None -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rpn_account_attention_title)) },
        text = { Text(details) },
        confirmButton = { TextButton(onClick = onGetHelp) { Text(stringResource(R.string.rpn_account_contact_support)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}

@Composable
private fun PurchaseHistoryTab(viewModel: PurchaseHistoryViewModel) {
    val history = viewModel.historyFlow.collectAsLazyPagingItems()
    if (history.itemCount == 0) {
        EmptyAccountState(stringResource(R.string.rpn_account_no_history))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(count = history.itemCount, key = history.itemKey { it.id }) { index ->
            history[index]?.let {
                HistoryRow(
                    item = it,
                    position = cardPositionFor(index, history.itemCount - 1),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: SubscriptionStateHistory, position: CardPosition) =
    RethinkListItem(
        headline = "${item.fromStateName} → ${item.toStateName}",
        supporting = listOfNotNull(item.reason?.takeIf(String::isNotBlank), formatTime(item.timestamp)).joinToString(" · "),
        position = position,
    )

@Composable
private fun ServerOrdersTab(viewModel: ServerOrderHistoryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        ServerOrderHistoryViewModel.UiState.Loading ->
            Column(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
        is ServerOrderHistoryViewModel.UiState.Empty ->
            EmptyAccountState(stringResource(R.string.rpn_account_no_orders))
        is ServerOrderHistoryViewModel.UiState.Error ->
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::reload) { Text(stringResource(R.string.rpn_account_retry)) }
            }
        is ServerOrderHistoryViewModel.UiState.Success ->
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(current.orders, key = { _, order -> order.purchaseToken }) { index, order ->
                    OrderRow(
                        item = order,
                        position = cardPositionFor(index, current.orders.lastIndex),
                    )
                }
            }
    }
}

@Composable
private fun OrderRow(item: ServerOrderEntry, position: CardPosition) {
    val state = item.subscriptionState ?: when (item.purchaseState) {
        ServerOrderEntry.PURCHASE_STATE_PURCHASED -> "PURCHASED"
        ServerOrderEntry.PURCHASE_STATE_PENDING -> "PENDING"
        else -> "UNKNOWN"
    }
    val time = listOf(item.expiryTimeMs, item.purchaseTimeMs, item.mtime).firstOrNull { it > 0L } ?: 0L
    RethinkListItem(
        headline = item.productId.ifBlank { item.sku },
        supporting =
            listOfNotNull(
                state.removePrefix("SUBSCRIPTION_STATE_").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                formatTime(time).takeIf { time > 0L },
            ).joinToString(" · "),
        position = position,
    )
}

@Composable
private fun EmptyAccountState(message: String) {
    CompactEmptyState(
        message = message,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

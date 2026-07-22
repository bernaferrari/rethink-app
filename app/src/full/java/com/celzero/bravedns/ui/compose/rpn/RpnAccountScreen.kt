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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
        "Entitlement",
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
    var details by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                RpnProxyManager.getEntitlementDetails()?.let {
                    listOf(
                        "Status" to it.status(), "Client ID" to it.cid().take(12),
                        "Device ID" to it.did().take(4), "Expiry" to it.expiry().toString(),
                        "Provider" to it.providerID(), "Allow restore" to it.allowRestore().toString(),
                    )
                }
            }
        }.onSuccess { details = it }.onFailure { error = it.message ?: "Unable to load entitlement" }
    }
    when {
        error != null -> EmptyAccountState(error.orEmpty())
        details == null -> Column(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
        else -> LazyColumn(Modifier.fillMaxSize()) { items(details.orEmpty(), key = { it.first }) { (label, value) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value) }
        } }
    }
}

@Composable
private fun ManagePurchaseTab(viewModel: ManagePurchaseViewModel, onSupportClick: () -> Unit) {
    val state by viewModel.operationState.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val busy = state is ManagePurchaseViewModel.OperationState.InProgress

    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.rpn_account_manage_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.rpn_account_manage_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val current = state) {
            is ManagePurchaseViewModel.OperationState.InProgress -> {
                CircularProgressIndicator()
                Text(current.step.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
            }
            is ManagePurchaseViewModel.OperationState.Success ->
                Text(current.message, color = MaterialTheme.colorScheme.primary)
            is ManagePurchaseViewModel.OperationState.Failure ->
                Text(current.message, color = MaterialTheme.colorScheme.error)
            ManagePurchaseViewModel.OperationState.Idle -> Unit
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = { confirmCancel = true },
        ) { Text(stringResource(R.string.rpn_account_cancel)) }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = { confirmRevoke = true },
        ) { Text(stringResource(R.string.rpn_account_revoke)) }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSupportClick,
        ) { Text(stringResource(R.string.rpn_account_support)) }
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
            append("The subscription service reported a conflict while ${error.operation.name.lowercase()}.")
            error.serverMessage?.takeIf(String::isNotBlank)?.let { append("\n\n$it") }
        }
        is ServerApiError.Unauthorized401 ->
            "The subscription service could not verify this device. Open support to send a diagnostic report."
        is ServerApiError.DeviceNotRegistered ->
            "This device is not registered with the current entitlement. Open support to send a diagnostic report."
        is ServerApiError.GenericError -> error.message
        is ServerApiError.NetworkError -> error.message ?: "A network error interrupted the subscription request."
        ServerApiError.None -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subscription needs attention") },
        text = { Text(details) },
        confirmButton = { TextButton(onClick = onGetHelp) { Text("Contact support") } },
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
            history[index]?.let { HistoryRow(it) }
        }
    }
}

@Composable
private fun HistoryRow(item: SubscriptionStateHistory) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("${item.fromStateName} → ${item.toStateName}", fontWeight = FontWeight.Medium)
        item.reason?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Text(formatTime(item.timestamp), style = MaterialTheme.typography.labelSmall)
    }
}

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
                items(current.orders, key = ServerOrderEntry::purchaseToken) { OrderRow(it) }
            }
    }
}

@Composable
private fun OrderRow(item: ServerOrderEntry) {
    val state = item.subscriptionState ?: when (item.purchaseState) {
        ServerOrderEntry.PURCHASE_STATE_PURCHASED -> "PURCHASED"
        ServerOrderEntry.PURCHASE_STATE_PENDING -> "PENDING"
        else -> "UNKNOWN"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(item.productId.ifBlank { item.sku }, fontWeight = FontWeight.Medium)
        Text(state.removePrefix("SUBSCRIPTION_STATE_").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
        val time = listOf(item.expiryTimeMs, item.purchaseTimeMs, item.mtime).firstOrNull { it > 0L } ?: 0L
        if (time > 0L) Text(formatTime(time), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyAccountState(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

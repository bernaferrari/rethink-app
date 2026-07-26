/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.ui.compose.rpn

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
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.SubscriptionStateHistory
import com.bernaferrari.bravedns.iab.InAppBillingHandler
import com.bernaferrari.bravedns.iab.ServerApiError
import com.bernaferrari.bravedns.iab.ServerOrderEntry
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.CompactEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.viewmodel.ManagePurchaseViewModel
import com.bernaferrari.bravedns.viewmodel.PurchaseHistoryViewModel
import com.bernaferrari.bravedns.viewmodel.ServerOrderHistoryViewModel
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
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

    var entitlementRows by remember { mutableStateOf<List<RethinkRpnAccountEntry>>(emptyList()) }
    var entitlementError by remember { mutableStateOf<String?>(null) }
    var entitlementLoading by remember { mutableStateOf(true) }
    val entitlementLabels = listOf(
        stringResource(R.string.rpn_account_entitlement_status),
        stringResource(R.string.rpn_account_entitlement_client_id),
        stringResource(R.string.rpn_account_entitlement_device_id),
        stringResource(R.string.rpn_account_entitlement_expiry),
        stringResource(R.string.rpn_account_entitlement_provider),
        stringResource(R.string.rpn_account_entitlement_allow_restore),
    )
    androidx.compose.runtime.LaunchedEffect(Unit) {
        entitlementLoading = true
        runCatching {
            withContext(Dispatchers.IO) {
                RpnProxyManager.getEntitlementDetails()?.let {
                    listOf(
                        RethinkRpnAccountEntry(entitlementLabels[0], it.status()),
                        RethinkRpnAccountEntry(entitlementLabels[1], it.cid().take(12)),
                        RethinkRpnAccountEntry(entitlementLabels[2], it.did().take(4)),
                        RethinkRpnAccountEntry(entitlementLabels[3], it.expiry().toString()),
                        RethinkRpnAccountEntry(entitlementLabels[4], it.providerID()),
                        RethinkRpnAccountEntry(entitlementLabels[5], it.allowRestore().toString()),
                    )
                }.orEmpty()
            }
        }.onSuccess { entitlementRows = it }.onFailure { entitlementError = it.message ?: "Unable to load entitlement." }
        entitlementLoading = false
    }
    val manageState by manageViewModel.operationState.collectAsStateWithLifecycle()
    val historyItems = historyViewModel.historyFlow.collectAsLazyPagingItems()
    val orderState by ordersViewModel.uiState.collectAsStateWithLifecycle()
    val accountManage = when (val value = manageState) {
        is ManagePurchaseViewModel.OperationState.InProgress -> RethinkRpnAccountOperation(busy = true, message = value.step.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
        is ManagePurchaseViewModel.OperationState.Success -> RethinkRpnAccountOperation(message = value.message)
        is ManagePurchaseViewModel.OperationState.Failure -> RethinkRpnAccountOperation(message = value.message, error = true)
        ManagePurchaseViewModel.OperationState.Idle -> RethinkRpnAccountOperation()
    }
    val accountHistory = historyItems.itemSnapshotList.items.map { item ->
        RethinkRpnAccountEntry(
            title = "${item.fromStateName} → ${item.toStateName}",
            supporting = listOfNotNull(item.reason?.takeIf(String::isNotBlank), formatTime(item.timestamp)).joinToString(" · "),
        )
    }
    val accountOrders = (orderState as? ServerOrderHistoryViewModel.UiState.Success)?.orders.orEmpty().map { item ->
        val status = item.subscriptionState ?: when (item.purchaseState) {
            ServerOrderEntry.PURCHASE_STATE_PURCHASED -> "PURCHASED"
            ServerOrderEntry.PURCHASE_STATE_PENDING -> "PENDING"
            else -> "UNKNOWN"
        }
        val time = listOf(item.expiryTimeMs, item.purchaseTimeMs, item.mtime).firstOrNull { it > 0L } ?: 0L
        RethinkRpnAccountEntry(
            title = item.productId.ifBlank { item.sku },
            supporting = listOfNotNull(status.removePrefix("SUBSCRIPTION_STATE_").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), formatTime(time).takeIf { time > 0L }).joinToString(" · "),
        )
    }
    RethinkRpnAccountScreen(
        state = RethinkRpnAccountState(
            entitlementLoading = entitlementLoading,
            entitlementError = entitlementError,
            entitlement = entitlementRows,
            manage = accountManage,
            historyLoading = historyItems.loadState.refresh is androidx.paging.LoadState.Loading,
            history = accountHistory,
            ordersLoading = orderState is ServerOrderHistoryViewModel.UiState.Loading,
            ordersError = (orderState as? ServerOrderHistoryViewModel.UiState.Error)?.message,
            orders = accountOrders,
        ),
        strings = RethinkRpnAccountStrings(
            title = stringResource(R.string.rpn_account_title),
            manageTab = tabs[0], historyTab = tabs[1], ordersTab = tabs[2], entitlementTab = tabs[3],
            manageHeading = stringResource(R.string.rpn_account_manage_heading), manageDescription = stringResource(R.string.rpn_account_manage_desc),
            actionsHeading = stringResource(R.string.rpn_account_actions_heading), actionsDescription = stringResource(R.string.rpn_account_actions_desc),
            cancel = stringResource(R.string.rpn_account_cancel), cancelDescription = stringResource(R.string.rpn_account_cancel_desc),
            revoke = stringResource(R.string.rpn_account_revoke), revokeDescription = stringResource(R.string.rpn_account_revoke_desc),
            cancelConfirmation = stringResource(R.string.rpn_account_cancel_confirm), revokeConfirmation = stringResource(R.string.rpn_account_revoke_confirm),
            proceed = stringResource(R.string.lbl_proceed), dismiss = stringResource(R.string.lbl_cancel),
            helpHeading = stringResource(R.string.rpn_account_help_heading), helpDescription = stringResource(R.string.rpn_account_help_desc),
            support = stringResource(R.string.rpn_account_support), supportDescription = stringResource(R.string.rpn_account_support_desc),
            noHistory = stringResource(R.string.rpn_account_no_history), noOrders = stringResource(R.string.rpn_account_no_orders), retry = stringResource(R.string.rpn_account_retry), loading = stringResource(R.string.lbl_loading),
        ),
        onBackClick = onBackClick,
        onCancelSubscription = manageViewModel::cancelSubscription,
        onRevokeSubscription = manageViewModel::revokeSubscription,
        onSupport = { showSupport = true },
        onRetryOrders = ordersViewModel::reload,
    )

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

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

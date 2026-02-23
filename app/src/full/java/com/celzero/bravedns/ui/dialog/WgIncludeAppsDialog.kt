/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.dialog

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.IncludeAppRow
import com.celzero.bravedns.adapter.IncludeDialogHost
import com.celzero.bravedns.adapter.IncludeDialogState
import com.celzero.bravedns.adapter.updateProxyIdForApp
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetActionRow
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetCard
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkSegmentedChoiceRow
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Composable
fun WgIncludeAppsDialog(
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    WgDialog(onDismissRequest = onDismiss, useSurface = true) {
        WgIncludeAppsDialogScreen(
            viewModel = viewModel,
            proxyId = proxyId,
            proxyName = proxyName,
            onDismiss = onDismiss
        )
    }
}

private const val REFRESH_TIMEOUT: Long = 4000

enum class TopLevelFilter(val id: Int) {
    ALL_APPS(0),
    SELECTED_APPS(1),
    UNSELECTED_APPS(2);

    fun getLabelId(): Int {
        return when (this) {
            ALL_APPS -> R.string.lbl_all
            SELECTED_APPS -> R.string.rt_filter_parent_selected
            UNSELECTED_APPS -> R.string.lbl_unselected
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WgIncludeAppsDialogScreen(
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshDatabase = remember { RefreshDatabaseProvider.get() }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TopLevelFilter.ALL_APPS) }
    var selectAllChecked by remember { mutableStateOf(false) }
    var pendingSelectAll by remember { mutableStateOf<Boolean?>(null) }
    var showRemainingDialog by remember { mutableStateOf(false) }
    var pendingDialog by remember { mutableStateOf<IncludeDialogState?>(null) }
    val appCount =
        viewModel.getAppCountById(proxyId).asFlow().collectAsState(initial = 0).value
    val apps = viewModel.apps.asFlow().collectAsLazyPagingItems()
    var isDialogVisible by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun updateInterfaceDetails(mapping: com.celzero.bravedns.database.ProxyApplicationMapping, include: Boolean) {
        scope.launch(Dispatchers.IO) {
            val appUidList = FirewallManager.getAppNamesByUid(mapping.uid)
            if (FirewallManager.isAppExcludedFromProxy(mapping.uid)) {
                withContext(Dispatchers.Main) {
                    Utilities.showToastUiCentered(
                        context,
                        context.resources.getString(R.string.exclude_apps_from_proxy_failure_toast),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (appUidList.count() > 1) {
                    pendingDialog = IncludeDialogState(appUidList, mapping, include)
                } else {
                    updateProxyIdForApp(mapping.uid, proxyId, proxyName, include)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { isDialogVisible = false }
    }

    LaunchedEffect(query, selectedFilter) {
        viewModel.setFilter(query, selectedFilter, proxyId)
    }

    fun refreshApps() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch(Dispatchers.IO) {
            refreshDatabase.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE)
        }
        scope.launch {
            delay(REFRESH_TIMEOUT)
            if (isDialogVisible) {
                isRefreshing = false
                Utilities.showToastUiCentered(
                    context,
                    context.resources.getString(R.string.refresh_complete),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "wgRefresh")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(750, easing = LinearEasing)),
            label = "wgRefreshRotation"
        )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = proxyName,
                subtitle = context.resources.getString(R.string.add_remove_apps, appCount.toString()),
                onBackClick = onDismiss,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { refreshApps() }, enabled = !isRefreshing) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                RethinkBottomSheetActionRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.screenPaddingHorizontal,
                                vertical = Dimensions.spacingSm
                            ),
                    primaryText = stringResource(R.string.ada_noapp_dialog_positive),
                    onPrimaryClick = {
                        viewModel.setFilter("", TopLevelFilter.ALL_APPS, proxyId)
                        onDismiss()
                    },
                    secondaryText = stringResource(R.string.lbl_remaining_apps),
                    onSecondaryClick = { showRemainingDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Dimensions.screenPaddingHorizontal)
        ) {
            IncludeDialogHost(
                state = pendingDialog,
                onDismiss = { pendingDialog = null },
                onConfirm = { mapping, include ->
                    updateProxyIdForApp(mapping.uid, proxyId, proxyName, include)
                }
            )

            RethinkBottomSheetCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Dimensions.spacingSmMd)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_proxy_add_apps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
                        colors =
                            TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                    )

                    RethinkSegmentedChoiceRow(
                        options = TopLevelFilter.entries,
                        selectedOption = selectedFilter,
                        onOptionSelected = { selectedFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        fillEqually = true,
                        label = { filter, _ ->
                            Text(
                                text = stringResource(filter.getLabelId()),
                                maxLines = 1
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.lbl_select_all),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = selectAllChecked,
                            onCheckedChange = { checked -> pendingSelectAll = checked }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Dimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
            ) {
                items(count = apps.itemCount) { index ->
                    val item = apps[index] ?: return@items
                    IncludeAppRow(
                        mapping = item,
                        proxyId = proxyId,
                        proxyName = proxyName,
                        onInterfaceUpdate = { mapping, include ->
                            updateInterfaceDetails(mapping, include)
                        }
                    )
                }
            }
        }
    }

    pendingSelectAll?.let { toAdd ->
        val (title, message, positiveText) =
            if (toAdd) {
                Triple(
                    stringResource(R.string.include_all_app_wg_dialog_title),
                    stringResource(R.string.include_all_app_wg_dialog_desc),
                    stringResource(R.string.lbl_include)
                )
            } else {
                Triple(
                    stringResource(R.string.exclude_all_app_wg_dialog_title),
                    stringResource(R.string.exclude_all_app_wg_dialog_desc),
                    stringResource(R.string.exclude)
                )
            }

        WgConfirmDialog(
            title = title,
            message = message,
            confirmText = positiveText,
            isConfirmDestructive = !toAdd,
            onDismiss = { pendingSelectAll = null },
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    if (toAdd) {
                        Napier.i("Adding all apps to proxy $proxyId, $proxyName")
                        ProxyManager.setProxyIdForAllApps(proxyId, proxyName)
                    } else {
                        Napier.i("Removing all apps from proxy $proxyId, $proxyName")
                        ProxyManager.setNoProxyForAllApps()
                    }
                }
                selectAllChecked = toAdd
                pendingSelectAll = null
            }
        )
    }

    if (showRemainingDialog) {
        WgConfirmDialog(
            title = stringResource(R.string.remaining_apps_dialog_title),
            message = stringResource(R.string.remaining_apps_dialog_desc),
            confirmText = stringResource(R.string.lbl_include),
            isConfirmDestructive = false,
            onDismiss = { showRemainingDialog = false },
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    Napier.i("Adding remaining apps to proxy $proxyId, $proxyName")
                    ProxyManager.setProxyIdForUnselectedApps(proxyId, proxyName)
                }
                showRemainingDialog = false
            }
        )
    }
}

private object RefreshDatabaseProvider : KoinComponent {
    val refreshDatabase: RefreshDatabase by inject()

    fun get(): RefreshDatabase = refreshDatabase
}

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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgIncludeAppsAdapter
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Composable
fun WgIncludeAppsDialog(
    adapter: WgIncludeAppsAdapter,
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            WgIncludeAppsDialogScreen(
                adapter = adapter,
                viewModel = viewModel,
                proxyId = proxyId,
                proxyName = proxyName,
                onDismiss = onDismiss
            )
        }
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
private fun WgIncludeAppsDialogScreen(
    adapter: WgIncludeAppsAdapter,
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val refreshDatabase = remember { RefreshDatabaseProvider.get() }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TopLevelFilter.ALL_APPS) }
    var selectAllChecked by remember { mutableStateOf(false) }
    var pendingSelectAll by remember { mutableStateOf<Boolean?>(null) }
    var showRemainingDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val appCount =
        viewModel.getAppCountById(proxyId).asFlow().collectAsState(initial = 0).value
    val apps = viewModel.apps.asFlow().collectAsLazyPagingItems()
    var isDialogVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { isDialogVisible = false }
    }

    LaunchedEffect(query, selectedFilter) {
        viewModel.setFilter(query, selectedFilter, proxyId)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = context.getString(R.string.add_remove_apps, appCount.toString()),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            adapter.IncludeDialogHost()
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_proxy_add_apps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                    IconButton(
                        onClick = {
                            if (isRefreshing) return@IconButton
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
                                        context.getString(R.string.refresh_complete),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        val transition = rememberInfiniteTransition(label = "wgRefresh")
                        val rotation by
                            transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(750, easing = LinearEasing)
                                    ),
                                label = "wgRefreshRotation"
                            )
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopLevelFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(text = stringResource(filter.getLabelId())) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lbl_select_all),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = selectAllChecked,
                    onCheckedChange = { checked ->
                        pendingSelectAll = checked
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(count = apps.itemCount) { index ->
                    val item = apps[index] ?: return@items
                    adapter.IncludeAppRow(item)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { showRemainingDialog = true }) {
                    Text(text = stringResource(R.string.lbl_remaining_apps))
                }
                Button(
                    onClick = {
                        viewModel.setFilter("", TopLevelFilter.ALL_APPS, proxyId)
                        onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.ada_noapp_dialog_positive))
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

        AlertDialog(
            onDismissRequest = { pendingSelectAll = null },
            title = { Text(text = title) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(
                    onClick = {
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
                ) {
                    Text(text = positiveText)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSelectAll = null }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    if (showRemainingDialog) {
        AlertDialog(
            onDismissRequest = { showRemainingDialog = false },
            title = { Text(text = stringResource(R.string.remaining_apps_dialog_title)) },
            text = { Text(text = stringResource(R.string.remaining_apps_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            Napier.i("Adding remaining apps to proxy $proxyId, $proxyName")
                            ProxyManager.setProxyIdForUnselectedApps(proxyId, proxyName)
                        }
                        showRemainingDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_include))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemainingDialog = false }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }
}

private object RefreshDatabaseProvider : KoinComponent {
    val refreshDatabase: RefreshDatabase by inject()

    fun get(): RefreshDatabase = refreshDatabase
}

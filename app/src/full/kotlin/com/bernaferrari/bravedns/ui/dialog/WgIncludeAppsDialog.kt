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
package com.bernaferrari.bravedns.ui.dialog

import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.IncludeAppRow
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.database.ProxyApplicationMapping
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkIncludeAppsFilter
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkIncludeAppsPicker
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkIncludeAppsPickerItem
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkIncludeAppsPickerStrings
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialog
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.ProxyAppsFilter
import com.bernaferrari.bravedns.viewmodel.ProxyAppsMappingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

@Composable
fun WgIncludeAppsDialog(
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    RethinkWireguardDialog(onDismissRequest = onDismiss) {
        WgIncludeAppsDialogScreen(
            viewModel = viewModel,
            proxyId = proxyId,
            proxyName = proxyName,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun WgIncludeAppsScreen(
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit
) {
    WgIncludeAppsDialogScreen(
        viewModel = viewModel,
        proxyId = proxyId,
        proxyName = proxyName,
        onDismiss = onDismiss,
        inDialog = false
    )
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
    viewModel: ProxyAppsMappingViewModel,
    proxyId: String,
    proxyName: String,
    onDismiss: () -> Unit,
    inDialog: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshDatabase = remember { RefreshDatabaseProvider.get() }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TopLevelFilter.ALL_APPS) }
    val apps by viewModel.apps.collectAsState(initial = emptyList())
    val allApps by viewModel.allApps.collectAsState(initial = emptyList())
    var isDialogVisible by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var excludedUids by remember { mutableStateOf<Set<Int>>(emptySet()) }

    if (inDialog) {
        TransparentDialogSystemBars()
    } else {
        BackHandler(onBack = onDismiss)
    }

    fun updateInterfaceDetails(mapping: com.bernaferrari.bravedns.database.ProxyApplicationMapping, include: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (FirewallManager.isAppExcludedFromProxy(mapping.uid)) {
                withContext(Dispatchers.Main) {
                    Utilities.showToastUiCentered(
                        context,
                        context.getString(R.string.exclude_apps_from_proxy_failure_toast),
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }
            if (include) {
                ProxyManager.updateProxyIdForPackage(mapping.uid, mapping.packageName, proxyId, proxyName)
            } else {
                ProxyManager.setNoProxyForPackage(mapping.uid, mapping.packageName)
            }
        }
    }

    fun selectAllApps() {
        val appSnapshot = allApps.distinctBy { it.uid to it.packageName }
        val excludedSnapshot = excludedUids
        scope.launch(Dispatchers.IO) {
            // Apply selection in one DB/cache update so the UI reflects quickly.
            ProxyManager.setProxyIdForAllApps(proxyId, proxyName)

            // Keep excluded apps out of proxy routing.
            if (excludedSnapshot.isNotEmpty()) {
                appSnapshot
                    .asSequence()
                    .filter { excludedSnapshot.contains(it.uid) }
                    .forEach { mapping ->
                        ProxyManager.setNoProxyForPackage(mapping.uid, mapping.packageName)
                    }
            } else {
                appSnapshot.forEach { mapping ->
                    if (FirewallManager.isAppExcludedFromProxy(mapping.uid)) {
                        ProxyManager.setNoProxyForPackage(mapping.uid, mapping.packageName)
                    }
                }
            }
        }
    }

    fun unselectAllApps() {
        val appSnapshot = allApps.distinctBy { it.uid to it.packageName }
        scope.launch(Dispatchers.IO) {
            ProxyManager.removeProxyId(proxyId)
            // Sweep per app to clear any stale/legacy Orbot mappings missed by id-only bulk update.
            appSnapshot.forEach { mapping ->
                val isMappedToCurrentProxy =
                    mapping.proxyId.equals(proxyId, ignoreCase = true) ||
                        mapping.proxyName.equals(proxyName, ignoreCase = true)
                if (isMappedToCurrentProxy) {
                    ProxyManager.setNoProxyForPackage(mapping.uid, mapping.packageName)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { isDialogVisible = false }
    }

    LaunchedEffect(query, selectedFilter) {
        viewModel.setFilter(query, selectedFilter.toProxyAppsFilter(), proxyId)
    }

    LaunchedEffect(allApps) {
        val snapshot = allApps
        excludedUids =
            withContext(Dispatchers.IO) {
                val excluded = mutableSetOf<Int>()
                snapshot.forEach { mapping ->
                    if (FirewallManager.isAppExcludedFromProxy(mapping.uid)) {
                        excluded.add(mapping.uid)
                    }
                }
                excluded
            }
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
                    context.getString(R.string.refresh_complete),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    val allAppsSelected =
        allApps.isNotEmpty() &&
            allApps.all { mapping ->
                excludedUids.contains(mapping.uid) ||
                    mapping.proxyId.equals(proxyId, ignoreCase = true) ||
                    mapping.proxyName.equals(proxyName, ignoreCase = true)
            }

    val mappingsById = remember(apps) { apps.associateBy { "${it.uid}:${it.packageName}" } }
    RethinkIncludeAppsPicker(
        title = proxyName,
        items = apps.map { mapping ->
            RethinkIncludeAppsPickerItem(
                id = "${mapping.uid}:${mapping.packageName}",
                title = mapping.appName.ifBlank { mapping.packageName },
                sectionKey = appInitial(mapping.appName, mapping.packageName),
            )
        },
        query = query,
        selectedFilter = if (selectedFilter == TopLevelFilter.SELECTED_APPS) RethinkIncludeAppsFilter.Selected else RethinkIncludeAppsFilter.All,
        allItemsSelected = allAppsSelected,
        isRefreshing = isRefreshing,
        strings = RethinkIncludeAppsPickerStrings(
            search = stringResource(R.string.search_proxy_add_apps),
            clearSearch = stringResource(R.string.cd_clear_search),
            all = stringResource(R.string.lbl_all),
            selected = stringResource(R.string.rt_filter_parent_selected),
            refresh = stringResource(R.string.cd_refresh),
            loading = stringResource(R.string.lbl_loading),
            selectAll = stringResource(R.string.lbl_select_all),
            unselectAll = stringResource(R.string.lbl_unselect_all),
            done = stringResource(R.string.lbl_done),
            empty = stringResource(R.string.fapps_empty_subtitle),
            more = stringResource(R.string.cd_more),
        ),
        onBackClick = onDismiss,
        onQueryChange = { query = it },
        onFilterChange = { filter ->
            selectedFilter = if (filter == RethinkIncludeAppsFilter.Selected) TopLevelFilter.SELECTED_APPS else TopLevelFilter.ALL_APPS
        },
        onRefresh = ::refreshApps,
        onToggleAll = { if (allAppsSelected) unselectAllApps() else selectAllApps() },
        onDone = onDismiss,
        itemContent = { item, position ->
            mappingsById[item.id]?.let { mapping ->
                IncludeAppRow(
                    mapping = mapping,
                    proxyId = proxyId,
                    position = position,
                    onInterfaceUpdate = ::updateInterfaceDetails,
                )
            }
        },
    )
}

private fun TopLevelFilter.toProxyAppsFilter(): ProxyAppsFilter = when (this) {
    TopLevelFilter.ALL_APPS -> ProxyAppsFilter.All
    TopLevelFilter.SELECTED_APPS -> ProxyAppsFilter.Selected
    TopLevelFilter.UNSELECTED_APPS -> ProxyAppsFilter.Unselected
}

@Composable
private fun TransparentDialogSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val originalNavBarColor = window.navigationBarColor
            val originalNavBarDividerColor =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor
                } else {
                    null
                }
            val originalContrastEnforced =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced
                } else {
                    null
                }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = AndroidColor.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.navigationBarColor = originalNavBarColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalNavBarDividerColor != null) {
                    window.navigationBarDividerColor = originalNavBarDividerColor
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalContrastEnforced != null) {
                    window.isNavigationBarContrastEnforced = originalContrastEnforced
                }
            }
        } else {
            onDispose {}
        }
    }
}

private object RefreshDatabaseProvider : KoinComponent {
    val refreshDatabase: RefreshDatabase by inject()

    fun get(): RefreshDatabase = refreshDatabase
}

private fun appInitial(appName: String, packageName: String): String {
    val source = appName.ifBlank { packageName }.trim()
    if (source.isEmpty()) return "#"
    val first = source.first()
    return if (first.isLetter()) {
        first.uppercaseChar().toString()
    } else {
        source.first().toString().uppercase(Locale.getDefault())
    }
}

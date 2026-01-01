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

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WgIncludeAppsDialog(
    private var activity: Activity,
    internal var adapter: com.celzero.bravedns.adapter.WgIncludeAppsAdapter,
    var viewModel: ProxyAppsMappingViewModel,
    themeID: Int,
    private val proxyId: String,
    private val proxyName: String
) : Dialog(activity, themeID), KoinComponent {

    private val refreshDatabase by inject<RefreshDatabase>()
    private var filterType: TopLevelFilter = TopLevelFilter.ALL_APPS
    private var searchText = ""

    companion object {
        private const val REFRESH_TIMEOUT: Long = 4000
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    RethinkTheme {
                        WgIncludeAppsDialogScreen()
                    }
                }
            }
        )
        setCancelable(false)
        initializeValues()
    }

    private fun initializeValues() {
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun refreshDatabase() {
        io { refreshDatabase.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE) }
    }

    private fun clearSearch() {
        viewModel.setFilter("", TopLevelFilter.ALL_APPS, proxyId)
    }

    private fun showDialog(toAdd: Boolean, onCancel: () -> Unit) {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        if (toAdd) {
            builder.setTitle(context.getString(R.string.include_all_app_wg_dialog_title))
            builder.setMessage(context.getString(R.string.include_all_app_wg_dialog_desc))
        } else {
            builder.setTitle(context.getString(R.string.exclude_all_app_wg_dialog_title))
            builder.setMessage(context.getString(R.string.exclude_all_app_wg_dialog_desc))
        }
        builder.setCancelable(true)
        builder.setPositiveButton(
            if (toAdd) context.getString(R.string.lbl_include)
            else context.getString(R.string.exclude)
        ) { _, _ ->
            // add all if the list is empty or remove all if the list is full
            io {
                if (toAdd) {
                    Napier.i("Adding all apps to proxy $proxyId, $proxyName")
                    ProxyManager.setProxyIdForAllApps(proxyId, proxyName)
                } else {
                    Napier.i("Removing all apps from proxy $proxyId, $proxyName")
                    ProxyManager.setNoProxyForAllApps()
                }
            }
        }

        builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ ->
            onCancel()
        }

        builder.create().show()
    }

    private fun showConfirmationDialog() {
        val builder = MaterialAlertDialogBuilder(context, R.style.App_Dialog_NoDim)
        builder.setTitle(context.getString(R.string.remaining_apps_dialog_title))
        builder.setMessage(context.getString(R.string.remaining_apps_dialog_desc))
        builder.setCancelable(true)
        builder.setPositiveButton(context.getString(R.string.lbl_include)) { _, _ ->
            io {
                Napier.i("Adding remaining apps to proxy $proxyId, $proxyName")
                ProxyManager.setProxyIdForUnselectedApps(proxyId, proxyName)
            }
        }

        builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ ->
            // no-op
        }

        builder.create().show()
    }

    private fun io(f: suspend () -> Unit) {
        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    @Composable
    private fun WgIncludeAppsDialogScreen() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf(searchText) }
        var selectedFilter by remember { mutableStateOf(filterType) }
        var selectAllChecked by remember { mutableStateOf(false) }
        var isRefreshing by remember { mutableStateOf(false) }
        val appCount =
            viewModel.getAppCountById(proxyId).asFlow().collectAsState(initial = 0).value
        val apps = viewModel.apps.asFlow().collectAsLazyPagingItems()

        LaunchedEffect(query, selectedFilter) {
            searchText = query
            filterType = selectedFilter
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
                                refreshDatabase()
                                scope.launch {
                                    delay(REFRESH_TIMEOUT)
                                    if (this@WgIncludeAppsDialog.isShowing) {
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
                            selectAllChecked = checked
                            showDialog(checked) { selectAllChecked = !checked }
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
                    Button(onClick = { showConfirmationDialog() }) {
                        Text(text = stringResource(R.string.lbl_remaining_apps))
                    }
                    Button(
                        onClick = {
                            clearSearch()
                            dismiss()
                        }
                    ) {
                        Text(text = stringResource(R.string.ada_noapp_dialog_positive))
                    }
                }
            }
        }
    }
}

/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.logs

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.AppWiseIpsAdapter
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.bottomsheet.AppIpRulesSheet
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWiseIpLogsScreen(
    uid: Int,
    isAsn: Boolean,
    viewModel: AppConnectionsViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var appInfo by remember { mutableStateOf<AppInfo?>(null) }
    var searchHint by remember { mutableStateOf("") }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    val showDeleteIcon = remember { !isAsn }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
    
    val isRethinkApp = remember(uid) { 
        Utilities.getApplicationInfo(context, context.packageName)?.uid == uid 
    }

    LaunchedEffect(uid) {
        if (uid == INVALID_UID) {
            onBackClick?.invoke()
            return@LaunchedEffect
        }

        viewModel.timeCategoryChanged(selectedCategory, isDomain = false)
        withContext(Dispatchers.IO) {
            val info = FirewallManager.getAppInfoByUid(uid)
            if (info == null) {
                withContext(Dispatchers.Main) { onBackClick?.invoke() }
                return@withContext
            }
            
            val packages = FirewallManager.getPackageNamesByUid(info.uid)
            val count = packages.count()
            val appName = if (count >= 2) {
                context.getString(R.string.ctbs_app_other_apps, info.appName, (count - 1).toString())
            } else {
                info.appName
            }
            
            val appNameTruncated = appName.substring(0, appName.length.coerceAtMost(10))
            val hint = if (isAsn) {
                val txt = context.getString(
                    R.string.two_argument_space,
                    context.getString(R.string.lbl_search),
                    context.getString(R.string.lbl_service_providers)
                )
                context.getString(R.string.two_argument_colon, appNameTruncated, txt)
            } else {
                context.getString(
                    R.string.two_argument_colon,
                    appNameTruncated,
                    context.getString(R.string.search_universal_ips)
                )
            }

            val icon = Utilities.getIcon(context, info.packageName, info.appName)

            withContext(Dispatchers.Main) {
                appInfo = info
                searchHint = hint
                appIcon = icon
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = appInfo?.appName ?: "") },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = stringResource(R.string.ada_delete_logs_dialog_title)) },
                text = { Text(text = stringResource(R.string.ada_delete_logs_dialog_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteLogs(uid)
                        }
                    ) {
                        Text(text = stringResource(R.string.lbl_proceed))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(R.string.lbl_cancel))
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ToggleRow(
                selectedCategory = selectedCategory,
                onCategoryChange = { category ->
                    selectedCategory = category
                    viewModel.timeCategoryChanged(category, isDomain = false)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            HeaderRow(
                viewModel = viewModel,
                isAsn = isAsn,
                searchHint = searchHint,
                appIcon = appIcon,
                showDeleteIcon = showDeleteIcon,
                onDeleteClick = { showDeleteDialog = true }
            )
            
            AppWiseIpList(
                viewModel = viewModel,
                uid = uid,
                isAsn = isAsn,
                isRethinkApp = isRethinkApp,
                eventLogger = eventLogger
            )
        }
    }
}

@Composable
private fun ToggleRow(
    selectedCategory: AppConnectionsViewModel.TimeCategory,
    onCategoryChange: (AppConnectionsViewModel.TimeCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleButton(
            label = stringResource(R.string.ci_desc, "1", stringResource(R.string.lbl_hour)),
            selected = selectedCategory == AppConnectionsViewModel.TimeCategory.ONE_HOUR,
            onClick = { onCategoryChange(AppConnectionsViewModel.TimeCategory.ONE_HOUR) }
        )
        ToggleButton(
            label = stringResource(R.string.ci_desc, "24", stringResource(R.string.lbl_hour)),
            selected = selectedCategory == AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR,
            onClick = { onCategoryChange(AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR) }
        )
        ToggleButton(
            label = stringResource(R.string.ci_desc, "7", stringResource(R.string.lbl_day)),
            selected = selectedCategory == AppConnectionsViewModel.TimeCategory.SEVEN_DAYS,
            onClick = { onCategoryChange(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
        )
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val background =
        if (selected) {
            Color(UIUtils.fetchToggleBtnColors(context, R.color.accentGood))
        } else {
            Color(UIUtils.fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
        }
    val content =
        if (selected) {
            Color(UIUtils.fetchColor(context, R.attr.homeScreenHeaderTextColor))
        } else {
            Color(UIUtils.fetchColor(context, R.attr.primaryTextColor))
        }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = content
        )
    ) {
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun HeaderRow(
    viewModel: AppConnectionsViewModel,
    isAsn: Boolean,
    searchHint: String,
    appIcon: Drawable?,
    showDeleteIcon: Boolean,
    onDeleteClick: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(1000)
            .distinctUntilChanged()
            .collect { value ->
                val type =
                    if (isAsn) {
                        AppConnectionsViewModel.FilterType.ASN
                    } else {
                        AppConnectionsViewModel.FilterType.IP
                    }
                viewModel.setFilter(value, type)
            }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconDrawable = appIcon ?: Utilities.getDefaultIcon(LocalContext.current)
        val bitmap = remember(iconDrawable) {
            iconDrawable?.toBitmap(width = 48, height = 48)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isAsn,
            label = { Text(text = searchHint.ifEmpty { stringResource(R.string.search_universal_ips) }) }
        )

        if (showDeleteIcon) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AppWiseIpList(
    viewModel: AppConnectionsViewModel,
    uid: Int,
    isAsn: Boolean,
    isRethinkApp: Boolean,
    eventLogger: EventLogger
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showIpRulesSheet by remember { mutableStateOf(false) }
    var selectedIp by remember { mutableStateOf("") }
    var selectedDomains by remember { mutableStateOf("") }
    
    val adapter = remember {
        AppWiseIpsAdapter(
            context,
            lifecycleOwner,
            uid,
            isAsn,
            onShowIpRules = { ip, domains ->
                selectedIp = ip
                selectedDomains = domains
                showIpRulesSheet = true
            }
        )
    }

    if (showIpRulesSheet && selectedIp.isNotEmpty()) {
        AppIpRulesSheet(
            uid = uid,
            ipAddress = selectedIp,
            domains = selectedDomains,
            eventLogger = eventLogger,
            onDismiss = { showIpRulesSheet = false },
            onUpdated = { adapter.notifyRulesChanged() }
        )
    }

    LaunchedEffect(uid, isRethinkApp) {
        if (!isRethinkApp) {
            viewModel.setUid(uid)
        }
    }

    // Need to collect valid flow based on type
    // In Activity:
    // if (isRethinkApp) viewModel.rinrIpLogs else if (isAsn) viewModel.asnLogs else viewModel.appIpLogs
    
    val flow = remember(isRethinkApp, isAsn) {
        if (isRethinkApp) {
            viewModel.rinrIpLogs
        } else if (isAsn) {
            viewModel.asnLogs
        } else {
            viewModel.appIpLogs
        }
    }
    
    val items = flow.asFlow().collectAsLazyPagingItems()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            adapter.IpRow(item)
        }
    }
}

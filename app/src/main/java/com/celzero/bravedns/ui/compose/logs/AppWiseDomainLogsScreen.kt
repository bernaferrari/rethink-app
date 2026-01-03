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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.paging.compose.LazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.CloseConnsDialog
import com.celzero.bravedns.adapter.DomainRow
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.ui.bottomsheet.AppDomainRulesSheet
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

data class AppWiseDomainLogsState(
    val uid: Int,
    val isActiveConns: Boolean,
    val isRethinkApp: Boolean,
    val searchHint: String,
    val appIcon: Drawable?,
    val showToggleGroup: Boolean,
    val showDeleteIcon: Boolean,
    val selectedCategory: AppConnectionsViewModel.TimeCategory
)

@Composable
fun AppWiseDomainLogsScreen(
    state: AppWiseDomainLogsState,
    items: LazyPagingItems<AppConnection>,
    eventLogger: EventLogger,
    onTimeCategoryChange: (AppConnectionsViewModel.TimeCategory) -> Unit,
    onFilterChange: (String) -> Unit,
    onDeleteLogs: () -> Unit,
    defaultIcon: Drawable?
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.showToggleGroup) {
            ToggleRow(
                selectedCategory = state.selectedCategory,
                onCategorySelected = onTimeCategoryChange
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        HeaderRow(
            appIcon = state.appIcon ?: defaultIcon,
            searchHint = state.searchHint,
            showDeleteIcon = state.showDeleteIcon,
            onDeleteClick = { showDeleteDialog = true },
            onQueryChange = onFilterChange
        )
        AppWiseDomainList(
            items = items,
            uid = state.uid,
            eventLogger = eventLogger
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.ada_delete_logs_dialog_title)) },
            text = { Text(text = stringResource(R.string.ada_delete_logs_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteLogs()
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
}

@Composable
private fun ToggleRow(
    selectedCategory: AppConnectionsViewModel.TimeCategory,
    onCategorySelected: (AppConnectionsViewModel.TimeCategory) -> Unit
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
            onClick = { onCategorySelected(AppConnectionsViewModel.TimeCategory.ONE_HOUR) }
        )
        ToggleButton(
            label = stringResource(R.string.ci_desc, "24", stringResource(R.string.lbl_hour)),
            selected = selectedCategory == AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR,
            onClick = { onCategorySelected(AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR) }
        )
        ToggleButton(
            label = stringResource(R.string.ci_desc, "7", stringResource(R.string.lbl_day)),
            selected = selectedCategory == AppConnectionsViewModel.TimeCategory.SEVEN_DAYS,
            onClick = { onCategorySelected(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
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
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
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
    appIcon: Drawable?,
    searchHint: String,
    showDeleteIcon: Boolean,
    onDeleteClick: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(1000L)
            .distinctUntilChanged()
            .collect { value -> onQueryChange(value) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = remember(appIcon) {
            appIcon?.toBitmap(width = 48, height = 48)
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
            label = { Text(text = searchHint.ifEmpty { stringResource(R.string.search_custom_domains) }) }
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
private fun AppWiseDomainList(
    items: LazyPagingItems<AppConnection>,
    uid: Int,
    eventLogger: EventLogger
) {
    var showDomainRulesSheet by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf(0) }
    var pendingCloseDialog by remember { mutableStateOf<AppConnection?>(null) }
    
    val context = LocalContext.current
    
    pendingCloseDialog?.let { conn ->
        CloseConnsDialog(
            conn = conn,
            onConfirm = { pendingCloseDialog = null },
            onDismiss = { pendingCloseDialog = null }
        )
    }

    if (showDomainRulesSheet && selectedDomain.isNotEmpty()) {
        AppDomainRulesSheet(
            uid = uid,
            domain = selectedDomain,
            eventLogger = eventLogger,
            onDismiss = { showDomainRulesSheet = false },
            onUpdated = { refreshToken++ }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            DomainRow(
                conn = item,
                uid = uid,
                isActiveConn = false, // TODO: check if this should be dynamic
                refreshToken = refreshToken,
                onIpClick = { conn ->
                    selectedDomain = conn.appOrDnsName.orEmpty()
                    if (selectedDomain.isNotEmpty()) {
                        showDomainRulesSheet = true
                    }
                }
            )
        }
    }
}

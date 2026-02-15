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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.celzero.bravedns.ui.compose.theme.Dimensions
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(bottom = Dimensions.spacingMd)
        ) {
            Column(modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)) {
                if (state.showToggleGroup) {
                    ToggleRow(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = onTimeCategoryChange
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                }
                HeaderRow(
                    appIcon = state.appIcon ?: defaultIcon,
                    searchHint = state.searchHint,
                    showDeleteIcon = state.showDeleteIcon,
                    onDeleteClick = { showDeleteDialog = true },
                    onQueryChange = onFilterChange
                )
            }
        }
        
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
            .padding(vertical = Dimensions.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            AppConnectionsViewModel.TimeCategory.ONE_HOUR to stringResource(R.string.ci_desc, "1", stringResource(R.string.lbl_hour)),
            AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR to stringResource(R.string.ci_desc, "24", stringResource(R.string.lbl_hour)),
            AppConnectionsViewModel.TimeCategory.SEVEN_DAYS to stringResource(R.string.ci_desc, "7", stringResource(R.string.lbl_day))
        ).forEach { (category, label) ->
            ToggleButton(
                label = label,
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(Dimensions.buttonCornerRadiusLarge)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val clearSearchContentDescription = stringResource(R.string.cd_clear_search)
    val deleteContentDescription = stringResource(R.string.lbl_delete)
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(500L)
            .distinctUntilChanged()
            .collect { value -> onQueryChange(value) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(Dimensions.spacingSm)
                    .size(Dimensions.iconSizeMd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(appIcon) {
                    appIcon?.toBitmap(width = 48, height = 48)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = searchHint.ifEmpty { stringResource(R.string.search_custom_domains) }, style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = clearSearchContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.iconSizeSm)
                    )
                }
            }

            if (showDeleteIcon) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = deleteContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.iconSizeMd)
                    )
                }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimensions.screenPaddingHorizontal, vertical = Dimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
    ) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            DomainRow(
                conn = item,
                uid = uid,
                isActiveConn = false,
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

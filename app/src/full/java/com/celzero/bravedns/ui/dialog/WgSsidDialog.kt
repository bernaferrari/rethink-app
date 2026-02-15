/*
 * Copyright 2025 RethinkDNS and its authors
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.celzero.bravedns.R
import com.celzero.bravedns.data.SsidItem
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities

@Composable
fun WgSsidDialog(
    currentSsids: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SsidDialogContent(
            currentSsids = currentSsids,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun SsidDialogContent(
    currentSsids: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ssidItems = remember {
        mutableStateListOf<SsidItem>().apply {
            addAll(SsidItem.parseStorageList(currentSsids))
        }
    }
    var ssidInput by remember { mutableStateOf("") }
    var isEqual by remember { mutableStateOf(true) }
    var isExact by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SsidItem?>(null) }

    val canEdit = ssidInput.isNotBlank()
    val pauseTxt =
        context.resources.getString(R.string.notification_action_pause_vpn).lowercase()
            .replaceFirstChar { it.uppercase() }
    val connectTxt =
        context.resources.getString(R.string.lbl_connect).lowercase()
            .replaceFirstChar { it.uppercase() }
    val firstArg = if (isEqual) connectTxt else pauseTxt
    val secArg = context.resources.getString(R.string.lbl_ssid)
    val exactMatchTxt = context.resources.getString(R.string.wg_ssid_type_exact).lowercase()
    val partialMatchTxt = context.resources.getString(R.string.wg_ssid_type_wildcard).lowercase()
    val thirdArg = if (isExact) exactMatchTxt else partialMatchTxt
    val description = context.resources.getString(R.string.wg_ssid_dialog_description, firstArg, secArg, thirdArg)

    if (deleteTarget != null) {
        val item = deleteTarget ?: return
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.lbl_delete)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.two_argument_space,
                        stringResource(R.string.lbl_delete),
                        item.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ssidItems.remove(item)
                        deleteTarget = null
                    }
                ) {
                    Text(text = stringResource(R.string.lbl_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.lbl_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.wg_setting_ssid_title), style = MaterialTheme.typography.titleLarge)
        Text(text = description, style = MaterialTheme.typography.bodyMedium)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ssidItems, key = { it.name + it.type.id }) { item ->
                SsidRow(ssidItem = item, onDeleteClick = { deleteTarget = item })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.lbl_action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    RadioButton(
                        selected = isEqual,
                        onClick = { if (canEdit) isEqual = true },
                        enabled = canEdit
                    )
                    Text(
                        text = stringResource(R.string.lbl_connect),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Row {
                    RadioButton(
                        selected = !isEqual,
                        onClick = { if (canEdit) isEqual = false },
                        enabled = canEdit
                    )
                    Text(text = pauseTxt, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.lbl_criteria),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    RadioButton(
                        selected = isExact,
                        onClick = { if (canEdit) isExact = true },
                        enabled = canEdit
                    )
                    Text(
                        text = stringResource(R.string.wg_ssid_type_exact),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Row {
                    RadioButton(
                        selected = !isExact,
                        onClick = { if (canEdit) isExact = false },
                        enabled = canEdit
                    )
                    Text(
                        text = stringResource(R.string.wg_ssid_type_wildcard),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.lbl_ssid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ssidInput,
                onValueChange = { ssidInput = it },
                placeholder = { Text(text = stringResource(R.string.lbl_ssid)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    addSsid(
                        context = context,
                        ssidInput = ssidInput,
                        isEqual = isEqual,
                        isExact = isExact,
                        items = ssidItems,
                        onReset = {
                            ssidInput = ""
                            isEqual = true
                            isExact = false
                        }
                    )
                },
                enabled = canEdit
            ) {
                Text(
                    text = stringResource(R.string.lbl_add),
                    color = if (canEdit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    val finalSsids = SsidItem.toStorageList(ssidItems.toList())
                    onSave(finalSsids)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.fapps_info_dialog_positive_btn))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.lbl_cancel))
            }
        }
    }
}

@Composable
private fun SsidRow(ssidItem: SsidItem, onDeleteClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = ssidItem.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = ssidItem.type.getDisplayName(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        IconButton(onClick = onDeleteClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(R.string.lbl_delete)
            )
        }
    }
}

private fun isValidSsidName(ssidName: String): Boolean {
    return ssidName.length <= 32 && ssidName.isNotBlank()
}

private fun addSsid(
    context: android.content.Context,
    ssidInput: String,
    isEqual: Boolean,
    isExact: Boolean,
    items: MutableList<SsidItem>,
    onReset: () -> Unit
) {
    val ssidName = ssidInput.trim()
    if (ssidName.isBlank()) {
        Utilities.showToastUiCentered(
            context,
            context.resources.getString(R.string.wg_ssid_invalid_error, context.resources.getString(R.string.lbl_ssids)),
            Toast.LENGTH_SHORT
        )
        return
    }

    if (!isValidSsidName(ssidName)) {
        Utilities.showToastUiCentered(
            context,
            context.resources.getString(R.string.config_add_success_toast),
            Toast.LENGTH_SHORT
        )
        return
    }

    val selectedType = when {
        isEqual && isExact -> SsidItem.SsidType.EQUAL_EXACT
        isEqual && !isExact -> SsidItem.SsidType.EQUAL_WILDCARD
        !isEqual && isExact -> SsidItem.SsidType.NOTEQUAL_EXACT
        else -> SsidItem.SsidType.NOTEQUAL_WILDCARD
    }

    val existingWithSameType =
        items.find { it.name.equals(ssidName, ignoreCase = true) && it.type == selectedType }
    if (existingWithSameType != null) {
        onReset()
        return
    }

    val existingWithDifferentType =
        items.find { it.name.equals(ssidName, ignoreCase = true) && it.type != selectedType }
    if (existingWithDifferentType != null) {
        items.remove(existingWithDifferentType)
    }

    items.add(SsidItem(ssidName, selectedType))
    onReset()
}

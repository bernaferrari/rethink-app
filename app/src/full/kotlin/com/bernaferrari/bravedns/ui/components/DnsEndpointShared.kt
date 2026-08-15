/*
Copyright 2026 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.bernaferrari.bravedns.ui.components

import Logger
import Logger.LOG_TAG_UI

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.util.UIUtils.clipboardCopy
import com.bernaferrari.bravedns.util.UIUtils.getDnsStatusStringRes
import com.bernaferrari.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DnsInfoDialogModel(
    val title: String,
    val message: String,
    val copyValue: String? = null,
    val copyToastRes: Int = R.string.info_dialog_url_copy_toast_msg,
    val confirmTextRes: Int = R.string.dns_info_positive,
    val copyTextRes: Int = R.string.dns_info_neutral
)

internal data class DnsDeleteDialogModel(
    val id: Int,
    val titleRes: Int,
    val messageRes: Int,
    val successToast: String
)

@Composable
internal fun DnsInfoDialog(
    model: DnsInfoDialogModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val copyClipboardLabel = stringResource(R.string.copy_clipboard_label)
    val copyToast = stringResource(model.copyToastRes)
    RethinkMultiActionDialog(
        onDismissRequest = onDismiss,
        title = model.title,
        message = model.message,
        primaryText = stringResource(model.confirmTextRes),
        onPrimary = onDismiss,
        secondaryText =
            if (model.copyValue.isNullOrEmpty()) {
                null
            } else {
                stringResource(model.copyTextRes)
            },
        onSecondary = {
            val copyValue = model.copyValue
            if (copyValue.isNullOrEmpty()) return@RethinkMultiActionDialog
            clipboardCopy(
                context,
                copyValue,
                copyClipboardLabel
            )
            Utilities.showToastUiCentered(
                context,
                copyToast,
                Toast.LENGTH_SHORT
            )
        }
    )
}

@Composable
internal fun DnsDeleteDialog(
    model: DnsDeleteDialogModel,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = stringResource(model.titleRes),
        message = stringResource(model.messageRes),
        confirmText = stringResource(R.string.lbl_delete),
        dismissText = stringResource(R.string.lbl_cancel),
        isConfirmDestructive = true,
        onConfirm = { onConfirm(model.id) },
        onDismiss = onDismiss
    )
}

@Composable
internal fun rememberDnsStatusStrings(): Map<Int, String> {
    val failedUsingDefault = stringResource(R.string.failed_using_default)
    val starting = stringResource(R.string.lbl_starting)
    val connected = stringResource(R.string.dns_connected)
    val noInternet = stringResource(R.string.status_no_internet)
    val serverDown = stringResource(R.string.status_dns_server_down)
    val dnsError = stringResource(R.string.status_dns_error)
    val failing = stringResource(R.string.status_failing)
    val selected = stringResource(R.string.rt_filter_parent_selected)
    return remember(
        failedUsingDefault,
        starting,
        connected,
        noInternet,
        serverDown,
        dnsError,
        failing,
        selected
    ) {
        mapOf(
            R.string.failed_using_default to failedUsingDefault,
            R.string.lbl_starting to starting,
            R.string.dns_connected to connected,
            R.string.status_no_internet to noInternet,
            R.string.status_dns_server_down to serverDown,
            R.string.status_dns_error to dnsError,
            R.string.status_failing to failing,
            R.string.rt_filter_parent_selected to selected
        )
    }
}

@Composable
internal fun rememberDnsStatusExplanation(
    key: Any,
    isSelected: Boolean,
    smartDnsEnabled: Boolean,
    tag: String,
    pollIntervalMs: Long = 1000L,
    requireTunnel: Boolean = true,
    selectedFallbackText: String? = stringResource(R.string.rt_filter_parent_selected),
    statusTextMapper: ((Int) -> String)? = null
): String {
    val statusStrings = rememberDnsStatusStrings()
    val mapper =
        statusTextMapper
            ?: remember(statusStrings) {
                { statusRes: Int ->
                    (statusStrings[statusRes]
                            ?: statusStrings.getValue(R.string.rt_filter_parent_selected))
                        .replaceFirstChar(Char::titlecase)
                }
            }
    var explanation by remember(key) { mutableStateOf("") }

    LaunchedEffect(key, isSelected, smartDnsEnabled, selectedFallbackText, mapper) {
        if (isSelected && !smartDnsEnabled && (!requireTunnel || VpnController.hasTunnel())) {
            while (isActive) {
                val status =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val state = VpnController.getDnsStatus(Backend.Preferred)
                            getDnsStatusStringRes(state)
                        }
                    }.getOrElse {
                        Logger.e(LOG_TAG_UI, "$tag failed to read dns status", Logger.throwableToException(it))
                        R.string.rt_filter_parent_selected
                    }
                explanation = mapper(status)
                delay(pollIntervalMs)
            }
        } else if (isSelected && selectedFallbackText != null) {
            explanation = selectedFallbackText
        } else {
            explanation = ""
        }
    }

    return explanation
}

@Composable
internal fun resolveDnsDescriptionText(message: String?): String {
    if (message.isNullOrEmpty()) return ""
    if (!message.contains("R.string.")) return message
    val context = LocalContext.current
    // Stored endpoint text may embed "R.string.<name>"; look up the id dynamically,
    // then resolve via stringResource so this stays out of LocalContextGetResourceValueCall.
    val resId =
        remember(message, context.packageName) {
            runCatching {
                val key = message.substringAfter("R.string.")
                context.resources.getIdentifier(key, "string", context.packageName)
            }.getOrNull()
        }
    return when {
        resId == null -> ""
        resId == 0 -> message
        else -> stringResource(resId)
    }
}

internal fun launchDnsEndpointSelectionUpdate(
    scope: CoroutineScope,
    context: Context,
    tag: String,
    failingToast: String,
    apply: suspend () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        runCatching { apply() }.onFailure {
            Logger.e(LOG_TAG_UI, "$tag failed to update endpoint", Logger.throwableToException(it))
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    failingToast,
                    Toast.LENGTH_SHORT
                )
            }
        }
    }
}

internal fun launchDnsEndpointDelete(
    scope: CoroutineScope,
    context: Context,
    successToast: String,
    failingToast: String,
    apply: suspend () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        runCatching { apply() }.onSuccess {
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    successToast,
                    Toast.LENGTH_SHORT
                )
            }
        }.onFailure {
            Logger.e(LOG_TAG_UI, "dns endpoint delete failed", Logger.throwableToException(it))
            withContext(Dispatchers.Main) {
                Utilities.showToastUiCentered(
                    context,
                    failingToast,
                    Toast.LENGTH_SHORT
                )
            }
        }
    }
}

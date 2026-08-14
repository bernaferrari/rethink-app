/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.AppUpdater
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.util.UIUtils

@Composable
internal fun HomeConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    isConfirmDestructive: Boolean = false
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        message = message,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = onConfirm,
        onDismiss = onDismiss ?: onDismissRequest,
        isConfirmDestructive = isConfirmDestructive
    )
}

@Composable
internal fun HomeAlwaysOnStopDialog(
    title: String,
    message: String,
    stopText: String,
    openSettingsText: String,
    cancelText: String,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    // Destructive: stopping Always-on VPN. Scrim must not confirm the stop.
    RethinkMultiActionDialog(
        onDismissRequest = {},
        title = title,
        message = message,
        primaryText = stopText,
        onPrimary = onStop,
        secondaryText = openSettingsText,
        onSecondary = onOpenSettings,
        tertiaryText = cancelText,
        onTertiary = onCancel
    )
}

@Composable
internal fun HomeStatsDialog(
    title: String,
    displayText: String,
    dismissText: String,
    copyText: String,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    RethinkMultiActionDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = {
            SelectionContainer {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(Dimensions.spacingSm)
                ) {
                    Text(text = displayText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        primaryText = dismissText,
        onPrimary = onDismiss,
        secondaryText = copyText,
        onSecondary = onCopy
    )
}

@Composable
internal fun HomeNewFeaturesDialog(
    title: String,
    dismissText: String,
    contactText: String,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit,
    onContact: () -> Unit,
    content: @Composable () -> Unit
) {
    RethinkMultiActionDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = content,
        primaryText = dismissText,
        onPrimary = onDismiss,
        secondaryText = contactText,
        onSecondary = onContact
    )
}

internal data class HomeDialogHostActions(
    val onRestoreConfirm: (String) -> Unit,
    val onDownloadConfirm: (HomeDialog.Download, isUpdateAvailable: Boolean) -> Unit,
    val onDownloadLater: () -> Unit,
    val onAlwaysOnStop: () -> Unit,
    val onOpenVpnProfile: () -> Unit,
    val onPrivateDnsSettings: () -> Unit,
    val onFirstTimeVpnProceed: () -> Unit,
    val onCopyStats: (String) -> Unit,
    val onNoLogEmail: () -> Unit,
    val onContactEmail: () -> Unit,
    val onAccessibilityAppInfo: () -> Unit,
)

@Composable
internal fun HomeDialogHost(
    dialog: HomeDialog,
    onDismiss: () -> Unit,
    actions: HomeDialogHostActions,
) {
    when (dialog) {
        is HomeDialog.Restore -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.brbs_restore_dialog_title),
                message = stringResource(R.string.brbs_restore_dialog_message),
                confirmText = stringResource(R.string.brbs_restore_dialog_positive),
                onConfirm = {
                    onDismiss()
                    actions.onRestoreConfirm(dialog.uriString)
                },
                dismissText = stringResource(R.string.lbl_cancel),
                onDismiss = onDismiss
            )
        }

        is HomeDialog.Download -> {
            val isUpdateAvailable =
                dialog.title == stringResource(R.string.download_update_dialog_title)
            val resolvedMessage =
                if (isUpdateAvailable && dialog.source == AppUpdater.InstallSource.STORE) {
                    stringResource(R.string.download_update_dialog_message)
                } else {
                    dialog.message
                }
            val primaryLabel =
                if (isUpdateAvailable && dialog.source != AppUpdater.InstallSource.STORE) {
                    stringResource(R.string.hs_download_positive_website)
                } else {
                    stringResource(R.string.hs_download_positive_default)
                }

            HomeConfirmDialog(
                onDismissRequest = {
                    if (!isUpdateAvailable) {
                        onDismiss()
                    }
                },
                title = dialog.title,
                message = resolvedMessage,
                confirmText = primaryLabel,
                dismissText =
                    if (isUpdateAvailable) {
                        stringResource(R.string.hs_download_negative_default)
                    } else {
                        null
                    },
                onConfirm = {
                    actions.onDownloadConfirm(dialog, isUpdateAvailable)
                    onDismiss()
                },
                onDismiss = {
                    if (isUpdateAvailable) {
                        actions.onDownloadLater()
                        onDismiss()
                    }
                }
            )
        }

        is HomeDialog.AlwaysOnStop -> {
            HomeAlwaysOnStopDialog(
                title = stringResource(R.string.always_on_dialog_stop_heading),
                message = dialog.message,
                stopText = stringResource(R.string.always_on_dialog_positive),
                openSettingsText = stringResource(R.string.always_on_dialog_neutral),
                cancelText = stringResource(R.string.lbl_cancel),
                onStop = {
                    onDismiss()
                    actions.onAlwaysOnStop()
                },
                onOpenSettings = {
                    onDismiss()
                    actions.onOpenVpnProfile()
                },
                onCancel = onDismiss
            )
        }

        HomeDialog.AlwaysOnDisable -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.always_on_dialog_heading),
                message = stringResource(R.string.always_on_dialog),
                confirmText = stringResource(R.string.always_on_dialog_positive_btn),
                dismissText = stringResource(R.string.lbl_cancel),
                onConfirm = {
                    onDismiss()
                    actions.onOpenVpnProfile()
                },
                onDismiss = onDismiss
            )
        }

        HomeDialog.PrivateDns -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.private_dns_dialog_heading),
                message = stringResource(R.string.private_dns_dialog_desc),
                confirmText = stringResource(R.string.private_dns_dialog_positive),
                dismissText = stringResource(R.string.lbl_dismiss),
                onConfirm = {
                    onDismiss()
                    actions.onPrivateDnsSettings()
                },
                onDismiss = onDismiss
            )
        }

        HomeDialog.FirstTimeVpn -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.hsf_vpn_dialog_header),
                message = stringResource(R.string.hsf_vpn_dialog_message),
                confirmText = stringResource(R.string.lbl_proceed),
                dismissText = stringResource(R.string.lbl_cancel),
                onConfirm = {
                    onDismiss()
                    actions.onFirstTimeVpnProceed()
                },
                onDismiss = onDismiss
            )
        }

        is HomeDialog.Stats -> {
            HomeStatsDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.title_statistics),
                displayText = dialog.displayText,
                dismissText = stringResource(R.string.fapps_info_dialog_positive_btn),
                copyText = stringResource(R.string.dns_info_neutral),
                onDismiss = onDismiss,
                onCopy = { actions.onCopyStats(dialog.dump) }
            )
        }

        HomeDialog.Contributors -> {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    ContributorsDialogContent(onDismiss = onDismiss)
                }
            }
        }

        HomeDialog.NoLog -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.about_bug_no_log_dialog_title),
                message = stringResource(R.string.about_bug_no_log_dialog_message),
                confirmText = stringResource(R.string.about_bug_no_log_dialog_positive_btn),
                dismissText = stringResource(R.string.lbl_cancel),
                onConfirm = {
                    onDismiss()
                    actions.onNoLogEmail()
                },
                onDismiss = onDismiss
            )
        }

        is HomeDialog.NewFeatures -> {
            HomeNewFeaturesDialog(
                onDismissRequest = onDismiss,
                title = dialog.title,
                dismissText = stringResource(R.string.about_dialog_positive_button),
                contactText = stringResource(R.string.about_dialog_neutral_button),
                onDismiss = onDismiss,
                onContact = {
                    onDismiss()
                    actions.onContactEmail()
                },
                content = { WhatsNewDialogContent() }
            )
        }

        HomeDialog.AccessibilityCrash -> {
            HomeConfirmDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.lbl_action_required),
                message = stringResource(R.string.alert_firewall_accessibility_regrant_explanation),
                confirmText = stringResource(R.string.univ_accessibility_crash_dialog_positive),
                dismissText = stringResource(R.string.lbl_cancel),
                onConfirm = {
                    actions.onAccessibilityAppInfo()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }

        HomeDialog.Hidden -> Unit
    }
}

@Composable
private fun WhatsNewDialogContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimensions.spacingLg)
                .verticalScroll(rememberScrollState())
    ) {
        HomeHtmlText(
            text = stringResource(R.string.whats_new_version_update),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun ContributorsDialogContent(onDismiss: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimensions.spacingXl)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    imageVector = MaterialSymbols.Filled.Close,
                    contentDescription = stringResource(R.string.lbl_dismiss)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_authors),
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeMd)
                )
                Spacer(modifier = Modifier.width(Dimensions.spacingSm))
                Text(
                    text = stringResource(R.string.contributors_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.spacingMd))

        HomeHtmlText(
            text = stringResource(R.string.contributors_list),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeHtmlText(text: String, textAlign: TextAlign) {
    val textValue = remember(text) { UIUtils.htmlToSpannedText(text).toString() }
    Text(
        text = textValue,
        modifier = Modifier.fillMaxWidth(),
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign
            )
    )
}

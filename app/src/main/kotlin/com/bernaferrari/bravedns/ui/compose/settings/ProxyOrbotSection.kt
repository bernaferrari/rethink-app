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
package com.bernaferrari.bravedns.ui.compose.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.ProxyManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMultiActionDialog
import com.bernaferrari.bravedns.util.OrbotHelper
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ProxyOrbotDialogUi(
    val showInfo: Boolean,
    val showInstall: Boolean,
    val showMode: Boolean,
    val showStop: Boolean,
    val selectedMode: String,
    val stopHasDnsHint: Boolean,
)

internal data class ProxyOrbotDialogStrings(
    val installError: String,
    val websiteLink: String,
    val noAppToast: String,
    val vpnDisabledError: String,
    val stopTitle: String,
    val stopMessage: String,
    val stopMessageCombo: String,
)

@Composable
internal fun ProxyOrbotDialogs(
    ui: ProxyOrbotDialogUi,
    orbotHelper: OrbotHelper,
    persistentState: PersistentState,
    strings: ProxyOrbotDialogStrings,
    onUiChange: (ProxyOrbotDialogUi) -> Unit,
    onStopOrbot: () -> Unit,
    onLogEvent: (String) -> Unit,
    onReloadUi: () -> Unit,
    onNavigateToDns: (() -> Unit)?,
) {
    if (ui.showInfo) {
        ProxyOrbotInfoDialog(onDismiss = { onUiChange(ui.copy(showInfo = false)) })
    }
    if (ui.showInstall) {
        ProxyOrbotInstallDialog(
            orbotHelper = orbotHelper,
            installError = strings.installError,
            websiteLink = strings.websiteLink,
            onDismiss = { onUiChange(ui.copy(showInstall = false)) },
        )
    }
    if (ui.showMode) {
        ProxyOrbotModeDialogHost(
            ui = ui,
            orbotHelper = orbotHelper,
            persistentState = persistentState,
            strings = strings,
            onUiChange = onUiChange,
            onStopOrbot = onStopOrbot,
            onLogEvent = onLogEvent,
            onReloadUi = onReloadUi,
        )
    }
    if (ui.showStop) {
        ProxyOrbotStopDialog(
            ui = ui,
            orbotHelper = orbotHelper,
            strings = strings,
            onUiChange = onUiChange,
            onNavigateToDns = onNavigateToDns,
        )
    }
}

@Composable
private fun ProxyOrbotInfoDialog(onDismiss: () -> Unit) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.orbot_title),
        message = stringResource(R.string.orbot_explanation),
        confirmText = stringResource(R.string.lbl_dismiss),
        onConfirm = onDismiss
    )
}

@Composable
private fun ProxyOrbotInstallDialog(
    orbotHelper: OrbotHelper,
    installError: String,
    websiteLink: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    RethinkMultiActionDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.orbot_install_dialog_title),
        message = stringResource(R.string.orbot_install_dialog_message),
        primaryText = stringResource(R.string.orbot_install_dialog_positive),
        onPrimary = {
            onDismiss()
            val installIntent = orbotHelper.getIntentForDownload()
            if (installIntent == null) {
                Utilities.showToastUiCentered(
                    context,
                    installError,
                    Toast.LENGTH_SHORT
                )
                return@RethinkMultiActionDialog
            }

            try {
                context.startActivity(installIntent)
            } catch (_: ActivityNotFoundException) {
                Utilities.showToastUiCentered(
                    context,
                    installError,
                    Toast.LENGTH_SHORT
                )
            }
        },
        secondaryText = stringResource(R.string.orbot_install_dialog_neutral),
        onSecondary = {
            onDismiss()
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        websiteLink.toUri()
                    )
                )
            } catch (_: ActivityNotFoundException) {
                Utilities.showToastUiCentered(
                    context,
                    installError,
                    Toast.LENGTH_SHORT
                )
            }
        },
        tertiaryText = stringResource(R.string.lbl_dismiss),
        onTertiary = onDismiss
    )
}

@Composable
private fun ProxyOrbotModeDialogHost(
    ui: ProxyOrbotDialogUi,
    orbotHelper: OrbotHelper,
    persistentState: PersistentState,
    strings: ProxyOrbotDialogStrings,
    onUiChange: (ProxyOrbotDialogUi) -> Unit,
    onStopOrbot: () -> Unit,
    onLogEvent: (String) -> Unit,
    onReloadUi: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OrbotModeDialog(
        supportsHttp = Utilities.isAtleastQ(),
        selectedMode = ui.selectedMode,
        onSelectedMode = { onUiChange(ui.copy(selectedMode = it)) },
        onDismiss = { onUiChange(ui.copy(showMode = false)) },
        onConfirm = {
            scope.launch {
                onUiChange(ui.copy(showMode = false))

                if (ui.selectedMode == AppConfig.ProxyType.NONE.name) {
                    onStopOrbot()
                    return@launch
                }

                if (!ProxyManager.isAnyAppSelected(ProxyManager.ID_ORBOT_BASE)) {
                    Utilities.showToastUiCentered(
                        context,
                        strings.noAppToast,
                        Toast.LENGTH_SHORT
                    )
                    onReloadUi()
                    return@launch
                }

                if (!VpnController.hasTunnel()) {
                    Utilities.showToastUiCentered(
                        context,
                        strings.vpnDisabledError,
                        Toast.LENGTH_SHORT
                    )
                    onReloadUi()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    persistentState.orbotConnectionStatus.value = true
                    orbotHelper.startOrbot(ui.selectedMode)
                }
                onLogEvent("Orbot mode updated: ${ui.selectedMode}")
                onReloadUi()
            }
        }
    )
}

@Composable
private fun ProxyOrbotStopDialog(
    ui: ProxyOrbotDialogUi,
    orbotHelper: OrbotHelper,
    strings: ProxyOrbotDialogStrings,
    onUiChange: (ProxyOrbotDialogUi) -> Unit,
    onNavigateToDns: (() -> Unit)?,
) {
    RethinkMultiActionDialog(
        onDismissRequest = { onUiChange(ui.copy(showStop = false)) },
        title = strings.stopTitle,
        text = {
            Text(
                text = if (ui.stopHasDnsHint) strings.stopMessageCombo else strings.stopMessage
            )
        },
        primaryText = stringResource(R.string.lbl_dismiss),
        onPrimary = { onUiChange(ui.copy(showStop = false)) },
        secondaryText = if (ui.stopHasDnsHint && onNavigateToDns != null) stringResource(R.string.orbot_stop_dialog_neutral) else null,
        onSecondary = if (ui.stopHasDnsHint && onNavigateToDns != null) {
            {
                onUiChange(ui.copy(showStop = false))
                onNavigateToDns()
            }
        } else {
            null
        },
        tertiaryText = stringResource(R.string.orbot_stop_dialog_negative),
        onTertiary = {
            onUiChange(ui.copy(showStop = false))
            orbotHelper.openOrbotApp()
        }
    )
}

@Composable
private fun OrbotModeDialog(
    supportsHttp: Boolean,
    selectedMode: String,
    onSelectedMode: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val options =
        buildList {
            add(RethinkProxyModeOption(AppConfig.ProxyType.SOCKS5.name, stringResource(R.string.orbot_socks5)))
            if (supportsHttp) {
                add(RethinkProxyModeOption(AppConfig.ProxyType.HTTP.name, stringResource(R.string.orbot_http)))
                add(RethinkProxyModeOption(AppConfig.ProxyType.HTTP_SOCKS5.name, stringResource(R.string.orbot_both)))
            }
            add(RethinkProxyModeOption(AppConfig.ProxyType.NONE.name, stringResource(R.string.orbot_none)))
        }
    RethinkProxyModeDialog(
        title = stringResource(R.string.orbot_title),
        options = options,
        selectedId = selectedMode,
        save = stringResource(R.string.lbl_save),
        cancel = stringResource(R.string.lbl_cancel),
        onSelected = onSelectedMode,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

internal suspend fun formatOrbotDescription(
    appConfig: AppConfig,
    orbotDisabledDescription: String,
    orbotStatus2Description: String,
    orbotStatus1Template: String,
    orbotStatus3Template: String,
    orbotStatusArgDns: String,
    orbotStatusArgProxy: String
): String {
    val isInstalled = FirewallManager.isOrbotInstalled()
    if (!isInstalled) {
        return orbotDisabledDescription
    }

    if (!appConfig.isOrbotProxyEnabled()) {
        return orbotDisabledDescription
    }

    val isOrbotDns = appConfig.isOrbotDns()
    return when (appConfig.getProxyType()) {
        AppConfig.ProxyType.HTTP.name -> {
            orbotStatus2Description
        }

        AppConfig.ProxyType.SOCKS5.name -> {
            if (isOrbotDns) {
                String.format(orbotStatus1Template, orbotStatusArgDns)
            } else {
                String.format(orbotStatus1Template, orbotStatusArgProxy)
            }
        }

        AppConfig.ProxyType.HTTP_SOCKS5.name -> {
            if (isOrbotDns) {
                String.format(orbotStatus3Template, orbotStatusArgDns)
            } else {
                String.format(orbotStatus3Template, orbotStatusArgProxy)
            }
        }

        else -> orbotDisabledDescription
    }
}

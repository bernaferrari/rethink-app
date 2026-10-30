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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

internal data class ProxyDialogAppOption(
    val packageName: String,
    val label: String,
    val iconLookupName: String = label
)

internal data class Socks5DialogState(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val selectedAppPackage: String,
    val appOptions: List<ProxyDialogAppOption>,
    val udpBlocked: Boolean,
    val includeProxyApps: Boolean,
    val lockdown: Boolean,
    val error: String? = null
)

internal data class HttpDialogState(
    val host: String,
    val selectedAppPackage: String,
    val appOptions: List<ProxyDialogAppOption>,
    val includeProxyApps: Boolean,
    val lockdown: Boolean,
    val error: String? = null
)

@Composable
internal fun proxyConfigurationStrings(
    title: String,
    description: String?,
): RethinkProxyConfigurationStrings =
    RethinkProxyConfigurationStrings(
        title = title,
        description = description,
        host = stringResource(R.string.proxy_host_label),
        port = stringResource(R.string.proxy_port_label),
        username = stringResource(R.string.proxy_username_optional_label),
        password = stringResource(R.string.proxy_password_optional_label),
        appDescription = stringResource(R.string.settings_dns_proxy_dialog_app_desc),
        udpBlocked = stringResource(R.string.settings_udp_block),
        includeProxyApps = stringResource(R.string.settings_exclude_proxy_apps_heading),
        lockdownDescription = stringResource(R.string.settings_lock_down_mode_desc),
        save = stringResource(R.string.lbl_save),
        cancel = stringResource(R.string.lbl_cancel),
    )

internal fun selectedProxyAppLabel(selectedPackageName: String, options: List<ProxyDialogAppOption>): String {
    return options.firstOrNull { it.packageName == selectedPackageName }?.label
        ?: options.firstOrNull()?.label
        ?: ""
}

/**
 * The picked proxy app's launcher icon (the shared [AppIcon], default app icon as its fallback).
 * "None" has no app, so it shows a quiet block glyph instead of an empty tile.
 */
@Composable
internal fun ProxyDialogAppIcon(
    option: ProxyDialogAppOption?,
    size: androidx.compose.ui.unit.Dp
) {
    if (option == null || option.packageName.isBlank()) {
        Icon(MaterialSymbols.Filled.Block, null, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        AppIcon(option.packageName, appName = option.iconLookupName, size = size)
    }
}

internal suspend fun buildProxyDialogAppOptions(
    context: android.content.Context,
    defaultApp: String
): List<ProxyDialogAppOption> {
    val sortedApps = FirewallManager.getAllAppsSortedByVpnPermission(context)
    val duplicateCountByName = sortedApps.groupingBy { it.appName }.eachCount()

    return buildList {
        add(ProxyDialogAppOption(packageName = "", label = defaultApp))
        sortedApps.forEach { appInfo ->
            val appName = appInfo.appName
            val packageName = appInfo.packageName.orEmpty()
            val label =
                if (duplicateCountByName[appName] ?: 0 > 1 && packageName.isNotBlank()) {
                    "$appName ($packageName)"
                } else {
                    appName
                }
            add(
                ProxyDialogAppOption(
                    packageName = packageName,
                    label = label,
                    iconLookupName = appName
                )
            )
        }
    }
}

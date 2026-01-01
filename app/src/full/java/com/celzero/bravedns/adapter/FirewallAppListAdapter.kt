/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.celzero.bravedns.adapter

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.FirewallManager.updateFirewallStatus
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.ProxyManager.ID_NONE
import com.celzero.bravedns.ui.activity.AppInfoActivity
import com.celzero.bravedns.ui.activity.AppInfoActivity.Companion.INTENT_UID
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getIcon
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun FirewallAppRow(appInfo: AppInfo, eventLogger: EventLogger) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageManager = context.packageManager
    var appStatus by remember(appInfo.uid) {
        mutableStateOf(FirewallManager.FirewallStatus.getStatus(appInfo.firewallStatus))
    }
    var connStatus by remember(appInfo.uid) {
        mutableStateOf(FirewallManager.ConnectionStatus.getStatus(appInfo.connectionStatus))
    }
    var appIcon by remember(appInfo.uid) { mutableStateOf<Drawable?>(null) }
    var proxyEnabled by remember(appInfo.uid) { mutableStateOf(false) }
    val isSelfApp = appInfo.packageName == context.packageName
    val tombstoned = appInfo.tombstoneTs > 0
    val nameAlpha = if (appInfo.hasInternetPermission(packageManager)) 1f else 0.4f

    LaunchedEffect(appInfo.uid, appInfo.packageName, appInfo.appName) {
        appStatus = withContext(Dispatchers.IO) { FirewallManager.appStatus(appInfo.uid) }
        connStatus = withContext(Dispatchers.IO) { FirewallManager.connectionStatus(appInfo.uid) }
        appIcon = getIcon(context, appInfo.packageName, appInfo.appName)
        val proxyId = ProxyManager.getProxyIdForApp(appInfo.uid)
        proxyEnabled = !appInfo.isProxyExcluded && proxyId.isNotEmpty() && proxyId != ID_NONE
    }

    val dataUsageText = buildDataUsageText(context, appInfo)
    val statusText =
        if (isSelfApp) {
            context.getString(R.string.firewall_status_allow)
        } else {
            getFirewallText(context, appStatus, connStatus)
        }
    val wifiIcon = wifiIconRes(appStatus, connStatus, isSelfApp)
    val mobileIcon = mobileIconRes(appStatus, connStatus, isSelfApp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { openAppDetailActivity(context, appInfo.uid) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AndroidView(
                factory = { ctx -> AppCompatImageView(ctx) },
                update = { imageView ->
                    Glide.with(imageView)
                        .load(appIcon)
                        .error(Utilities.getDefaultIcon(context))
                        .into(imageView)
                },
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName + if (proxyEnabled) context.getString(R.string.symbol_key) else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = nameAlpha),
                    textDecoration = if (tombstoned) TextDecoration.LineThrough else null
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = dataUsageText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (wifiIcon != null) {
                IconButton(onClick = { handleWifiToggle(context, scope, eventLogger, appInfo) }) {
                    Icon(
                        painter = painterResource(id = wifiIcon),
                        contentDescription = null
                    )
                }
            }
            if (mobileIcon != null) {
                IconButton(onClick = { handleMobileToggle(context, scope, eventLogger, appInfo) }) {
                    Icon(
                        painter = painterResource(id = mobileIcon),
                        contentDescription = null
                    )
                }
            }
        }
        Spacer(modifier = Modifier.fillMaxWidth())
    }
}

private fun buildDataUsageText(context: Context, appInfo: AppInfo): String {
    val u = Utilities.humanReadableByteCount(appInfo.uploadBytes, true)
    val uploadBytes = context.getString(R.string.symbol_upload, u)
    val d = Utilities.humanReadableByteCount(appInfo.downloadBytes, true)
    val downloadBytes = context.getString(R.string.symbol_download, d)
    return context.getString(R.string.two_argument, uploadBytes, downloadBytes)
}

private fun getFirewallText(
    context: Context,
    aStat: FirewallManager.FirewallStatus,
    cStat: FirewallManager.ConnectionStatus
): String {
    return when (aStat) {
        FirewallManager.FirewallStatus.NONE ->
            when (cStat) {
                FirewallManager.ConnectionStatus.ALLOW ->
                    context.getString(R.string.firewall_status_allow)
                FirewallManager.ConnectionStatus.METERED ->
                    context.getString(R.string.firewall_status_block_metered)
                FirewallManager.ConnectionStatus.UNMETERED ->
                    context.getString(R.string.firewall_status_block_unmetered)
                FirewallManager.ConnectionStatus.BOTH ->
                    context.getString(R.string.firewall_status_blocked)
            }
        FirewallManager.FirewallStatus.EXCLUDE ->
            context.getString(R.string.firewall_status_excluded)
        FirewallManager.FirewallStatus.ISOLATE ->
            context.getString(R.string.firewall_status_isolate)
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL ->
            context.getString(R.string.firewall_status_whitelisted)
        FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
            context.getString(R.string.firewall_status_bypass_dns_firewall)
        FirewallManager.FirewallStatus.UNTRACKED ->
            context.getString(R.string.firewall_status_unknown)
    }
}

private fun wifiIconRes(
    firewallStatus: FirewallManager.FirewallStatus,
    connStatus: FirewallManager.ConnectionStatus,
    isSelfApp: Boolean
): Int? {
    if (isSelfApp) return null
    return when (firewallStatus) {
        FirewallManager.FirewallStatus.NONE ->
            when (connStatus) {
                FirewallManager.ConnectionStatus.ALLOW -> R.drawable.ic_firewall_wifi_on
                FirewallManager.ConnectionStatus.UNMETERED -> R.drawable.ic_firewall_wifi_off
                FirewallManager.ConnectionStatus.METERED -> R.drawable.ic_firewall_wifi_on
                FirewallManager.ConnectionStatus.BOTH -> R.drawable.ic_firewall_wifi_off
            }
        FirewallManager.FirewallStatus.EXCLUDE,
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL,
        FirewallManager.FirewallStatus.ISOLATE,
        FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
            R.drawable.ic_firewall_wifi_on_grey
        else -> R.drawable.ic_firewall_wifi_on
    }
}

private fun mobileIconRes(
    firewallStatus: FirewallManager.FirewallStatus,
    connStatus: FirewallManager.ConnectionStatus,
    isSelfApp: Boolean
): Int? {
    if (isSelfApp) return null
    return when (firewallStatus) {
        FirewallManager.FirewallStatus.NONE ->
            when (connStatus) {
                FirewallManager.ConnectionStatus.ALLOW -> R.drawable.ic_firewall_data_on
                FirewallManager.ConnectionStatus.UNMETERED -> R.drawable.ic_firewall_data_on
                FirewallManager.ConnectionStatus.METERED -> R.drawable.ic_firewall_data_off
                FirewallManager.ConnectionStatus.BOTH -> R.drawable.ic_firewall_data_off
            }
        FirewallManager.FirewallStatus.EXCLUDE,
        FirewallManager.FirewallStatus.BYPASS_UNIVERSAL,
        FirewallManager.FirewallStatus.ISOLATE,
        FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
            R.drawable.ic_firewall_data_on_grey
        else -> R.drawable.ic_firewall_data_on
    }
}

private fun handleWifiToggle(
    context: Context,
    scope: CoroutineScope,
    eventLogger: EventLogger,
    appInfo: AppInfo
) {
    enableAfterDelay(scope, TimeUnit.SECONDS.toMillis(1L))
    scope.launch(Dispatchers.IO) {
        val appNames = FirewallManager.getAppNamesByUid(appInfo.uid)
        val connStatus = FirewallManager.connectionStatus(appInfo.uid)
        if (appNames.count() > 1) {
            withContext(Dispatchers.Main) {
                showDialog(context, scope, eventLogger, appNames, appInfo, isWifi = true, connStatus)
            }
            return@launch
        }
        toggleWifi(eventLogger, appInfo, connStatus)
    }
}

private fun handleMobileToggle(
    context: Context,
    scope: CoroutineScope,
    eventLogger: EventLogger,
    appInfo: AppInfo
) {
    enableAfterDelay(scope, TimeUnit.SECONDS.toMillis(1L))
    scope.launch(Dispatchers.IO) {
        val appNames = FirewallManager.getAppNamesByUid(appInfo.uid)
        val connStatus = FirewallManager.connectionStatus(appInfo.uid)
        if (appNames.count() > 1) {
            withContext(Dispatchers.Main) {
                showDialog(context, scope, eventLogger, appNames, appInfo, isWifi = false, connStatus)
            }
            return@launch
        }
        toggleMobileData(eventLogger, appInfo, connStatus)
    }
}

private suspend fun toggleMobileData(
    eventLogger: EventLogger,
    appInfo: AppInfo,
    connStatus: FirewallManager.ConnectionStatus
) {
    when (connStatus) {
        FirewallManager.ConnectionStatus.METERED -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.ALLOW)
        }
        FirewallManager.ConnectionStatus.UNMETERED -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.BOTH)
        }
        FirewallManager.ConnectionStatus.BOTH -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.UNMETERED)
        }
        FirewallManager.ConnectionStatus.ALLOW -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.METERED)
        }
    }
    logEvent(eventLogger, "UID: ${appInfo.uid}, App: ${appInfo.appName}, New FW status: ${FirewallManager.connectionStatus(appInfo.uid)}")
}

private suspend fun toggleWifi(
    eventLogger: EventLogger,
    appInfo: AppInfo,
    connStatus: FirewallManager.ConnectionStatus
) {
    when (connStatus) {
        FirewallManager.ConnectionStatus.METERED -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.BOTH)
        }
        FirewallManager.ConnectionStatus.UNMETERED -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.ALLOW)
        }
        FirewallManager.ConnectionStatus.BOTH -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.METERED)
        }
        FirewallManager.ConnectionStatus.ALLOW -> {
            updateFirewallStatus(
                appInfo.uid,
                FirewallManager.FirewallStatus.NONE,
                FirewallManager.ConnectionStatus.UNMETERED)
        }
    }
    logEvent(eventLogger, "UID: ${appInfo.uid}, App: ${appInfo.appName}, New FW status: ${FirewallManager.connectionStatus(appInfo.uid)}")
}

private fun openAppDetailActivity(context: Context, uid: Int) {
    val intent = Intent(context, AppInfoActivity::class.java)
    intent.putExtra(INTENT_UID, uid)
    context.startActivity(intent)
}

private fun showDialog(
    context: Context,
    scope: CoroutineScope,
    eventLogger: EventLogger,
    packageList: List<String>,
    appInfo: AppInfo,
    isWifi: Boolean,
    connStatus: FirewallManager.ConnectionStatus
) {
    val builderSingle = MaterialAlertDialogBuilder(context)
    builderSingle.setIcon(R.drawable.ic_firewall_block_grey)
    val count = packageList.count()
    builderSingle.setTitle(
        context.getString(
            R.string.ctbs_block_other_apps, appInfo.appName, count.toString()))

    val arrayAdapter =
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_activated_1)
    arrayAdapter.addAll(packageList)
    builderSingle.setCancelable(false)
    builderSingle.setItems(packageList.toTypedArray(), null)
    builderSingle
        .setPositiveButton(context.getString(R.string.lbl_proceed)) { _: DialogInterface, _: Int ->
            scope.launch(Dispatchers.IO) {
                if (isWifi) {
                    toggleWifi(eventLogger, appInfo, connStatus)
                    return@launch
                }
                toggleMobileData(eventLogger, appInfo, connStatus)
            }
        }
        .setNeutralButton(context.getString(R.string.ctbs_dialog_negative_btn)) { _: DialogInterface, _: Int ->
        }

    val alertDialog: AlertDialog = builderSingle.create()
    alertDialog.listView.setOnItemClickListener { _, _, _, _ -> }
    alertDialog.show()
}

private fun enableAfterDelay(scope: CoroutineScope, delayMs: Long) {
    scope.launch {
        delay(delayMs)
    }
}

private fun logEvent(eventLogger: EventLogger, details: String) {
    eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "App list, rule change", EventSource.UI, false, details)
}

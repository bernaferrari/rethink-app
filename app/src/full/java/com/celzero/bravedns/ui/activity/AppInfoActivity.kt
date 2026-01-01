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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.AppWiseDomainsAdapter
import com.celzero.bravedns.adapter.AppWiseIpsAdapter
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.ProxyManager.ID_NONE
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.AppDomainRulesSheet
import com.celzero.bravedns.ui.bottomsheet.AppIpRulesSheet
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Constants.Companion.RETHINK_PACKAGE
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.openAndroidAppInfo
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppInfoActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private val ipRulesViewModel: CustomIpViewModel by viewModel()
    private val domainRulesViewModel: CustomDomainViewModel by viewModel()
    private val networkLogsViewModel: AppConnectionsViewModel by viewModel()

    private var uid: Int = INVALID_UID
    private var appInfo by mutableStateOf<AppInfo?>(null)
    private var appStatus by mutableStateOf(FirewallManager.FirewallStatus.NONE)
    private var connStatus by mutableStateOf(FirewallManager.ConnectionStatus.ALLOW)
    private var firewallStatusText by mutableStateOf("")
    private var isProxyExcluded by mutableStateOf(false)
    private var isTempAllowed by mutableStateOf(false)
    private var tempAllowExpiryTime by mutableStateOf(0L)
    private var proxyDetails by mutableStateOf("")
    private var showNoAppFoundDialog by mutableStateOf(false)

    companion object {
        const val INTENT_UID = "UID"
        const val INTENT_ACTIVE_CONNS = "ACTIVE_CONNS"
        const val INTENT_ASN = "ASN"
        private const val TAG = "AppInfoActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)
        handleFrostEffectIfNeeded(persistentState.theme)
        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        uid = intent.getIntExtra(INTENT_UID, INVALID_UID)
        ipRulesViewModel.setUid(uid)
        domainRulesViewModel.setUid(uid)
        networkLogsViewModel.setUid(uid)

        setContent {
            RethinkTheme {
                AppInfoContent()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun AppInfoContent() {
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        var showDomainRulesSheet by remember { mutableStateOf(false) }
        var selectedDomain by remember { mutableStateOf("") }
        var showIpRulesSheet by remember { mutableStateOf(false) }
        var selectedIp by remember { mutableStateOf("") }
        var selectedDomains by remember { mutableStateOf("") }

        val domainAdapter =
            remember {
                AppWiseDomainsAdapter(
                    this,
                    lifecycleOwner,
                    uid,
                    onShowDomainRules = { domain ->
                        selectedDomain = domain
                        showDomainRulesSheet = true
                    }
                )
            }
        val ipAdapter =
            remember {
                AppWiseIpsAdapter(
                    this,
                    lifecycleOwner,
                    uid,
                    onShowIpRules = { ip, domains ->
                        selectedIp = ip
                        selectedDomains = domains
                        showIpRulesSheet = true
                    }
                )
            }
        val activeAdapter =
            remember {
                AppWiseDomainsAdapter(
                    this,
                    lifecycleOwner,
                    uid,
                    isActiveConn = true,
                    onShowDomainRules = { }
                )
            }

        val isRethink = appInfo?.packageName == RETHINK_PACKAGE
        val uptime = VpnController.uptimeMs()
        val activeConns =
            if (isRethink) {
                networkLogsViewModel.getRethinkActiveConnsLimited(uptime)
            } else {
                networkLogsViewModel.fetchTopActiveConnections(uid, uptime)
            }
        val activeItems = activeConns.asFlow().collectAsLazyPagingItems()
        val domainItems =
            if (isRethink) {
                networkLogsViewModel.getRethinkDomainLogsLimited().asFlow().collectAsLazyPagingItems()
            } else {
                networkLogsViewModel.getDomainLogsLimited(uid).asFlow().collectAsLazyPagingItems()
            }
        val ipItems =
            if (isRethink) {
                networkLogsViewModel.getRethinkIpLogsLimited().asFlow().collectAsLazyPagingItems()
            } else {
                networkLogsViewModel.getIpLogsLimited(uid).asFlow().collectAsLazyPagingItems()
            }

        LaunchedEffect(uid) {
            loadAppInfo()
        }

        activeAdapter.CloseDialogHost()

        if (showNoAppFoundDialog) {
            AlertDialog(
                onDismissRequest = { showNoAppFoundDialog = false },
                title = { Text(text = getString(R.string.ada_noapp_dialog_title)) },
                text = { Text(text = getString(R.string.ada_noapp_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNoAppFoundDialog = false
                            finish()
                        }
                    ) {
                        Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                    }
                }
            )
        }

        if (showDomainRulesSheet && selectedDomain.isNotEmpty()) {
            AppDomainRulesSheet(
                uid = uid,
                domain = selectedDomain,
                eventLogger = eventLogger,
                onDismiss = { showDomainRulesSheet = false },
                onUpdated = { domainAdapter.notifyRulesChanged() }
            )
        }
        if (showIpRulesSheet && selectedIp.isNotEmpty()) {
            AppIpRulesSheet(
                uid = uid,
                ipAddress = selectedIp,
                domains = selectedDomains,
                eventLogger = eventLogger,
                onDismiss = { showIpRulesSheet = false },
                onUpdated = { ipAdapter.notifyRulesChanged() }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (appInfo == null) {
                Text(text = stringResource(id = R.string.ada_noapp_dialog_message))
                Button(onClick = { finish() }) { Text(text = stringResource(id = R.string.ada_noapp_dialog_positive)) }
                return@Column
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = appInfo?.appName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(text = appInfo?.packageName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(text = firewallStatusText, style = MaterialTheme.typography.bodySmall)
                    if (proxyDetails.isNotEmpty()) {
                        Text(text = proxyDetails, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openAndroidAppInfo(this@AppInfoActivity, appInfo?.packageName) }) {
                    Text(text = stringResource(id = R.string.about_settings_app_info))
                }
                Button(onClick = { openCustomRulesScreen() }) {
                    Text(text = stringResource(id = R.string.ada_app_dns_settings))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.exclude_apps_from_proxy))
                    Text(text = stringResource(id = R.string.settings_exclude_proxy_apps_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isProxyExcluded,
                    onCheckedChange = { enabled ->
                        isProxyExcluded = enabled
                        updateProxyExcluded(enabled)
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.temp_allow_label))
                    Text(text = stringResource(id = R.string.temp_allow_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isTempAllowed,
                    onCheckedChange = { enabled ->
                        isTempAllowed = enabled
                        updateTempAllow(enabled)
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { toggleWifi() }) {
                    Text(text = stringResource(id = R.string.ada_app_unmetered))
                }
                Button(onClick = { toggleMobileData() }) {
                    Text(text = stringResource(id = R.string.ada_app_metered))
                }
                Button(onClick = { toggleIsolate() }) {
                    Text(text = stringResource(id = R.string.ada_app_isolate))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { toggleBypassDnsFirewall() }) {
                    Text(text = stringResource(id = R.string.ada_app_bypass_dns_firewall))
                }
                Button(onClick = { toggleBypassUniversal() }) {
                    Text(text = stringResource(id = R.string.ada_app_bypass_univ))
                }
                Button(onClick = { toggleExclude() }) {
                    Text(text = stringResource(id = R.string.ada_app_exclude))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.top_active_conns), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(200.dp)) {
                items(count = activeItems.itemCount) { index ->
                    val item = activeItems[index] ?: return@items
                    activeAdapter.DomainRow(item)
                }
            }

            Text(text = stringResource(id = R.string.ssv_most_contacted_domain_heading), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(200.dp)) {
                items(count = domainItems.itemCount) { index ->
                    val item = domainItems[index] ?: return@items
                    domainAdapter.DomainRow(item)
                }
            }

            Text(text = stringResource(id = R.string.ssv_most_contacted_ips_heading), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(200.dp)) {
                items(count = ipItems.itemCount) { index ->
                    val item = ipItems[index] ?: return@items
                    ipAdapter.IpRow(item)
                }
            }
        }
    }

    private suspend fun loadAppInfo() {
        val info = withContext(Dispatchers.IO) { FirewallManager.getAppInfoByUid(uid) }
        if (info == null || uid == INVALID_UID || info.tombstoneTs > 0) {
            showNoAppFoundDialog = true
            return
        }
        val status = FirewallManager.appStatus(info.uid)
        val conn = FirewallManager.connectionStatus(info.uid)
        val proxy =
            ProxyManager.getProxyIdForApp(uid).takeIf { it.isNotEmpty() && it != ID_NONE }
                ?.let { getString(R.string.wireguard_apps_proxy_map_desc, it) }
                .orEmpty()
        appInfo = info
        appStatus = status
        connStatus = conn
        isProxyExcluded = info.isProxyExcluded
        isTempAllowed = FirewallManager.isTempAllowed(info.uid)
        tempAllowExpiryTime = info.tempAllowExpiryTime
        proxyDetails = proxy
        firewallStatusText = getFirewallText(appStatus, connStatus)
    }

    private fun openCustomRulesScreen() {
        val intent = Intent(this, CustomRulesActivity::class.java)
        intent.putExtra(INTENT_UID, uid)
        startActivity(intent)
    }

    private fun updateProxyExcluded(enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            FirewallManager.updateIsProxyExcluded(uid, enabled)
        }
    }

    private fun updateTempAllow(enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (enabled) {
                FirewallManager.updateTempAllow(uid, true)
            } else {
                FirewallManager.updateTempAllow(uid, false)
            }
        }
    }

    private fun toggleBypassDnsFirewall() {
        val next =
            if (appStatus == FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL) {
                FirewallManager.FirewallStatus.NONE
            } else {
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL
            }
        updateFirewallStatus(next, FirewallManager.ConnectionStatus.ALLOW)
    }

    private fun toggleBypassUniversal() {
        val next =
            if (appStatus == FirewallManager.FirewallStatus.BYPASS_UNIVERSAL) {
                FirewallManager.FirewallStatus.NONE
            } else {
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL
            }
        updateFirewallStatus(next, FirewallManager.ConnectionStatus.ALLOW)
    }

    private fun toggleExclude() {
        val next =
            if (appStatus == FirewallManager.FirewallStatus.EXCLUDE) {
                FirewallManager.FirewallStatus.NONE
            } else {
                FirewallManager.FirewallStatus.EXCLUDE
            }
        updateFirewallStatus(next, FirewallManager.ConnectionStatus.ALLOW)
    }

    private fun toggleIsolate() {
        val next =
            if (appStatus == FirewallManager.FirewallStatus.ISOLATE) {
                FirewallManager.FirewallStatus.NONE
            } else {
                FirewallManager.FirewallStatus.ISOLATE
            }
        updateFirewallStatus(next, FirewallManager.ConnectionStatus.ALLOW)
    }

    private fun toggleWifi() {
        val newConnStatus =
            when (connStatus) {
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.METERED
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.UNMETERED
            }
        updateFirewallStatus(FirewallManager.FirewallStatus.NONE, newConnStatus, connStatus)
    }

    private fun toggleMobileData() {
        val newConnStatus =
            when (connStatus) {
                FirewallManager.ConnectionStatus.METERED -> FirewallManager.ConnectionStatus.ALLOW
                FirewallManager.ConnectionStatus.UNMETERED -> FirewallManager.ConnectionStatus.BOTH
                FirewallManager.ConnectionStatus.BOTH -> FirewallManager.ConnectionStatus.UNMETERED
                FirewallManager.ConnectionStatus.ALLOW -> FirewallManager.ConnectionStatus.METERED
            }
        updateFirewallStatus(FirewallManager.FirewallStatus.NONE, newConnStatus, connStatus)
    }

    private fun updateFirewallStatus(
        aStat: FirewallManager.FirewallStatus,
        cStat: FirewallManager.ConnectionStatus,
        prevConnStat: FirewallManager.ConnectionStatus = FirewallManager.ConnectionStatus.ALLOW
    ) {
        val info = appInfo ?: return
        if (aStat == FirewallManager.FirewallStatus.EXCLUDE && FirewallManager.isUnknownPackage(uid)) {
            showToastUiCentered(this, getString(R.string.exclude_no_package_err_toast), Toast.LENGTH_LONG)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            FirewallManager.updateFirewallStatus(info.uid, aStat, cStat)
            withContext(Dispatchers.Main) {
                appStatus = aStat
                connStatus = cStat
                firewallStatusText = getFirewallText(appStatus, connStatus)
                logEvent(
                    "Firewall status changed",
                    "Firewall status changed for ${info.appName} (${info.uid}), new status: $aStat, conn status: $cStat"
                )
            }
        }
    }

    private fun getFirewallText(
        aStat: FirewallManager.FirewallStatus,
        cStat: FirewallManager.ConnectionStatus
    ): String {
        return when (aStat) {
            FirewallManager.FirewallStatus.NONE -> {
                when (cStat) {
                    FirewallManager.ConnectionStatus.METERED ->
                        getString(R.string.ada_app_status_block_md)
                    FirewallManager.ConnectionStatus.UNMETERED ->
                        getString(R.string.ada_app_status_block_wifi)
                    FirewallManager.ConnectionStatus.BOTH ->
                        getString(R.string.ada_app_status_block)
                    FirewallManager.ConnectionStatus.ALLOW ->
                        getString(R.string.ada_app_status_allow)
                }
            }
            FirewallManager.FirewallStatus.EXCLUDE -> getString(R.string.ada_app_status_exclude)
            FirewallManager.FirewallStatus.BYPASS_UNIVERSAL ->
                getString(R.string.ada_app_status_whitelist)
            FirewallManager.FirewallStatus.ISOLATE -> getString(R.string.ada_app_status_isolate)
            FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
                getString(R.string.ada_app_status_bypass_dns_firewall)
            FirewallManager.FirewallStatus.UNTRACKED -> getString(R.string.ada_app_status_unknown)
        }
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(
            type = EventType.FW_RULE_MODIFIED,
            severity = Severity.LOW,
            message = msg,
            source = EventSource.MANAGER,
            userAction = true,
            details = details
        )
    }
}

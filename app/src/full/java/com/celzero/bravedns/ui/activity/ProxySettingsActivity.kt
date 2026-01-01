/*
 * Copyright 2023 RethinkDNS and its authors
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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.ProxyEndpoint
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.celzero.bravedns.ui.bottomsheet.OrbotDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.OrbotHelper
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.delay
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.isValidPort
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.RouterStats
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class ProxySettingsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val orbotHelper by inject<OrbotHelper>()
    private val eventLogger by inject<EventLogger>()
    private val proxyAppsMappingViewModel: ProxyAppsMappingViewModel by viewModel()

    private var socks5Enabled by mutableStateOf(false)
    private var socks5Desc by mutableStateOf("")
    private var socks5Loading by mutableStateOf(false)
    private var httpProxyEnabled by mutableStateOf(false)
    private var httpProxyDesc by mutableStateOf("")
    private var httpProxyLoading by mutableStateOf(false)
    private var orbotDesc by mutableStateOf("")
    private var wireguardDesc by mutableStateOf("")
    private var canEnableProxy by mutableStateOf(true)
    private var showVpnLockdownDesc by mutableStateOf(false)
    private var isRefreshing by mutableStateOf(false)

    companion object {
        private const val REFRESH_TIMEOUT: Long = 4000
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            RethinkTheme {
                ProxySettingsScreen()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        refreshOrbotUi()
        displayHttpProxyUi()
        displaySocks5Ui()
        displayWireguardUi()
        handleProxyUi()
    }

    private fun refresh() {
        isRefreshing = true
        io { VpnController.refreshOrPauseOrResumeOrReAddProxies() }
        delay(REFRESH_TIMEOUT, lifecycleScope) {
            isRefreshing = false
            showToastUiCentered(this, getString(R.string.dc_refresh_toast), Toast.LENGTH_SHORT)
        }
    }

    /** Prompt user to download the Orbot app based on the current BUILDCONFIG flavor. */
    private fun showOrbotInstallDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.orbot_install_dialog_title)
        builder.setMessage(R.string.orbot_install_dialog_message)
        builder.setPositiveButton(getString(R.string.orbot_install_dialog_positive)) { _, _ ->
            handleOrbotInstall()
        }
        builder.setNegativeButton(getString(R.string.lbl_dismiss)) { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton(getString(R.string.orbot_install_dialog_neutral)) { _, _ ->
            launchOrbotWebsite()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun launchOrbotWebsite() {
        openUrl(this, getString(R.string.orbot_website_link))
    }

    private fun handleOrbotInstall() {
        startOrbotInstallActivity(orbotHelper.getIntentForDownload())
    }

    private fun startOrbotInstallActivity(intent: Intent?) {
        if (intent == null) {
            showToastUiCentered(
                this,
                getString(R.string.orbot_install_dialog_error),
                Toast.LENGTH_SHORT
            )
            return
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToastUiCentered(this, getString(R.string.orbot_install_dialog_error), Toast.LENGTH_SHORT)
        }
    }

    private fun handleOrbotUiEvent() {
        lifecycleScope.launch {
            if (!FirewallManager.isOrbotInstalled()) {
                showOrbotInstallDialog()
                return@launch
            }

            if (!VpnController.hasTunnel()) {
                showToastUiCentered(
                    this@ProxySettingsActivity,
                    getString(R.string.settings_socks5_vpn_disabled_error),
                    Toast.LENGTH_SHORT
                )
                return@launch
            }

            OrbotDialog(this@ProxySettingsActivity, proxyAppsMappingViewModel).show()
        }
    }

    private fun openWireguardActivity() {
        startActivity(Intent(this, WgMainActivity::class.java))
    }

    private fun displayHttpProxyUi() {
        httpProxyEnabled = appConfig.isCustomHttpProxyEnabled()
        httpProxyDesc = getString(R.string.settings_https_desc)

        if (!appConfig.isCustomHttpProxyEnabled()) return

        io {
            val endpoint = try {
                appConfig.getHttpProxyDetails()
            } catch (e: Exception) {
                Napier.e("Error fetching HTTP proxy details in displayHttpProxyUi: ${e.message}", e)
                null
            }
            if (endpoint == null) {
                uiCtx {
                    showToastUiCentered(this, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                }
                return@io
            }
            val m = ProxyManager.ProxyMode.get(endpoint.proxyMode) ?: return@io
            if (!m.isCustomHttp()) return@io

            uiCtx {
                if (httpProxyEnabled) {
                    httpProxyDesc = getString(R.string.settings_http_proxy_desc, endpoint.proxyIP)
                }
            }
        }
    }

    private fun displayWireguardUi() {
        val activeWgs = WireguardManager.getActiveConfigs()
        if (activeWgs.isEmpty()) {
            wireguardDesc = getString(R.string.wireguard_description)
            return
        }
        io {
            var wgStatus = ""
            activeWgs.forEach {
                val id = ProxyManager.ID_WG_BASE + it.getId()
                val statusPair = VpnController.getProxyStatusById(id)
                val stats = VpnController.getProxyStats(id)
                val dnsStatusId = VpnController.getDnsStatus(id)

                val statusText =
                    if (statusPair.first == Backend.TPU) {
                        getString(UIUtils.getProxyStatusStringRes(UIUtils.ProxyStatus.TPU.id))
                            .replaceFirstChar(Char::titlecase)
                    } else if (dnsStatusId != null && isDnsError(dnsStatusId)) {
                        getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
                    } else {
                        getProxyStatusText(statusPair, stats)
                    }

                wgStatus +=
                    getString(
                        R.string.ci_ip_label,
                        it.getName(),
                        statusText.padStart(1, ' ')
                    ) + "\n"
                Napier.d("current proxy status for $id: $statusText")
            }
            wgStatus = wgStatus.trimEnd()
            uiCtx { wireguardDesc = wgStatus }
        }
    }

    private fun isDnsError(statusId: Long?): Boolean {
        if (statusId == null) return true

        val s = Transaction.Status.fromId(statusId)
        return s == Transaction.Status.BAD_QUERY ||
            s == Transaction.Status.BAD_RESPONSE ||
            s == Transaction.Status.NO_RESPONSE ||
            s == Transaction.Status.SEND_FAIL ||
            s == Transaction.Status.CLIENT_ERROR ||
            s == Transaction.Status.INTERNAL_ERROR ||
            s == Transaction.Status.TRANSPORT_ERROR
    }

    private fun getProxyStatusText(statusPair: Pair<Long?, String>, stats: RouterStats?): String {
        val status = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
        return getStatusText(status, stats, statusPair.second)
    }

    private fun getStatusText(
        status: UIUtils.ProxyStatus?,
        stats: RouterStats?,
        errMsg: String?
    ): String {
        if (status == null) {
            val txt =
                if (!errMsg.isNullOrEmpty()) {
                    getString(R.string.status_waiting) + " ($errMsg)"
                } else {
                    getString(R.string.status_waiting)
                }
            return txt.replaceFirstChar(Char::titlecase)
        }

        val now = System.currentTimeMillis()
        val lastOk = stats?.lastOK ?: 0L
        val since = stats?.since ?: 0L
        if (now - since > WG_UPTIME_THRESHOLD && lastOk == 0L) {
            return getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
        }

        val baseText =
            getString(UIUtils.getProxyStatusStringRes(status.id)).replaceFirstChar(Char::titlecase)

        val handshakeTime =
            if (stats != null && stats.lastOK > 0L) {
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    stats.lastOK,
                    now,
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else {
                null
            }

        return if (stats?.lastOK != 0L && handshakeTime != null) {
            getString(R.string.about_version_install_source, baseText, handshakeTime)
        } else {
            baseText
        }
    }

    private fun displaySocks5Ui() {
        socks5Enabled = appConfig.isCustomSocks5Enabled()
        socks5Loading = false
        socks5Desc = getString(R.string.settings_socks_forwarding_default_desc)

        if (!socks5Enabled) return

        io {
            val endpoint: ProxyEndpoint? = appConfig.getSocks5ProxyDetails()
            if (endpoint == null) {
                uiCtx {
                    showToastUiCentered(
                        this,
                        getString(R.string.blocklist_update_check_failure),
                        Toast.LENGTH_SHORT
                    )
                }
                return@io
            }
            val m = ProxyManager.ProxyMode.get(endpoint.proxyMode) ?: return@io
            if (!m.isCustomSocks5()) return@io

            val desc =
                if (endpoint.proxyAppName.isNullOrBlank() ||
                    endpoint.proxyAppName.equals(getString(R.string.settings_app_list_default_app))
                ) {
                    getString(
                        R.string.settings_socks_forwarding_desc_no_app,
                        endpoint.proxyIP,
                        endpoint.proxyPort.toString()
                    )
                } else {
                    val app = FirewallManager.getAppInfoByPackage(endpoint.proxyAppName!!)
                    if (app == null) {
                        getString(
                            R.string.settings_socks_forwarding_desc_no_app,
                            endpoint.proxyIP,
                            endpoint.proxyPort.toString()
                        )
                    } else {
                        getString(
                            R.string.settings_socks_forwarding_desc,
                            endpoint.proxyIP,
                            endpoint.proxyPort.toString(),
                            app.appName
                        )
                    }
                }

            uiCtx { socks5Desc = desc }
        }
    }

    private fun refreshOrbotUi() {
        io {
            val isOrbotInstalled = FirewallManager.isOrbotInstalled()
            val isOrbotDns = appConfig.isOrbotDns()
            uiCtx {
                if (!isOrbotInstalled) {
                    orbotDesc = getString(R.string.settings_orbot_install_desc)
                    return@uiCtx
                }

                if (!appConfig.isOrbotProxyEnabled()) {
                    orbotDesc = getString(R.string.orbot_bs_status_4)
                    return@uiCtx
                }

                orbotDesc =
                    when (appConfig.getProxyType()) {
                        AppConfig.ProxyType.HTTP.name -> {
                            getString(R.string.orbot_bs_status_2)
                        }
                        AppConfig.ProxyType.SOCKS5.name -> {
                            if (isOrbotDns) {
                                getString(
                                    R.string.orbot_bs_status_1,
                                    getString(R.string.orbot_status_arg_3)
                                )
                            } else {
                                getString(
                                    R.string.orbot_bs_status_1,
                                    getString(R.string.orbot_status_arg_2)
                                )
                            }
                        }
                        AppConfig.ProxyType.HTTP_SOCKS5.name -> {
                            if (isOrbotDns) {
                                getString(
                                    R.string.orbot_bs_status_3,
                                    getString(R.string.orbot_status_arg_3)
                                )
                            } else {
                                getString(
                                    R.string.orbot_bs_status_3,
                                    getString(R.string.orbot_status_arg_2)
                                )
                            }
                        }
                        else -> getString(R.string.orbot_bs_status_4)
                    }
            }
        }
    }

    private fun showSocks5ProxyDialog(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String
    ) {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(false)
        dialog.window?.attributes = lp

        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                Socks5ProxyDialogContent(
                    endpoint = endpoint,
                    appNames = appNames,
                    appName = appName,
                    onDismiss = { dialog.dismiss() },
                    onApply = { selection, ip, port, userName, password, udpBlocked, excludeApps ->
                        persistentState.excludeAppsInProxy = !excludeApps
                        persistentState.setUdpBlocked(udpBlocked)
                        insertSocks5Endpoint(
                            endpoint.id,
                            ip,
                            port,
                            selection,
                            userName,
                            password,
                            udpBlocked
                        )
                        socks5Desc =
                            if (selection == getString(R.string.settings_app_list_default_app)) {
                                getString(
                                    R.string.settings_socks_forwarding_desc_no_app,
                                    ip,
                                    port.toString()
                                )
                            } else {
                                getString(
                                    R.string.settings_socks_forwarding_desc,
                                    ip,
                                    port.toString(),
                                    selection
                                )
                            }
                        socks5Enabled = true
                        dialog.dismiss()
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    private fun handleProxyUi() {
        val vpnLockdown = VpnController.isVpnLockdown()
        showVpnLockdownDesc = vpnLockdown
        canEnableProxy = !vpnLockdown
    }

    private fun showHttpProxyDialog(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String?
    ) {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(false)
        dialog.window?.attributes = lp

        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                HttpProxyDialogContent(
                    endpoint = endpoint,
                    appNames = appNames,
                    appName = appName,
                    onDismiss = { dialog.dismiss() },
                    onApply = { selection, ip, port, userName, password ->
                        insertHttpProxyEndpointDB(endpoint.id, ip, port, selection)
                        httpProxyDesc = getString(R.string.settings_http_proxy_desc, ip)
                        httpProxyEnabled = true
                        dialog.dismiss()
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProxySettingsScreen() {
        val scrollState = rememberScrollState()
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(text = getString(R.string.settings_proxy_header)) })
            }
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeader(text = getString(R.string.lbl_wireguard).lowercase())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = wireguardDesc, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { refresh() }, enabled = canEnableProxy) {
                        val transition = rememberInfiniteTransition(label = "wgRefresh")
                        val rotation by
                            transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(750, easing = LinearEasing)
                                    ),
                                label = "wgRefreshRotation"
                            )
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }
                Button(onClick = { openWireguardActivity() }, enabled = canEnableProxy) {
                    Text(text = getString(R.string.lbl_view))
                }

                SectionHeader(text = getString(R.string.orbot).lowercase())
                Text(text = orbotDesc, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { handleOrbotUiEvent() }, enabled = canEnableProxy) {
                    Text(text = getString(R.string.lbl_configure))
                }

                SectionHeader(text = getString(R.string.category_name_others).lowercase())

                ProxyToggleRow(
                    title = getString(R.string.settings_socks5_heading),
                    description = socks5Desc,
                    checked = socks5Enabled,
                    enabled = canEnableProxy,
                    loading = socks5Loading,
                    onToggle = { checked ->
                        if (!checked) {
                            appConfig.removeProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                            socks5Desc = getString(R.string.settings_socks_forwarding_default_desc)
                            socks5Enabled = false
                            logEvent("Custom SOCKS5 Proxy disabled", "disabled custom SOCKS5 proxy")
                            return@ProxyToggleRow
                        }

                        if (appConfig.getBraveMode().isDnsMode()) {
                            socks5Enabled = false
                            return@ProxyToggleRow
                        }

                        if (!appConfig.canEnableSocks5Proxy()) {
                            val s = persistentState.proxyProvider.lowercase().replaceFirstChar(Char::titlecase)
                            showToastUiCentered(
                                this@ProxySettingsActivity,
                                getString(R.string.settings_socks5_disabled_error, s),
                                Toast.LENGTH_SHORT
                            )
                            socks5Enabled = false
                            return@ProxyToggleRow
                        }

                        io {
                            val endpoint = appConfig.getSocks5ProxyDetails()
                            if (endpoint == null) {
                                uiCtx {
                                    showToastUiCentered(
                                        this@ProxySettingsActivity,
                                        getString(R.string.blocklist_update_check_failure),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                                return@io
                            }
                            val packageName = endpoint.proxyAppName
                            val app = FirewallManager.getAppInfoByPackage(packageName)?.appName ?: ""
                            val appNames: MutableList<String> = ArrayList()
                            appNames.add(getString(R.string.settings_app_list_default_app))
                            appNames.addAll(
                                FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity)
                            )
                            uiCtx { showSocks5ProxyDialog(endpoint, appNames, app) }
                        }
                    }
                )

                ProxyToggleRow(
                    title = getString(R.string.settings_https_heading),
                    description = httpProxyDesc,
                    checked = httpProxyEnabled,
                    enabled = canEnableProxy,
                    loading = httpProxyLoading,
                    onToggle = { checked ->
                        if (!checked) {
                            appConfig.removeProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                            httpProxyDesc = getString(R.string.settings_https_desc)
                            httpProxyEnabled = false
                            return@ProxyToggleRow
                        }

                        if (appConfig.getBraveMode().isDnsMode()) {
                            httpProxyEnabled = false
                            return@ProxyToggleRow
                        }

                        if (!appConfig.canEnableHttpProxy()) {
                            val s = persistentState.proxyProvider.lowercase().replaceFirstChar(Char::titlecase)
                            showToastUiCentered(
                                this@ProxySettingsActivity,
                                getString(R.string.settings_https_disabled_error, s),
                                Toast.LENGTH_SHORT
                            )
                            httpProxyEnabled = false
                            return@ProxyToggleRow
                        }

                        io {
                            val endpoint = try {
                                appConfig.getHttpProxyDetails()
                            } catch (e: Exception) {
                                Napier.e("err fetching HTTP proxy details: ${e.message}", e)
                                null
                            }
                            if (endpoint == null) {
                                uiCtx {
                                    showToastUiCentered(
                                        this@ProxySettingsActivity,
                                        getString(R.string.blocklist_update_check_failure),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                                return@io
                            }
                            val appNames: MutableList<String> = ArrayList()
                            appNames.add(getString(R.string.settings_app_list_default_app))
                            appNames.addAll(
                                FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity)
                            )
                            val app = FirewallManager.getAppInfoByPackage(endpoint.proxyAppName)
                            uiCtx { showHttpProxyDialog(endpoint, appNames, app?.appName) }
                        }
                    }
                )

                if (showVpnLockdownDesc) {
                    Text(
                        text = getString(R.string.settings_lock_down_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.7f)
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }

    @Composable
    private fun ProxyToggleRow(
        title: String,
        description: String,
        checked: Boolean,
        enabled: Boolean,
        loading: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().clickable(enabled = enabled && !loading) {
                    onToggle(!checked)
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled && !loading)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    private fun proxyModeLabel(mode: ProxyManager.ProxyMode): String {
        return when (mode) {
            ProxyManager.ProxyMode.SOCKS5 -> getString(R.string.lbl_socks5)
            ProxyManager.ProxyMode.HTTP -> getString(R.string.lbl_http)
            ProxyManager.ProxyMode.ORBOT_SOCKS5 -> getString(R.string.orbot_socks5)
            ProxyManager.ProxyMode.ORBOT_HTTP -> getString(R.string.orbot_http)
        }
    }

    @Composable
    private fun Socks5ProxyDialogContent(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String,
        onDismiss: () -> Unit,
        onApply: (String, String, Int, String, String, Boolean, Boolean) -> Unit
    ) {
        var appNameSelected by remember { mutableStateOf(appName) }
        val initialMode = ProxyManager.ProxyMode.get(endpoint.proxyMode) ?: ProxyManager.ProxyMode.SOCKS5
        var selectedProxyMode by remember { mutableStateOf(proxyModeLabel(initialMode)) }
        var ip by remember { mutableStateOf(endpoint.proxyIP.orEmpty()) }
        var port by remember { mutableStateOf(endpoint.proxyPort.toString()) }
        var userName by remember { mutableStateOf(endpoint.userName.orEmpty()) }
        var password by remember { mutableStateOf(endpoint.password.orEmpty()) }
        var errorText by remember { mutableStateOf("") }
        var udpBlocked by remember { mutableStateOf(persistentState.getUdpBlocked()) }
        var excludeApps by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }
        var expanded by remember { mutableStateOf(false) }
        var appExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = getString(R.string.settings_socks5_heading), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text(getString(R.string.settings_socks5_dialog_hint_ip)) }
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(getString(R.string.settings_socks5_dialog_hint_port)) }
            )

            TextButton(onClick = { expanded = true }) {
                Text(text = selectedProxyMode.ifBlank { getString(R.string.lbl_socks5) })
            }
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ProxyManager.ProxyMode.entries.forEach { mode ->
                    if (mode.isCustomSocks5()) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(proxyModeLabel(mode)) },
                            onClick = {
                                selectedProxyMode = proxyModeLabel(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text(getString(R.string.settings_dns_proxy_dialog_username)) }
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(getString(R.string.settings_dns_proxy_dialog_password)) }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = udpBlocked, onCheckedChange = { udpBlocked = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = getString(R.string.settings_udp_block))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = excludeApps, onCheckedChange = { excludeApps = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = getString(R.string.settings_exclude_apps_in_proxy))
            }

            TextButton(onClick = { appExpanded = true }) {
                Text(text = appNameSelected.ifBlank { getString(R.string.settings_app_list_default_app) })
            }
            androidx.compose.material3.DropdownMenu(expanded = appExpanded, onDismissRequest = { appExpanded = false }) {
                appNames.forEach { name ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            appNameSelected = name
                            appExpanded = false
                        }
                    )
                }
            }

            if (errorText.isNotEmpty()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(text = getString(R.string.lbl_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val portValue = port.toIntOrNull()
                    if (ip.isBlank() || portValue == null || !isValidPort(portValue)) {
                        errorText = getString(R.string.settings_dns_proxy_invalid_port)
                        return@Button
                    }
                    onApply(appNameSelected, ip, portValue, userName, password, udpBlocked, excludeApps)
                }) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    @Composable
    private fun HttpProxyDialogContent(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String?,
        onDismiss: () -> Unit,
        onApply: (String, String, Int, String, String) -> Unit
    ) {
        var appNameSelected by remember { mutableStateOf(appName.orEmpty()) }
        var ip by remember { mutableStateOf(endpoint.proxyIP.orEmpty()) }
        var port by remember { mutableStateOf(endpoint.proxyPort.toString()) }
        var userName by remember { mutableStateOf(endpoint.userName.orEmpty()) }
        var password by remember { mutableStateOf(endpoint.password.orEmpty()) }
        var errorText by remember { mutableStateOf("") }
        var appExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = getString(R.string.http_proxy_dialog_heading), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text(getString(R.string.lbl_ip)) }
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(getString(R.string.settings_socks5_dialog_hint_port)) }
            )

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text(getString(R.string.settings_dns_proxy_dialog_username)) }
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(getString(R.string.settings_dns_proxy_dialog_password)) }
            )

            TextButton(onClick = { appExpanded = true }) {
                Text(text = appNameSelected.ifBlank { getString(R.string.settings_app_list_default_app) })
            }
            androidx.compose.material3.DropdownMenu(expanded = appExpanded, onDismissRequest = { appExpanded = false }) {
                appNames.forEach { name ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            appNameSelected = name
                            appExpanded = false
                        }
                    )
                }
            }

            if (errorText.isNotEmpty()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(text = getString(R.string.lbl_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val portValue = port.toIntOrNull()
                    if (ip.isBlank() || portValue == null || !isValidPort(portValue)) {
                        errorText = getString(R.string.settings_dns_proxy_invalid_port)
                        return@Button
                    }
                    onApply(appNameSelected, ip, portValue, userName, password)
                }) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    private fun insertSocks5Endpoint(
        id: Int,
        ip: String,
        port: Int,
        appName: String,
        userName: String,
        password: String,
        udpBlocked: Boolean
    ) {
        val proxyMode = ProxyManager.ProxyMode.SOCKS5

        val proxy =
            constructProxy(
                id,
                ip,
                port,
                appName,
                proxyMode.value,
                userName,
                password,
                udpBlocked
            )

        if (proxy != null) {
            io { appConfig.updateCustomSocks5Proxy(proxy) }
            logEvent("SOCKS5 proxy added", "SOCKS5 proxy: $ip:$port")
        }
    }

    private fun insertHttpProxyEndpointDB(id: Int, ip: String, port: Int, appName: String) {
        io {
            val appNameResolved = appName.ifBlank { getString(R.string.settings_app_list_default_app) }
            val proxy =
                constructProxy(
                    id,
                    ip,
                    port,
                    appNameResolved,
                    ProxyManager.ProxyMode.HTTP.value,
                    null,
                    null,
                    persistentState.getUdpBlocked()
                )
            if (proxy != null) {
                appConfig.updateCustomHttpProxy(proxy)
                logEvent("HTTP proxy added", "HTTP proxy: $ip")
            }
        }
    }

    private fun constructProxy(
        id: Int,
        ip: String,
        port: Int,
        appName: String,
        proxyMode: Int,
        userName: String?,
        password: String?,
        udpBlocked: Boolean
    ): ProxyEndpoint? {
        if (Utilities.normalizeIp(ip) == null) {
            showToastUiCentered(
                this,
                getString(R.string.settings_dns_proxy_invalid_ip),
                Toast.LENGTH_SHORT
            )
            return null
        }

        if (!isValidPort(port)) {
            showToastUiCentered(
                this,
                getString(R.string.settings_dns_proxy_invalid_port),
                Toast.LENGTH_SHORT
            )
            return null
        }

        return ProxyEndpoint(
            id = id,
            proxyName = appName,
            proxyMode = proxyMode,
            proxyType = ProxyEndpoint.DEFAULT_PROXY_TYPE,
            proxyAppName = appName,
            proxyIP = ip,
            proxyPort = port,
            userName = userName,
            password = password,
            isUDP = udpBlocked,
            isSelected = true,
            isCustom = true,
            modifiedDataTime = System.currentTimeMillis(),
            latency = 0
        )
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(
            type = EventType.PROXY_SWITCH,
            severity = Severity.MEDIUM,
            message = msg,
            source = EventSource.MANAGER,
            userAction = true,
            details = details
        )
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

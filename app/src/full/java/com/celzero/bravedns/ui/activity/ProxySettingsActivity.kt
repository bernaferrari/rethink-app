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

import Logger
import Logger.LOG_TAG_PROXY
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.ProxyEndpoint
import com.celzero.bravedns.database.ProxyEndpoint.Companion.DEFAULT_PROXY_TYPE
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.FragmentProxyConfigureBinding
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.celzero.bravedns.ui.bottomsheet.OrbotDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class ProxySettingsActivity : AppCompatActivity(R.layout.fragment_proxy_configure) {
    private val b by viewBinding(FragmentProxyConfigureBinding::bind)

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val orbotHelper by inject<OrbotHelper>()
    private val eventLogger by inject<EventLogger>()
    private val proxyAppsMappingViewModel: ProxyAppsMappingViewModel by viewModel()
    private lateinit var animation: Animation

    companion object {
        private const val REFRESH_TIMEOUT: Long = 4000
        private const val ANIMATION_DURATION = 750L
        private const val ANIMATION_REPEAT_COUNT = -1
        private const val ANIMATION_PIVOT_VALUE = 0.5f
        private const val ANIMATION_START_DEGREE = 0.0f
        private const val ANIMATION_END_DEGREE = 360.0f
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            UI_MODE_NIGHT_YES
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

        initAnimation()
        initView()
        initClickListeners()
    }

    private fun initAnimation() {
        animation =
            RotateAnimation(
                ANIMATION_START_DEGREE,
                ANIMATION_END_DEGREE,
                Animation.RELATIVE_TO_SELF,
                ANIMATION_PIVOT_VALUE,
                Animation.RELATIVE_TO_SELF,
                ANIMATION_PIVOT_VALUE
            )
        animation.repeatCount = ANIMATION_REPEAT_COUNT
        animation.duration = ANIMATION_DURATION
    }

    override fun onResume() {
        super.onResume()
        refreshOrbotUi()
        handleProxyUi()
        displayWireguardUi()
    }

    private fun initView() {
        b.settingsActivityHttpProxyProgress.visibility = View.GONE
        b.settingsWireguardTitle.text = getString(R.string.lbl_wireguard).lowercase()
        b.orbotTitle.text = getString(R.string.orbot).lowercase()
        b.otherTitle.text = getString(R.string.category_name_others).lowercase()

        /*if (RpnProxyManager.isRpnEnabled()) {
            b.rpnTitle.visibility = View.VISIBLE
            b.settingsActivityRpnContainer.visibility = View.VISIBLE
        } else {
            b.rpnTitle.visibility = View.GONE
            b.settingsActivityRpnContainer.visibility = View.GONE
        }*/
        b.rpnTitle.visibility = View.GONE
        b.settingsActivityRpnContainer.visibility = View.GONE

        displayHttpProxyUi()
        displaySocks5Ui()
    }

    private fun initClickListeners() {

        b.settingsActivityRpnContainer.setOnClickListener {
            // create an empty activity and load RethinkPlusDashboard fragment
        }

        b.wgRefresh.setOnClickListener { refresh() }

        b.settingsActivitySocks5Rl.setOnClickListener {
            b.settingsActivitySocks5Switch.isChecked = !b.settingsActivitySocks5Switch.isChecked
        }

        b.settingsActivitySocks5Switch.setOnCheckedChangeListener {
            _: CompoundButton,
            checked: Boolean ->
            if (!checked) {
                appConfig.removeProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                b.settingsActivitySocks5Desc.text =
                    getString(R.string.settings_socks_forwarding_default_desc)
                logEvent("Custom SOCKS5 Proxy disabled", "disabled custom SOCKS5 proxy")
                return@setOnCheckedChangeListener
            }

            if (appConfig.getBraveMode().isDnsMode()) {
                b.settingsActivitySocks5Switch.isChecked = false
                return@setOnCheckedChangeListener
            }

            if (!appConfig.canEnableSocks5Proxy()) {
                val s = persistentState.proxyProvider.lowercase().replaceFirstChar(Char::titlecase)
                showToastUiCentered(
                    this,
                    getString(R.string.settings_socks5_disabled_error, s),
                    Toast.LENGTH_SHORT
                )

                b.settingsActivitySocks5Switch.isChecked = false
                return@setOnCheckedChangeListener
            }
            io {
                val endpoint = appConfig.getSocks5ProxyDetails()
                if (endpoint == null) {
                    uiCtx {
                        showToastUiCentered(this, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    }
                    return@io
                }
                val packageName = endpoint.proxyAppName
                val app = FirewallManager.getAppInfoByPackage(packageName)?.appName ?: ""
                val m = ProxyManager.ProxyMode.get(endpoint.proxyMode)
                if (m?.isCustomSocks5() == true) {
                    val appNames: MutableList<String> = ArrayList()
                    appNames.add(getString(R.string.settings_app_list_default_app))
                    appNames.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity))
                    uiCtx { showSocks5ProxyDialog(endpoint, appNames, app) }
                } else {
                    val appNames: MutableList<String> = ArrayList()
                    appNames.add(getString(R.string.settings_app_list_default_app))
                    appNames.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity))
                    uiCtx { showSocks5ProxyDialog(endpoint, appNames, app) }
                }
            }
        }

        b.settingsActivityOrbotImg.setOnClickListener { handleOrbotUiEvent() }

        b.settingsActivityOrbotContainer.setOnClickListener { handleOrbotUiEvent() }

        b.settingsActivityWireguardContainer.setOnClickListener { openWireguardActivity() }

        b.settingsActivityWireguardImg.setOnClickListener { openWireguardActivity() }

        b.settingsActivityHttpProxyContainer.setOnClickListener {
            b.settingsActivityHttpProxySwitch.isChecked =
                !b.settingsActivityHttpProxySwitch.isChecked
        }

        b.settingsActivityHttpProxySwitch.setOnCheckedChangeListener {
            _: CompoundButton,
            checked: Boolean ->
            if (!checked) {
                appConfig.removeProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                b.settingsActivityHttpProxyDesc.text = getString(R.string.settings_https_desc)
                return@setOnCheckedChangeListener
            }

            if (appConfig.getBraveMode().isDnsMode()) {
                b.settingsActivityHttpProxySwitch.isChecked = false
                return@setOnCheckedChangeListener
            }

            if (!appConfig.canEnableHttpProxy()) {
                val s = persistentState.proxyProvider.lowercase().replaceFirstChar(Char::titlecase)
                showToastUiCentered(
                    this,
                    getString(R.string.settings_https_disabled_error, s),
                    Toast.LENGTH_SHORT
                )
                b.settingsActivityHttpProxySwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            io {
                val endpoint = try {
                    appConfig.getHttpProxyDetails()
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_PROXY, "err fetching HTTP proxy details: ${e.message}", e)
                    null
                }
                if (endpoint == null) {
                    uiCtx {
                        showToastUiCentered(this, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    }
                    return@io
                }
                val packageName = endpoint.proxyAppName
                val app = FirewallManager.getAppInfoByPackage(packageName)
                val m = ProxyManager.ProxyMode.get(endpoint.proxyMode)
                if (m?.isCustomHttp() == true) {
                    val appNames: MutableList<String> = ArrayList()
                    appNames.add(getString(R.string.settings_app_list_default_app))
                    appNames.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity))
                    uiCtx { showHttpProxyDialog(endpoint, appNames, app?.appName) }
                } else {
                    val appNames: MutableList<String> = ArrayList()
                    appNames.add(getString(R.string.settings_app_list_default_app))
                    appNames.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this@ProxySettingsActivity))
                    uiCtx { showHttpProxyDialog(endpoint, appNames, app?.appName) }
                }
            }
        }
    }

    private fun refresh() {
        b.wgRefresh.isEnabled = false
        b.wgRefresh.animation = animation
        b.wgRefresh.startAnimation(animation)
        io { VpnController.refreshOrPauseOrResumeOrReAddProxies() }
        delay(REFRESH_TIMEOUT, lifecycleScope) {
            b.wgRefresh.isEnabled = true
            b.wgRefresh.clearAnimation()
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
                getString(R.string.orbot_install_activity_error),
                Toast.LENGTH_SHORT
            )
            return
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToastUiCentered(
                this,
                getString(R.string.orbot_install_activity_error),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun handleOrbotUiEvent() {
        io {
            val isOrbotInstalled = FirewallManager.isOrbotInstalled()
            uiCtx {
                if (!isOrbotInstalled) {
                    showOrbotInstallDialog()
                    return@uiCtx
                }

                if (!appConfig.canEnableOrbotProxy()) {
                    val s =
                        persistentState.proxyProvider.lowercase().replaceFirstChar(Char::titlecase)
                    if (s.lowercase() == AppConfig.ProxyProvider.CUSTOM.name.lowercase()) {
                        showToastUiCentered(
                            this,
                            getString(R.string.settings_orbot_disabled_error),
                            Toast.LENGTH_SHORT
                        )
                    } else {
                        showToastUiCentered(
                            this,
                            getString(R.string.settings_socks5_disabled_error, s),
                            Toast.LENGTH_SHORT
                        )
                    }
                    return@uiCtx
                }

                openOrbotBottomSheet()
            }
        }
    }

    private fun openWireguardActivity() {
        val intent = Intent(this, WgMainActivity::class.java)
        startActivity(intent)
    }

    private fun openOrbotBottomSheet() {
        if (!VpnController.hasTunnel()) {
            showToastUiCentered(
                this,
                getString(R.string.settings_socks5_vpn_disabled_error),
                Toast.LENGTH_SHORT
            )
            return
        }

        OrbotDialog(this, proxyAppsMappingViewModel).show()
    }

    private fun displayHttpProxyUi() {
        val isCustomHttpProxyEnabled = appConfig.isCustomHttpProxyEnabled()
        b.settingsActivityHttpProxyContainer.visibility = View.VISIBLE
        b.settingsActivityHttpProxySwitch.isChecked = isCustomHttpProxyEnabled

        if (!appConfig.isCustomHttpProxyEnabled()) return

        io {
            val endpoint = try {
                appConfig.getHttpProxyDetails()
            } catch (e: Exception) {
                Logger.e(LOG_TAG_PROXY, "Error fetching HTTP proxy details in displayHttpProxyUi: ${e.message}", e)
                null
            }
            if (endpoint == null) {
                uiCtx {
                    showToastUiCentered(this, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                }
                return@io
            }
            val m = ProxyManager.ProxyMode.get(endpoint.proxyMode) ?: return@io

            // only update below ui if its custom http proxy
            if (!m.isCustomHttp()) return@io

            uiCtx {
                b.settingsActivityHttpProxyContainer.visibility = View.VISIBLE
                if (b.settingsActivityHttpProxySwitch.isChecked) {
                    b.settingsActivityHttpProxyDesc.text =
                        getString(R.string.settings_http_proxy_desc, endpoint.proxyIP)
                }
            }
        }
    }

    private fun displayWireguardUi() {

        val activeWgs = WireguardManager.getActiveConfigs()

        if (activeWgs.isEmpty()) {
            b.settingsActivityWireguardDesc.text = getString(R.string.wireguard_description)
            return
        }
        io {
            var wgStatus = ""
            activeWgs.forEach {
                val id = ProxyManager.ID_WG_BASE + it.getId()
                val statusPair = VpnController.getProxyStatusById(id)
                val stats = VpnController.getProxyStats(id)
                val dnsStatusId = VpnController.getDnsStatus(id)


                val statusText = if (statusPair.first == Backend.TPU) {
                    getString(UIUtils.getProxyStatusStringRes(UIUtils.ProxyStatus.TPU.id)).replaceFirstChar(Char::titlecase)
                } else if (dnsStatusId != null && isDnsError(dnsStatusId)) {
                    // DNS is failing, show failing status
                    getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
                } else {
                    // DNS is okay, show proxy status
                    getProxyStatusText(statusPair, stats)
                }

                wgStatus += getString(
                    R.string.ci_ip_label,
                    it.getName(),
                    statusText.padStart(1, ' ')
                ) + "\n"
                Logger.d(LOG_TAG_PROXY, "current proxy status for $id: $statusText")
            }
            wgStatus = wgStatus.trimEnd()
            uiCtx {
                b.settingsActivityWireguardDesc.text = wgStatus
            }
        }
    }

    private fun isDnsError(statusId: Long?): Boolean {
        if (statusId == null) return true

        val s = Transaction.Status.fromId(statusId)
        return s == Transaction.Status.BAD_QUERY || s == Transaction.Status.BAD_RESPONSE ||
               s == Transaction.Status.NO_RESPONSE || s == Transaction.Status.SEND_FAIL ||
               s == Transaction.Status.CLIENT_ERROR || s == Transaction.Status.INTERNAL_ERROR ||
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
            val txt = if (errMsg != null && errMsg.isNotEmpty()) {
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

        val baseText = getString(UIUtils.getProxyStatusStringRes(status.id))
            .replaceFirstChar(Char::titlecase)

        val handshakeTime = if (stats != null && stats.lastOK > 0L) {
            DateUtils.getRelativeTimeSpanString(
                stats.lastOK,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
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
        val isCustomSocks5Enabled = appConfig.isCustomSocks5Enabled()

        b.settingsActivitySocks5Progress.visibility = View.GONE
        b.settingsActivitySocks5Switch.isChecked = isCustomSocks5Enabled

        if (!isCustomSocks5Enabled) {
            return
        }

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

            // only update below ui if its custom http proxy
            if (!m.isCustomSocks5()) return@io

            if (
                endpoint.proxyAppName.isNullOrBlank() ||
                    endpoint.proxyAppName.equals(getString(R.string.settings_app_list_default_app))
            ) {
                uiCtx {
                    b.settingsActivitySocks5Desc.text =
                        getString(
                            R.string.settings_socks_forwarding_desc_no_app,
                            endpoint.proxyIP,
                            endpoint.proxyPort.toString()
                        )
                }
            } else {
                val app = FirewallManager.getAppInfoByPackage(endpoint.proxyAppName!!)
                if (app == null) {
                    uiCtx {
                        b.settingsActivitySocks5Desc.text =
                            getString(
                                R.string.settings_socks_forwarding_desc_no_app,
                                endpoint.proxyIP,
                                endpoint.proxyPort.toString()
                            )
                    }
                } else {
                    uiCtx {
                        b.settingsActivitySocks5Desc.text =
                            getString(
                                R.string.settings_socks_forwarding_desc,
                                endpoint.proxyIP,
                                endpoint.proxyPort.toString(),
                                app.appName
                            )
                    }
                }
            }
        }
    }

    private fun refreshOrbotUi() {
        // Checks whether the Orbot is installed.
        // If not, then prompt the user for installation.
        // Else, enable the Orbot bottom sheet fragment.
        io {
            val isOrbotInstalled = FirewallManager.isOrbotInstalled()
            val isOrbotDns = appConfig.isOrbotDns()
            uiCtx {
                if (!isOrbotInstalled) {
                    b.settingsActivityHttpOrbotDesc.text =
                        getString(R.string.settings_orbot_install_desc)
                    return@uiCtx
                }

                if (!appConfig.isOrbotProxyEnabled()) {
                    b.settingsActivityHttpOrbotDesc.text = getString(R.string.orbot_bs_status_4)
                    return@uiCtx
                }

                when (appConfig.getProxyType()) {
                    AppConfig.ProxyType.HTTP.name -> {
                        b.settingsActivityHttpOrbotDesc.text = getString(R.string.orbot_bs_status_2)
                    }
                    AppConfig.ProxyType.SOCKS5.name -> {
                        if (isOrbotDns) {
                            b.settingsActivityHttpOrbotDesc.text =
                                getString(
                                    R.string.orbot_bs_status_1,
                                    getString(R.string.orbot_status_arg_3)
                                )
                        } else {
                            b.settingsActivityHttpOrbotDesc.text =
                                getString(
                                    R.string.orbot_bs_status_1,
                                    getString(R.string.orbot_status_arg_2)
                                )
                        }
                    }
                    AppConfig.ProxyType.HTTP_SOCKS5.name -> {
                        if (isOrbotDns) {
                            b.settingsActivityHttpOrbotDesc.text =
                                getString(
                                    R.string.orbot_bs_status_3,
                                    getString(R.string.orbot_status_arg_3)
                                )
                        } else {
                            b.settingsActivityHttpOrbotDesc.text =
                                getString(
                                    R.string.orbot_bs_status_3,
                                    getString(R.string.orbot_status_arg_2)
                                )
                        }
                    }
                    else -> {
                        b.settingsActivityHttpOrbotDesc.text = getString(R.string.orbot_bs_status_4)
                    }
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
                        if (selection == getString(R.string.settings_app_list_default_app)) {
                            b.settingsActivitySocks5Desc.text =
                                getString(
                                    R.string.settings_socks_forwarding_desc_no_app,
                                    ip,
                                    port.toString()
                                )
                        } else {
                            b.settingsActivitySocks5Desc.text =
                                getString(
                                    R.string.settings_socks_forwarding_desc,
                                    ip,
                                    port.toString(),
                                    selection
                                )
                        }
                        logEvent(
                            "Custom SOCKS5 Proxy set",
                            "custom SOCKS5 proxy to $ip:$port, app: $selection"
                        )
                        dialog.dismiss()
                    },
                    onCancel = {
                        b.settingsActivitySocks5Switch.isChecked = false
                        appConfig.removeProxy(AppConfig.ProxyType.SOCKS5, AppConfig.ProxyProvider.CUSTOM)
                        b.settingsActivitySocks5Desc.text =
                            getString(R.string.settings_socks_forwarding_default_desc)
                        dialog.dismiss()
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    private fun enableTcpProxy() {
        io { TcpProxyHelper.enable() }
    }

    // Should be in disabled state when the brave mode is in DNS only / Vpn in lockdown mode.
    private fun handleProxyUi() {
        val canEnableProxy = appConfig.canEnableProxy()

        if (canEnableProxy) {
            b.settingsActivityOrbotContainer.alpha = 1f
            b.settingsActivityVpnLockdownDesc.visibility = View.GONE
            b.settingsActivityWireguardContainer.alpha = 1f
            b.settingsActivitySocks5Rl.alpha = 1f
            b.settingsActivityHttpProxyContainer.alpha = 1f
            b.wgRefresh.visibility = View.VISIBLE
        } else {
            b.settingsActivityOrbotContainer.alpha = 0.5f
            b.settingsActivityWireguardContainer.alpha = 0.5f
            b.settingsActivityVpnLockdownDesc.visibility = View.VISIBLE
            b.settingsActivitySocks5Rl.alpha = 0.5f
            b.settingsActivityHttpProxyContainer.alpha = 0.5f
            b.wgRefresh.visibility = View.GONE
        }

        // Wireguard
        b.settingsActivityWireguardImg.isEnabled = canEnableProxy
        b.settingsActivityWireguardContainer.isEnabled = canEnableProxy
        // Orbot
        b.settingsActivityOrbotImg.isEnabled = canEnableProxy
        b.settingsActivityOrbotContainer.isEnabled = canEnableProxy
        // SOCKS5
        b.settingsActivitySocks5Switch.isEnabled = canEnableProxy
        // HTTP Proxy
        b.settingsActivityHttpProxySwitch.isEnabled = canEnableProxy
    }

    private fun showHttpProxyDialog(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String?
    ) {
        val defaultHost = "http://127.0.0.1:8118"
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.attributes = lp

        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                HttpProxyDialogContent(
                    endpoint = endpoint,
                    appNames = appNames,
                    appName = appName,
                    defaultHost = defaultHost,
                    onDismiss = { dialog.dismiss() },
                    onApply = { selection, host, excludeApps ->
                        insertHttpProxyEndpointDB(endpoint.id, host, selection)
                        dialog.dismiss()
                        persistentState.excludeAppsInProxy = !excludeApps
                        showToastUiCentered(
                            this,
                            getString(R.string.settings_http_proxy_toast_success),
                            Toast.LENGTH_SHORT
                        )
                        if (b.settingsActivityHttpProxySwitch.isChecked) {
                            b.settingsActivityHttpProxyDesc.text =
                                getString(R.string.settings_http_proxy_desc, host)
                        }
                        logEvent(
                            "Custom HTTP Proxy set",
                            "custom HTTP proxy to $host, app: $selection"
                        )
                    },
                    onCancel = {
                        dialog.dismiss()
                        appConfig.removeProxy(AppConfig.ProxyType.HTTP, AppConfig.ProxyProvider.CUSTOM)
                        b.settingsActivityHttpProxyDesc.text = getString(R.string.settings_https_desc)
                        b.settingsActivityHttpProxySwitch.isChecked = false
                    }
                )
            }
        }
        dialog.setView(composeView)
    }

    @Composable
    private fun Socks5ProxyDialogContent(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String,
        onDismiss: () -> Unit,
        onApply: (String, String, Int, String, String, Boolean, Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        val lockdown = VpnController.isVpnLockdown()
        var errorText by remember { mutableStateOf("") }
        var selectedIndex by remember {
            mutableStateOf(
                if (
                    !endpoint.proxyIP.isNullOrBlank() &&
                        !endpoint.proxyAppName.isNullOrBlank() &&
                        endpoint.proxyAppName != getString(R.string.settings_app_list_default_app)
                ) {
                    appNames.indexOf(appName).coerceAtLeast(0)
                } else {
                    0
                }
            )
        }
        var host by remember {
            mutableStateOf(endpoint.proxyIP ?: Constants.SOCKS_DEFAULT_IP)
        }
        var portText by remember {
            mutableStateOf(
                if (!endpoint.proxyIP.isNullOrBlank()) {
                    endpoint.proxyPort.toString()
                } else {
                    Constants.SOCKS_DEFAULT_PORT.toString()
                }
            )
        }
        var userName by remember { mutableStateOf(endpoint.userName ?: "") }
        var password by remember { mutableStateOf(endpoint.password ?: "") }
        var udpBlocked by remember { mutableStateOf(persistentState.getUdpBlocked()) }
        var excludeApps by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }
        var appMenuExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = getString(R.string.settings_dns_proxy_dialog_header), style = MaterialTheme.typography.titleMedium)

            if (lockdown) {
                TextButton(
                    onClick = {
                        onDismiss()
                        UIUtils.openVpnProfile(this@ProxySettingsActivity)
                    }
                ) {
                    Text(text = getString(R.string.settings_lock_down_mode_desc))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getString(R.string.settings_dns_proxy_dialog_app),
                    modifier = Modifier.weight(0.3f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(0.7f)) {
                    TextButton(onClick = { appMenuExpanded = true }) {
                        Text(text = appNames.getOrNull(selectedIndex) ?: "")
                    }
                    DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                        appNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(text = name) },
                                onClick = {
                                    selectedIndex = index
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(text = getString(R.string.settings_dns_proxy_dialog_hostname)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it },
                label = { Text(text = getString(R.string.settings_dns_proxy_dialog_port_number)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text(text = getString(R.string.settings_dns_proxy_dialog_username)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(text = getString(R.string.settings_dns_proxy_dialog_password)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = udpBlocked, onCheckedChange = {
                    udpBlocked = it
                    logEvent(
                        "UDP Block in SOCKS5 Proxy toggled",
                        "UDP block in SOCKS5 proxy: $udpBlocked"
                    )
                })
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = getString(R.string.univ_firewall_rule_4))
            }

            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (lockdown) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = excludeApps,
                    onCheckedChange = {
                        if (!lockdown) {
                            excludeApps = it
                            logEvent(
                                "loopback proxy forwarder apps SOCKS5 Proxy toggled",
                                "loopback proxy forwarder apps in SOCKS5 proxy: $excludeApps"
                            )
                        }
                    },
                    enabled = !lockdown
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(text = getString(R.string.settings_exclude_proxy_apps_heading))
                    Text(
                        text = getString(R.string.settings_exclude_proxy_apps_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (host.isBlank()) {
                            errorText = getString(R.string.settings_http_proxy_error_text3)
                            return@Button
                        }
                        val port = portText.toIntOrNull()
                        if (port == null) {
                            errorText = getString(R.string.settings_http_proxy_error_text2)
                            return@Button
                        }
                        val isValid =
                            if (Utilities.isLanIpv4(host)) {
                                Utilities.isValidLocalPort(port)
                            } else {
                                isValidPort(port)
                            }
                        if (!isValid) {
                            errorText = getString(R.string.settings_http_proxy_error_text1)
                            return@Button
                        }

                        val selection = appNames.getOrNull(selectedIndex) ?: ""
                        onApply(selection, host, port, userName, password, udpBlocked, excludeApps)
                    }
                ) {
                    Text(text = getString(R.string.settings_dns_proxy_dialog_positive_btn))
                }
            }
        }
    }

    @Composable
    private fun HttpProxyDialogContent(
        endpoint: ProxyEndpoint,
        appNames: List<String>,
        appName: String?,
        defaultHost: String,
        onDismiss: () -> Unit,
        onApply: (String, String, Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        val lockdown = VpnController.isVpnLockdown()
        var errorText by remember { mutableStateOf("") }
        var selectedIndex by remember {
            mutableStateOf(
                if (
                    !endpoint.proxyIP.isNullOrBlank() &&
                        !endpoint.proxyAppName.isNullOrBlank() &&
                        endpoint.proxyAppName != getString(R.string.settings_app_list_default_app)
                ) {
                    appNames.indexOf(appName).coerceAtLeast(0)
                } else {
                    0
                }
            )
        }
        var host by remember {
            mutableStateOf(endpoint.proxyIP ?: defaultHost)
        }
        var excludeApps by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }
        var appMenuExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = getString(R.string.http_proxy_dialog_heading), style = MaterialTheme.typography.titleMedium)
            Text(text = getString(R.string.http_proxy_dialog_desc), style = MaterialTheme.typography.bodyMedium)

            if (lockdown) {
                TextButton(
                    onClick = {
                        onDismiss()
                        UIUtils.openVpnProfile(this@ProxySettingsActivity)
                    }
                ) {
                    Text(text = getString(R.string.settings_lock_down_mode_desc))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getString(R.string.settings_dns_proxy_dialog_app),
                    modifier = Modifier.weight(0.3f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(0.7f)) {
                    TextButton(onClick = { appMenuExpanded = true }) {
                        Text(text = appNames.getOrNull(selectedIndex) ?: "")
                    }
                    DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                        appNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(text = name) },
                                onClick = {
                                    selectedIndex = index
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(text = getString(R.string.settings_dns_proxy_dialog_hostname)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (lockdown) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = excludeApps,
                    onCheckedChange = {
                        if (!lockdown) {
                            excludeApps = it
                            logEvent(
                                "loopback proxy forwarder apps in HTTP Proxy toggled",
                                "loopback proxy forwarder apps in HTTP proxy: $excludeApps"
                            )
                        }
                    },
                    enabled = !lockdown
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(text = getString(R.string.settings_exclude_proxy_apps_heading))
                    Text(
                        text = getString(R.string.settings_exclude_proxy_apps_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (host.isBlank()) {
                            errorText = getString(R.string.settings_http_proxy_error_text3)
                            return@Button
                        }
                        val selection = appNames.getOrNull(selectedIndex) ?: ""
                        onApply(selection, host, excludeApps)
                    }
                ) {
                    Text(text = getString(R.string.settings_dns_proxy_dialog_positive_btn))
                }
            }
        }
    }

    private fun insertSocks5Endpoint(
        id: Int,
        ip: String,
        port: Int?,
        appName: String,
        userName: String,
        password: String,
        isUDPBlock: Boolean
    ) {
        b.settingsActivitySocks5Switch.isEnabled = false
        b.settingsActivitySocks5Switch.visibility = View.GONE
        b.settingsActivitySocks5Progress.visibility = View.VISIBLE
        delay(TimeUnit.SECONDS.toMillis(1L), lifecycleScope) {
            b.settingsActivitySocks5Switch.isEnabled = true
            b.settingsActivitySocks5Progress.visibility = View.GONE
            b.settingsActivitySocks5Switch.visibility = View.VISIBLE
        }
        io {
            val proxyName = ProxyManager.ProxyMode.SOCKS5.name
            val mode = ProxyManager.ProxyMode.SOCKS5
            val appPackage =
                if (appName == getString(R.string.settings_app_list_default_app)) {
                    ""
                } else {
                    FirewallManager.getPackageNameByAppName(appName) ?: ""
                }
            val proxyEndpoint =
                constructProxy(
                    id,
                    proxyName,
                    mode,
                    appPackage,
                    ip,
                    port ?: 0,
                    userName,
                    password,
                    isUDPBlock
                )

            if (proxyEndpoint != null) {
                // insertSocks5Endpoint: 127.0.0.1, 10808, SOCKS5, Socks5, false
                appConfig.updateCustomSocks5Proxy(proxyEndpoint)
            }
        }
    }

    private fun insertHttpProxyEndpointDB(id: Int, ip: String, appName: String) {
        b.settingsActivityHttpProxySwitch.isEnabled = false
        b.settingsActivityHttpProxySwitch.visibility = View.GONE
        b.settingsActivityHttpProxyProgress.visibility = View.VISIBLE
        delay(TimeUnit.SECONDS.toMillis(1L), lifecycleScope) {
            b.settingsActivityHttpProxySwitch.isEnabled = true
            b.settingsActivityHttpProxyProgress.visibility = View.GONE
            b.settingsActivityHttpProxySwitch.visibility = View.VISIBLE
        }
        io {
            val proxyName = Constants.HTTP
            val mode = ProxyManager.ProxyMode.HTTP
            val packageName =
                if (appName == getString(R.string.settings_app_list_default_app)) {
                    ""
                } else {
                    FirewallManager.getPackageNameByAppName(appName) ?: ""
                }
            val proxyEndpoint =
                constructProxy(
                    id,
                    proxyName,
                    mode,
                    packageName,
                    ip,
                    0,
                    userName = "",
                    password = "",
                    false /* isUdp */
                )
            if (proxyEndpoint != null) {
                appConfig.updateCustomHttpProxy(proxyEndpoint)
            }
        }
    }

    private fun constructProxy(
        id: Int,
        name: String,
        mode: ProxyManager.ProxyMode,
        appName: String,
        ip: String?,
        port: Int,
        userName: String,
        password: String,
        isUdp: Boolean
    ): ProxyEndpoint? {
        if (ip.isNullOrEmpty()) {
            Logger.w(LOG_TAG_PROXY, "cannot construct proxy with values ip: $ip, port: $port")
            return null
        }

        if (mode == ProxyManager.ProxyMode.SOCKS5 && (!isValidPort(port))) {
            Logger.w(LOG_TAG_PROXY, "cannot construct proxy with values ip: $ip, port: $port")
            return null
        }

        return ProxyEndpoint(
            id,
            name,
            mode.value,
            proxyType = DEFAULT_PROXY_TYPE,
            appName,
            ip,
            port,
            userName,
            password,
            isSelected = true,
            isCustom = true,
            isUDP = isUdp,
            modifiedDataTime = 0L,
            latency = 0
        )
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.fragment

import Logger
import Logger.LOG_TAG_UI
import Logger.LOG_TAG_VPN
import android.Manifest
import android.R.attr.type
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.icu.text.CompactDecimalFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateUtils
import android.util.StatsLog.logEvent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.FragmentHomeScreenBinding
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.DnsLogTracker
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.service.WireguardManager.WG_HANDSHAKE_TIMEOUT
import com.celzero.bravedns.service.WireguardManager.WG_UPTIME_THRESHOLD
import com.celzero.bravedns.ui.activity.AlertsActivity
import com.celzero.bravedns.ui.activity.AppListActivity
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity.Companion.RETHINK_BLOCKLIST_NAME
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity.Companion.RETHINK_BLOCKLIST_URL
import com.celzero.bravedns.ui.activity.CustomRulesActivity
import com.celzero.bravedns.ui.activity.DnsDetailActivity
import com.celzero.bravedns.ui.activity.FirewallActivity
import com.celzero.bravedns.ui.activity.NetworkLogsActivity
import com.celzero.bravedns.ui.activity.PauseActivity
import com.celzero.bravedns.ui.activity.ProxySettingsActivity
import com.celzero.bravedns.ui.activity.WgMainActivity
import com.celzero.bravedns.ui.bottomsheet.HomeScreenSettingBottomSheet
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.RETHINKDNS_SPONSOR_LINK
import com.celzero.bravedns.util.NotificationActionType
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.UIUtils.openNetworkSettings
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.UIUtils.openVpnProfile
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.delay
import com.celzero.bravedns.util.Utilities.getPrivateDnsMode
import com.celzero.bravedns.util.Utilities.isAtleastN
import com.celzero.bravedns.util.Utilities.isAtleastP
import com.celzero.bravedns.util.Utilities.isAtleastR
import com.celzero.bravedns.util.Utilities.isOtherVpnHasAlwaysOn
import com.celzero.bravedns.util.Utilities.isPrivateDnsActive
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.firestack.backend.Backend
import com.facebook.shimmer.Shimmer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.Locale
import java.util.concurrent.TimeUnit

import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celzero.bravedns.ui.compose.home.HomeScreen
import com.celzero.bravedns.ui.compose.home.HomeScreenUiState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.compose.home.HomeScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreenFragment : Fragment() {
    private val viewModel: HomeScreenViewModel by viewModel()

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val workScheduler by inject<WorkScheduler>()
    private val eventLogger by inject<EventLogger>()

    private lateinit var themeNames: Array<String>
    private lateinit var startForResult: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionResult: ActivityResultLauncher<String>

    private val batteryPermissionHelper = BatteryPermissionHelper.getInstance()

    companion object {
        private const val TAG = "HSFragment"
        private const val GRACE_DIALOG_REMIND_AFTER_DAYS = 1 // days to remind again

        // UI interaction delays (milliseconds)
        private const val UI_DELAY_MS = 500L

        // Time calculation constants
        private const val MILLISECONDS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val MINUTES_PER_HOUR = 60L
        private const val HOURS_PER_DAY = 24L
        private const val DAYS_PER_MONTH = 30.0

        // Sponsorship calculation constants
        private const val BASE_AMOUNT_PER_MONTH = 0.60
        private const val ADDITIONAL_AMOUNT_PER_MONTH = 0.20

        // DNS latency thresholds (milliseconds)
        private const val LATENCY_VERY_FAST_MAX = 19L
        private const val LATENCY_FAST_MIN = 20L
        private const val LATENCY_FAST_MAX = 50L
        private const val LATENCY_SLOW_MIN = 50L
        private const val LATENCY_SLOW_MAX = 100L

        // Traffic display rotation
        private const val TRAFFIC_DISPLAY_CYCLE_MODULO = 3
        private const val TRAFFIC_DISPLAY_STATS_RATE = 0
        private const val TRAFFIC_DISPLAY_BANDWIDTH = 1
        private const val TRAFFIC_DISPLAY_DELAY_MS = 2500L

        // Byte conversion constants (KB, MB, GB, TB)
        private const val BYTES_PER_KB = 1024.0
        private const val BYTES_PER_MB = 1024.0 * 1024.0
        private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
        private const val BYTES_PER_TB = 1024.0 * 1024.0 * 1024.0 * 1024.0

        // Byte conversion thresholds (Long)
        private const val KB_THRESHOLD = 1024L
        private const val MB_THRESHOLD = 1024L * 1024L
        private const val GB_THRESHOLD = 1024L * 1024L * 1024L
        private const val TB_THRESHOLD = 1024L * 1024L * 1024L * 1024L

        // Shimmer animation constants
        private const val SHIMMER_DURATION_MS = 2000L
        private const val SHIMMER_BASE_ALPHA = 0.85f
        private const val SHIMMER_DROPOFF = 1f
        private const val SHIMMER_HIGHLIGHT_ALPHA = 0.35f
    }

    enum class ScreenType {
        DNS,
        FIREWALL,
        LOGS,
        RULES,
        PROXY,
        ALERTS,
        RETHINK,
        PROXY_WIREGUARD
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        registerForActivityResult()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RethinkTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        uiState = state,
                        onStartStopClick = { handleMainScreenBtnClickEvent() },
                        onDnsClick = { startDnsActivity(DnsDetailActivity.Tabs.CONFIGURE.screen) },
                        onFirewallClick = { startFirewallActivity(FirewallActivity.Tabs.UNIVERSAL.screen) },
                        onProxyClick = {
                            if (appConfig.isWireGuardEnabled()) {
                                startActivity(ScreenType.PROXY_WIREGUARD)
                            } else {
                                startActivity(ScreenType.PROXY)
                            }
                        },
                        onLogsClick = { startActivity(ScreenType.LOGS, NetworkLogsActivity.Tabs.NETWORK_LOGS.screen) },
                        onAppsClick = { startAppsActivity() },
                        onSponsorClick = { promptForAppSponsorship() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.v(LOG_TAG_UI, "$TAG: init view in home screen fragment")
        appConfig.getBraveModeObservable().postValue(appConfig.getBraveMode().mode)
    }

    private fun promptForAppSponsorship() {
        val installTime = requireContext().packageManager.getPackageInfo(
            requireContext().packageName,
            0
        ).firstInstallTime
        val timeDiff = System.currentTimeMillis() - installTime
        val days = (timeDiff / (MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE * MINUTES_PER_HOUR * HOURS_PER_DAY)).toDouble()
        val month = days / DAYS_PER_MONTH
        val amount = month * (BASE_AMOUNT_PER_MONTH + ADDITIONAL_AMOUNT_PER_MONTH)
        
        val alertBuilder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_sponsor_info, null)
        alertBuilder.setView(dialogView)
        alertBuilder.setCancelable(true)

        val amountTxt = dialogView.findViewById<AppCompatTextView>(R.id.dialog_sponsor_info_amount)
        val usageTxt = dialogView.findViewById<AppCompatTextView>(R.id.dialog_sponsor_info_usage)
        val sponsorBtn = dialogView.findViewById<AppCompatTextView>(R.id.dialog_sponsor_info_sponsor)

        val dialog = alertBuilder.create()

        val msg = getString(R.string.sponser_dialog_usage_msg, days.toInt().toString(), "%.2f".format(amount))
        amountTxt.text = getString(R.string.two_argument_no_space, getString(R.string.symbol_dollar), "%.2f".format(amount))
        usageTxt.text = msg

        sponsorBtn.setOnClickListener {
            openUrl(requireContext(), RETHINKDNS_SPONSOR_LINK)
        }
        dialog.show()
    }

    private fun handleMainScreenBtnClickEvent() {
        delay(TimeUnit.MILLISECONDS.toMillis(UI_DELAY_MS), lifecycleScope) {
            // isAdded check
        }
        handleVpnActivation()
    }

    private fun handleVpnActivation() {
        if (handleAlwaysOnVpn()) return

        if (VpnController.isOn()) {
            stopVpnService()
        } else {
            prepareAndStartVpn()
        }
    }

    private fun handleAlwaysOnVpn(): Boolean {
        if (isOtherVpnHasAlwaysOn(requireContext())) {
            showAlwaysOnDisableDialog()
            return true
        }

        if (VpnController.isAlwaysOn(requireContext()) && VpnController.isOn()) {
            showAlwaysOnStopDialog()
            return true
        }

        return false
    }

    private fun showAlwaysOnStopDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.always_on_dialog_stop_heading)
        if (VpnController.isVpnLockdown()) {
            builder.setMessage(htmlToSpannedText(getString(R.string.always_on_dialog_lockdown_stop_message)))
        } else {
            builder.setMessage(R.string.always_on_dialog_stop_message)
        }
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.always_on_dialog_positive) { _, _ -> stopVpnService() }
        builder.setNegativeButton(R.string.lbl_cancel) { _, _ -> }
        builder.setNeutralButton(R.string.always_on_dialog_neutral) { _, _ -> openVpnProfile(requireContext()) }
        builder.create().show()
    }

    private fun showAlwaysOnDisableDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.always_on_dialog_heading)
        builder.setMessage(R.string.always_on_dialog)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.always_on_dialog_positive_btn) { _, _ -> openVpnProfile(requireContext()) }
        builder.setNegativeButton(R.string.lbl_cancel) { _, _ -> }
        builder.create().show()
    }

    @Suppress("DEPRECATION")
    private fun maybeAutoStartVpn() {

        if (VpnController.state().activationRequested && !VpnController.isOn()) {
            Logger.i(LOG_TAG_VPN, "start VPN (previous state)")
            prepareAndStartVpn()
        }
    }

    private fun handleLockdownModeIfNeeded() {
        if (VpnController.isVpnLockdown() && !appConfig.getBraveMode().isDnsFirewallMode()) {
            lifecycleScope.launch(Dispatchers.IO) { appConfig.changeBraveMode(AppConfig.BraveMode.DNS_FIREWALL.mode) }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAutoStartVpn()
        handleLockdownModeIfNeeded()
    }
    
    override fun onPause() {
        super.onPause()
    }

    private fun startDnsActivity(screenToLoad: Int) {
        if (isPrivateDnsActive(requireContext())) {
            showPrivateDnsDialog()
            return
        }

        if (canStartRethinkActivity()) {
            // no need to pass value in intent, as default load to Rethink remote
            startActivity(ScreenType.RETHINK, screenToLoad)
            return
        }

        startActivity(ScreenType.DNS, screenToLoad)
        return
    }

    private fun canStartRethinkActivity(): Boolean {
        val dns = appConfig.getDnsType()
        return dns.isRethinkRemote() && !WireguardManager.oneWireGuardEnabled()
    }

    private fun showPrivateDnsDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.private_dns_dialog_heading)
        builder.setMessage(R.string.private_dns_dialog_desc)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.private_dns_dialog_positive) { _, _ ->
            openNetworkSettings(requireContext(), Settings.ACTION_WIRELESS_SETTINGS)
        }

        builder.setNegativeButton(R.string.lbl_dismiss) { _, _ ->
            // no-op
        }
        builder.create().show()
    }

    private fun startFirewallActivity(screenToLoad: Int) {
        startActivity(ScreenType.FIREWALL, screenToLoad)
        return
    }

    private fun startAppsActivity() {
        Logger.d(LOG_TAG_VPN, "Status : ${VpnController.isOn()} , BraveMode: ${appConfig.getBraveMode()}")

        // no need to check for app modes to open this activity
        // one use case: https://github.com/celzero/rethink-app/issues/611
        val intent = Intent(requireContext(), AppListActivity::class.java)
        startActivity(intent)
    }

    private fun startActivity(type: ScreenType, screenToLoad: Int = 0) {
        val intent =
            when (type) {
                ScreenType.DNS -> Intent(requireContext(), DnsDetailActivity::class.java)
                ScreenType.FIREWALL -> Intent(requireContext(), FirewallActivity::class.java)
                ScreenType.LOGS -> Intent(requireContext(), NetworkLogsActivity::class.java)
                ScreenType.RULES -> Intent(requireContext(), CustomRulesActivity::class.java)
                ScreenType.PROXY -> Intent(requireContext(), ProxySettingsActivity::class.java)
                ScreenType.ALERTS -> Intent(requireContext(), AlertsActivity::class.java)
                ScreenType.RETHINK ->
                    Intent(requireContext(), ConfigureRethinkBasicActivity::class.java)

                ScreenType.PROXY_WIREGUARD -> Intent(requireContext(), WgMainActivity::class.java)
            }
        if (type == ScreenType.RETHINK) {
            io {
                val endpoint = appConfig.getRemoteRethinkEndpoint()
                val url = endpoint?.url
                val name = endpoint?.name
                intent.putExtra(RETHINK_BLOCKLIST_NAME, name)
                intent.putExtra(RETHINK_BLOCKLIST_URL, url)
                uiCtx { startActivity(intent) }
            }
        } else {
            intent.putExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, screenToLoad)
            startActivity(intent)
        }
    }

    private fun prepareAndStartVpn() {
        if (prepareVpnService()) {
            startVpnService()
        }
    }

    private fun stopShimmer() {
        // Handled by Compose
    }

    private fun startShimmer() {
        // Handled by Compose
    }

    private fun stopVpnService() {
        VpnController.stop("home", requireContext())
    }

    private fun startVpnService() {
        // runtime permission for notification (Android 13)
        getNotificationPermissionIfNeeded()
        VpnController.start(requireContext(), true)
    }

    private fun getNotificationPermissionIfNeeded() {
        if (!Utilities.isAtleastT()) {
            // notification permission is needed for version 13 or above
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // notification permission is granted to the app, do nothing
            return
        }

        if (!persistentState.shouldRequestNotificationPermission) {
            // user rejected notification permission
            Logger.w(LOG_TAG_VPN, "User rejected notification permission for the app")
            return
        }

        notificationPermissionResult.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Throws(ActivityNotFoundException::class)
    private fun prepareVpnService(): Boolean {
        val prepareVpnIntent: Intent? =
            try {
                // In some cases, the intent used to register the VPN service does not open the
                // application (from Android settings). This happens in some of the Android
                // versions.
                // VpnService.prepare() is now registered with requireContext() instead of context.
                // Issue #469
                Logger.i(LOG_TAG_VPN, "Preparing VPN service")
                VpnService.prepare(requireContext())
            } catch (e: NullPointerException) {
                // This exception is not mentioned in the documentation, but it has been encountered
                // users and also by other developers, e.g.
                // https://stackoverflow.com/questions/45470113.
                Logger.e(LOG_TAG_VPN, "Device does not support system-wide VPN mode.", e)
                return false
            }
        // If the VPN.prepare() is not null, then the first time VPN dialog is shown, Show info
        // dialog before that.
        if (prepareVpnIntent != null) {
            Logger.i(LOG_TAG_VPN, "VPN service is prepared")
            showFirstTimeVpnDialog(prepareVpnIntent)
            return false
        }
        Logger.i(LOG_TAG_VPN, "VPN service is prepared, starting VPN service")
        return true
    }

    private fun showFirstTimeVpnDialog(prepareVpnIntent: Intent) {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.hsf_vpn_dialog_header)
        builder.setMessage(R.string.hsf_vpn_dialog_message)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.lbl_proceed) { _, _ ->
            try {
                startForResult.launch(prepareVpnIntent)
            } catch (e: ActivityNotFoundException) {
                Logger.e(LOG_TAG_VPN, "Activity not found to start VPN service", e)
                showToastUiCentered(
                    requireContext(),
                    getString(R.string.hsf_vpn_prepare_failure),
                    Toast.LENGTH_LONG
                )
            }
        }

        builder.setNegativeButton(R.string.lbl_cancel) { _, _ ->
            // no-op
        }
        builder.create().show()
    }

    /*private fun maybeShowGracePeriodDialog() {
        val now = System.currentTimeMillis()

        val lastShown = persistentState.lastGracePeriodReminderTime
        val daysSinceLastShown = TimeUnit.MILLISECONDS.toDays(now - lastShown)
        if (daysSinceLastShown < GRACE_DIALOG_REMIND_AFTER_DAYS) return
        Logger.d(LOG_TAG_UI, "$TAG Grace period dialog last shown $daysSinceLastShown days ago")
        io {
            val currentSubs = RpnProxyManager.getSubscriptionState()
            if (currentSubs.isActive) {
                Logger.v(LOG_TAG_UI, "$TAG Current subscription is active, skipping grace period dialog")
                return@io
            }
            if (!currentSubs.isCancelled) {
                Logger.v(LOG_TAG_UI, "$TAG Current subscription is not cancelled, skipping grace period dialog, state: ${currentSubs.state().name}")
                return@io
            }
            val subsData = RpnProxyManager.getSubscriptionData()
            if (subsData == null) {
                Logger.v(LOG_TAG_UI, "$TAG No subscription data found, skipping grace period dialog")
                return@io
            }

            val billingExpiry = subsData.subscriptionStatus.billingExpiry
            val accountExpiry = subsData.subscriptionStatus.accountExpiry
            // grace period is calculated based on billingExpiry and accountExpiry
            val timeLeft = accountExpiry.minus(now)
            val timeLeftDays = TimeUnit.MILLISECONDS.toDays(timeLeft)
            val gracePeriod = accountExpiry - billingExpiry
            val gracePeriodDays = TimeUnit.MILLISECONDS.toDays(gracePeriod)
            if (gracePeriodDays <= 0L) {
                Logger.v(LOG_TAG_UI, "$TAG No grace period available($gracePeriodDays), skipping grace period dialog")
                return@io
            }

            if (timeLeftDays <= 0L) {
                Logger.i(LOG_TAG_UI, "$TAG Grace period has ended(@$timeLeftDays), skipping grace period dialog")
                return@io
            }

            val daysRemaining = TimeUnit.MILLISECONDS.toDays(timeLeft).toInt().coerceAtLeast(1)
            if (daysRemaining <= 0) {
                Logger.v(LOG_TAG_UI, "$TAG No days remaining in grace period, skipping dialog")
                return@io
            }
            Logger.v(LOG_TAG_UI, "$TAG Showing grace period dialog, $daysRemaining days remaining")
            uiCtx {
                val dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_grace_period_layout, null)

                dialogView.findViewById<AppCompatTextView>(R.id.dialog_days_left).text =
                    "\u23F3 $daysRemaining days remaining"

                dialogView.findViewById<LinearProgressIndicator>(R.id.dialog_progress).apply {
                    max = 100
                    // should be decreased from 100 to 0
                    progress =  100 - (timeLeftDays * 100 / gracePeriodDays).toInt()
                    if (progress < 0) 0 else progress
                    Logger.v(LOG_TAG_UI, "$TAG Grace period progress: $progress%")
                }

                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                dialogView.findViewById<AppCompatButton>(R.id.button_renew).setOnClickListener {
                    dialog.dismiss()
                    findNavController().navigate(R.id.rethinkPlus)
                }

                dialogView.findViewById<AppCompatButton>(R.id.button_later).setOnClickListener {
                    dialog.dismiss()
                    persistentState.lastGracePeriodReminderTime = System.currentTimeMillis()
                }
                persistentState.lastGracePeriodReminderTime = System.currentTimeMillis()
                dialog.show()
            }
        }
    }*/

    private fun registerForActivityResult() {
        startForResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        startVpnService()
                    }
                    Activity.RESULT_CANCELED -> {
                        showToastUiCentered(
                            requireContext(),
                            getString(R.string.hsf_vpn_prepare_failure),
                            Toast.LENGTH_LONG
                        )
                    }
                    else -> {
                        stopVpnService()
                    }
                }
            }

        // Sets up permissions request launcher.
        notificationPermissionResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                persistentState.shouldRequestNotificationPermission = it
                if (it) {
                    Logger.i(LOG_TAG_UI, "User accepted notification permission")
                } else {
                    Logger.w(LOG_TAG_UI, "User rejected notification permission")
                    Snackbar.make(
                        requireActivity().findViewById<View>(android.R.id.content).rootView,
                        getString(R.string.hsf_notification_permission_failure),
                        Snackbar.LENGTH_LONG
                    )
                        .show()
                }
            }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun ui(n: String, f: suspend () -> Unit): Job {
        val cctx = CoroutineName(n) + Dispatchers.Main
        return lifecycleScope.launch(cctx) { f() }
    }
}

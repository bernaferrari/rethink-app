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
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui


import androidx.compose.material3.LoadingIndicator
import Logger
import Logger.LOG_TAG_APP_UPDATE
import Logger.LOG_TAG_DOWNLOAD
import Logger.LOG_TAG_UI
import Logger.LOG_TAG_VPN
import android.Manifest
import android.app.UiModeManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bernaferrari.bravedns.BuildConfig
import com.bernaferrari.bravedns.NonStoreAppUpdater
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.AppInfoRepository
import com.bernaferrari.bravedns.database.EventDao
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.scheduler.BugReportZipper
import com.bernaferrari.bravedns.scheduler.EnhancedBugReport
import com.bernaferrari.bravedns.service.AppUpdater
import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.service.WireguardManager

import com.bernaferrari.bravedns.ui.compose.dns.ConfigureRethinkScreenType
import com.bernaferrari.bravedns.ui.compose.navigation.HomeAboutActions
import com.bernaferrari.bravedns.ui.compose.navigation.HomeConfigureActions
import com.bernaferrari.bravedns.ui.compose.navigation.HomeDnsActions
import com.bernaferrari.bravedns.ui.compose.navigation.CustomRulesMode
import com.bernaferrari.bravedns.ui.compose.navigation.CustomRulesTab
import com.bernaferrari.bravedns.ui.compose.navigation.HomeWgActions
import com.bernaferrari.bravedns.ui.compose.navigation.RethinkRoute
import com.bernaferrari.bravedns.ui.compose.navigation.configureRethinkBasicRoute
import com.bernaferrari.bravedns.ui.compose.navigation.customRulesRoute
import com.bernaferrari.bravedns.ui.compose.navigation.detailedStatsRoute
import com.bernaferrari.bravedns.ui.compose.navigation.domainConnectionsRoute
import com.bernaferrari.bravedns.ui.compose.navigation.wireGuardDetailRoute
import com.bernaferrari.bravedns.ui.compose.navigation.wireGuardEditorRoute
import com.bernaferrari.bravedns.ui.compose.wireguard.WgType
import com.bernaferrari.bravedns.ui.compose.navigation.HomeScreenRoot
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTheme
import com.bernaferrari.bravedns.ui.compose.theme.RethinkColorPreset
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.settings.AppLockScreen
import com.bernaferrari.bravedns.ui.compose.settings.checkBiometricRequired
import com.bernaferrari.bravedns.ui.compose.settings.AppLockResult
import com.bernaferrari.bravedns.ui.compose.home.PauseScreen
import com.bernaferrari.bravedns.util.BioMetricType
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.bernaferrari.bravedns.util.Constants.Companion.PKG_NAME_PLAY_STORE
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel
import com.bernaferrari.bravedns.util.FirebaseErrorReporting
import com.bernaferrari.bravedns.util.FirebaseErrorReporting.TOKEN_LENGTH
import com.bernaferrari.bravedns.util.FirebaseErrorReporting.TOKEN_REGENERATION_PERIOD_DAYS
import com.bernaferrari.bravedns.util.RemoteFileTagUtil
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.UIUtils.openNetworkSettings
import com.bernaferrari.bravedns.util.UIUtils.openUrl
import com.bernaferrari.bravedns.util.UIUtils.openVpnProfile
import com.bernaferrari.bravedns.util.UIUtils.sendEmailIntent
import com.bernaferrari.bravedns.util.Themes
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.getPackageMetadata
import com.bernaferrari.bravedns.util.Utilities.getRandomString
import com.bernaferrari.bravedns.util.Utilities.isPlayStoreFlavour
import com.bernaferrari.bravedns.util.Utilities.isWebsiteFlavour
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import com.bernaferrari.bravedns.util.disableFrostTemporarily
import com.bernaferrari.bravedns.util.handleFrostEffectIfNeeded
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel
import com.bernaferrari.bravedns.viewmodel.CustomDomainViewModel
import com.bernaferrari.bravedns.viewmodel.CustomIpViewModel
import com.bernaferrari.bravedns.viewmodel.CheckoutViewModel
import com.bernaferrari.bravedns.viewmodel.DomainConnectionsViewModel
import com.bernaferrari.bravedns.viewmodel.EventsViewModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class HomeScreenActivity : FragmentActivity() {
    private val persistentState by inject<PersistentState>()
    private val appInfoDb by inject<AppInfoRepository>()
    private val appUpdateManager by inject<AppUpdater>()
    private val rdb by inject<RefreshDatabase>()
    private val appConfig by inject<AppConfig>()
    private val appDatabase by inject<AppDatabase>()
    private val eventDao by inject<EventDao>()
    private val eventLogger by inject<EventLogger>()

    private val homeViewModel by viewModel<com.bernaferrari.bravedns.ui.compose.home.HomeScreenViewModel>()
    private val summaryViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel>()
    private val aboutViewModel by viewModel<com.bernaferrari.bravedns.ui.compose.about.AboutViewModel>()
    private val detailedStatsViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DetailedStatisticsViewModel>()
    private val domainConnectionsViewModel by viewModel<DomainConnectionsViewModel>()
    private val eventsViewModel by viewModel<EventsViewModel>()
    private val appInfoIpRulesViewModel by viewModel<CustomIpViewModel>()
    private val appInfoDomainRulesViewModel by viewModel<CustomDomainViewModel>()
    private val appInfoNetworkLogsViewModel by viewModel<AppConnectionsViewModel>()
    private val consoleLogViewModel by inject<com.bernaferrari.bravedns.viewmodel.ConsoleLogViewModel>()
    private val consoleLogRepository by inject<com.bernaferrari.bravedns.database.ConsoleLogRepository>()
    private val consoleLogDao by inject<com.bernaferrari.bravedns.database.ConsoleLogDAO>()
    private val proxyAppsMappingViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.ProxyAppsMappingViewModel>()
    private val dnsSettingsViewModel by viewModel<com.bernaferrari.bravedns.ui.compose.dns.DnsSettingsViewModel>()
    private val appDownloadManager by inject<com.bernaferrari.bravedns.download.AppDownloadManager>()
    private val rethinkEndpointViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.RethinkEndpointViewModel>()
    private val remoteFileTagViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.RethinkRemoteFileTagViewModel>()
    private val localFileTagViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.RethinkLocalFileTagViewModel>()
    private val remoteBlocklistPacksMapViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.RemoteBlocklistPacksMapViewModel>()
    private val localBlocklistPacksMapViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.LocalBlocklistPacksMapViewModel>()
    private val appInfoViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.AppInfoViewModel>()
    private val connectionTrackerViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.ConnectionTrackerViewModel>()
    private val dnsLogViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DnsLogViewModel>()
    private val connectionTrackerRepository by inject<com.bernaferrari.bravedns.database.ConnectionTrackerRepository>()
    private val dnsLogRepository by inject<com.bernaferrari.bravedns.database.DnsLogRepository>()

    // ConfigureOtherDns ViewModels
    private val dohViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DoHEndpointViewModel>()
    private val dotViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DoTEndpointViewModel>()
    private val dnsProxyViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DnsProxyEndpointViewModel>()
    private val dnsCryptViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DnsCryptEndpointViewModel>()
    private val dnsCryptRelayViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.DnsCryptRelayEndpointViewModel>()
    private val oDohViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.ODoHEndpointViewModel>()
    private val checkoutViewModel: CheckoutViewModel? by lazy {
        runCatching { get<CheckoutViewModel>() }.getOrNull()
    }
    private val managePurchaseViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.ManagePurchaseViewModel>()
    private val blockFreeDnsViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.BlockFreeDnsViewModel>()
    private val purchaseHistoryViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.PurchaseHistoryViewModel>()
    private val serverOrderHistoryViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.ServerOrderHistoryViewModel>()
    private val wgConfigViewModel by viewModel<com.bernaferrari.bravedns.viewmodel.WgConfigViewModel>()


    private var appInBackground = false
    private val pendingDialog = MutableStateFlow<HomeDialog?>(null)
    private val pendingBugReportSheet = MutableStateFlow(false)
    private var snackbarHostState: SnackbarHostState? = null
    private var homeNavRequest by mutableStateOf<RethinkRoute?>(null)

    private val intentRouter = HomeIntentRouter()
    private val billingController = HomeBillingController()
    private val backupController = HomeBackupController(
        activity = this,
        onRestorePrompt = { showRestoreDialog(it) },
        onRestartRefresh = {
            io {
                com.bernaferrari.bravedns.backup.RestoreRestart.refreshBlocklists(this, persistentState)
                rdb.refresh(RefreshDatabase.ACTION_REFRESH_RESTORE)
            }
        },
    )

    private lateinit var startForResult: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionResult: androidx.activity.result.ActivityResultLauncher<String>

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = 0x00000000,
                darkScrim = 0x00000000
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = 0x00000000,
                darkScrim = 0x00000000
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false


        val resolvedThemePreference =
            Themes.resolveThemePreference(isDarkThemeOn(), persistentState.theme)

        val homeStartDestination =
            if (persistentState.firstTimeLaunch && !isAppRunningOnTv()) {
                RethinkRoute.Welcome
            } else {
                RethinkRoute.Home
            }

        handleFrostEffectIfNeeded(resolvedThemePreference)

        registerForActivityResult()
        backupController.observeRestoreWorker()

        handleIncomingIntents(intent)

        initUpdateCheck()


        regenerateFirebaseTokenIfNeeded()

        // Re-asserting the mode builds the whole AppConfig graph and writes a preference; keep
        // that off the main thread so the first frame is not waiting on it.
        lifecycleScope.launch(Dispatchers.Default) {
            appConfig.changeBraveMode(appConfig.getBraveMode().mode)
        }

        setContent {
            var composeThemePreference by remember { mutableStateOf(persistentState.theme) }
            var composeThemeColorPreset by remember { mutableStateOf(persistentState.themeColorPreset) }

            val colorPreset = RethinkColorPreset.fromId(composeThemeColorPreset)
            RethinkTheme(
                themePreference = composeThemePreference,
                colorPreset = colorPreset
            ) {
                val hostState = remember { SnackbarHostState() }
                DisposableEffect(hostState) {
                    snackbarHostState = hostState
                    onDispose {
                        if (snackbarHostState == hostState) {
                            snackbarHostState = null
                        }
                    }
                }
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                val aboutState by aboutViewModel.uiState.collectAsStateWithLifecycle()

                var isUnlocked by rememberAppUnlockState {
                    checkBiometricRequired(this@HomeScreenActivity, persistentState)
                }
                val homeStateHolder = rememberSaveableStateHolder()
                var homeDialogState by rememberSaveable(stateSaver = HomeDialog.Saver) {
                    mutableStateOf(pendingDialog.value ?: HomeDialog.Hidden)
                }
                var showBugReportSheet by rememberSaveable { mutableStateOf(false) }
                val incomingDialog by pendingDialog.collectAsStateWithLifecycle()
                val incomingBugReport by pendingBugReportSheet.collectAsStateWithLifecycle()
                LaunchedEffect(incomingDialog) {
                    val dialog = incomingDialog ?: return@LaunchedEffect
                    homeDialogState = dialog
                    pendingDialog.value = null
                }
                LaunchedEffect(incomingBugReport) {
                    if (!incomingBugReport) return@LaunchedEffect
                    showBugReportSheet = true
                    pendingBugReportSheet.value = false
                }
                val vpnState by VpnController.connectionStatus.collectAsStateWithLifecycle()

                if (!isUnlocked) {
                    AppLockScreen(
                        persistentState = persistentState,
                        onAuthResult = { result ->
                            when (result) {
                                AppLockResult.Success, AppLockResult.NotRequired -> {
                                    isUnlocked = true
                                }

                                AppLockResult.Failure -> {
                                    finish()
                                }

                                AppLockResult.Pending -> {
                                    // Waiting for user interaction
                                }
                            }
                        }
                    )
                } else {
                    homeStateHolder.SaveableStateProvider("unlocked-home") {
                    // Keep HomeScreenRoot composed while paused so rememberNavBackStack survives.
                    PauseOverlayHost {
                        HomeScreenRoot(
                        homeUiState = homeState,
                        onHomeStartStopClick = { handleMainScreenBtnClickEvent() },
                        summaryViewModel = summaryViewModel,
                        onOpenDetailedStats = { type -> openDetailedStatsUi(type) },
                        startDestination = homeStartDestination,
                        isDebug = DEBUG,
                        configureActions = HomeConfigureActions(
                            onConfigureAppsClick = { homeNavRequest = RethinkRoute.AppList },
                            onConfigureDnsClick = { navigateToDnsDetailIfAllowed() },
                            onConfigureFirewallClick = {
                                homeNavRequest = RethinkRoute.FirewallSettings()
                            },
                            onFirewallUniversalClick = {
                                homeNavRequest = RethinkRoute.UniversalFirewall
                            },
                            onFirewallCustomIpClick = {
                                homeNavRequest =
                                    customRulesRoute(
                                        uid = UID_EVERYBODY,
                                        tab = CustomRulesTab.IP,
                                        mode = CustomRulesMode.APP_SPECIFIC
                                    )
                            },
                            onFirewallAppWiseIpClick = { openAppWiseIpScreen() },
                            onConfigureProxyClick = {
                                homeNavRequest = RethinkRoute.ProxySettings()
                            },
                            onConfigureNetworkClick = {
                                homeNavRequest = RethinkRoute.TunnelSettings()
                            },
                            onConfigureOthersClick = {
                                homeNavRequest = RethinkRoute.MiscSettings()
                            },
                            onConfigureLogsClick = {
                                homeNavRequest = RethinkRoute.NetworkLogs()
                            },
                            onConfigureAntiCensorshipClick = {
                                homeNavRequest = RethinkRoute.AntiCensorship
                            },
                            onConfigureAdvancedClick = {
                                homeNavRequest = RethinkRoute.AdvancedSettings
                            },
                            onNavigateToLogs = { searchQuery ->
                                homeNavRequest = RethinkRoute.NetworkLogs(searchQuery)
                            },
                            onOpenAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onNavigateToProxy = {
                                homeNavRequest = RethinkRoute.ProxySettings()
                            },
                        ),
                        aboutUiState = aboutState,
                        aboutActions = HomeAboutActions(
                            onTelegramClick = {
                                openUrl(this, getString(R.string.about_telegram_link))
                            },
                            onBugReportClick = { aboutViewModel.triggerBugReport() },
                            onWhatsNewClick = { showNewFeaturesDialog() },
                            onAppUpdateClick = {
                                checkForUpdate(AppUpdater.UserPresent.INTERACTIVE)
                            },
                            onWebsiteClick = {
                                openUrl(this, getString(R.string.about_website_link))
                            },
                            onGithubClick = {
                                openUrl(this, getString(R.string.about_github_link))
                            },
                            onFaqClick = { openUrl(this, getString(R.string.about_faq_link)) },
                            onDocsClick = { openUrl(this, getString(R.string.about_docs_link)) },
                            onPrivacyPolicyClick = {
                                openUrl(this, getString(R.string.about_privacy_policy_link))
                            },
                            onTermsOfServiceClick = {
                                openUrl(this, getString(R.string.about_terms_link))
                            },
                            onLicenseClick = {
                                openUrl(this, getString(R.string.about_license_link))
                            },
                            onTwitterClick = {
                                openUrl(this, getString(R.string.about_twitter_handle))
                            },
                            onEmailClick = { disableFrostTemporarily(); sendEmailIntent(this) },
                            onRedditClick = {
                                openUrl(this, getString(R.string.about_reddit_handle))
                            },
                            onElementClick = {
                                openUrl(this, getString(R.string.about_matrix_handle))
                            },
                            onMastodonClick = {
                                openUrl(this, getString(R.string.about_mastodom_handle))
                            },
                            onGeneralSettingsClick = {
                                homeNavRequest = RethinkRoute.MiscSettings()
                            },
                            onAppInfoClick = { UIUtils.openAndroidAppInfo(this, packageName) },
                            onVpnProfileClick = { openVpnProfile(this) },
                            onNotificationClick = { openNotificationSettings() },
                            onStatsClick = { openStatsDialog() },
                            onDbStatsClick = { openDatabaseDumpDialog() },
                            onFlightRecordClick = { initiateFlightRecord() },
                            onEventLogsClick = { openEventLogs() },
                            onTokenClick = { copyTokenToClipboard() },
                            onFossClick = {
                                openUrl(this, getString(R.string.about_foss_link))
                            },
                            onFlossFundsClick = {
                                openUrl(this, getString(R.string.about_floss_fund_link))
                            },
                        ),
                        snackbarHostState = hostState,
                        detailedStatsViewModel = detailedStatsViewModel,
                        domainConnectionsViewModel = domainConnectionsViewModel,
                        eventsViewModel = eventsViewModel,
                        eventDao = eventDao,
                        appInfoEventLogger = eventLogger,
                        appInfoIpRulesViewModel = appInfoIpRulesViewModel,
                        appInfoDomainRulesViewModel = appInfoDomainRulesViewModel,
                        appInfoNetworkLogsViewModel = appInfoNetworkLogsViewModel,
                        persistentState = persistentState,
                        appConfig = appConfig,
                        onOpenVpnProfile = { UIUtils.openVpnProfile(this@HomeScreenActivity) },
                        onRefreshDatabase = { lifecycleScope.launch { rdb.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE) } },
                        onThemeModeChanged = { composeThemePreference = it },
                        onThemeColorChanged = { composeThemeColorPreset = it },
                        consoleLogViewModel = consoleLogViewModel,
                        consoleLogRepository = consoleLogRepository,
                        onShareConsoleLogs = { shareConsoleLogs() },
                        onConsoleLogsDeleteComplete = {
                            showToastUiCentered(
                                this@HomeScreenActivity,
                                getString(R.string.config_add_success_toast),
                                Toast.LENGTH_SHORT
                            )
                        },
                        proxyAppsMappingViewModel = proxyAppsMappingViewModel,
                        dnsSettingsViewModel = dnsSettingsViewModel,
                        appDownloadManager = appDownloadManager,
                        dnsActions = HomeDnsActions(
                            onDnsCustomDnsClick = { startCustomDnsActivity() },
                            onDnsRethinkPlusDnsClick = { startRethinkPlusDnsActivity() },
                            onDnsLocalBlocklistConfigureClick = {
                                startLocalBlocklistConfigureActivity()
                            },
                            onConfigureOtherDns = { index ->
                                homeNavRequest = RethinkRoute.ConfigureOtherDns(index)
                            },
                        ),
                        homeNavRequest = homeNavRequest,
                        onHomeNavConsumed = { homeNavRequest = null },
                        rethinkEndpointViewModel = rethinkEndpointViewModel,
                        remoteFileTagViewModel = remoteFileTagViewModel,
                        localFileTagViewModel = localFileTagViewModel,
                        remoteBlocklistPacksMapViewModel = remoteBlocklistPacksMapViewModel,
                        localBlocklistPacksMapViewModel = localBlocklistPacksMapViewModel,
                        appInfoViewModel = appInfoViewModel,
                        refreshDatabase = rdb,
                        connectionTrackerViewModel = connectionTrackerViewModel,
                        dnsLogViewModel = dnsLogViewModel,
                        connectionTrackerRepository = connectionTrackerRepository,
                        dnsLogRepository = dnsLogRepository,
                        dohViewModel = dohViewModel,
                        dotViewModel = dotViewModel,
                        dnsProxyViewModel = dnsProxyViewModel,
                        dnsCryptViewModel = dnsCryptViewModel,
                        dnsCryptRelayViewModel = dnsCryptRelayViewModel,
                        oDohViewModel = oDohViewModel,
                        wgConfigViewModel = wgConfigViewModel,
                        checkoutViewModel = checkoutViewModel,
                        managePurchaseViewModel = managePurchaseViewModel,
                        purchaseHistoryViewModel = purchaseHistoryViewModel,
                        serverOrderHistoryViewModel = serverOrderHistoryViewModel,
                        blockFreeDnsViewModel = blockFreeDnsViewModel,
                        wgActions = HomeWgActions(
                            onWgCreateClick = {
                                homeNavRequest = wireGuardEditorRoute(
                                    WireguardManager.INVALID_CONF_ID,
                                    WgType.DEFAULT
                                )
                            },
                            onWgImportClick = { backupController.launchFileImport() },
                            onWgQrScanClick = { backupController.launchQrScanner() },
                        ),
                        appDatabase = appDatabase
                        )
                        if (vpnState == BraveVPNService.State.PAUSED) {
                            PauseScreen(onFinish = { })
                        }
                    }
                    }
                }

                if (isUnlocked) {
                if (showBugReportSheet) {
                    BugReportFilesSheet(onDismiss = { showBugReportSheet = false })
                }
                HomeDialogHost(
                    dialog = homeDialogState,
                    onDismiss = { homeDialogState = HomeDialog.Hidden },
                    actions = HomeDialogHostActions(
                        onRestoreConfirm = { uriString ->
                            val uri = uriString.toUri()
                            backupController.startRestore(uri)
                        },
                        onDownloadConfirm = { download, isUpdateAvailable ->
                            if (isUpdateAvailable) {
                                if (download.source == AppUpdater.InstallSource.STORE) {
                                    appUpdateManager.completeUpdate()
                                } else {
                                    initiateDownload()
                                }
                            }
                        },
                        onDownloadLater = {
                            persistentState.lastAppUpdateCheck = System.currentTimeMillis()
                        },
                        onAlwaysOnStop = { stopVpnService() },
                        onOpenVpnProfile = { openVpnProfile(this@HomeScreenActivity) },
                        onPrivateDnsSettings = {
                            openNetworkSettings(
                                this@HomeScreenActivity,
                                Settings.ACTION_WIRELESS_SETTINGS
                            )
                        },
                        onFirstTimeVpnProceed = { proceedFirstTimeVpn() },
                        onCopyStats = { dump ->
                            copyToClipboard("stats_dump", dump)
                            showToastUiCentered(
                                this@HomeScreenActivity,
                                getString(R.string.copied_clipboard),
                                Toast.LENGTH_SHORT
                            )
                        },
                        onNoLogEmail = { sendEmailIntent(this@HomeScreenActivity) },
                        onContactEmail = { sendEmailIntent(this@HomeScreenActivity) },
                        onAccessibilityAppInfo = {
                            UIUtils.openAndroidAppInfo(this@HomeScreenActivity, packageName)
                        },
                    )
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntents(intent)
        Logger.v(LOG_TAG_UI, "home screen activity received new intent")
    }

    override fun onResume() {
        super.onResume()
        // if app is coming from background, don't reset the activity stack
        if (appInBackground) {
            appInBackground = false
            Logger.d(LOG_TAG_UI, "app restored from background, maintaining activity stack")
        }
    }

    // check if app running on TV
    private fun isAppRunningOnTv(): Boolean {
        return try {
            val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
            uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        } catch (_: Exception) {
            false
        }
    }

    private fun isInForeground(): Boolean {
        return !this.isFinishing && !this.isDestroyed
    }

    private fun handleIncomingIntents(intent: Intent?) {
        backupController.handleIncomingIntent()
        applyIntentResult(intentRouter.parse(intent), intent)
    }

    private fun applyIntentResult(result: HomeIntentResult?, intent: Intent?) {
        when (result) {
            is HomeIntentResult.Navigate -> homeNavRequest = result.route
            HomeIntentResult.AccessibilityCrash -> showDialog(HomeDialog.AccessibilityCrash)
            HomeIntentResult.RpnAccountAlert -> {
                if (intent != null) {
                    billingController.applyNotification(intent)
                }
                homeNavRequest = RethinkRoute.RpnAccount
            }
            HomeIntentResult.Ignored, null -> Unit
        }
    }

    private fun showDialog(dialog: HomeDialog) {
        pendingDialog.value = dialog
    }

    private fun regenerateFirebaseTokenIfNeeded() {
        if (Utilities.isFdroidFlavour()) return
        if (!persistentState.firebaseErrorReportingEnabled) return

        val now = System.currentTimeMillis()
        val fortyFiveDaysMs = TimeUnit.DAYS.toMillis(TOKEN_REGENERATION_PERIOD_DAYS)

        var token = persistentState.firebaseUserToken
        var ts = persistentState.firebaseUserTokenTimestamp
        if (token.isBlank() || now - ts > fortyFiveDaysMs) {
            token = getRandomString(TOKEN_LENGTH)
            ts = now
            persistentState.firebaseUserToken = token
            persistentState.firebaseUserTokenTimestamp = ts
            FirebaseErrorReporting.setUserId(token)
        }
    }

    private fun showRestoreDialog(uri: Uri) {
        if (!isInForeground()) return
        showDialog(HomeDialog.Restore(uri.toString()))
    }


    private fun initUpdateCheck() {
        if (!isUpdateRequired()) return

        val diff = System.currentTimeMillis() - persistentState.lastAppUpdateCheck

        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diff)
        Logger.i(LOG_TAG_UI, "App update check initiated, number of days: $daysElapsed")
        if (daysElapsed <= 1L) return

        checkForUpdate()
    }

    private fun isUpdateRequired(): Boolean {
        val calendar: Calendar = Calendar.getInstance()
        val day: Int = calendar.get(Calendar.DAY_OF_WEEK)
        return (day == Calendar.FRIDAY || day == Calendar.SATURDAY) &&
                persistentState.checkForAppUpdate
    }

    fun checkForUpdate(
        isInteractive: AppUpdater.UserPresent = AppUpdater.UserPresent.NONINTERACTIVE
    ) {
        // do not check for debug builds
        if (BuildConfig.DEBUG) return

        // Check updates only for play store / website version. Not fDroid.
        if (!isPlayStoreFlavour() && !isWebsiteFlavour()) {
            Logger.i(LOG_TAG_APP_UPDATE, "update check not for ${BuildConfig.FLAVOR}")
            return
        }

        if (isGooglePlayServicesAvailable() && isPlayStoreFlavour()) {
            try {
                appUpdateManager.checkForAppUpdate(
                    isInteractive,
                    this,
                    installStateUpdatedListener
                ) // Might be play updater or web updater
            } catch (e: Exception) {
                Logger.crash(LOG_TAG_APP_UPDATE, "err in app update check: ${e.message}", e)
                runOnUiThread {
                    showDownloadDialog(
                        AppUpdater.InstallSource.STORE,
                        getString(R.string.download_update_dialog_failure_title),
                        getString(R.string.download_update_dialog_failure_message)
                    )
                }
            }
        } else {
            try {
                get<NonStoreAppUpdater>()
                    .checkForAppUpdate(
                        isInteractive,
                        this,
                        installStateUpdatedListener
                    ) // Always web updater
            } catch (e: Exception) {
                Logger.e(LOG_TAG_APP_UPDATE, "Error in app (web) update check: ${e.message}", e)
                runOnUiThread {
                    showDownloadDialog(
                        AppUpdater.InstallSource.OTHER,
                        getString(R.string.download_update_dialog_failure_title),
                        getString(R.string.download_update_dialog_failure_message)
                    )
                }
            }
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        // applicationInfo.enabled - When false, indicates that all components within
        // this application are considered disabled, regardless of their individually set enabled
        // status.
        // TODO: prompt dialog to user that Playservice is disabled, so switch to update
        // check for website
        return Utilities.getApplicationInfo(this, PKG_NAME_PLAY_STORE)?.enabled == true
    }

    private val installStateUpdatedListener =
        object : AppUpdater.InstallStateListener {
            override fun onStateUpdate(state: AppUpdater.InstallState) {
                Logger.i(LOG_TAG_UI, "InstallStateUpdatedListener: state: " + state.status)
                when (state.status) {
                    AppUpdater.InstallStatus.DOWNLOADED -> {
                        // CHECK THIS if AppUpdateType.FLEXIBLE, otherwise you can skip
                        showUpdateCompleteSnackbar()
                    }

                    else -> {
                        appUpdateManager.unregisterListener(this)
                    }
                }
            }

            override fun onUpdateCheckFailed(
                installSource: AppUpdater.InstallSource,
                isInteractive: AppUpdater.UserPresent
            ) {
                runOnUiThread {
                    if (isInteractive == AppUpdater.UserPresent.INTERACTIVE) {
                        showDownloadDialog(
                            installSource,
                            getString(R.string.download_update_dialog_failure_title),
                            getString(R.string.download_update_dialog_failure_message)
                        )
                    }
                }
            }

            override fun onUpToDate(
                installSource: AppUpdater.InstallSource,
                isInteractive: AppUpdater.UserPresent
            ) {
                runOnUiThread {
                    if (isInteractive == AppUpdater.UserPresent.INTERACTIVE) {
                        showDownloadDialog(
                            installSource,
                            getString(R.string.download_update_dialog_message_ok_title),
                            getString(R.string.download_update_dialog_message_ok)
                        )
                    }
                }
            }

            override fun onUpdateAvailable(installSource: AppUpdater.InstallSource) {
                runOnUiThread {
                    showDownloadDialog(
                        installSource,
                        getString(R.string.download_update_dialog_title),
                        getString(R.string.download_update_dialog_message)
                    )
                }
            }

            override fun onUpdateQuotaExceeded(installSource: AppUpdater.InstallSource) {
                runOnUiThread {
                    showDownloadDialog(
                        installSource,
                        getString(R.string.download_update_dialog_trylater_title),
                        getString(R.string.download_update_dialog_trylater_message)
                    )
                }
            }
        }

    private fun showUpdateCompleteSnackbar() {
        lifecycleScope.launch {
            val result =
                snackbarHostState?.showSnackbar(
                    message = getString(R.string.update_complete_snack_message),
                    actionLabel = getString(R.string.update_complete_action_snack),
                    duration = SnackbarDuration.Indefinite
                )
            if (result == SnackbarResult.ActionPerformed) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    private fun showDownloadDialog(
        source: AppUpdater.InstallSource,
        title: String,
        message: String
    ) {
        if (!isInForeground()) return
        showDialog(HomeDialog.Download(source, title, message))
    }

    private fun initiateDownload() {
        try {
            val url = Constants.RETHINK_APP_DOWNLOAD_LINK
            val uri = url.toUri()
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToastUiCentered(this, getString(R.string.no_browser_error), Toast.LENGTH_SHORT)
            Logger.w(Logger.LOG_TAG_VPN, "err opening rethink download link: ${e.message}", e)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_DOWNLOAD, "Unregister receiver exception")
        }
        // mark that app is going to background
        appInBackground = true
        Logger.v(LOG_TAG_UI, "home screen activity is stopped, app going to background")
    }


    private fun openDetailedStatsUi(type: SummaryStatisticsType) {
        val timeCategory = summaryViewModel.uiState.value.timeCategory.value
        val category =
            SummaryStatisticsViewModel.TimeCategory.fromValue(timeCategory)
                ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR
        homeNavRequest = detailedStatsRoute(type, category)
    }

    private fun handleMainScreenBtnClickEvent() {
        Utilities.delay(TimeUnit.MILLISECONDS.toMillis(500L), lifecycleScope) { }
        handleVpnActivation()
    }

    private fun handleVpnActivation() {
        if (handleAlwaysOnVpn()) return

        if (VpnController.hasTunnel()) {
            stopVpnService()
        } else {
            prepareAndStartVpn()
        }
    }

    private fun handleAlwaysOnVpn(): Boolean {
        if (Utilities.isOtherVpnHasAlwaysOn(this)) {
            showAlwaysOnDisableDialog()
            return true
        }

        if (VpnController.isAlwaysOn(this) && VpnController.hasTunnel()) {
            showAlwaysOnStopDialog()
            return true
        }

        return false
    }

    private fun showAlwaysOnStopDialog() {
        val message =
            if (VpnController.isVpnLockdown()) {
                UIUtils.htmlToSpannedText(getString(R.string.always_on_dialog_lockdown_stop_message))
                    .toString()
            } else {
                getString(R.string.always_on_dialog_stop_message)
            }
        showDialog(HomeDialog.AlwaysOnStop(message))
    }

    private fun showAlwaysOnDisableDialog() {
        showDialog(HomeDialog.AlwaysOnDisable)
    }

    private fun navigateToDnsDetailIfAllowed() {
        if (Utilities.isPrivateDnsActive(this)) {
            showPrivateDnsDialog()
            return
        }
        homeNavRequest = RethinkRoute.DnsDetail()
    }

    private fun startCustomDnsActivity() {
        homeNavRequest = RethinkRoute.DnsList
    }

    private fun startRethinkPlusDnsActivity() {
        homeNavRequest = configureRethinkBasicRoute(ConfigureRethinkScreenType.DB_LIST)
    }

    private fun startLocalBlocklistConfigureActivity() {
        homeNavRequest = configureRethinkBasicRoute(ConfigureRethinkScreenType.LOCAL)
    }

    private fun showPrivateDnsDialog() {
        showDialog(HomeDialog.PrivateDns)
    }

    private fun shareConsoleLogs() {
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                consoleLogDao.getLogsCursor(
                    androidx.room3.RoomRawQuery("SELECT * FROM ConsoleLog order by id"),
                )
            }
            if (logs.isEmpty()) {
                showToastUiCentered(
                    this@HomeScreenActivity,
                    getString(R.string.about_bug_no_log_dialog_title),
                    Toast.LENGTH_SHORT,
                )
                return@launch
            }
            val text = buildString {
                for (log in logs) {
                    append(log.timestamp)
                    append('\t')
                    append(log.message)
                    append('\n')
                }
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.console_log_title))
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(
                Intent.createChooser(intent, getString(R.string.about_bug_report_desc)),
            )
        }
    }

    private fun openAppWiseIpScreen() {
        homeNavRequest =
            customRulesRoute(
                uid = UID_EVERYBODY,
                tab = CustomRulesTab.IP,
                mode = CustomRulesMode.ALL_RULES
            )
    }

    private fun prepareAndStartVpn() {
        if (prepareVpnService()) {
            startVpnService()
        }
    }

    private fun stopVpnService() {
        VpnController.stop("home", this)
    }

    private fun startVpnService() {
        getNotificationPermissionIfNeeded()
        VpnController.start(this, true)
    }

    private fun getNotificationPermissionIfNeeded() {
        if (!Utilities.isAtleastT()) {
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (!persistentState.shouldRequestNotificationPermission) {
            Logger.w(LOG_TAG_VPN, "User rejected notification permission for the app")
            return
        }

        notificationPermissionResult.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Throws(ActivityNotFoundException::class)
    private fun prepareVpnService(): Boolean {
        val prepareVpnIntent: Intent? =
            try {
                Logger.i(LOG_TAG_VPN, "Preparing VPN service")
                VpnService.prepare(this)
            } catch (e: NullPointerException) {
                Logger.e(LOG_TAG_VPN, "Device does not support system-wide VPN mode.", e)
                return false
            }
        if (prepareVpnIntent != null) {
            Logger.i(LOG_TAG_VPN, "VPN service is prepared")
            showFirstTimeVpnDialog()
            return false
        }
        Logger.i(LOG_TAG_VPN, "VPN service is prepared, starting VPN service")
        return true
    }

    private fun showFirstTimeVpnDialog() {
        showDialog(HomeDialog.FirstTimeVpn)
    }

    private fun proceedFirstTimeVpn() {
        val prepareVpnIntent = try {
            VpnService.prepare(this)
        } catch (e: NullPointerException) {
            Logger.e(LOG_TAG_VPN, "Device does not support system-wide VPN mode.", e)
            null
        }
        if (prepareVpnIntent == null) {
            startVpnService()
            return
        }
        try {
            startForResult.launch(prepareVpnIntent)
        } catch (e: ActivityNotFoundException) {
            Logger.e(LOG_TAG_VPN, "Activity not found to start VPN service", e)
            showToastUiCentered(
                this,
                getString(R.string.hsf_vpn_prepare_failure),
                Toast.LENGTH_LONG
            )
        }
    }

    private fun registerForActivityResult() {
        startForResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        startVpnService()
                    }

                    Activity.RESULT_CANCELED -> {
                        showToastUiCentered(
                            this,
                            getString(R.string.hsf_vpn_prepare_failure),
                            Toast.LENGTH_LONG
                        )
                    }

                    else -> {
                        stopVpnService()
                    }
                }
            }

        notificationPermissionResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                persistentState.shouldRequestNotificationPermission = it
                if (it) {
                    Logger.i(LOG_TAG_UI, "User accepted notification permission")
                } else {
                    Logger.w(LOG_TAG_UI, "User rejected notification permission")
                    lifecycleScope.launch {
                        snackbarHostState?.showSnackbar(
                            message = getString(R.string.hsf_notification_permission_failure),
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
    }

    private fun copyTokenToClipboard() {
        val text = persistentState.firebaseUserToken
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("token", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun initiateFlightRecord() {
        io { VpnController.performFlightRecording() }
        Toast.makeText(this, R.string.flight_recording_started, Toast.LENGTH_SHORT).show()
    }

    private fun openEventLogs() {
        homeNavRequest = RethinkRoute.Events
    }

    private fun getVersionName(): String {
        return Utilities.getPackageMetadata(packageManager, packageName)?.versionName ?: ""
    }

    private fun openStatsDialog() {
        io {
            val stat = VpnController.getNetStat()
            val formatedStat = UIUtils.formatNetStat(stat)
            val vpnStats = VpnController.vpnStats()
            val stats = formatedStat + vpnStats
            uiCtx {
                val displayText = if (formatedStat == null) "No Stats" else stats
                showDialog(HomeDialog.Stats(displayText, stats))
            }
        }
    }

    private fun copyToClipboard(label: String, text: String): ClipboardManager? {
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        return clipboard
    }

    private fun openDatabaseDumpDialog() {
        homeNavRequest = RethinkRoute.Database
    }

    private fun openNotificationSettings() {
        val packageName = packageName
        try {
            val intent = Intent()
            if (Utilities.isAtleastO()) {
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToastUiCentered(
                this,
                getString(R.string.notification_screen_error),
                Toast.LENGTH_SHORT
            )
            Logger.w(LOG_TAG_UI, "activity not found ${e.message}", e)
        }
    }

    private fun showNewFeaturesDialog() {
        val v = getVersionName().slice(0..6)
        val title = getString(R.string.about_whats_new, v)
        showDialog(HomeDialog.NewFeatures(title))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BugReportFilesSheet(onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        val bugReportFiles = remember { mutableStateListOf<BugReportFile>() }
        var isSending by remember { mutableStateOf(false) }
        var progressText by remember { mutableStateOf("") }
        var pendingDelete by remember { mutableStateOf<BugReportFile?>(null) }

        LaunchedEffect(Unit) {
            try {
                val files = withContext(Dispatchers.IO) { collectAllBugReportFiles() }
                bugReportFiles.clear()
                bugReportFiles.addAll(files)
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "err loading bug report: ${e.message}", e)
                showToastUiCentered(
                    this@HomeScreenActivity,
                    getString(R.string.bug_report_file_not_found),
                    Toast.LENGTH_SHORT
                )
                onDismiss()
            }
        }

        val totalSize = bugReportFiles.filter { it.isSelected }.sumOf { it.file.length() }
        val hasSelection = bugReportFiles.any { it.isSelected }
        val allSelected = bugReportFiles.isNotEmpty() && bugReportFiles.all { it.isSelected }

        RethinkModalBottomSheet(
            onDismissRequest = onDismiss,
            contentPadding = PaddingValues(SharedDimensions.spacingNone),
            verticalSpacing = SharedDimensions.spacingNone,
            includeBottomSpacer = false
        ) {
            Column(modifier = Modifier.padding(SharedDimensions.spacingLg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SharedDimensions.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                bugReportFiles.indices.forEach { i ->
                                    bugReportFiles[i] =
                                        bugReportFiles[i].copy(isSelected = checked)
                                }
                            }
                        )
                        Text(
                            text =
                                if (allSelected) {
                                    getString(R.string.bug_report_deselect_all)
                                } else {
                                    getString(R.string.lbl_select_all)
                                        .replaceFirstChar(Char::titlecase)
                                },
                            modifier = Modifier.clickable {
                                val selectAll = !allSelected
                                bugReportFiles.indices.forEach { i ->
                                    bugReportFiles[i] =
                                        bugReportFiles[i].copy(isSelected = selectAll)
                                }
                            }
                        )
                    }
                    Text(
                        text = formatFileSize(totalSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (bugReportFiles.isEmpty()) {
                    Text(text = getString(R.string.bug_report_no_files_available))
                } else {
                    RethinkListGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        bugReportFiles.forEachIndexed { index, item ->
                            BugReportFileRow(
                                fileItem = item,
                                position = cardPositionFor(index, bugReportFiles.lastIndex),
                                onToggle = { selected ->
                                    val path = item.file.absolutePath
                                    val fileIndex = bugReportFiles.indexOfFirst {
                                        it.file.absolutePath == path
                                    }
                                    if (fileIndex >= 0) {
                                        bugReportFiles[fileIndex] =
                                            bugReportFiles[fileIndex].copy(isSelected = selected)
                                    }
                                },
                                onShare = { openBugReportFile(item.file) },
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SharedDimensions.spacingMd))

                if (isSending) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SharedDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(modifier = Modifier.size(SharedDimensions.iconSizeMd))
                        Spacer(modifier = Modifier.width(SharedDimensions.spacingSm))
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RethinkFormActionRow(
                    confirmLabel = getString(R.string.about_bug_report_dialog_positive_btn),
                    onConfirm = {
                        scope.launch {
                            sendBugReport(
                                bugReportFiles = bugReportFiles,
                                onSending = { sending, text ->
                                    isSending = sending
                                    progressText = text
                                },
                                onDone = onDismiss
                            )
                        }
                    },
                    dismissLabel = getString(R.string.lbl_cancel),
                    onDismiss = onDismiss,
                    dismissEnabled = !isSending,
                    confirmEnabled = hasSelection && !isSending
                )
            }
        }

        pendingDelete?.let { fileItem ->
            RethinkConfirmDialog(
                onDismissRequest = { pendingDelete = null },
                title = getString(R.string.lbl_delete),
                message = getString(R.string.bug_report_delete_confirmation, fileItem.name),
                confirmText = getString(R.string.lbl_delete),
                dismissText = getString(R.string.lbl_cancel),
                isConfirmDestructive = true,
                onConfirm = {
                    pendingDelete = null
                    scope.launch {
                        deleteBugReportFile(
                            fileItem = fileItem,
                            bugReportFiles = bugReportFiles,
                            onDismiss = onDismiss
                        )
                    }
                },
                onDismiss = { pendingDelete = null }
            )
        }
    }

    @Composable
    private fun BugReportFileRow(
        fileItem: BugReportFile,
        position: CardPosition,
        onToggle: (Boolean) -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        RethinkListItem(
            headline = fileItem.name,
            supporting =
                "${formatFileSize(fileItem.file.length())} - ${formatDate(fileItem.file.lastModified())}",
            position = position,
            highlighted = fileItem.isSelected,
            onClick = { onToggle(!fileItem.isSelected) },
            leadingContent = {
                Checkbox(
                    checked = fileItem.isSelected,
                    onCheckedChange = null
                )
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = MaterialSymbols.Filled.Share,
                            contentDescription = getString(R.string.about_bug_report_desc)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = MaterialSymbols.Filled.Delete,
                            contentDescription = getString(R.string.lbl_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    private fun collectAllBugReportFiles(): List<BugReportFile> {
        val files = mutableListOf<BugReportFile>()
        val dir = filesDir

        val bugReportZip = File(BugReportZipper.getZipFileName(dir))
        if (bugReportZip.exists() && bugReportZip.length() > 0) {
            files.add(
                BugReportFile(
                    file = bugReportZip,
                    name = bugReportZip.name,
                    type = FileType.ZIP,
                    isSelected = true
                )
            )
        }

        if (Utilities.isAtleastO()) {
            val tombstoneZip = EnhancedBugReport.getTombstoneZipFile(this)
            if (tombstoneZip != null && tombstoneZip.exists() && tombstoneZip.length() > 0) {
                files.add(
                    BugReportFile(
                        file = tombstoneZip,
                        name = tombstoneZip.name,
                        type = FileType.ZIP,
                        isSelected = true
                    )
                )
            }
        }

        val bugReportDir = File(dir, BugReportZipper.BUG_REPORT_DIR_NAME)
        if (bugReportDir.exists() && bugReportDir.isDirectory) {
            bugReportDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    files.add(
                        BugReportFile(
                            file = file,
                            name = file.name,
                            type = getFileType(file),
                            isSelected = true
                        )
                    )
                }
            }
        }

        if (Utilities.isAtleastO()) {
            val tombstoneDir = File(dir, EnhancedBugReport.TOMBSTONE_DIR_NAME)
            if (tombstoneDir.exists() && tombstoneDir.isDirectory) {
                tombstoneDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        files.add(
                            BugReportFile(
                                file = file,
                                name = file.name,
                                type = FileType.TEXT,
                                isSelected = true
                            )
                        )
                    }
                }
            }
        }

        return files.sortedByDescending { it.file.lastModified() }
    }

    private fun getFileType(file: File): FileType {
        return when (file.extension.lowercase()) {
            "zip" -> FileType.ZIP
            "txt", "log" -> FileType.TEXT
            else -> FileType.TEXT
        }
    }

    private suspend fun sendBugReport(
        bugReportFiles: List<BugReportFile>,
        onSending: (Boolean, String) -> Unit,
        onDone: () -> Unit
    ) {
        val selectedFiles = bugReportFiles.filter { it.isSelected }.map { it.file }

        if (selectedFiles.isEmpty()) {
            showToastUiCentered(
                this,
                getString(R.string.bug_report_no_files_selected),
                Toast.LENGTH_SHORT
            )
            return
        }

        onSending(true, getString(R.string.bug_report_creating_zip))

        try {
            val attachmentUri = withContext(Dispatchers.IO) {
                if (selectedFiles.size == 1) {
                    getFileUri(selectedFiles[0])
                } else {
                    createCombinedZip(selectedFiles)
                }
            }

            if (attachmentUri != null) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.about_mail_to)))
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.about_mail_bugreport_subject)
                    )
                    putExtra(Intent.EXTRA_STREAM, attachmentUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(
                    Intent.createChooser(
                        emailIntent,
                        getString(R.string.about_mail_bugreport_share_title)
                    )
                )
                onDone()
            } else {
                showToastUiCentered(
                    this,
                    getString(R.string.error_loading_log_file),
                    Toast.LENGTH_SHORT
                )
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err sending bug report: ${e.message}", e)
            showToastUiCentered(
                this,
                getString(R.string.error_loading_log_file),
                Toast.LENGTH_SHORT
            )
        } finally {
            onSending(false, "")
        }
    }

    private fun createCombinedZip(files: List<File>): Uri? {
        val tempDir = cacheDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(tempDir, "rethinkdns_bugreport_$timestamp.zip")

        try {
            val addedEntries = mutableSetOf<String>()

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { file ->
                    if (file.extension == "zip") {
                        ZipFile(file).use { zf ->
                            val entries = zf.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (!entry.isDirectory && !addedEntries.contains(entry.name)) {
                                    addedEntries.add(entry.name)

                                    val newEntry = ZipEntry(entry.name)
                                    zos.putNextEntry(newEntry)
                                    zf.getInputStream(entry).use { input ->
                                        input.copyTo(zos)
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    } else {
                        if (!addedEntries.contains(file.name)) {
                            addedEntries.add(file.name)

                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { input ->
                                input.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            return getFileUri(zipFile)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err creating combined zip: ${e.message}", e)
            zipFile.delete()
            return null
        }
    }

    private fun getFileUri(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                this,
                BugReportZipper.FILE_PROVIDER_NAME,
                file
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err getting file uri: ${e.message}", e)
            null
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < BYTES_IN_KB -> "$size B"
            size < BYTES_IN_MB -> "${size / BYTES_IN_KB} KB"
            else -> String.format(Locale.US, "%.1f MB", size / MB_DIVISOR)
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(timestamp))
    }

    private suspend fun deleteBugReportFile(
        fileItem: BugReportFile,
        bugReportFiles: MutableList<BugReportFile>,
        onDismiss: () -> Unit
    ) {
        try {
            val deleted = withContext(Dispatchers.IO) { fileItem.file.delete() }

            if (deleted) {
                bugReportFiles.remove(fileItem)

                showToastUiCentered(
                    this,
                    getString(R.string.bug_report_file_deleted, fileItem.name),
                    Toast.LENGTH_SHORT
                )

                if (bugReportFiles.isEmpty()) {
                    showToastUiCentered(
                        this,
                        getString(R.string.bug_report_no_files_available),
                        Toast.LENGTH_SHORT
                    )
                    onDismiss()
                }
            } else {
                showToastUiCentered(
                    this,
                    getString(R.string.bug_report_delete_failed, fileItem.name),
                    Toast.LENGTH_SHORT
                )
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err deleting file: ${e.message}", e)
            showToastUiCentered(
                this,
                getString(R.string.bug_report_delete_failed, fileItem.name),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun openBugReportFile(file: File) {
        try {
            val uri = getFileUri(file) ?: return

            val mimeType = when (file.extension.lowercase()) {
                "zip" -> "application/zip"
                "txt", "log" -> "text/plain"
                else -> "text/plain"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.about_bug_report)))
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err opening file: ${e.message}", e)
            showToastUiCentered(
                this,
                getString(R.string.bug_report_error_opening_file),
                Toast.LENGTH_SHORT
            )
        }
    }

    data class BugReportFile(
        val file: File,
        val name: String,
        val type: FileType,
        val isSelected: Boolean
    )

    enum class FileType {
        ZIP,
        TEXT
    }

    companion object {
        private const val BYTES_IN_KB = 1024L
        private const val BYTES_IN_MB = 1024L * 1024L
        private const val MB_DIVISOR = 1024.0 * 1024.0
        const val EXTRA_NAV_TARGET = "extra_nav_target"
        const val NAV_TARGET_DOMAIN_CONNECTIONS = "nav_target_domain_connections"
        const val NAV_TARGET_APP_INFO = "nav_target_app_info"
        const val NAV_TARGET_NETWORK_LOGS = "nav_target_network_logs"
        const val NAV_TARGET_WG_MAIN = "nav_target_wg_main"
        const val EXTRA_DC_TYPE = "extra_dc_type"
        const val EXTRA_DC_FLAG = "extra_dc_flag"
        const val EXTRA_DC_DOMAIN = "extra_dc_domain"
        const val EXTRA_DC_ASN = "extra_dc_asn"
        const val EXTRA_DC_IP = "extra_dc_ip"
        const val EXTRA_DC_IS_BLOCKED = "extra_dc_is_blocked"
        const val EXTRA_DC_TIME_CATEGORY = "extra_dc_time_category"
        const val EXTRA_APP_INFO_UID = "extra_app_info_uid"
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

// No-receiver host so HomeScreenRoot callbacks keep Activity `this`, not BoxScope.
@Composable
private fun PauseOverlayHost(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

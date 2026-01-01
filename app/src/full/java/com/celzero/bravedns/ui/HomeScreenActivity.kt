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
package com.celzero.bravedns.ui

import Logger
import Logger.LOG_TAG_APP_UPDATE
import Logger.LOG_TAG_BACKUP_RESTORE
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
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.celzero.bravedns.BuildConfig
import com.celzero.bravedns.NonStoreAppUpdater
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.backup.BackupHelper
import com.celzero.bravedns.backup.BackupHelper.Companion.BACKUP_FILE_EXTN
import com.celzero.bravedns.backup.BackupHelper.Companion.INTENT_RESTART_APP
import com.celzero.bravedns.backup.BackupHelper.Companion.INTENT_SCHEME
import com.celzero.bravedns.backup.RestoreAgent
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.database.AppDatabase
import com.celzero.bravedns.database.AppInfoRepository
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.scheduler.BugReportZipper
import com.celzero.bravedns.scheduler.EnhancedBugReport
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.AppUpdater
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.AdvancedSettingActivity
import com.celzero.bravedns.ui.activity.AlertsActivity
import com.celzero.bravedns.ui.activity.AntiCensorshipActivity
import com.celzero.bravedns.ui.activity.AppListActivity
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.activity.CustomRulesActivity
import com.celzero.bravedns.ui.compose.navigation.HomeNavRequest
import com.celzero.bravedns.ui.activity.DnsDetailActivity
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import com.celzero.bravedns.ui.activity.EventsActivity
import com.celzero.bravedns.ui.activity.FirewallActivity
import com.celzero.bravedns.ui.activity.MiscSettingsActivity
import com.celzero.bravedns.ui.activity.NetworkLogsActivity
import com.celzero.bravedns.ui.activity.PauseActivity
import com.celzero.bravedns.ui.activity.ProxySettingsActivity
import com.celzero.bravedns.ui.activity.TunnelSettingsActivity
import com.celzero.bravedns.ui.activity.WelcomeActivity
import com.celzero.bravedns.ui.activity.WgMainActivity
import com.celzero.bravedns.ui.compose.navigation.HomeScreenRoot
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.MAX_ENDPOINT
import com.celzero.bravedns.util.Constants.Companion.PKG_NAME_PLAY_STORE
import com.celzero.bravedns.util.Constants.Companion.RETHINKDNS_SPONSOR_LINK
import com.celzero.bravedns.util.FirebaseErrorReporting
import com.celzero.bravedns.util.FirebaseErrorReporting.TOKEN_LENGTH
import com.celzero.bravedns.util.FirebaseErrorReporting.TOKEN_REGENERATION_PERIOD_DAYS
import com.celzero.bravedns.util.NewSettingsManager
import com.celzero.bravedns.util.RemoteFileTagUtil
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.openAppInfo
import com.celzero.bravedns.util.UIUtils.openNetworkSettings
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.UIUtils.openVpnProfile
import com.celzero.bravedns.util.UIUtils.sendEmailIntent
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getPackageMetadata
import com.celzero.bravedns.util.Utilities.getRandomString
import com.celzero.bravedns.util.Utilities.isAtleastO_MR1
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.isPlayStoreFlavour
import com.celzero.bravedns.util.Utilities.isWebsiteFlavour
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.disableFrostTemporarily
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import kotlinx.coroutines.Dispatchers
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

class HomeScreenActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appInfoDb by inject<AppInfoRepository>()
    private val appUpdateManager by inject<AppUpdater>()
    private val rdb by inject<RefreshDatabase>()
    private val appConfig by inject<AppConfig>()
    private val workScheduler by inject<WorkScheduler>()
    private val appDatabase by inject<AppDatabase>()

    private val homeViewModel by viewModel<com.celzero.bravedns.ui.compose.home.HomeScreenViewModel>()
    private val summaryViewModel by viewModel<com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel>()
    private val aboutViewModel by viewModel<com.celzero.bravedns.ui.compose.about.AboutViewModel>()
    private val detailedStatsViewModel by viewModel<com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel>()

    // TODO: see if this can be replaced with a more robust solution
    // keep track of when app went to background
    private var appInBackground = false
    private var showBugReportSheet by mutableStateOf(false)
    private var homeDialogState by mutableStateOf<HomeDialog?>(null)
    private var snackbarHostState: SnackbarHostState? = null
    private var homeNavRequest by mutableStateOf<HomeNavRequest?>(null)

    private lateinit var startForResult: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionResult: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var miscSettingsResultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    // TODO - #324 - Usage of isDarkTheme() in all activities.
    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        // do not launch on board activity when app is running on TV
        if (persistentState.firstTimeLaunch && !isAppRunningOnTv()) {
            launchOnboardActivity()
            return
        }

        handleFrostEffectIfNeeded(persistentState.theme)

        registerForActivityResult()

        updateNewVersion()

        // handle intent receiver for backup/restore
        handleIntent()

        initUpdateCheck()

        observeAppState()

        NewSettingsManager.handleNewSettings()

        regenerateFirebaseTokenIfNeeded()

        appConfig.getBraveModeObservable().postValue(appConfig.getBraveMode().mode)

        setContent {
            RethinkTheme {
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
                HomeScreenRoot(
                    homeUiState = homeState,
                    onHomeStartStopClick = { handleMainScreenBtnClickEvent() },
                    onHomeDnsClick = { startDnsActivity(0) },
                    onHomeFirewallClick = { startFirewallActivity(0) },
                    onHomeProxyClick = {
                        if (appConfig.isWireGuardEnabled()) {
                            startActivity(ScreenType.PROXY_WIREGUARD)
                        } else {
                            startActivity(ScreenType.PROXY)
                        }
                    },
                    onHomeLogsClick = { startActivity(ScreenType.LOGS, NetworkLogsActivity.Tabs.NETWORK_LOGS.screen) },
                    onHomeAppsClick = { startAppsActivity() },
                    onHomeSponsorClick = { promptForAppSponsorship() },
                    summaryViewModel = summaryViewModel,
                    onOpenDetailedStats = { type -> openDetailedStatsUi(type) },
                    isDebug = DEBUG,
                    onConfigureAppsClick = { startActivity(ConfigureScreenType.APPS) },
                    onConfigureDnsClick = { startActivity(ConfigureScreenType.DNS) },
                    onConfigureFirewallClick = { startActivity(ConfigureScreenType.FIREWALL) },
                    onConfigureProxyClick = { startActivity(ConfigureScreenType.PROXY) },
                    onConfigureNetworkClick = { startActivity(ConfigureScreenType.VPN) },
                    onConfigureOthersClick = { startActivity(ConfigureScreenType.OTHERS) },
                    onConfigureLogsClick = { startActivity(ConfigureScreenType.LOGS) },
                    onConfigureAntiCensorshipClick = { startActivity(ConfigureScreenType.ANTI_CENSORSHIP) },
                    onConfigureAdvancedClick = { startActivity(ConfigureScreenType.ADVANCED) },
                    aboutUiState = aboutState,
                    onSponsorClick = { openUrl(this, RETHINKDNS_SPONSOR_LINK) },
                    onTelegramClick = { openUrl(this, getString(R.string.about_telegram_link)) },
                    onBugReportClick = { aboutViewModel.triggerBugReport() },
                    onWhatsNewClick = { showNewFeaturesDialog() },
                    onAppUpdateClick = { checkForUpdate(AppUpdater.UserPresent.INTERACTIVE) },
                    onContributorsClick = { showContributors() },
                    onTranslateClick = { openUrl(this, getString(R.string.about_translate_link)) },
                    onWebsiteClick = { openUrl(this, getString(R.string.about_website_link)) },
                    onGithubClick = { openUrl(this, getString(R.string.about_github_link)) },
                    onFaqClick = { openUrl(this, getString(R.string.about_faq_link)) },
                    onDocsClick = { openUrl(this, getString(R.string.about_docs_link)) },
                    onPrivacyPolicyClick = { openUrl(this, getString(R.string.about_privacy_policy_link)) },
                    onTermsOfServiceClick = { openUrl(this, getString(R.string.about_terms_link)) },
                    onLicenseClick = { openUrl(this, getString(R.string.about_license_link)) },
                    onTwitterClick = { openUrl(this, getString(R.string.about_twitter_handle)) },
                    onEmailClick = { disableFrostTemporarily(); sendEmailIntent(this) },
                    onRedditClick = { openUrl(this, getString(R.string.about_reddit_handle)) },
                    onElementClick = { openUrl(this, getString(R.string.about_matrix_handle)) },
                    onMastodonClick = { openUrl(this, getString(R.string.about_mastodom_handle)) },
                    onAppInfoClick = { openAppInfo(this) },
                    onVpnProfileClick = { openVpnProfile(this) },
                    onNotificationClick = { openNotificationSettings() },
                    onStatsClick = { openStatsDialog() },
                    onDbStatsClick = { openDatabaseDumpDialog() },
                    onFlightRecordClick = { initiateFlightRecord() },
                    onEventLogsClick = { openEventLogs() },
                    onTokenClick = { copyTokenToClipboard() },
                    onTokenDoubleTap = { aboutViewModel.generateNewToken() },
                    onFossClick = { openUrl(this, getString(R.string.about_foss_link)) },
                    onFlossFundsClick = { openUrl(this, getString(R.string.about_floss_fund_link)) },
                    snackbarHostState = hostState,
                    detailedStatsViewModel = detailedStatsViewModel,
                    homeNavRequest = homeNavRequest,
                    onHomeNavConsumed = { homeNavRequest = null }
                )
                if (showBugReportSheet) {
                    BugReportFilesSheet(onDismiss = { showBugReportSheet = false })
                }
                HomeDialogHost()
            }
        }

        // enable in-app messaging, will be used to show in-app messages in case of billing issues
        //enableInAppMessaging()
    }


    /*private fun enableInAppMessaging() {
        initiateBillingIfNeeded()
        // enable in-app messaging
        InAppBillingHandler.enableInAppMessaging(this)
        Logger.v(LOG_IAB, "enableInAppMessaging: enabled")
    }

    private fun initiateBillingIfNeeded() {
        if (InAppBillingHandler.isBillingClientSetup()) {
            Logger.i(LOG_IAB, "ensureBillingSetup: billing client already setup")
            return
        }

        InAppBillingHandler.initiate(this.applicationContext)
        Logger.i(LOG_IAB, "ensureBillingSetup: billing client initiated")
    }*/

    /*override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // by simply receiving and setting the new intent, we ensure that when the activity
        // is brought back to the foreground, it uses the latest intent state
        Logger.v(LOG_TAG_UI, "home screen activity received new intent")
    }*/

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

    private fun handleIntent() {
        val intent = this.intent ?: return
        if (
            intent.scheme?.equals(INTENT_SCHEME) == true &&
            intent.data?.path?.contains(BACKUP_FILE_EXTN) == true
        ) {
            handleRestoreProcess(intent.data)
        } else if (intent.scheme?.equals(INTENT_SCHEME) == true) {
            showToastUiCentered(
                this,
                getString(R.string.brbs_restore_no_uri_toast),
                Toast.LENGTH_SHORT
            )
        } else if (intent.getBooleanExtra(INTENT_RESTART_APP, false)) {
            Logger.i(LOG_TAG_UI, "Restart from restore, so refreshing app database...")
            io { rdb.refresh(RefreshDatabase.ACTION_REFRESH_RESTORE) }
        }
    }

    private fun handleRestoreProcess(uri: Uri?) {
        if (uri == null) {
            showToastUiCentered(
                this,
                getString(R.string.brbs_restore_no_uri_toast),
                Toast.LENGTH_SHORT
            )
            return
        }

        showRestoreDialog(uri)
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
        homeDialogState = HomeDialog.Restore(uri)
    }

    private fun startRestore(fileUri: Uri) {
        Logger.i(LOG_TAG_BACKUP_RESTORE, "invoke worker to initiate the restore process")
        val data = Data.Builder()
        data.putString(BackupHelper.DATA_BUILDER_RESTORE_URI, fileUri.toString())

        val importWorker =
            OneTimeWorkRequestBuilder<RestoreAgent>()
                .setInputData(data.build())
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(RestoreAgent.TAG)
                .build()
        WorkManager.getInstance(this).beginWith(importWorker).enqueue()
    }

    private fun observeRestoreWorker() {
        val workManager = WorkManager.getInstance(this.applicationContext)

        // observer for custom download manager worker
        workManager.getWorkInfosByTagLiveData(RestoreAgent.TAG).observe(this) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                LOG_TAG_BACKUP_RESTORE,
                "WorkManager state: ${workInfo.state} for ${RestoreAgent.TAG}"
            )
            if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                showToastUiCentered(
                    this,
                    getString(R.string.brbs_restore_complete_toast),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                WorkInfo.State.FAILED == workInfo.state
            ) {
                showToastUiCentered(
                    this,
                    getString(R.string.brbs_restore_no_uri_toast),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(RestoreAgent.TAG)
            } else { // state == blocked
                // no-op
            }
        }
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it == BraveVPNService.State.PAUSED) {
                startActivity(Intent().setClass(this, PauseActivity::class.java))
                finish()
            }
        }
    }

    private fun removeThisMethod() {
        // set allowBypass to false for all versions, overriding the user's preference.
        // the default was true for Play Store and website versions, and false for F-Droid.
        // when allowBypass is true, some OEMs bypass the VPN service, causing connections
        // to fail due to the "Block connections without VPN" option.
        persistentState.allowBypass = false

        io {
            appInfoDb.setRethinkToBypassDnsAndFirewall()
            appInfoDb.setRethinkToBypassProxy(true)
        }

        // change the persistent state for defaultDnsUrl, if its google.com (only for v055d)
        // TODO: remove this post v054.
        // this is to fix the default dns url, as the default dns url is changed from
        // dns.google.com to dns.google. In servers.xml default ips available for dns.google
        // so changing the default dns url to dns.google
        if (persistentState.defaultDnsUrl.contains("dns.google.com")) {
            persistentState.defaultDnsUrl = Constants.DEFAULT_DNS_LIST[2].url
        }
        moveRemoteBlocklistFileFromAsset()
        // if biometric auth is enabled, then set the biometric auth type to 3 (15 minutes)
        if (persistentState.biometricAuth) {
            persistentState.biometricAuthType =
                MiscSettingsActivity.BioMetricType.FIFTEEN_MIN.action
            // reset the bio metric auth time, as now the value is changed from System.currentTimeMillis
            // to SystemClock.elapsedRealtime
            persistentState.biometricAuthTime = SystemClock.elapsedRealtime()
        }

        // reset the local blocklist download from android download manager to custom in v055o
        persistentState.useCustomDownloadManager = true

        // delete residue wgs from database, remove this post v055o
        io { WireguardManager.deleteResidueWgs() }
        // reset the plus url to empty if it is set as /rec
        io {
            val rpe = appConfig.getRethinkPlusEndpoint()
            val url = rpe?.url ?: return@io
            if (url == "https://max.rethinkdns.com/rec" || url == "https://rplus.rethinkdns.com/rec") {
                val newUrl = if (rpe.url.contains(MAX_ENDPOINT)) {
                    Constants.RETHINK_BASE_URL_MAX
                } else {
                    Constants.RETHINK_BASE_URL_SKY
                }
                appConfig.updateRethinkEndpoint(Constants.RETHINK_DNS_PLUS, newUrl, 0)
            }
        }
    }

    // fixme: find a cleaner way to implement this, move this to some other place
    private fun moveRemoteBlocklistFileFromAsset() {
        io {
            // already there is a remote blocklist file available
            if (
                persistentState.remoteBlocklistTimestamp >
                Constants.PACKAGED_REMOTE_FILETAG_TIMESTAMP
            ) {
                RethinkBlocklistManager.readJson(
                    this,
                    RethinkBlocklistManager.DownloadType.REMOTE,
                    persistentState.remoteBlocklistTimestamp
                )
                return@io
            }

            RemoteFileTagUtil.moveFileToLocalDir(this.applicationContext, persistentState)
        }
    }

    private fun launchOnboardActivity() {
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateNewVersion() {
        if (!isNewVersion()) return

        // no need to show new settings on first time launch
        if (persistentState.appVersion != 0) {
            // if app version is not 0, then it means the app is updated
            NewSettingsManager.initializeNewSettings()
            Logger.i(LOG_TAG_UI, "app version already set, so its update, showing new settings")
        }
        val version = getLatestVersion()
        Logger.i(LOG_TAG_UI, "new version detected, updating the app version, version: $version")
        persistentState.appVersion = version
        persistentState.showWhatsNewChip = true
        persistentState.appUpdateTimeTs = System.currentTimeMillis()

        // FIXME: remove this post v054
        removeThisMethod()
    }

    private fun isNewVersion(): Boolean {
        val versionStored = persistentState.appVersion
        val version = getLatestVersion()
        return (version != 0 && version != versionStored)
    }

    private fun getLatestVersion(): Int {
        val pInfo: PackageInfo? = getPackageMetadata(this.packageManager, this.packageName)
        // TODO: modify this to use the latest version code api
        @Suppress("DEPRECATION")
        val v = pInfo?.versionCode ?: 0
        // latest version has apk variant (baseAbiVersionCode * 10000000 + variant.versionCode)
        // so we need to mod the version code by 10000000 to get the actual version code
        // for example: 10000000 + 45 = 10000045, so the version code is 1
        // see build.gradle (:app), #project.ext.versionCodes
        val latestVersionCode = v % 10000000 // 10000000 is the base version code
        Logger.i(LOG_TAG_UI, "latest version code: $latestVersionCode")
        return latestVersionCode
    }

    // FIXME - Move it to Android's built-in WorkManager
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
        homeDialogState = HomeDialog.Download(source, title, message)
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

    enum class ConfigureScreenType {
        APPS,
        DNS,
        FIREWALL,
        PROXY,
        VPN,
        OTHERS,
        LOGS,
        ANTI_CENSORSHIP,
        ADVANCED
    }

    private sealed interface HomeDialog {
        data class Restore(val uri: Uri) : HomeDialog
        data class Download(
            val source: AppUpdater.InstallSource,
            val title: String,
            val message: String
        ) : HomeDialog
        data class AlwaysOnStop(val message: String) : HomeDialog
        data object AlwaysOnDisable : HomeDialog
        data object PrivateDns : HomeDialog
        data class FirstTimeVpn(val intent: Intent) : HomeDialog
        data class Stats(val displayText: String, val dump: String) : HomeDialog
        data class Sponsor(val usageMessage: String, val amount: String) : HomeDialog
        data object Contributors : HomeDialog
        data class DatabaseTables(val tables: List<String>) : HomeDialog
        data class DatabaseDump(val title: String, val dump: String) : HomeDialog
        data object NoLog : HomeDialog
        data class NewFeatures(val title: String) : HomeDialog
    }

    private fun openDetailedStatsUi(type: SummaryStatisticsType) {
        val timeCategory = summaryViewModel.uiState.value.timeCategory.value
        val category =
            SummaryStatisticsViewModel.TimeCategory.fromValue(timeCategory)
                ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR
        homeNavRequest = HomeNavRequest.DetailedStats(type, category)
    }

    private fun promptForAppSponsorship() {
        val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        val timeDiff = System.currentTimeMillis() - installTime
        val days = (timeDiff / (1000L * 60L * 60L * 24L)).toDouble()
        val month = days / 30.0
        val amount = month * (0.60 + 0.20)

        val msg = getString(R.string.sponser_dialog_usage_msg, days.toInt().toString(), "%.2f".format(amount))
        val formattedAmount =
            getString(R.string.two_argument_no_space, getString(R.string.symbol_dollar), "%.2f".format(amount))
        homeDialogState = HomeDialog.Sponsor(msg, formattedAmount)
    }

    private fun handleMainScreenBtnClickEvent() {
        Utilities.delay(TimeUnit.MILLISECONDS.toMillis(500L), lifecycleScope) { }
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
        if (Utilities.isOtherVpnHasAlwaysOn(this)) {
            showAlwaysOnDisableDialog()
            return true
        }

        if (VpnController.isAlwaysOn(this) && VpnController.isOn()) {
            showAlwaysOnStopDialog()
            return true
        }

        return false
    }

    private fun showAlwaysOnStopDialog() {
        val message =
            if (VpnController.isVpnLockdown()) {
                UIUtils.htmlToSpannedText(getString(R.string.always_on_dialog_lockdown_stop_message)).toString()
            } else {
                getString(R.string.always_on_dialog_stop_message)
            }
        homeDialogState = HomeDialog.AlwaysOnStop(message)
    }

    private fun showAlwaysOnDisableDialog() {
        homeDialogState = HomeDialog.AlwaysOnDisable
    }

    private fun startDnsActivity(screenToLoad: Int) {
        if (Utilities.isPrivateDnsActive(this)) {
            showPrivateDnsDialog()
            return
        }

        if (canStartRethinkActivity()) {
            startActivity(ScreenType.RETHINK, screenToLoad)
            return
        }

        startActivity(ScreenType.DNS, screenToLoad)
    }

    private fun canStartRethinkActivity(): Boolean {
        val dns = appConfig.getDnsType()
        return dns.isRethinkRemote() && !WireguardManager.oneWireGuardEnabled()
    }

    private fun showPrivateDnsDialog() {
        homeDialogState = HomeDialog.PrivateDns
    }

    private fun startFirewallActivity(screenToLoad: Int) {
        startActivity(ScreenType.FIREWALL, screenToLoad)
    }

    private fun startAppsActivity() {
        val intent = Intent(this, AppListActivity::class.java)
        startActivity(intent)
    }

    private fun startActivity(type: ScreenType, screenToLoad: Int = 0) {
        val intent =
            when (type) {
                ScreenType.DNS -> Intent(this, DnsDetailActivity::class.java)
                ScreenType.FIREWALL -> Intent(this, FirewallActivity::class.java)
                ScreenType.LOGS -> Intent(this, NetworkLogsActivity::class.java)
                ScreenType.RULES -> Intent(this, CustomRulesActivity::class.java)
                ScreenType.PROXY -> Intent(this, ProxySettingsActivity::class.java)
                ScreenType.ALERTS -> Intent(this, AlertsActivity::class.java)
                ScreenType.RETHINK -> Intent(this, ConfigureRethinkBasicActivity::class.java)
                ScreenType.PROXY_WIREGUARD -> Intent(this, WgMainActivity::class.java)
            }
        if (type == ScreenType.RETHINK) {
            io {
                val endpoint = appConfig.getRemoteRethinkEndpoint()
                val url = endpoint?.url
                val name = endpoint?.name
                intent.putExtra(ConfigureRethinkBasicActivity.RETHINK_BLOCKLIST_NAME, name)
                intent.putExtra(ConfigureRethinkBasicActivity.RETHINK_BLOCKLIST_URL, url)
                uiCtx { startActivity(intent) }
            }
        } else {
            intent.putExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, screenToLoad)
            startActivity(intent)
        }
    }

    private fun startActivity(type: ConfigureScreenType) {
        val intent =
            when (type) {
                ConfigureScreenType.APPS -> Intent(this, AppListActivity::class.java)
                ConfigureScreenType.DNS -> Intent(this, DnsDetailActivity::class.java)
                ConfigureScreenType.FIREWALL -> Intent(this, FirewallActivity::class.java)
                ConfigureScreenType.PROXY -> Intent(this, ProxySettingsActivity::class.java)
                ConfigureScreenType.VPN -> Intent(this, TunnelSettingsActivity::class.java)
                ConfigureScreenType.OTHERS -> Intent(this, MiscSettingsActivity::class.java)
                ConfigureScreenType.LOGS -> Intent(this, NetworkLogsActivity::class.java)
                ConfigureScreenType.ANTI_CENSORSHIP -> Intent(this, AntiCensorshipActivity::class.java)
                ConfigureScreenType.ADVANCED -> Intent(this, AdvancedSettingActivity::class.java)
            }

        if (type == ConfigureScreenType.OTHERS) {
            miscSettingsResultLauncher.launch(intent)
        } else {
            startActivity(intent)
        }
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
            showFirstTimeVpnDialog(prepareVpnIntent)
            return false
        }
        Logger.i(LOG_TAG_VPN, "VPN service is prepared, starting VPN service")
        return true
    }

    private fun showFirstTimeVpnDialog(prepareVpnIntent: Intent) {
        homeDialogState = HomeDialog.FirstTimeVpn(prepareVpnIntent)
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

        miscSettingsResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == MiscSettingsActivity.THEME_CHANGED_RESULT) {
                    recreate()
                }
            }
    }

    private fun copyTokenToClipboard() {
        val text = persistentState.firebaseUserToken
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("token", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun initiateFlightRecord() {
        io { VpnController.performFlightRecording() }
        Toast.makeText(this, "Flight recording started", Toast.LENGTH_SHORT).show()
    }

    private fun openEventLogs() {
        val intent = Intent(this, EventsActivity::class.java)
        startActivity(intent)
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
                homeDialogState = HomeDialog.Stats(displayText, stats)
            }
        }
    }

    private fun copyToClipboard(label: String, text: String): ClipboardManager? {
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        return clipboard
    }

    private fun openDatabaseDumpDialog() {
        io {
            val tables = getDatabaseTables()
            if (tables.isEmpty()) {
                uiCtx { showNoLogDialog() }
                return@io
            }
            uiCtx { showDatabaseTablesDialog(tables) }
        }
    }

    private fun showDatabaseTablesDialog(tables: List<String>) {
        homeDialogState = HomeDialog.DatabaseTables(tables)
    }

    private fun showDatabaseDumpDialog(title: String, dump: String) {
        homeDialogState = HomeDialog.DatabaseDump(title, dump)
    }

    private fun getDatabaseTables(): List<String> {
        val db = appDatabase.openHelper.readableDatabase
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val tableName = cursor.getString(0)
            if (tableName != "android_metadata" && tableName != "room_master_table") {
                tables.add(tableName)
            }
        }
        cursor.close()
        return tables
    }

    private fun buildTableDump(table: String): String {
        val db = appDatabase.openHelper.readableDatabase
        val cursor = db.query("SELECT * FROM $table")
        val columnNames = cursor.columnNames
        val result = StringBuilder()
        result.append("Table: $table\n")
        result.append(columnNames.joinToString(separator = "\t"))
        result.append("\n")
        while (cursor.moveToNext()) {
            for (i in columnNames.indices) {
                result.append(cursor.getString(i)).append("\t")
            }
            result.append("\n")
        }
        cursor.close()
        return result.toString()
    }

    private fun hasAnyLogsAvailable(): Boolean {
        val dir = filesDir
        val bugReportDir = java.io.File(dir, BugReportZipper.BUG_REPORT_DIR_NAME)
        if (bugReportDir.exists() && bugReportDir.isDirectory) {
            val bugReportFiles = bugReportDir.listFiles()
            if (bugReportFiles != null && bugReportFiles.any { it.isFile && it.length() > 0 }) {
                return true
            }
        }

        return false
    }

    private fun showNoLogDialog() {
        homeDialogState = HomeDialog.NoLog
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
        homeDialogState = HomeDialog.NewFeatures(title)
    }

    private fun showContributors() {
        homeDialogState = HomeDialog.Contributors
    }

    private fun promptCrashLogAction() {
        if (Utilities.isAtleastO()) {
            io {
                try {
                    EnhancedBugReport.addLogsToZipFile(this@HomeScreenActivity)
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_UI, "err adding tombstone to zip: ${e.message}", e)
                }
            }
        }

        val dir = filesDir
        val zipPath = BugReportZipper.getZipFileName(dir)
        val zipFile = java.io.File(zipPath)

        if (!zipFile.exists() || zipFile.length() <= 0) {
            showToastUiCentered(
                this,
                getString(R.string.log_file_not_available),
                Toast.LENGTH_SHORT
            )
            return
        }

        showBugReportSheet = true
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

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                bugReportFiles.forEach { it.isSelected = checked }
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
                                bugReportFiles.forEach { it.isSelected = !allSelected }
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(bugReportFiles, key = { it.file.absolutePath }) { item ->
                            BugReportFileRow(
                                fileItem = item,
                                onShare = { openBugReportFile(item.file) },
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSending) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = progressText)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (!isSending) {
                                scope.launch {
                                    sendBugReport(
                                        bugReportFiles = bugReportFiles,
                                        onSending = { sending, text ->
                                            isSending = sending
                                            progressText = text
                                        },
                                        onDone = {
                                            showBugReportSheet = false
                                        }
                                    )
                                }
                            }
                        },
                        enabled = hasSelection && !isSending
                    ) {
                        Text(text = getString(R.string.about_bug_report_dialog_positive_btn))
                    }
                }
            }
        }

        pendingDelete?.let { fileItem ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(text = getString(R.string.lbl_delete)) },
                text = {
                    Text(
                        text = getString(R.string.bug_report_delete_confirmation, fileItem.name)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            scope.launch {
                                deleteBugReportFile(
                                    fileItem = fileItem,
                                    bugReportFiles = bugReportFiles,
                                    onDismiss = onDismiss
                                )
                            }
                        }
                    ) {
                        Text(text = getString(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun BugReportFileRow(
        fileItem: BugReportFile,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = fileItem.isSelected,
                onCheckedChange = { checked -> fileItem.isSelected = checked }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text =
                        "${formatFileSize(fileItem.file.length())} - ${formatDate(fileItem.file.lastModified())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = null
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
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

    private fun handleShowAppExitInfo() {
        if (WorkScheduler.isWorkRunning(this, WorkScheduler.APP_EXIT_INFO_JOB_TAG)) return

        workScheduler.scheduleOneTimeWorkForAppExitInfo()

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.getWorkInfosByTagLiveData(WorkScheduler.APP_EXIT_INFO_ONE_TIME_JOB_TAG).observe(
            this
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_SCHEDULER,
                "WorkManager state: ${workInfo.state} for ${WorkScheduler.APP_EXIT_INFO_ONE_TIME_JOB_TAG}"
            )
            if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                onAppExitInfoSuccess()
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                WorkInfo.State.FAILED == workInfo.state
            ) {
                onAppExitInfoFailure()
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(WorkScheduler.APP_EXIT_INFO_ONE_TIME_JOB_TAG)
            } else {
                // no-op
            }
        }
    }

    data class BugReportFile(
        val file: File,
        val name: String,
        val type: FileType,
        var isSelected: Boolean
    )

    enum class FileType {
        ZIP,
        TEXT
    }

    companion object {
        private const val BYTES_IN_KB = 1024L
        private const val BYTES_IN_MB = 1024L * 1024L
        private const val MB_DIVISOR = 1024.0 * 1024.0
    }

    @Composable
    private fun WhatsNewDialogContent() {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(15.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            HtmlText(
                text = getString(R.string.whats_new_version_update),
                textAlign = TextAlign.Start
            )
        }
    }

    @Composable
    private fun HomeDialogHost() {
        when (val dialog = homeDialogState) {
            is HomeDialog.Restore -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = getString(R.string.brbs_restore_dialog_title)) },
                    text = { Text(text = getString(R.string.brbs_restore_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                startRestore(dialog.uri)
                                observeRestoreWorker()
                            }
                        ) {
                            Text(text = getString(R.string.brbs_restore_dialog_positive))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            is HomeDialog.Download -> {
                val isUpdateAvailable = dialog.title == getString(R.string.download_update_dialog_title)
                val resolvedMessage =
                    if (isUpdateAvailable && dialog.source == AppUpdater.InstallSource.STORE) {
                        "A new version is available. Please update from Play Store."
                    } else {
                        dialog.message
                    }

                AlertDialog(
                    onDismissRequest = {
                        if (!isUpdateAvailable) {
                            homeDialogState = null
                        }
                    },
                    title = { Text(text = dialog.title) },
                    text = { Text(text = resolvedMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (isUpdateAvailable) {
                                    if (dialog.source == AppUpdater.InstallSource.STORE) {
                                        appUpdateManager.completeUpdate()
                                    } else {
                                        initiateDownload()
                                    }
                                }
                                homeDialogState = null
                            }
                        ) {
                            val label =
                                if (isUpdateAvailable && dialog.source != AppUpdater.InstallSource.STORE) {
                                    getString(R.string.hs_download_positive_website)
                                } else {
                                    getString(R.string.hs_download_positive_default)
                                }
                            Text(text = label)
                        }
                    },
                    dismissButton = {
                        if (isUpdateAvailable) {
                            TextButton(
                                onClick = {
                                    persistentState.lastAppUpdateCheck = System.currentTimeMillis()
                                    homeDialogState = null
                                }
                            ) {
                                Text(text = getString(R.string.hs_download_negative_default))
                            }
                        }
                    }
                )

            }
            is HomeDialog.AlwaysOnStop -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = getString(R.string.always_on_dialog_stop_heading)) },
                    text = { Text(text = dialog.message) },
                    confirmButton = {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = {
                                    homeDialogState = null
                                    stopVpnService()
                                }
                            ) {
                                Text(text = getString(R.string.always_on_dialog_positive))
                            }
                            TextButton(
                                onClick = {
                                    homeDialogState = null
                                    openVpnProfile(this@HomeScreenActivity)
                                }
                            ) {
                                Text(text = getString(R.string.always_on_dialog_neutral))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            HomeDialog.AlwaysOnDisable -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = getString(R.string.always_on_dialog_heading)) },
                    text = { Text(text = getString(R.string.always_on_dialog)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                openVpnProfile(this@HomeScreenActivity)
                            }
                        ) {
                            Text(text = getString(R.string.always_on_dialog_positive_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            HomeDialog.PrivateDns -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = getString(R.string.private_dns_dialog_heading)) },
                    text = { Text(text = getString(R.string.private_dns_dialog_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                openNetworkSettings(this@HomeScreenActivity, Settings.ACTION_WIRELESS_SETTINGS)
                            }
                        ) {
                            Text(text = getString(R.string.private_dns_dialog_positive))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_dismiss))
                        }
                    }
                )
            }
            is HomeDialog.FirstTimeVpn -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = getString(R.string.hsf_vpn_dialog_header)) },
                    text = { Text(text = getString(R.string.hsf_vpn_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                try {
                                    startForResult.launch(dialog.intent)
                                } catch (e: ActivityNotFoundException) {
                                    Logger.e(LOG_TAG_VPN, "Activity not found to start VPN service", e)
                                    showToastUiCentered(
                                        this@HomeScreenActivity,
                                        getString(R.string.hsf_vpn_prepare_failure),
                                        Toast.LENGTH_LONG
                                    )
                                }
                            }
                        ) {
                            Text(text = getString(R.string.lbl_proceed))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            is HomeDialog.Stats -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = getString(R.string.title_statistics)) },
                    text = {
                        SelectionContainer {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(8.dp)
                            ) {
                                Text(text = dialog.displayText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                copyToClipboard("stats_dump", dialog.dump)
                                showToastUiCentered(
                                    this@HomeScreenActivity,
                                    getString(R.string.copied_clipboard),
                                    Toast.LENGTH_SHORT
                                )
                            }
                        ) {
                            Text(text = getString(R.string.dns_info_neutral))
                        }
                    }
                )
            }
            is HomeDialog.Sponsor -> {
                Dialog(
                    onDismissRequest = { homeDialogState = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = getString(R.string.about_sponsor_link_text),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SponsorInfoDialogContent(
                                amount = dialog.amount,
                                usageMessage = dialog.usageMessage,
                                onSponsorClick = { openUrl(this@HomeScreenActivity, RETHINKDNS_SPONSOR_LINK) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { homeDialogState = null }) {
                                    Text(text = getString(R.string.lbl_cancel))
                                }
                            }
                        }
                    }
                }
            }
            HomeDialog.Contributors -> {
                Dialog(
                    onDismissRequest = { homeDialogState = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ContributorsDialogContent(onDismiss = { homeDialogState = null })
                    }
                }
            }
            is HomeDialog.DatabaseTables -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = getString(R.string.title_database_dump)) },
                    text = {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dialog.tables.forEach { table ->
                                TextButton(
                                    onClick = {
                                        homeDialogState = null
                                        io {
                                            val dump = buildTableDump(table)
                                            uiCtx { homeDialogState = HomeDialog.DatabaseDump(table, dump) }
                                        }
                                    }
                                ) {
                                    Text(text = table)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            is HomeDialog.DatabaseDump -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = dialog.title) },
                    text = {
                        SelectionContainer {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(8.dp)
                            ) {
                                Text(text = dialog.dump, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                copyToClipboard("db_dump", dialog.dump)
                                showToastUiCentered(
                                    this@HomeScreenActivity,
                                    getString(R.string.copied_clipboard),
                                    Toast.LENGTH_SHORT
                                )
                            }
                        ) {
                            Text(text = getString(R.string.dns_info_neutral))
                        }
                    }
                )
            }
            HomeDialog.NoLog -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = getString(R.string.about_bug_no_log_dialog_title)) },
                    text = { Text(text = getString(R.string.about_bug_no_log_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                sendEmailIntent(this@HomeScreenActivity)
                            }
                        ) {
                            Text(text = getString(R.string.about_bug_no_log_dialog_positive_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
            is HomeDialog.NewFeatures -> {
                AlertDialog(
                    onDismissRequest = { homeDialogState = null },
                    title = { Text(text = dialog.title) },
                    text = { WhatsNewDialogContent() },
                    confirmButton = {
                        TextButton(onClick = { homeDialogState = null }) {
                            Text(text = getString(R.string.about_dialog_positive_button))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                homeDialogState = null
                                sendEmailIntent(this@HomeScreenActivity)
                            }
                        ) {
                            Text(text = getString(R.string.about_dialog_neutral_button))
                        }
                    }
                )
            }
            null -> Unit
        }
    }

    @Composable
    private fun ContributorsDialogContent(onDismiss: () -> Unit) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = getString(R.string.lbl_dismiss)
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getString(R.string.contributors_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HtmlText(
                text = getString(R.string.contributors_list),
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun SponsorInfoDialogContent(
        amount: String,
        usageMessage: String,
        onSponsorClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = amount, style = MaterialTheme.typography.titleLarge)
            Text(text = usageMessage, style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSponsorClick) {
                    Text(text = getString(R.string.about_sponsor_link_text))
                }
            }
        }
    }

    @Composable
    private fun HtmlText(text: String, textAlign: TextAlign) {
        val textValue = remember(text) { UIUtils.htmlToSpannedText(text).toString() }
        Text(
            text = textValue,
            modifier = Modifier.fillMaxWidth(),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    color = Color(fetchColor(this@HomeScreenActivity, R.attr.primaryTextColor)),
                    textAlign = textAlign
                )
        )
    }

    private fun onAppExitInfoFailure() {
        showToastUiCentered(
            this,
            getString(R.string.log_file_not_available),
            Toast.LENGTH_SHORT
        )
        hideBugReportProgressUi()
    }

    private fun onAppExitInfoSuccess() {
        promptCrashLogAction()
    }

    private fun hideBugReportProgressUi() {
        aboutViewModel.setBugReportRunning(false)
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

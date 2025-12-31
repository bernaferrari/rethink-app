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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.database.AppDatabase
import com.celzero.bravedns.databinding.DialogInfoRulesLayoutBinding
import com.celzero.bravedns.databinding.DialogWhatsnewBinding
import com.celzero.bravedns.scheduler.BugReportZipper
import com.celzero.bravedns.scheduler.BugReportZipper.getZipFileName
import com.celzero.bravedns.scheduler.EnhancedBugReport
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.AppUpdater
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.HomeScreenActivity
import com.celzero.bravedns.ui.bottomsheet.BugReportFilesBottomSheet
import com.celzero.bravedns.ui.compose.about.AboutScreen
import com.celzero.bravedns.ui.compose.about.AboutViewModel
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.RETHINKDNS_SPONSOR_LINK
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.UIUtils.openAppInfo
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.UIUtils.openVpnProfile
import com.celzero.bravedns.util.UIUtils.sendEmailIntent
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getPackageMetadata
import com.celzero.bravedns.util.Utilities.isAtleastO
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.disableFrostTemporarily
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import java.io.File
import java.util.concurrent.TimeUnit
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import Logger
import Logger.LOG_TAG_UI

class AboutFragment : Fragment(), KoinComponent {
    private val viewModel: AboutViewModel by viewModel()

    private val workScheduler by inject<WorkScheduler>()
    private val appDatabase by inject<AppDatabase>()
    private val persistentState by inject<PersistentState>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RethinkTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    AboutScreen(
                        uiState = uiState,
                        onSponsorClick = { openUrl(requireContext(), RETHINKDNS_SPONSOR_LINK) },
                        onTelegramClick = { openUrl(requireContext(), getString(R.string.about_telegram_link)) },
                        onBugReportClick = { viewModel.triggerBugReport() },
                        onWhatsNewClick = { showNewFeaturesDialog() },
                        onAppUpdateClick = { (requireContext() as HomeScreenActivity).checkForUpdate(AppUpdater.UserPresent.INTERACTIVE) },
                        onContributorsClick = { showContributors() },
                        onTranslateClick = { openUrl(requireContext(), getString(R.string.about_translate_link)) },
                        onWebsiteClick = { openUrl(requireContext(), getString(R.string.about_website_link)) },
                        onGithubClick = { openUrl(requireContext(), getString(R.string.about_github_link)) },
                        onFaqClick = { openUrl(requireContext(), getString(R.string.about_faq_link)) },
                        onDocsClick = { openUrl(requireContext(), getString(R.string.about_docs_link)) },
                        onPrivacyPolicyClick = { openUrl(requireContext(), getString(R.string.about_privacy_policy_link)) },
                        onTermsOfServiceClick = { openUrl(requireContext(), getString(R.string.about_terms_link)) },
                        onLicenseClick = { openUrl(requireContext(), getString(R.string.about_license_link)) },
                        onTwitterClick = { openUrl(requireContext(), getString(R.string.about_twitter_handle)) },
                        onEmailClick = { disableFrostTemporarily(); sendEmailIntent(requireContext()) },
                        onRedditClick = { openUrl(requireContext(), getString(R.string.about_reddit_handle)) },
                        onElementClick = { openUrl(requireContext(), getString(R.string.about_matrix_handle)) },
                        onMastodonClick = { openUrl(requireContext(), getString(R.string.about_mastodom_handle)) },
                        onAppInfoClick = { openAppInfo(requireContext()) },
                        onVpnProfileClick = { openVpnProfile(requireContext()) },
                        onNotificationClick = { openNotificationSettings() },
                        onStatsClick = { openStatsDialog() },
                        onDbStatsClick = { openDatabaseDumpDialog() },
                        onFlightRecordClick = { initiateFlightRecord() },
                        onEventLogsClick = { openEventLogs() },
                        onTokenClick = { copyTokenToClipboard() },
                        onTokenDoubleTap = { viewModel.generateNewToken() },
                        onFossClick = { openUrl(requireContext(), getString(R.string.about_foss_link)) },
                        onFlossFundsClick = { openUrl(requireContext(), getString(R.string.about_floss_fund_link)) }
                    )
                }
            }
        }
    }

    private fun copyTokenToClipboard() {
        val text = persistentState.firebaseUserToken
        val clipboard = getSystemService(requireContext(), ClipboardManager::class.java)
        val clip = ClipData.newPlainText("token", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }


    private fun initiateFlightRecord() {
        io { VpnController.performFlightRecording() }
        Toast.makeText(requireContext(), "Flight recording started", Toast.LENGTH_SHORT).show()
    }

    private fun openEventLogs() {
        val intent = Intent(requireContext(), com.celzero.bravedns.ui.activity.EventsActivity::class.java)
        startActivity(intent)
    }

    private fun getVersionName(): String {
        return Utilities.getPackageMetadata(requireContext().packageManager, requireContext().packageName)?.versionName ?: ""
    }


    private fun openStatsDialog() {
        io {
            val stat = VpnController.getNetStat()
            val formatedStat = UIUtils.formatNetStat(stat)
            val vpnStats = VpnController.vpnStats()
            val stats = formatedStat + vpnStats
            uiCtx {
                if (!isAdded) return@uiCtx
                val tv = android.widget.TextView(requireContext())
                val pad = resources.getDimensionPixelSize(R.dimen.dots_margin_bottom)
                tv.setPadding(pad, pad, pad, pad)
                if (formatedStat == null) {
                    tv.text = "No Stats"
                } else {
                    tv.text = stats
                }
                tv.setTextIsSelectable(true)
                tv.typeface = android.graphics.Typeface.MONOSPACE
                val scroll = android.widget.ScrollView(requireContext())
                scroll.addView(tv)
                MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
                    .setTitle(getString(R.string.title_statistics))
                    .setView(scroll)
                    .setPositiveButton(R.string.fapps_info_dialog_positive_btn) { d, _ -> d.dismiss() }
                    .setNeutralButton(R.string.dns_info_neutral) { _, _ ->
                        copyToClipboard("stats_dump", stats)
                        showToastUiCentered(
                            requireContext(),
                            getString(R.string.copied_clipboard),
                            Toast.LENGTH_SHORT
                        )
                    }.create()
                    .show()
            }
        }
    }

    private fun copyToClipboard(label: String, text: String): ClipboardManager? {
        val cb = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        cb?.setPrimaryClip(ClipData.newPlainText(label, text))
        return cb
    }

    private fun openDatabaseDumpDialog() {
        io {
            val tables = getDatabaseTables()
            uiCtx {
                if (!isAdded) return@uiCtx
                if (tables.isEmpty()) {
                    showToastUiCentered(requireContext(), getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    return@uiCtx
                }
                val appended = mutableSetOf<String>()
                val ctx = requireContext()
                val pad = resources.getDimensionPixelSize(R.dimen.dots_margin_bottom)
                val tv = android.widget.TextView(ctx)
                tv.setPadding(pad, pad, pad, pad)
                tv.text = "Select a table to load its dump"
                tv.setTextIsSelectable(true)
                tv.typeface = android.graphics.Typeface.MONOSPACE
                val scroll = android.widget.ScrollView(ctx)
                scroll.addView(tv)

                val listView = android.widget.ListView(ctx)
                val listHeight = (resources.displayMetrics.heightPixels * 0.30).toInt()
                listView.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    listHeight
                )
                val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_1, tables)
                listView.adapter = adapter

                // load + append dump when a table is tapped
                listView.onItemClickListener =
                    android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                        val table = tables[position]
                        if (appended.contains(table)) {
                            showToastUiCentered(
                                ctx,
                                getString(R.string.config_add_success_toast),
                                Toast.LENGTH_SHORT
                            )
                            return@OnItemClickListener
                        }
                        appended.add(table)
                        tv.append("\nLoading $table ...\n")
                        io {
                            val dump = buildTableDump(table)
                            uiCtx {
                                if (!isAdded) return@uiCtx
                                // replace the temporary loading line (not strictly necessary)
                                tv.text = tv.text.toString().replace("Loading $table ...", "")
                                tv.append("\n===== TABLE: $table =====\n")
                                tv.append(dump)
                            }
                        }
                    }

                val container = android.widget.LinearLayout(ctx)
                container.orientation = android.widget.LinearLayout.VERTICAL
                container.addView(listView)
                container.addView(scroll)

                MaterialAlertDialogBuilder(ctx, R.style.App_Dialog_NoDim)
                    .setTitle(getString(R.string.title_database_dump))
                    .setView(container)
                    .setPositiveButton(R.string.fapps_info_dialog_positive_btn) { d, _ -> d.dismiss() }
                    .setNeutralButton(R.string.dns_info_neutral) { _, _ ->
                        copyToClipboard("db_dump", tv.text.toString())
                        showToastUiCentered(
                            ctx,
                            getString(R.string.copied_clipboard),
                            Toast.LENGTH_SHORT
                        )
                    }.create()
                    .show()
            }
        }
    }

    private fun getDatabaseTables(): List<String> {
        val db = appDatabase.openHelper.readableDatabase
        val cursor =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        val tablesToSkip = setOf(
            "android_metadata",
            "sqlite_sequence",
            "room_master_table",
            "TcpProxyEndpoint",
            "RpnProxy",
            "SubscriptionStatus",
            "SubscriptionStateHistory"
        )
        val tables = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                if (!tablesToSkip.contains(name)) tables.add(name)
            }
        }
        return tables
    }

    private fun buildTableDump(table: String): String {
        val db = appDatabase.openHelper.readableDatabase
        val sb = StringBuilder()
        return try {
            val pragma = db.query("PRAGMA table_info($table)")
            val columns = mutableListOf<String>()
            pragma.use { p ->
                while (p.moveToNext()) {
                    val colNameIdx = p.getColumnIndexOrThrow("name")
                    columns.add(p.getString(colNameIdx))
                }
            }
            sb.append(columns.joinToString(" | ")).append('\n')
            val maxRowsPerTable = 500
            val dataCursor = db.query("SELECT * FROM $table LIMIT $maxRowsPerTable")
            var rowCount = 0
            dataCursor.use { dc ->
                while (dc.moveToNext()) {
                    val row = buildString {
                        columns.forEachIndexed { idx, col ->
                            if (idx > 0) append(" | ")
                            val colIndex = dc.getColumnIndex(col)
                            if (colIndex >= 0) {
                                when (dc.getType(colIndex)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> append("NULL")
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> append(
                                        dc.getLong(
                                            colIndex
                                        )
                                    )

                                    android.database.Cursor.FIELD_TYPE_FLOAT -> append(
                                        dc.getDouble(
                                            colIndex
                                        )
                                    )

                                    android.database.Cursor.FIELD_TYPE_STRING -> {
                                        var v = dc.getString(colIndex)
                                        if (v.length > 200) v = v.substring(0, 200) + "…"
                                        append(v.replace('\n', ' '))
                                    }

                                    android.database.Cursor.FIELD_TYPE_BLOB -> append("<BLOB>")
                                    else -> append("?")
                                }
                            } else append("?")
                        }
                    }
                    sb.append(row).append('\n')
                    rowCount++
                }
            }
            val countCursor = db.query("SELECT COUNT(1) FROM $table")
            var total = rowCount
            countCursor.use { cc -> if (cc.moveToFirst()) total = cc.getInt(0) }
            if (total > rowCount) {
                sb.append("[shown ").append(rowCount).append(" of ").append(total)
                    .append(" rows]\n")
            } else {
                sb.append("[rows: ").append(total).append("]\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "Error dumping $table: ${e.message}\n"
        }
    }

    /**
     * Checks if any bug report logs are available (bug report zip or tombstone files).
     * @return true if at least one log file exists, false otherwise
     */
    private fun hasAnyLogsAvailable(): Boolean {
        val dir = requireContext().filesDir

        val bugReportZip = File(getZipFileName(dir))
        if (bugReportZip.exists() && bugReportZip.length() > 0) {
            return true
        }

        if (isAtleastO()) {
            val tombstoneZip = EnhancedBugReport.getTombstoneZipFile(requireContext())
            if (tombstoneZip != null && tombstoneZip.exists() && tombstoneZip.length() > 0) {
                return true
            }

            val tombstoneDir = File(dir, EnhancedBugReport.TOMBSTONE_DIR_NAME)
            if (tombstoneDir.exists() && tombstoneDir.isDirectory) {
                val tombstoneFiles = tombstoneDir.listFiles()
                if (tombstoneFiles != null && tombstoneFiles.any { it.isFile && it.length() > 0 }) {
                    return true
                }
            }
        }

        val bugReportDir = File(dir, BugReportZipper.BUG_REPORT_DIR_NAME)
        if (bugReportDir.exists() && bugReportDir.isDirectory) {
            val bugReportFiles = bugReportDir.listFiles()
            if (bugReportFiles != null && bugReportFiles.any { it.isFile && it.length() > 0 }) {
                return true
            }
        }

        return false
    }

    private fun showNoLogDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.about_bug_no_log_dialog_title)
        builder.setMessage(R.string.about_bug_no_log_dialog_message)
        builder.setPositiveButton(getString(R.string.about_bug_no_log_dialog_positive_btn)) { _, _ ->
            sendEmailIntent(requireContext())
        }
        builder.setNegativeButton(getString(R.string.lbl_cancel)) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun openNotificationSettings() {
        val packageName = requireContext().packageName
        try {
            val intent = Intent()
            if (isAtleastO()) {
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                intent.action = ACTION_APPLICATION_DETAILS_SETTINGS
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                intent.data = "$SCHEME_PACKAGE:$packageName".toUri()
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToastUiCentered(
                requireContext(),
                getString(R.string.notification_screen_error),
                Toast.LENGTH_SHORT
            )
            Logger.w(LOG_TAG_UI, "activity not found ${e.message}", e)
        }
    }

    private fun showNewFeaturesDialog() {
        val binding =
            DialogWhatsnewBinding.inflate(LayoutInflater.from(requireContext()), null, false)
        binding.desc.movementMethod = LinkMovementMethod.getInstance()
        binding.desc.text = htmlToSpannedText(getString(R.string.whats_new_version_update))
        // replace the version name in the title
        val v = getVersionName().slice(0..6)
        val title = getString(R.string.about_whats_new, v)
        MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
            .setView(binding.root)
            .setTitle(title)
            .setPositiveButton(getString(R.string.about_dialog_positive_button)) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .setNeutralButton(getString(R.string.about_dialog_neutral_button)) { _: DialogInterface, _: Int ->
                sendEmailIntent(requireContext())
            }
            .setCancelable(true)
            .create()
            .show()
    }

    private fun showContributors() {
        val dialogBinding = DialogInfoRulesLayoutBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val heading = dialogBinding.infoRulesDialogRulesTitle
        val okBtn = dialogBinding.infoRulesDialogCancelImg
        val descText = dialogBinding.infoRulesDialogRulesDesc
        dialogBinding.infoRulesDialogRulesIcon.visibility = View.GONE

        heading.text = getString(R.string.contributors_dialog_title)
        heading.setCompoundDrawablesWithIntrinsicBounds(
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_authors),
            null,
            null,
            null
        )

        heading.gravity = Gravity.CENTER
        descText.gravity = Gravity.CENTER

        descText.movementMethod = LinkMovementMethod.getInstance()
        descText.text = htmlToSpannedText(getString(R.string.contributors_list))

        okBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun promptCrashLogAction() {
        // ensure tombstone logs are added to zip if available
        if (isAtleastO()) {
            io {
                try {
                    EnhancedBugReport.addLogsToZipFile(requireContext())
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_UI, "err adding tombstone to zip: ${e.message}", e)
                }
            }
        }

        // see if bug report files exist
        val dir = requireContext().filesDir
        val zipPath = getZipFileName(dir)
        val zipFile = File(zipPath)

        if (!zipFile.exists() || zipFile.length() <= 0) {
            showToastUiCentered(
                requireContext(),
                getString(R.string.log_file_not_available),
                Toast.LENGTH_SHORT
            )
            return
        }

        // show btmsht with file list
        val bottomSheet = BugReportFilesBottomSheet()
        bottomSheet.show(parentFragmentManager, "BugReportFilesBottomSheet")
    }

    private fun handleShowAppExitInfo() {
        if (WorkScheduler.isWorkRunning(requireContext(), WorkScheduler.APP_EXIT_INFO_JOB_TAG))
            return

        workScheduler.scheduleOneTimeWorkForAppExitInfo()

        val workManager = WorkManager.getInstance(requireContext().applicationContext)
        workManager.getWorkInfosByTagLiveData(WorkScheduler.APP_EXIT_INFO_ONE_TIME_JOB_TAG).observe(
            viewLifecycleOwner
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
            } else { // state == blocked, queued, or running
                // no-op
            }
        }
    }

    private fun onAppExitInfoFailure() {
        showToastUiCentered(
            requireContext(),
            getString(R.string.log_file_not_available),
            Toast.LENGTH_SHORT
        )
        hideBugReportProgressUi()
    }


    private fun onAppExitInfoSuccess() {
        promptCrashLogAction()
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

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
package com.celzero.bravedns.ui.activity

import Logger
import Logger.LOG_TAG_BUG_REPORT
import Logger.LOG_TAG_UI
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConsoleLogRow
import com.celzero.bravedns.database.ConsoleLogRepository
import com.celzero.bravedns.net.go.GoVpnAdapter
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.util.disableFrostTemporarily
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.util.restoreFrost
import com.celzero.bravedns.viewmodel.ConsoleLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import org.koin.android.ext.android.inject
import java.io.File

class ConsoleLogActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    private val viewModel by inject<ConsoleLogViewModel>()
    private val consoleLogRepository by inject<ConsoleLogRepository>()
    private val workScheduler by inject<WorkScheduler>()

    private var infoText by mutableStateOf("")
    private var progressVisible by mutableStateOf(false)

    companion object {
        private const val FILE_NAME = "rethink_app_logs_"
        private const val FILE_EXTENSION = ".zip"
        private const val QUERY_TEXT_DELAY: Long = 1000
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
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

        initView()

        setContent {
            RethinkTheme {
                ConsoleLogScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        restoreFrost(themeId)
    }
    private fun initView() {
        io {
            val sinceTime = viewModel.sinceTime()
            if (sinceTime == 0L) return@io

            val since = Utilities.convertLongToTime(sinceTime, Constants.TIME_FORMAT_3)
            uiCtx {
                val desc = getString(R.string.console_log_desc)
                val sinceTxt = getString(R.string.logs_card_duration, since)
                infoText = getString(R.string.two_argument_space, desc, sinceTxt)
            }
        }
    }

    private fun showFilterDialog() {
        val items = arrayOf(
            getString(R.string.settings_gologger_dialog_option_0),
            getString(R.string.settings_gologger_dialog_option_1),
            getString(R.string.settings_gologger_dialog_option_2),
            getString(R.string.settings_gologger_dialog_option_3),
            getString(R.string.settings_gologger_dialog_option_4),
            getString(R.string.settings_gologger_dialog_option_5),
            getString(R.string.settings_gologger_dialog_option_6),
            getString(R.string.settings_gologger_dialog_option_7)
        )
        val checkedItem = Logger.uiLogLevel.toInt()
        val dialog = Dialog(this, R.style.App_Dialog_NoDim)
        dialog.setCancelable(true)
        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                var selectedIndex by remember { mutableStateOf(checkedItem) }
                AlertDialog(
                    onDismissRequest = { dialog.dismiss() },
                    title = { Text(text = getString(R.string.console_log_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.forEachIndexed { index, label ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedIndex == index,
                                        onClick = {
                                            selectedIndex = index
                                            Logger.uiLogLevel = index.toLong()
                                            GoVpnAdapter.setLogLevel(
                                                persistentState.goLoggerLevel.toInt(),
                                                Logger.uiLogLevel.toInt()
                                            )
                                            viewModel.setLogLevel(index.toLong())
                                            if (index < Logger.LoggerLevel.ERROR.id) {
                                                consoleLogRepository.setStartTimestamp(System.currentTimeMillis())
                                            }
                                            Logger.i(LOG_TAG_BUG_REPORT, "Log level set to ${items[index]}")
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { dialog.dismiss() }) {
                            Text(text = getString(R.string.fapps_info_dialog_positive_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog.dismiss() }) {
                            Text(text = getString(R.string.lbl_cancel))
                        }
                    }
                )
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }

    private fun handleShareLogs(filePath: String) {
        if (WorkScheduler.isWorkRunning(this, WorkScheduler.CONSOLE_LOG_SAVE_JOB_TAG)) return

        workScheduler.scheduleConsoleLogSaveJob(filePath)
        showLogGenerationProgressUi()

        val workManager = WorkManager.getInstance(this.applicationContext)
        workManager.getWorkInfosByTagLiveData(WorkScheduler.CONSOLE_LOG_SAVE_JOB_TAG).observe(
            this
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Logger.i(
                Logger.LOG_TAG_SCHEDULER,
                "WorkManager state: ${workInfo.state} for ${WorkScheduler.CONSOLE_LOG_SAVE_JOB_TAG}"
            )
            if (WorkInfo.State.SUCCEEDED == workInfo.state) {
                onSuccess()
                shareZipFileViaEmail(filePath)
                workManager.pruneWork()
            } else if (
                WorkInfo.State.CANCELLED == workInfo.state ||
                WorkInfo.State.FAILED == workInfo.state
            ) {
                onFailure()
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(WorkScheduler.CONSOLE_LOG_SAVE_JOB_TAG)
            }
        }
    }

    private fun onSuccess() {
        Logger.i(LOG_TAG_BUG_REPORT, "created logs successfully")
        progressVisible = false
        Toast.makeText(this, getString(R.string.config_add_success_toast), Toast.LENGTH_LONG).show()
    }

    private fun onFailure() {
        Logger.i(LOG_TAG_BUG_REPORT, "failed to create logs")
        progressVisible = false
        Toast.makeText(
            this,
            getString(R.string.download_update_dialog_failure_title),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLogGenerationProgressUi() {
        Logger.i(LOG_TAG_BUG_REPORT, "showing log generation progress UI")
        progressVisible = true
    }

    private fun shareZipFileViaEmail(filePath: String) {
        disableFrostTemporarily()
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(this, "${this.packageName}.provider", file)

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_mail_bugreport_subject))
                putExtra(Intent.EXTRA_TEXT, "Crash/Issue Report (Logs Attached)")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.about_mail_to)))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        startActivity(Intent.createChooser(intent, "Send email..."))
    }

    private fun makeConsoleLogFile(): String? {
        return try {
            val appVersion = getVersionName() + "_" + System.currentTimeMillis()
            val dir = filesDir.canonicalPath + File.separator
            val fileName: String = FILE_NAME + appVersion + FILE_EXTENSION
            val file = File(dir, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            file.absolutePath
        } catch (e: Exception) {
            Logger.w(LOG_TAG_BUG_REPORT, "err creating log file, ${e.message}")
            null
        }
    }

    private fun getVersionName(): String {
        val pInfo: PackageInfo? =
            Utilities.getPackageMetadata(this.packageManager, this.packageName)
        return pInfo?.versionName ?: ""
    }

    private fun showFileCreationErrorToast() {
        showToastUiCentered(
            this,
            getString(R.string.error_loading_log_file),
            Toast.LENGTH_SHORT
        )
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun ConsoleLogScreen() {
        var query by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            viewModel.setLogLevel(Logger.uiLogLevel)
            snapshotFlow { query }
                .debounce(QUERY_TEXT_DELAY)
                .distinctUntilChanged()
                .collect { value ->
                    viewModel.setFilter(value)
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    onFilterClick = { showFilterDialog() },
                    onShareClick = {
                        val filePath = makeConsoleLogFile()
                        if (filePath == null) {
                            showFileCreationErrorToast()
                            return@SearchRow
                        }
                        handleShareLogs(filePath)
                    },
                    onDeleteClick = {
                        io {
                            Logger.i(LOG_TAG_BUG_REPORT, "deleting all console logs")
                            consoleLogRepository.deleteAllLogs()
                            uiCtx {
                                showToastUiCentered(
                                    this@ConsoleLogActivity,
                                    getString(R.string.config_add_success_toast),
                                    Toast.LENGTH_SHORT
                                )
                                finish()
                            }
                        }
                    }
                )

                if (progressVisible) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = if (infoText.isEmpty()) stringResourceCompat(R.string.console_log_desc) else infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    ConsoleLogList()
                }
            }

            ExtendedFloatingActionButton(
                text = { Text(text = stringResourceCompat(R.string.about_bug_report_desc)) },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = null
                    )
                },
                onClick = {
                    val filePath = makeConsoleLogFile()
                    if (filePath == null) {
                        showFileCreationErrorToast()
                        return@ExtendedFloatingActionButton
                    }
                    handleShareLogs(filePath)
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }

    @Composable
    private fun SearchRow(
        query: String,
        onQueryChange: (String) -> Unit,
        onFilterClick: () -> Unit,
        onShareClick: () -> Unit,
        onDeleteClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(text = getString(R.string.lbl_search)) }
            )

            IconButton(onClick = onFilterClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_filter),
                    contentDescription = null
                )
            }

            IconButton(onClick = onShareClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = null
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null
                )
            }
        }
    }

    @Composable
    private fun ConsoleLogList() {
        val items = viewModel.logs.asFlow().collectAsLazyPagingItems()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            items(count = items.itemCount) { index ->
                val item = items[index] ?: return@items
                ConsoleLogRow(item)
            }
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}

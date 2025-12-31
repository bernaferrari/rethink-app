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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConsoleLogAdapter
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

class ConsoleLogActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    private var layoutManager: RecyclerView.LayoutManager? = null
    private val persistentState by inject<PersistentState>()

    private val viewModel by inject<ConsoleLogViewModel>()
    private val consoleLogRepository by inject<ConsoleLogRepository>()
    private val workScheduler by inject<WorkScheduler>()

    private var recyclerAdapter: ConsoleLogAdapter? = null
    private var recyclerViewRef: RecyclerView? = null
    private var searchViewRef: SearchView? = null

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
        setupQueryFilter()

        setContent {
            RethinkTheme {
                ConsoleLogScreen()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupQueryFilter() {
        lifecycleScope.launch {
            searchQuery
                .debounce(QUERY_TEXT_DELAY)
                .distinctUntilChanged()
                .collect { query ->
                    viewModel.setFilter(query)
                }
        }
    }

    override fun onResume() {
        super.onResume()
        val themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        restoreFrost(themeId)
        searchViewRef?.setQuery("", false)
        searchViewRef?.clearFocus()
        val imm = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        searchViewRef?.let { imm.restartInput(it) }
    }

    private fun initView() {
        setAdapter()
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

    private fun setAdapter() {
        try {
            layoutManager = object : LinearLayoutManager(this@ConsoleLogActivity) {
                override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                    try {
                        super.onLayoutChildren(recycler, state)
                    } catch (e: IndexOutOfBoundsException) {
                        Logger.w(LOG_TAG_UI, "err(console) layout children: ${e.message}")
                    }
                }
            }

            recyclerAdapter = ConsoleLogAdapter(this)
            viewModel.setLogLevel(Logger.uiLogLevel)
            observeLog()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err setting up console, recycler: ${e.message}")
        }
    }

    private fun observeLog() {
        viewModel.logs.observe(this) { pagingData ->
            lifecycleScope.launch {
                try {
                    recyclerAdapter?.submitData(pagingData)
                } catch (e: Exception) {
                    Logger.e(LOG_TAG_UI, "err submitting data: ${e.message}")
                    if (e is IndexOutOfBoundsException) {
                        recreateAdapter()
                    }
                }
            }
        }
    }

    private fun recreateAdapter() {
        try {
            recyclerAdapter = ConsoleLogAdapter(this)
            recyclerViewRef?.adapter = recyclerAdapter
            Logger.i(LOG_TAG_UI, "adapter recreated due to consistency error")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "err; recreate adapter: ${e.message}")
        }
    }

    private fun showFilterDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(getString(R.string.console_log_title))
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
        builder.setSingleChoiceItems(items.map { it }.toTypedArray(), checkedItem) { _, which ->
            Logger.uiLogLevel = which.toLong()
            GoVpnAdapter.setLogLevel(
                persistentState.goLoggerLevel.toInt(),
                Logger.uiLogLevel.toInt()
            )
            viewModel.setLogLevel(which.toLong())
            if (which < Logger.LoggerLevel.ERROR.id) {
                consoleLogRepository.setStartTimestamp(System.currentTimeMillis())
            }
            Logger.i(LOG_TAG_BUG_REPORT, "Log level set to ${items[which]}")
        }
        builder.setCancelable(true)
        builder.setPositiveButton(getString(R.string.fapps_info_dialog_positive_btn)) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.setNeutralButton(getString(R.string.lbl_cancel)) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
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

    val searchQuery = MutableStateFlow("")
    override fun onQueryTextSubmit(query: String): Boolean {
        searchQuery.value = query
        return true
    }

    override fun onQueryTextChange(query: String): Boolean {
        searchQuery.value = query
        return true
    }

    @Composable
    private fun ConsoleLogScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchRow(
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
                        lifecycleScope.launch {
                            recyclerAdapter?.submitData(PagingData.empty())
                        }
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
            AndroidView(
                factory = { ctx ->
                    SearchView(ctx).apply {
                        isIconified = false
                        queryHint = ctx.getString(R.string.lbl_search)
                        setOnQueryTextListener(this@ConsoleLogActivity)
                        searchViewRef = this
                    }
                },
                modifier = Modifier.weight(1f)
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
        val adapter = recyclerAdapter
        if (adapter == null) return
        AndroidView(
            factory = { ctx ->
                RecyclerView(ctx).apply {
                    setHasFixedSize(true)
                    itemAnimator = null
                    layoutManager = this@ConsoleLogActivity.layoutManager
                    this.adapter = adapter
                    recyclerViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}

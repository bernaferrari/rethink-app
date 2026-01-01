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
package com.celzero.bravedns.ui.activity

import Logger
import Logger.LOG_TAG_DNS
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.R
import com.celzero.bravedns.customdownloader.LocalBlocklistCoordinator
import com.celzero.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.celzero.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.celzero.bravedns.download.AppDownloadManager
import com.celzero.bravedns.download.DownloadConstants
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.dns.DnsSettingsScreen
import com.celzero.bravedns.ui.compose.dns.DnsSettingsViewModel
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.activity.DnsListActivity
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.celzero.bravedns.util.Constants.Companion.RETHINK_SEARCH_URL
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.blocklistCanonicalPath
import com.celzero.bravedns.util.Utilities.convertLongToTime
import com.celzero.bravedns.util.Utilities.deleteRecursive
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.firestack.backend.Backend
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import androidx.work.WorkInfo
import androidx.work.WorkManager

class DnsDetailActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()
    private val appDownloadManager by inject<AppDownloadManager>()
    private val viewModel: DnsSettingsViewModel by viewModel()
    private var showRecordTypesSheet by mutableStateOf(false)
    private var showLocalBlocklistsSheet by mutableStateOf(false)

    private var showDownloadDialog by mutableStateOf(false)
    private var downloadDialogIsRedownload by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)
    private var showLockdownDialog by mutableStateOf(false)

    private var enableLabel by mutableStateOf("")
    private var enableColor by mutableStateOf(Color.Unspecified)
    private var headingText by mutableStateOf("")
    private var versionText by mutableStateOf("")
    private var canConfigure by mutableStateOf(false)
    private var canCopy by mutableStateOf(false)
    private var canSearch by mutableStateOf(false)
    private var showCheckDownload by mutableStateOf(true)
    private var showDownload by mutableStateOf(false)
    private var showRedownload by mutableStateOf(false)
    private var isChecking by mutableStateOf(false)
    private var isDownloading by mutableStateOf(false)
    private var isRedownloading by mutableStateOf(false)

    companion object {
        private const val BUTTON_ALPHA_DISABLED = 0.5f
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(com.celzero.bravedns.util.Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            RethinkTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DnsSettingsScreen(
                    uiState = uiState,
                    onRefreshClick = { viewModel.refreshDns() },
                    onSystemDnsClick = { viewModel.enableSystemDns() },
                    onSystemDnsInfoClick = {
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val sysDns = VpnController.getSystemDns()
                            withContext(Dispatchers.Main) {
                                showSystemDnsDialog(sysDns)
                            }
                        }
                    },
                    onCustomDnsClick = { showCustomDns() },
                    onRethinkPlusDnsClick = { invokeRethinkActivity(ConfigureRethinkBasicActivity.FragmentLoader.DB_LIST) },
                    onSmartDnsClick = { viewModel.enableSmartDns() },
                    onSmartDnsInfoClick = { showSmartDnsInfoDialog() },
                    onLocalBlocklistClick = { openLocalBlocklist() },
                    onCustomDownloaderChange = { viewModel.setUseCustomDownloadManager(it) },
                    onPeriodicUpdateChange = { viewModel.setPeriodicallyCheckBlocklistUpdate(it) },
                    onDnsAlgChange = { viewModel.setDnsAlgEnabled(it) },
                    onSplitDnsChange = { viewModel.setSplitDns(it) },
                    onBypassDnsBlockChange = { viewModel.setBypassBlockInDns(it) },
                    onAllowedRecordTypesClick = { showDnsRecordTypesBottomSheet() },
                    onFavIconChange = { viewModel.setFavIconEnabled(it) },
                    onDnsCacheChange = { viewModel.setEnableDnsCache(it) },
                    onProxyDnsChange = { viewModel.setProxyDns(it) },
                    onUndelegatedDomainsChange = { viewModel.setUseSystemDnsForUndelegatedDomains(it) },
                    onFallbackChange = { viewModel.setUseFallbackDnsToBypass(it) },
                    onPreventLeaksChange = { viewModel.setPreventDnsLeaksEnabled(it) }
                )
                if (showRecordTypesSheet) {
                    DnsRecordTypesSheet(onDismiss = { showRecordTypesSheet = false })
                }
                if (showLocalBlocklistsSheet) {
                    LocalBlocklistsSheet()
                }
            }
        }

        initLocalBlocklistsState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateUiState()
    }

    private fun showDnsRecordTypesBottomSheet() {
        showRecordTypesSheet = true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DnsRecordTypesSheet(onDismiss: () -> Unit) {
        var isAutoMode by remember { mutableStateOf(persistentState.dnsRecordTypesAutoMode) }
        val selected = remember {
            mutableStateListOf<String>().apply {
                addAll(getInitialRecordSelection(persistentState.dnsRecordTypesAutoMode))
            }
        }

        val allTypes = remember {
            ResourceRecordTypes.entries.filter { it != ResourceRecordTypes.UNKNOWN }
        }

        val sortedTypes by remember(isAutoMode, selected) {
            derivedStateOf {
                allTypes.sortedWith(
                    compareByDescending<ResourceRecordTypes> {
                        if (isAutoMode) true else selected.contains(it.name)
                    }.thenBy { it.name }
                )
            }
        }

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = getString(R.string.cd_allowed_dns_record_types_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                )
                Text(
                    text = getString(R.string.cd_allowed_dns_record_types_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )

                RecordModeToggleRow(
                    isAutoMode = isAutoMode,
                    onAutoSelected = {
                        isAutoMode = true
                        persistentState.dnsRecordTypesAutoMode = true
                    },
                    onManualSelected = {
                        isAutoMode = false
                        persistentState.dnsRecordTypesAutoMode = false
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                    items(sortedTypes) { type ->
                        RecordTypeRow(
                            type = type,
                            isAutoMode = isAutoMode,
                            isSelected = if (isAutoMode) true else selected.contains(type.name),
                            onToggle = {
                                if (isAutoMode) return@RecordTypeRow
                                if (selected.contains(type.name)) {
                                    selected.remove(type.name)
                                } else {
                                    selected.add(type.name)
                                }
                                persistentState.setAllowedDnsRecordTypes(selected.toSet())
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RecordModeToggleRow(
        isAutoMode: Boolean,
        onAutoSelected: () -> Unit,
        onManualSelected: () -> Unit
    ) {
        val selectedBg = MaterialTheme.colorScheme.primaryContainer
        val unselectedBg = MaterialTheme.colorScheme.surfaceVariant
        val selectedText = MaterialTheme.colorScheme.onPrimaryContainer
        val unselectedText = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onAutoSelected,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isAutoMode) selectedBg else unselectedBg,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = getString(R.string.settings_ip_text_ipv46),
                    color = if (isAutoMode) selectedText else unselectedText
                )
            }
            TextButton(
                onClick = onManualSelected,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (!isAutoMode) selectedBg else unselectedBg,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = getString(R.string.lbl_manual),
                    color = if (!isAutoMode) selectedText else unselectedText
                )
            }
        }
    }

    @Composable
    private fun RecordTypeRow(
        type: ResourceRecordTypes,
        isAutoMode: Boolean,
        isSelected: Boolean,
        onToggle: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .alpha(if (isAutoMode) 0.6f else 1f)
                .clickable(enabled = !isAutoMode) { onToggle() }
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = type.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = if (isAutoMode) null else { _ -> onToggle() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (isAutoMode) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    private fun getInitialRecordSelection(autoMode: Boolean): List<String> {
        if (!autoMode) {
            return persistentState.getAllowedDnsRecordTypes().toList()
        }
        val storedSelection = persistentState.allowedDnsRecordTypesString
        if (storedSelection.isNotEmpty()) {
            return storedSelection.split(",").filter { it.isNotEmpty() }
        }
        return listOf(
            ResourceRecordTypes.A.name,
            ResourceRecordTypes.AAAA.name,
            ResourceRecordTypes.CNAME.name,
            ResourceRecordTypes.HTTPS.name,
            ResourceRecordTypes.SVCB.name
        )
    }

    private fun showSmartDnsInfoDialog() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ids = VpnController.getPlusResolvers()
            val dnsList: MutableList<String> = mutableListOf()
            ids.forEach {
                val index = it.substringAfter(Backend.Plus).getOrNull(0)
                if (index == null) {
                    Logger.w(LOG_TAG_DNS, "smart(plus) dns resolver id is empty: $it")
                    return@forEach
                }
                if (index != DOH_INDEX && index != DOT_INDEX) {
                    Logger.w(LOG_TAG_DNS, "smart(plus) dns resolver id is not doh or dot: $it")
                    return@forEach
                }
                val transport = VpnController.getPlusTransportById(it)
                val address = transport?.addr?.tos() ?: ""
                if (address.isNotEmpty()) dnsList.add(address)
            }

            Logger.i(LOG_TAG_DNS, "smart(plus) dns list size: ${dnsList.size}")
            withContext(Dispatchers.Main) {
                val stringBuilder = StringBuilder()
                val desc = getString(R.string.smart_dns_desc)
                stringBuilder.append(desc).append("\n\n")
                dnsList.forEach {
                    val txt = getString(R.string.symbol_star) + " " + it
                    stringBuilder.append(txt).append("\n")
                }
                val list = stringBuilder.toString()
                val dialog = Dialog(this@DnsDetailActivity, R.style.App_Dialog_NoDim)
                dialog.setCancelable(true)
                val composeView = ComposeView(this@DnsDetailActivity)
                composeView.setContent {
                    RethinkTheme {
                        AlertDialog(
                            onDismissRequest = { dialog.dismiss() },
                            title = { Text(text = getString(R.string.smart_dns)) },
                            text = { Text(text = list) },
                            confirmButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { dialog.dismiss() }) {
                                        Text(text = getString(R.string.ada_noapp_dialog_positive))
                                    }
                                    TextButton(
                                        onClick = {
                                            UIUtils.clipboardCopy(
                                                this@DnsDetailActivity,
                                                list,
                                                getString(R.string.copy_clipboard_label)
                                            )
                                            Utilities.showToastUiCentered(
                                                this@DnsDetailActivity,
                                                getString(R.string.info_dialog_url_copy_toast_msg),
                                                Toast.LENGTH_SHORT
                                            )
                                            dialog.dismiss()
                                        }
                                    ) {
                                        Text(text = getString(R.string.dns_info_neutral))
                                    }
                                }
                            }
                        )
                    }
                }
                dialog.setContentView(composeView)
                dialog.show()
            }
        }
    }

    private fun showSystemDnsDialog(dns: String) {
        val dialog = Dialog(this, R.style.App_Dialog_NoDim)
        dialog.setCancelable(true)
        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                AlertDialog(
                    onDismissRequest = { dialog.dismiss() },
                    title = { Text(text = getString(R.string.network_dns)) },
                    text = { Text(text = dns) },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { dialog.dismiss() }) {
                                Text(text = getString(R.string.ada_noapp_dialog_positive))
                            }
                            TextButton(
                                onClick = {
                                    UIUtils.clipboardCopy(
                                        this@DnsDetailActivity,
                                        dns,
                                        getString(R.string.copy_clipboard_label)
                                    )
                                    Utilities.showToastUiCentered(
                                        this@DnsDetailActivity,
                                        getString(R.string.info_dialog_url_copy_toast_msg),
                                        Toast.LENGTH_SHORT
                                    )
                                    dialog.dismiss()
                                }
                            ) {
                                Text(text = getString(R.string.dns_info_neutral))
                            }
                        }
                    }
                )
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }

    private fun openLocalBlocklist() {
        updateLocalBlocklistUi()
        initLocalBlocklistVersion()
        showLocalBlocklistsSheet = true
    }

    private fun invokeRethinkActivity(type: ConfigureRethinkBasicActivity.FragmentLoader) {
        val intent = Intent(this, ConfigureRethinkBasicActivity::class.java)
        intent.putExtra(ConfigureRethinkBasicActivity.INTENT, type.ordinal)
        startActivity(intent)
    }

    private fun showCustomDns() {
        val intent = Intent(this, DnsListActivity::class.java)
        startActivity(intent)
    }

    private fun initLocalBlocklistsState() {
        updateLocalBlocklistUi()
        initLocalBlocklistVersion()
        initializeLocalBlocklistObservers()
        observeLocalBlocklistWorkManager()
    }

    private fun dismissLocalBlocklistsSheet() {
        showLocalBlocklistsSheet = false
        viewModel.updateUiState()
    }

    private fun initLocalBlocklistVersion() {
        if (persistentState.localBlocklistTimestamp == INIT_TIME_MS) {
            showCheckUpdateUi()
            versionText = ""
            return
        }

        versionText =
            getString(
                R.string.settings_local_blocklist_version,
                convertLongToTime(
                    persistentState.localBlocklistTimestamp,
                    Constants.TIME_FORMAT_2
                )
            )

        if (persistentState.newestRemoteBlocklistTimestamp == INIT_TIME_MS) {
            showCheckUpdateUi()
            return
        }

        if (persistentState.newestLocalBlocklistTimestamp > persistentState.localBlocklistTimestamp) {
            showUpdateUi()
            return
        }

        showCheckUpdateUi()
    }

    private fun initializeLocalBlocklistObservers() {
        appDownloadManager.downloadRequired.observe(this) {
            Napier.i("Check for blocklist update, status: $it")
            if (it == null) return@observe

            handleDownloadStatus(it)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LocalBlocklistsSheet() {
        ModalBottomSheet(onDismissRequest = { dismissLocalBlocklistsSheet() }) {
            LocalBlocklistsContent()
        }
    }

    @Composable
    private fun LocalBlocklistsContent() {
        val borderColor = Color(UIUtils.fetchColor(this, R.attr.border))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Text(
                text = headingText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = enableLabel,
                    color = enableColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { enableBlocklist() }) {
                    Text(text = getString(R.string.lbl_apply))
                }
            }

            if (versionText.isNotEmpty()) {
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = getString(R.string.lbl_configure),
                    enabled = canConfigure,
                    modifier = Modifier.weight(1f)
                ) {
                    invokeLocalBlocklistActivity()
                }
                ActionButton(
                    text = getString(R.string.lbbs_copy),
                    enabled = canCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    val url = Constants.RETHINK_BASE_URL_MAX + persistentState.localBlocklistStamp
                    UIUtils.clipboardCopy(
                        this@DnsDetailActivity,
                        url,
                        getString(R.string.copy_clipboard_label)
                    )
                    Utilities.showToastUiCentered(
                        this@DnsDetailActivity,
                        getString(R.string.info_dialog_rethink_toast_msg),
                        Toast.LENGTH_SHORT
                    )
                }
                ActionButton(
                    text = getString(R.string.lbl_search),
                    enabled = canSearch,
                    modifier = Modifier.weight(1f)
                ) {
                    dismissLocalBlocklistsSheet()
                    val url = RETHINK_SEARCH_URL + Uri.encode(persistentState.localBlocklistStamp)
                    UIUtils.openUrl(this@DnsDetailActivity, url)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showCheckDownload) {
                    DownloadRow(
                        label = getString(R.string.lbbs_update_check),
                        isLoading = isChecking,
                        enabled = !isChecking
                    ) {
                        isChecking = true
                        isBlocklistUpdateAvailable()
                    }
                }
                if (showDownload) {
                    DownloadRow(
                        label = getString(R.string.local_blocklist_download),
                        isLoading = isDownloading,
                        enabled = !isDownloading
                    ) {
                        downloadDialogIsRedownload = false
                        showDownloadDialog = true
                    }
                }
                if (showRedownload) {
                    DownloadRow(
                        label = getString(R.string.local_blocklist_redownload),
                        isLoading = isRedownloading,
                        enabled = !isRedownloading
                    ) {
                        downloadDialogIsRedownload = true
                        showDownloadDialog = true
                    }
                }
                DownloadRow(
                    label = getString(R.string.lbl_delete),
                    isLoading = false,
                    enabled = true
                ) {
                    showDeleteDialog = true
                }
            }
        }

        if (showDownloadDialog) {
            val title =
                if (downloadDialogIsRedownload) {
                    getString(R.string.local_blocklist_redownload)
                } else {
                    getString(R.string.local_blocklist_download)
                }
            val message =
                if (downloadDialogIsRedownload) {
                    getString(
                        R.string.local_blocklist_redownload_desc,
                        convertLongToTime(
                            persistentState.localBlocklistTimestamp,
                            Constants.TIME_FORMAT_2
                        )
                    )
                } else {
                    getString(R.string.local_blocklist_download_desc)
                }
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text(text = title) },
                text = { Text(text = message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDownloadDialog = false
                            downloadLocalBlocklist(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = getString(R.string.settings_local_blocklist_dialog_positive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = getString(R.string.lbl_delete)) },
                text = { Text(text = getString(R.string.local_blocklist_delete_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            deleteLocalBlocklist()
                        }
                    ) {
                        Text(text = getString(R.string.lbl_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }

        if (showLockdownDialog) {
            AlertDialog(
                onDismissRequest = { showLockdownDialog = false },
                title = { Text(text = getString(R.string.lockdown_download_enable_inapp)) },
                text = { Text(text = getString(R.string.lockdown_download_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLockdownDialog = false
                            persistentState.useCustomDownloadManager = true
                            downloadLocalBlocklist(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = getString(R.string.lockdown_download_enable_inapp))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLockdownDialog = false
                            proceedWithDownload(downloadDialogIsRedownload)
                        }
                    ) {
                        Text(text = getString(R.string.lbl_cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun DownloadRow(label: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                TextButton(onClick = onClick, enabled = enabled) {
                    Text(text = getString(R.string.lbl_proceed))
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        text: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val alpha = if (enabled) 1f else BUTTON_ALPHA_DISABLED
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(36.dp).alpha(alpha),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(text = text, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }

    private fun showCheckUpdateUi() {
        showCheckDownload = true
        showDownload = false
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun showUpdateUi() {
        showCheckDownload = false
        showDownload = true
        showRedownload = false
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun showRedownloadUi() {
        showCheckDownload = false
        showDownload = false
        showRedownload = true
        isChecking = false
        isDownloading = false
        isRedownloading = false
    }

    private fun downloadLocalBlocklist(isRedownload: Boolean) {
        if (VpnController.isVpnLockdown() && !persistentState.useCustomDownloadManager) {
            showLockdownDialog = true
            return
        }

        proceedWithDownload(isRedownload)
    }

    private fun proceedWithDownload(isRedownload: Boolean) {
        ui {
            var status = AppDownloadManager.DownloadManagerStatus.NOT_STARTED
            isDownloading = !isRedownload
            isRedownloading = isRedownload
            val currentTs = persistentState.localBlocklistTimestamp
            ioCtx { status = appDownloadManager.downloadLocalBlocklist(currentTs, isRedownload) }
            handleDownloadStatus(status)
        }
    }

    private fun deleteLocalBlocklist() {
        ui {
            ioCtx {
                val path = blocklistCanonicalPath(this@DnsDetailActivity, LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME)
                val dir = File(path)
                deleteRecursive(dir)
                persistentState.localBlocklistTimestamp = INIT_TIME_MS
                persistentState.localBlocklistStamp = ""
                persistentState.newestLocalBlocklistTimestamp = INIT_TIME_MS
            }

            updateLocalBlocklistUi()
            showCheckUpdateUi()
            Utilities.showToastUiCentered(
                this@DnsDetailActivity,
                getString(R.string.config_add_success_toast),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun handleDownloadStatus(status: AppDownloadManager.DownloadManagerStatus) {
        when (status) {
            AppDownloadManager.DownloadManagerStatus.IN_PROGRESS -> {
                isChecking = true
            }
            AppDownloadManager.DownloadManagerStatus.STARTED -> {
                isChecking = true
            }
            AppDownloadManager.DownloadManagerStatus.NOT_STARTED -> {
                // no-op
            }
            AppDownloadManager.DownloadManagerStatus.SUCCESS -> {
                showUpdateUi()
                isChecking = false
                isDownloading = false
                isRedownloading = false
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.FAILURE -> {
                isChecking = false
                isDownloading = false
                isRedownloading = false
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.NOT_REQUIRED -> {
                showRedownloadUi()
                isChecking = false
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_update_check_not_required),
                    Toast.LENGTH_SHORT
                )
                appDownloadManager.downloadRequired.postValue(
                    AppDownloadManager.DownloadManagerStatus.NOT_STARTED
                )
            }
            AppDownloadManager.DownloadManagerStatus.NOT_AVAILABLE -> {
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_not_available_toast),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun updateLocalBlocklistUi() {
        if (Utilities.isPlayStoreFlavour()) {
            return
        }

        if (persistentState.blocklistEnabled) {
            enableBlocklistUi()
            return
        }

        disableBlocklistUi()
    }

    private fun enableBlocklistUi() {
        enableLabel = getString(R.string.lbbs_enabled)
        enableColor = Color(UIUtils.fetchToggleBtnColors(this, R.color.accentGood))
        headingText =
            getString(
                R.string.settings_local_blocklist_in_use,
                persistentState.numberOfLocalBlocklists.toString()
            )

        canConfigure = true
        canCopy = true
        canSearch = true
    }

    private fun disableBlocklistUi() {
        enableLabel = getString(R.string.lbl_disabled)
        enableColor = Color(UIUtils.fetchToggleBtnColors(this, R.color.accentBad))
        headingText = getString(R.string.lbbs_heading)

        canConfigure = false
        canCopy = false
        canSearch = false
    }

    private fun isBlocklistUpdateAvailable() {
        io { appDownloadManager.isDownloadRequired(RethinkBlocklistManager.DownloadType.LOCAL) }
    }

    private fun enableBlocklist() {
        if (persistentState.blocklistEnabled) {
            removeBraveDnsLocal()
            updateLocalBlocklistUi()
            return
        }

        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                this@DnsDetailActivity,
                getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        ui {
            val blocklistsExist =
                withContext(Dispatchers.Default) {
                    Utilities.hasLocalBlocklists(
                        this@DnsDetailActivity,
                        persistentState.localBlocklistTimestamp
                    )
                }

            if (blocklistsExist) {
                setBraveDnsLocal()
                if (isLocalBlocklistStampAvailable()) {
                    updateLocalBlocklistUi()
                } else {
                    invokeLocalBlocklistActivity()
                }
            } else {
                invokeLocalBlocklistActivity()
            }
        }
    }

    private fun invokeLocalBlocklistActivity() {
        if (!VpnController.hasTunnel()) {
            Utilities.showToastUiCentered(
                this@DnsDetailActivity,
                getString(R.string.ssv_toast_start_rethink),
                Toast.LENGTH_SHORT
            )
            return
        }

        dismissLocalBlocklistsSheet()
        val intent = Intent(this@DnsDetailActivity, ConfigureRethinkBasicActivity::class.java)
        intent.putExtra(
            ConfigureRethinkBasicActivity.INTENT,
            ConfigureRethinkBasicActivity.FragmentLoader.LOCAL.ordinal
        )
        startActivity(intent)
    }

    private fun isLocalBlocklistStampAvailable(): Boolean {
        return persistentState.localBlocklistStamp.isNotEmpty()
    }

    private fun setBraveDnsLocal() {
        persistentState.blocklistEnabled = true
    }

    private fun removeBraveDnsLocal() {
        persistentState.blocklistEnabled = false
    }

    private fun observeLocalBlocklistWorkManager() {
        val workManager = WorkManager.getInstance(applicationContext)

        workManager.getWorkInfosByTagLiveData(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD).observe(
            this
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${LocalBlocklistCoordinator.CUSTOM_DOWNLOAD}")
            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                isDownloading = true
            } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                isDownloading = false
                showUpdateUi()
                workManager.pruneWork()
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(LocalBlocklistCoordinator.CUSTOM_DOWNLOAD)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.DOWNLOAD_TAG).observe(
            this
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            Napier.i("WorkManager state: ${workInfo.state} for ${DownloadConstants.DOWNLOAD_TAG}")
            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                isDownloading = true
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DownloadConstants.DOWNLOAD_TAG)
                workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
            }
        }

        workManager.getWorkInfosByTagLiveData(DownloadConstants.FILE_TAG).observe(
            this
        ) { workInfoList ->
            val workInfo = workInfoList?.getOrNull(0) ?: return@observe
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                isDownloading = false
                showUpdateUi()
                workManager.pruneWork()
            } else if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                isDownloading = false
                Utilities.showToastUiCentered(
                    this@DnsDetailActivity,
                    getString(R.string.blocklist_update_check_failure),
                    Toast.LENGTH_SHORT
                )
                workManager.pruneWork()
                workManager.cancelAllWorkByTag(DownloadConstants.FILE_TAG)
            }
        }
    }

    private fun ui(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) { f() }
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

}

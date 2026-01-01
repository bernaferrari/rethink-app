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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.celzero.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.LocalBlocklistsDialog
import com.celzero.bravedns.ui.compose.dns.DnsSettingsScreen
import com.celzero.bravedns.ui.compose.dns.DnsSettingsViewModel
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.activity.DnsListActivity
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DnsDetailActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()
    private val viewModel: DnsSettingsViewModel by viewModel()
    private var showRecordTypesSheet by mutableStateOf(false)

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
            }
        }
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
        LocalBlocklistsDialog(this) {
            viewModel.updateUiState()
        }.show()
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

}

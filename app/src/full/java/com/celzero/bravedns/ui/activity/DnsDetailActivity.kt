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
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.celzero.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.DnsRecordTypesDialog
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
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DnsDetailActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()
    private val viewModel: DnsSettingsViewModel by viewModel()

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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateUiState()
    }

    private fun showDnsRecordTypesBottomSheet() {
        val dialog = DnsRecordTypesDialog(this, persistentState) {
            viewModel.updateUiState()
        }
        dialog.show()
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

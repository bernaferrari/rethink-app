/*
 * Copyright 2022 RethinkDNS and its authors
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
import Logger.LOG_TAG_DNS
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.AppConfig.Companion.DOH_INDEX
import com.celzero.bravedns.data.AppConfig.Companion.DOT_INDEX
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.FragmentDnsConfigureBinding
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.scheduler.WorkScheduler.Companion.BLOCKLIST_UPDATE_CHECK_JOB_TAG
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.ConfigureRethinkBasicActivity
import com.celzero.bravedns.ui.activity.DnsListActivity
import com.celzero.bravedns.ui.activity.PauseActivity
import com.celzero.bravedns.ui.bottomsheet.DnsRecordTypesBottomSheet
import com.celzero.bravedns.ui.bottomsheet.LocalBlocklistsBottomSheet
import com.celzero.bravedns.util.NewSettingsManager
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.setBadgeDotVisible
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastR
import com.celzero.bravedns.util.Utilities.isPlayStoreFlavour
import com.celzero.bravedns.util.Utilities.tos
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.celzero.bravedns.ui.compose.dns.DnsSettingsScreen
import com.celzero.bravedns.ui.compose.dns.DnsSettingsViewModel
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class DnsSettingsFragment : Fragment(),
    LocalBlocklistsBottomSheet.OnBottomSheetDialogFragmentDismiss {

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val eventLogger by inject<EventLogger>()
    private val viewModel: DnsSettingsViewModel by viewModel()

    companion object {
        fun newInstance() = DnsSettingsFragment()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RethinkTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    DnsSettingsScreen(
                        uiState = uiState,
                        onRefreshClick = { viewModel.refreshDns() },
                        onSystemDnsClick = { viewModel.enableSystemDns() },
                        onSystemDnsInfoClick = { 
                            lifecycleScope.launch {
                                val sysDns = VpnController.getSystemDns()
                                showSystemDnsDialog(sysDns)
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
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateUiState()
    }

    private fun showDnsRecordTypesBottomSheet() {
        val bottomSheet = DnsRecordTypesBottomSheet()
        // Update UI when bottom sheet is dismissed
        parentFragmentManager.setFragmentResultListener("dns_record_types_updated", viewLifecycleOwner) { _, _ ->
            viewModel.updateUiState()
        }
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
    }

    private fun showSmartDnsInfoDialog() {
        lifecycleScope.launch {
            val ids = VpnController.getPlusResolvers()
            val dnsList: MutableList<String> = mutableListOf()
            ids.forEach {
                val index = it.substringAfter(Backend.Plus).getOrNull(0)
                if (index == null) {
                    Logger.w(LOG_TAG_DNS, "smart(plus) dns resolver id is empty: $it")
                    return@forEach
                }
                // for now, only doh and dot are supported
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
                val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
                    .setTitle(R.string.smart_dns)
                    .setMessage(list)
                    .setCancelable(true)
                    .setPositiveButton(R.string.ada_noapp_dialog_positive) { di, _ ->
                        di.dismiss()
                    }.setNeutralButton(
                        requireContext().getString(R.string.dns_info_neutral)
                    ) { _: DialogInterface, _: Int ->
                        UIUtils.clipboardCopy(
                            requireContext(),
                            list,
                            requireContext().getString(R.string.copy_clipboard_label)
                        )
                        Utilities.showToastUiCentered(
                            requireContext(),
                            requireContext().getString(R.string.info_dialog_url_copy_toast_msg),
                            Toast.LENGTH_SHORT
                        )
                    }
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private fun showSystemDnsDialog(dns: String) {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.App_Dialog_NoDim)
            .setTitle(R.string.network_dns)
            .setMessage(dns)
            .setCancelable(true)
            .setPositiveButton(R.string.ada_noapp_dialog_positive) { di, _ ->
                di.dismiss()
            }
            .setNeutralButton(requireContext().getString(R.string.dns_info_neutral)) { _: DialogInterface, _: Int ->
                UIUtils.clipboardCopy(
                    requireContext(),
                    dns,
                    requireContext().getString(R.string.copy_clipboard_label)
                )
                Utilities.showToastUiCentered(
                    requireContext(),
                    requireContext().getString(R.string.info_dialog_url_copy_toast_msg),
                    Toast.LENGTH_SHORT
                )
            }
        val dialog = builder.create()
        dialog.show()
    }

    // open local blocklist bottom sheet
    private fun openLocalBlocklist() {
        val bottomSheetFragment = LocalBlocklistsBottomSheet()
        bottomSheetFragment.setDismissListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }

    private fun invokeRethinkActivity(type: ConfigureRethinkBasicActivity.FragmentLoader) {
        val intent = Intent(requireContext(), ConfigureRethinkBasicActivity::class.java)
        intent.putExtra(ConfigureRethinkBasicActivity.INTENT, type.ordinal)
        requireContext().startActivity(intent)
    }

    private fun showCustomDns() {
        val intent = Intent(requireContext(), DnsListActivity::class.java)
        startActivity(intent)
    }

    override fun onBtmSheetDismiss() {
        if (!isAdded) return
        viewModel.updateUiState()
    }
}

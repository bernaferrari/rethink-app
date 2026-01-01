/*
 * Copyright 2023 RethinkDNS and its authors
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
import Logger.LOG_TAG_PROXY
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.OneWgConfigAdapter
import com.celzero.bravedns.adapter.WgConfigAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.QrCodeFromFileScanner
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.TunnelImporter
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.WgConfigViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class WgMainActivity :
    AppCompatActivity(), OneWgConfigAdapter.DnsStatusListener {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val eventLogger by inject<EventLogger>()

    private val wgConfigViewModel: WgConfigViewModel by viewModel()

    private var selectedTab by mutableStateOf(WgTab.ONE)
    private var showEmpty by mutableStateOf(false)
    private var disclaimerText by mutableStateOf("")
    private var isFabExpanded by mutableStateOf(false)

    companion object {
        private const val IMPORT_LAUNCH_INPUT = "*/*"
        private const val EMPTY_ALPHA = 0.7f
    }

    private enum class WgTab {
        ONE,
        GENERAL
    }

    private val tunnelFileImportResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
            if (data == null) return@registerForActivityResult
            val contentResolver = contentResolver ?: return@registerForActivityResult
            lifecycleScope.launch {
                if (QrCodeFromFileScanner.validContentType(contentResolver, data)) {
                    try {
                        val qrCodeFromFileScanner =
                            QrCodeFromFileScanner(contentResolver, QRCodeReader())
                        val result = qrCodeFromFileScanner.scan(data)
                        Logger.i(LOG_TAG_PROXY, "result: $result, data: $data")
                        if (result != null) {
                            withContext(Dispatchers.Main) {
                                Logger.i(LOG_TAG_PROXY, "result: ${result.text}")
                                TunnelImporter.importTunnel(result.text) {
                                    Utilities.showToastUiCentered(
                                        this@WgMainActivity,
                                        it.toString(),
                                        Toast.LENGTH_LONG
                                    )
                                    Logger.e(LOG_TAG_PROXY, it.toString())
                                }
                                logEvent("Wireguard import", "imported from file")
                            }
                        } else {
                            val message =
                                resources.getString(
                                    R.string.generic_error,
                                    getString(R.string.invalid_file_error)
                                )
                            Utilities.showToastUiCentered(
                                this@WgMainActivity,
                                message,
                                Toast.LENGTH_LONG
                            )
                            Logger.e(LOG_TAG_PROXY, message)
                        }
                    } catch (e: Exception) {
                        val message =
                            resources.getString(
                                R.string.generic_error,
                                getString(R.string.invalid_file_error)
                            )
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            message,
                            Toast.LENGTH_LONG
                        )
                        Logger.e(LOG_TAG_PROXY, e.message ?: "err tun import", e)
                    }
                } else {
                    TunnelImporter.importTunnel(contentResolver, data) {
                        Logger.e(LOG_TAG_PROXY, it.toString())
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                    }
                }
            }
        }

    private val qrImportResultLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val qrCode = result.contents
            if (qrCode != null) {
                lifecycleScope.launch {
                    TunnelImporter.importTunnel(qrCode) {
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                        Logger.e(LOG_TAG_PROXY, it.toString())
                        logEvent("Wireguard import", "imported via QR scanner")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)
        init()

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isFabExpanded) {
                        collapseFab()
                    } else {
                        finish()
                    }
                }
            }
        )

        setContent {
            RethinkTheme {
                WgMainScreen()
            }
        }
    }

    private fun init() {
        collapseFab()
        observeConfig()
        observeDnsName()
        selectedTab =
            if (WireguardManager.isAnyWgActive() && !WireguardManager.oneWireGuardEnabled()) {
                WgTab.GENERAL
            } else {
                WgTab.ONE
            }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun observeConfig() {
        wgConfigViewModel.configCount().observe(this) {
            showEmpty = it == 0
        }
    }

    private fun observeDnsName() {
        val activeConfigs = WireguardManager.getActiveConfigs()
        if (WireguardManager.oneWireGuardEnabled()) {
            val dnsName = activeConfigs.firstOrNull()?.getName() ?: return
            disclaimerText = getString(R.string.wireguard_disclaimer, dnsName)
            appConfig.getConnectedDnsObservable().removeObservers(this)
        } else {
            appConfig.getConnectedDnsObservable().observe(this) { dns ->
                var dnsNames: String = dns.ifEmpty { "" }
                if (persistentState.splitDns) {
                    if (activeConfigs.isNotEmpty()) {
                        dnsNames += ", "
                    }
                    dnsNames += activeConfigs.joinToString(",") { it.getName() }
                }
                if (persistentState.useFallbackDnsToBypass) {
                    dnsNames += ", " + getString(R.string.lbl_fallback)
                }
                disclaimerText = getString(R.string.wireguard_disclaimer, dnsNames)
            }
        }
    }

    private fun openTunnelEditorActivity() {
        val intent = Intent(this, WgConfigEditorActivity::class.java)
        startActivity(intent)
    }

    private fun showDisableDialog(isOneWgToggle: Boolean) {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.wireguard_disable_title))
            .setMessage(getString(R.string.wireguard_disable_message))
            .setPositiveButton(getString(R.string.always_on_dialog_positive)) { _, _ ->
                io {
                    if (WireguardManager.canDisableAllActiveConfigs()) {
                        WireguardManager.disableAllActiveConfigs()
                        logEvent(
                            "Wireguard disable",
                            "all configs from toggle switch; isOneWgToggle: $isOneWgToggle"
                        )
                        uiCtx {
                            observeDnsName()
                            selectedTab = if (isOneWgToggle) WgTab.ONE else WgTab.GENERAL
                        }
                    } else {
                        val configs = WireguardManager.getActiveCatchAllConfig()
                        if (configs.isNotEmpty()) {
                            uiCtx {
                                Utilities.showToastUiCentered(
                                    this,
                                    getString(R.string.wireguard_disable_failure),
                                    Toast.LENGTH_LONG
                                )
                            }
                        } else {
                            uiCtx {
                                Utilities.showToastUiCentered(
                                    this,
                                    getString(R.string.wireguard_disable_failure_relay),
                                    Toast.LENGTH_LONG
                                )
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
            .create()
            .show()
    }

    private fun onOneWgToggleClick() {
        val activeConfigs = WireguardManager.getActiveConfigs()
        val isAnyConfigActive = activeConfigs.isNotEmpty()
        val isOneWgEnabled = WireguardManager.oneWireGuardEnabled()
        if (isAnyConfigActive && !isOneWgEnabled) {
            showDisableDialog(isOneWgToggle = true)
            return
        }
        selectedTab = WgTab.ONE
    }

    private fun onGeneralToggleClick() {
        if (WireguardManager.oneWireGuardEnabled()) {
            showDisableDialog(isOneWgToggle = false)
            return
        }
        selectedTab = WgTab.GENERAL
    }

    private fun expandFab() {
        isFabExpanded = true
    }

    private fun collapseFab() {
        isFabExpanded = false
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    override fun onDnsStatusChanged() {
        observeDnsName()
    }

    @Composable
    private fun WgMainScreen() {
        val context = LocalContext.current
        Box(modifier = Modifier.fillMaxSize()) {
            if (showEmpty) {
                EmptyState()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ToggleRow(
                        selectedTab = selectedTab,
                        onOneWgClick = { onOneWgToggleClick() },
                        onGeneralClick = { onGeneralToggleClick() }
                    )

                    Text(
                        text = disclaimerText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .alpha(EMPTY_ALPHA),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        val items = wgConfigViewModel.interfaces.asFlow().collectAsLazyPagingItems()
                        val padding = PaddingValues(bottom = 100.dp)
                        if (selectedTab == WgTab.GENERAL) {
                            val adapter =
                                remember {
                                    WgConfigAdapter(
                                        this@WgMainActivity,
                                        this@WgMainActivity,
                                        persistentState.splitDns,
                                        eventLogger
                                    )
                                }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = padding
                            ) {
                                items(count = items.itemCount) { index ->
                                    val item = items[index] ?: return@items
                                    adapter.ConfigRow(item)
                                }
                            }
                        }

                        if (selectedTab == WgTab.ONE) {
                            val adapter =
                                remember {
                                    OneWgConfigAdapter(
                                        this@WgMainActivity,
                                        this@WgMainActivity,
                                        eventLogger
                                    )
                                }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = padding
                            ) {
                                items(count = items.itemCount) { index ->
                                    val item = items[index] ?: return@items
                                    adapter.ConfigRow(item)
                                }
                            }
                        }
                    }
                }
            }

            FabStack(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                isExpanded = isFabExpanded,
                onMainClick = { if (isFabExpanded) collapseFab() else expandFab() },
                onCreateClick = { openTunnelEditorActivity() },
                onImportClick = {
                    try {
                        tunnelFileImportResultLauncher.launch(IMPORT_LAUNCH_INPUT)
                    } catch (e: ActivityNotFoundException) {
                        Logger.e(LOG_TAG_PROXY, "err; anf; while launching file import: ${e.message}", e)
                        Utilities.showToastUiCentered(context, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_PROXY, "err while launching file import: ${e.message}", e)
                        Utilities.showToastUiCentered(context, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    }
                },
                onQrClick = {
                    try {
                        qrImportResultLauncher.launch(
                            ScanOptions()
                                .setOrientationLocked(false)
                                .setBeepEnabled(false)
                                .setPrompt(resources.getString(R.string.lbl_qr_code))
                        )
                    } catch (e: ActivityNotFoundException) {
                        Logger.e(LOG_TAG_PROXY, "err; anf while launching QR scanner: ${e.message}", e)
                        Utilities.showToastUiCentered(context, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_PROXY, "err while launching QR scanner: ${e.message}", e)
                        Utilities.showToastUiCentered(context, getString(R.string.blocklist_update_check_failure), Toast.LENGTH_SHORT)
                    }
                }
            )
        }
    }

    @Composable
    private fun ToggleRow(
        selectedTab: WgTab,
        onOneWgClick: () -> Unit,
        onGeneralClick: () -> Unit
    ) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleButton(
                text = context.getString(R.string.rt_list_simple_btn_txt),
                selected = selectedTab == WgTab.ONE,
                onClick = onOneWgClick
            )
            ToggleButton(
                text = context.getString(R.string.lbl_advanced),
                selected = selectedTab == WgTab.GENERAL,
                onClick = onGeneralClick
            )
        }
    }

    @Composable
    private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) {
        val context = LocalContext.current
        val background =
            if (selected) {
                Color(fetchToggleBtnColors(context, R.color.accentGood))
            } else {
                Color(fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
            }
        val content =
            if (selected) {
                Color(UIUtils.fetchColor(context, R.attr.homeScreenHeaderTextColor))
            } else {
                Color(UIUtils.fetchColor(context, R.attr.primaryTextColor))
            }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(text = text)
        }
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResourceCompat(R.string.wireguard_no_config_msg),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Image(
                painter = painterResource(id = R.drawable.illustrations_no_record),
                contentDescription = null,
                modifier = Modifier.size(220.dp)
            )
        }
    }

    @Composable
    private fun FabStack(
        modifier: Modifier,
        isExpanded: Boolean,
        onMainClick: () -> Unit,
        onCreateClick: () -> Unit,
        onImportClick: () -> Unit,
        onQrClick: () -> Unit
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = onCreateClick,
                        text = { Text(text = stringResourceCompat(R.string.lbl_create)) },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ExtendedFloatingActionButton(
                        onClick = onImportClick,
                        text = { Text(text = stringResourceCompat(R.string.lbl_import)) },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_import_conf),
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ExtendedFloatingActionButton(
                        onClick = onQrClick,
                        text = { Text(text = stringResourceCompat(R.string.lbl_qr_code)) },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qr_code_scanner),
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            FloatingActionButton(onClick = onMainClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fab_without_border),
                    contentDescription = null
                )
            }
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}

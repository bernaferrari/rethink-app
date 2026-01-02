/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.wireguard

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.WgConfigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EMPTY_ALPHA = 0.7f

enum class WgTab {
    ONE,
    GENERAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WgMainScreen(
    wgConfigViewModel: WgConfigViewModel,
    persistentState: PersistentState,
    appConfig: AppConfig,
    eventLogger: EventLogger,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onQrScanClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember {
        mutableStateOf(
            if (WireguardManager.isAnyWgActive() && !WireguardManager.oneWireGuardEnabled()) {
                WgTab.GENERAL
            } else {
                WgTab.ONE
            }
        )
    }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var disableDialogIsOneWgToggle by remember { mutableStateOf(false) }
    var disclaimerText by remember { mutableStateOf("") }

    val configCount by wgConfigViewModel.configCount().asFlow()
        .collectAsStateWithLifecycle(initialValue = 0)
    val showEmpty = configCount == 0

    // Observe connected DNS for non-OneWG mode
    val connectedDns by appConfig.getConnectedDnsObservable().asFlow()
        .collectAsStateWithLifecycle(initialValue = "")

    // DNS status listener callback - updates disclaimer text
    fun updateDisclaimerText() {
        val activeConfigs = WireguardManager.getActiveConfigs()
        disclaimerText = if (WireguardManager.oneWireGuardEnabled()) {
            val dnsName = activeConfigs.firstOrNull()?.getName() ?: ""
            context.getString(R.string.wireguard_disclaimer, dnsName)
        } else {
            var dnsNames = connectedDns
            if (persistentState.splitDns && activeConfigs.isNotEmpty()) {
                if (dnsNames.isNotEmpty()) {
                    dnsNames += ", "
                }
                dnsNames += activeConfigs.joinToString(",") { it.getName() }
            }
            if (persistentState.useFallbackDnsToBypass) {
                dnsNames += ", " + context.getString(R.string.lbl_fallback)
            }
            context.getString(R.string.wireguard_disclaimer, dnsNames)
        }
    }

    // A counter to trigger disclaimer text refresh
    var dnsRefreshTrigger by remember { mutableStateOf(0) }

    // Initialize and update disclaimer text when tab, DNS, or refresh trigger changes
    LaunchedEffect(selectedTab, connectedDns, dnsRefreshTrigger) {
        updateDisclaimerText()
    }

    // Create adapters with listener
    val oneWgAdapter = remember {
        OneWgConfigAdapter(
            context = context,
            listener = object : OneWgConfigAdapter.DnsStatusListener {
                override fun onDnsStatusChanged() {
                    dnsRefreshTrigger++
                }
            },
            eventLogger = eventLogger
        )
    }

    val wgAdapter = remember {
        WgConfigAdapter(
            context = context,
            listener = object : OneWgConfigAdapter.DnsStatusListener {
                override fun onDnsStatusChanged() {
                    dnsRefreshTrigger++
                }
            },
            splitDns = persistentState.splitDns,
            eventLogger = eventLogger
        )
    }

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    if (showDisableDialog) {
        DisableConfigsDialog(
            onDismiss = { showDisableDialog = false },
            onConfirm = {
                showDisableDialog = false
                val isOneWgToggle = disableDialogIsOneWgToggle
                scope.launch(Dispatchers.IO) {
                    if (WireguardManager.canDisableAllActiveConfigs()) {
                        WireguardManager.disableAllActiveConfigs()
                        logEvent(
                            eventLogger,
                            "Wireguard disable",
                            "all configs from toggle switch; isOneWgToggle: $isOneWgToggle"
                        )
                        withContext(Dispatchers.Main) {
                            dnsRefreshTrigger++
                            selectedTab = if (isOneWgToggle) WgTab.ONE else WgTab.GENERAL
                        }
                    } else {
                        val configs = WireguardManager.getActiveCatchAllConfig()
                        withContext(Dispatchers.Main) {
                            val msgRes = if (configs.isNotEmpty()) {
                                R.string.wireguard_disable_failure
                            } else {
                                R.string.wireguard_disable_failure_relay
                            }
                            Utilities.showToastUiCentered(
                                context,
                                context.getString(msgRes),
                                Toast.LENGTH_LONG
                            )
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.lbl_wireguard)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showEmpty) {
                EmptyState()
            } else {
                WgConfigContent(
                    selectedTab = selectedTab,
                    disclaimerText = disclaimerText,
                    wgConfigViewModel = wgConfigViewModel,
                    oneWgAdapter = oneWgAdapter,
                    wgAdapter = wgAdapter,
                    onOneWgToggleClick = {
                        val activeConfigs = WireguardManager.getActiveConfigs()
                        val isAnyConfigActive = activeConfigs.isNotEmpty()
                        val isOneWgEnabled = WireguardManager.oneWireGuardEnabled()
                        if (isAnyConfigActive && !isOneWgEnabled) {
                            disableDialogIsOneWgToggle = true
                            showDisableDialog = true
                        } else {
                            selectedTab = WgTab.ONE
                        }
                    },
                    onGeneralToggleClick = {
                        if (WireguardManager.oneWireGuardEnabled()) {
                            disableDialogIsOneWgToggle = false
                            showDisableDialog = true
                        } else {
                            selectedTab = WgTab.GENERAL
                        }
                    }
                )
            }

            FabStack(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                isExpanded = isFabExpanded,
                onMainClick = { isFabExpanded = !isFabExpanded },
                onCreateClick = {
                    isFabExpanded = false
                    onCreateClick()
                },
                onImportClick = {
                    isFabExpanded = false
                    onImportClick()
                },
                onQrClick = {
                    isFabExpanded = false
                    onQrScanClick()
                }
            )
        }
    }
}

@Composable
private fun WgConfigContent(
    selectedTab: WgTab,
    disclaimerText: String,
    wgConfigViewModel: WgConfigViewModel,
    oneWgAdapter: OneWgConfigAdapter,
    wgAdapter: WgConfigAdapter,
    onOneWgToggleClick: () -> Unit,
    onGeneralToggleClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ToggleRow(
            selectedTab = selectedTab,
            onOneWgClick = onOneWgToggleClick,
            onGeneralClick = onGeneralToggleClick
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding
                ) {
                    items(count = items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        wgAdapter.ConfigRow(item)
                    }
                }
            }

            if (selectedTab == WgTab.ONE) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding
                ) {
                    items(count = items.itemCount) { index ->
                        val item = items[index] ?: return@items
                        oneWgAdapter.ConfigRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    selectedTab: WgTab,
    onOneWgClick: () -> Unit,
    onGeneralClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleButton(
            text = stringResource(id = R.string.rt_list_simple_btn_txt),
            selected = selectedTab == WgTab.ONE,
            onClick = onOneWgClick
        )
        ToggleButton(
            text = stringResource(id = R.string.lbl_advanced),
            selected = selectedTab == WgTab.GENERAL,
            onClick = onGeneralClick
        )
    }
}

@Composable
private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val background = if (selected) {
        Color(fetchToggleBtnColors(context, R.color.accentGood))
    } else {
        Color(fetchToggleBtnColors(context, R.color.defaultToggleBtnBg))
    }
    val content = if (selected) {
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
private fun DisableConfigsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.wireguard_disable_title)) },
        text = { Text(text = stringResource(id = R.string.wireguard_disable_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.always_on_dialog_positive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.lbl_cancel))
            }
        }
    )
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
            text = stringResource(id = R.string.wireguard_no_config_msg),
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
                    text = { Text(text = stringResource(id = R.string.lbl_create)) },
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
                    text = { Text(text = stringResource(id = R.string.lbl_import)) },
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
                    text = { Text(text = stringResource(id = R.string.lbl_qr_code)) },
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

private fun logEvent(eventLogger: EventLogger, msg: String, details: String) {
    eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
}

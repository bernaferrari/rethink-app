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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.celzero.bravedns.adapter.OneWgConfigRow
import com.celzero.bravedns.adapter.WgConfigRow
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
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
    onQrScanClick: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit
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
            context.resources.getString(R.string.wireguard_disclaimer, dnsName)
        } else {
            var dnsNames = connectedDns
            if (persistentState.splitDns && activeConfigs.isNotEmpty()) {
                if (dnsNames.isNotEmpty()) {
                    dnsNames += ", "
                }
                dnsNames += activeConfigs.joinToString(",") { it.getName() }
            }
            if (persistentState.useFallbackDnsToBypass) {
                dnsNames += ", " + context.resources.getString(R.string.lbl_fallback)
            }
            context.resources.getString(R.string.wireguard_disclaimer, dnsNames)
        }
    }

    // A counter to trigger disclaimer text refresh
    var dnsRefreshTrigger by remember { mutableStateOf(0) }

    // Initialize and update disclaimer text when tab, DNS, or refresh trigger changes
    LaunchedEffect(selectedTab, connectedDns, dnsRefreshTrigger) {
        updateDisclaimerText()
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
                                context.resources.getString(msgRes),
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
            RethinkTopBar(
                title = stringResource(id = R.string.lbl_wireguard),
                onBackClick = onBackClick
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
                Column(modifier = Modifier.fillMaxSize()) {
                    WireguardOverviewCard(disclaimerText = disclaimerText)
                    WgConfigContent(
                        selectedTab = selectedTab,
                        wgConfigViewModel = wgConfigViewModel,
                        eventLogger = eventLogger,
                        onDnsStatusChanged = { dnsRefreshTrigger++ },
                        onConfigDetailClick = onConfigDetailClick,
                        modifier = Modifier.weight(1f),
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
    wgConfigViewModel: WgConfigViewModel,
    eventLogger: EventLogger,
    onDnsStatusChanged: () -> Unit,
    onConfigDetailClick: (Int, WgType) -> Unit,
    modifier: Modifier = Modifier,
    onOneWgToggleClick: () -> Unit,
    onGeneralToggleClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        ToggleRow(
            selectedTab = selectedTab,
            onOneWgClick = onOneWgToggleClick,
            onGeneralClick = onGeneralToggleClick
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
                        WgConfigRow(
                            config = item,
                            eventLogger = eventLogger,
                            onDnsStatusChanged = onDnsStatusChanged,
                            onConfigDetailClick = onConfigDetailClick
                        )
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
                        OneWgConfigRow(
                            config = item,
                            eventLogger = eventLogger,
                            onDnsStatusChanged = onDnsStatusChanged,
                            onConfigDetailClick = onConfigDetailClick
                        )
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
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        SegmentedButton(
            selected = selectedTab == WgTab.ONE,
            onClick = onOneWgClick,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = {
                Text(text = stringResource(id = R.string.rt_list_simple_btn_txt))
            }
        )
        SegmentedButton(
            selected = selectedTab == WgTab.GENERAL,
            onClick = onGeneralClick,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = {
                Text(text = stringResource(id = R.string.lbl_advanced))
            }
        )
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
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.illustrations_no_record),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.wireguard_no_config_msg),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WireguardOverviewCard(disclaimerText: String) {
    Surface(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wireguard_icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Text(
                text = disclaimerText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

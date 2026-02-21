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
package com.celzero.bravedns.ui.compose.firewall

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class RulesTab { IP, DOMAIN }

data class RulesTabSpec(val tab: RulesTab, val label: String)

/**
 * Full Custom Rules Screen for navigation integration.
 * Displays IP and Domain custom rules with search, filtering, add, and delete.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun CustomRulesScreen(
    domainViewModel: CustomDomainViewModel,
    ipViewModel: CustomIpViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        RulesTabSpec(RulesTab.IP, stringResource(R.string.lbl_ip_rules)),
        RulesTabSpec(RulesTab.DOMAIN, stringResource(R.string.lbl_domain_rules))
    )

    val selectedTab = remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_configure),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.lbl_add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier.padding(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(Dimensions.spacingLg)) {
                    Text(
                        text = stringResource(R.string.lbl_configure),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.custom_rules_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab.intValue,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, spec ->
                    Tab(
                        selected = selectedTab.intValue == index,
                        onClick = { selectedTab.intValue = index },
                        text = { Text(text = spec.label) }
                    )
                }
            }

            when (tabs[selectedTab.intValue].tab) {
                RulesTab.IP -> IpRulesContent(viewModel = ipViewModel, eventLogger = eventLogger)
                RulesTab.DOMAIN -> DomainRulesContent(viewModel = domainViewModel, eventLogger = eventLogger)
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            isIpRule = tabs[selectedTab.intValue].tab == RulesTab.IP,
            onDismiss = { showAddDialog = false },
            onAddIpRule = { ip ->
                scope.launch(Dispatchers.IO) {
                    val ipAddress = IPAddressString(ip).address ?: return@launch
                    IpRulesManager.addIpRule(UID_EVERYBODY, ipAddress, null, IpRulesManager.IpRuleStatus.BLOCK, "", "")
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    "Added IP rule",
                    EventSource.UI,
                    false,
                    "IP: $ip"
                )
                showAddDialog = false
            },
            onAddDomainRule = { domain ->
                scope.launch(Dispatchers.IO) {
                    DomainRulesManager.addDomainRule(
                        domain,
                        DomainRulesManager.Status.BLOCK,
                        DomainRulesManager.DomainType.DOMAIN,
                        uid = UID_EVERYBODY
                    )
                }
                eventLogger.log(
                    EventType.FW_RULE_MODIFIED,
                    Severity.LOW,
                    "Added domain rule",
                    EventSource.UI,
                    false,
                    "Domain: $domain"
                )
                showAddDialog = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun IpRulesContent(viewModel: CustomIpViewModel, eventLogger: EventLogger) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = viewModel.customIpDetails.asFlow().collectAsLazyPagingItems()

    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.setUid(UID_EVERYBODY) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collect { q -> viewModel.setFilter(q) }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = Dimensions.screenPaddingHorizontal, vertical = Dimensions.spacingMd)
    ) {
        SectionHeader(title = stringResource(R.string.lbl_ip_rules))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.lbl_search)) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Text(text = stringResource(R.string.lbl_no_logs), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    IpRuleRow(rule = item, onDelete = {
                        scope.launch(Dispatchers.IO) {
                            IpRulesManager.removeIpRule(item.uid, item.ipAddress, item.port)
                        }
                        eventLogger.log(
                            EventType.FW_RULE_MODIFIED,
                            Severity.LOW,
                            "Removed IP rule",
                            EventSource.UI,
                            false,
                            "IP: ${item.ipAddress}"
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun IpRuleRow(rule: CustomIp, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = rule.ipAddress, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = IpRulesManager.IpRuleStatus.getStatus(rule.status).name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.lbl_delete))
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun DomainRulesContent(viewModel: CustomDomainViewModel, eventLogger: EventLogger) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = viewModel.customDomains.asFlow().collectAsLazyPagingItems()

    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.setUid(UID_EVERYBODY) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collect { q -> viewModel.setFilter(q) }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = Dimensions.screenPaddingHorizontal, vertical = Dimensions.spacingMd)
    ) {
        SectionHeader(title = stringResource(R.string.lbl_domain_rules))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.lbl_search)) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) {
            Text(text = stringResource(R.string.cd_no_rules_text), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    DomainRuleRow(rule = item, onDelete = {
                        scope.launch(Dispatchers.IO) {
                            DomainRulesManager.deleteDomain(item)
                        }
                        eventLogger.log(
                            EventType.FW_RULE_MODIFIED,
                            Severity.LOW,
                            "Removed domain rule",
                            EventSource.UI,
                            false,
                            "Domain: ${item.domain}"
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun DomainRuleRow(rule: CustomDomain, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = rule.domain, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = DomainRulesManager.Status.getStatus(rule.status).name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.lbl_delete))
        }
    }
}

@Composable
private fun AddRuleDialog(
    isIpRule: Boolean,
    onDismiss: () -> Unit,
    onAddIpRule: (String) -> Unit,
    onAddDomainRule: (String) -> Unit
) {
    var ruleValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (isIpRule) "Add IP Rule" else "Add Domain Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = ruleValue,
                    onValueChange = { ruleValue = it },
                    label = { Text(if (isIpRule) "IP Address" else "Domain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (ruleValue.isNotBlank()) {
                        if (isIpRule) onAddIpRule(ruleValue.trim())
                        else onAddDomainRule(ruleValue.trim())
                    }
                },
                enabled = ruleValue.isNotBlank()
            ) {
                Text(text = stringResource(R.string.lbl_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.lbl_cancel))
            }
        }
    )
}

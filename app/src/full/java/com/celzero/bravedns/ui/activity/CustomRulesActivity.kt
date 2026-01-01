/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CustomDomain
import com.celzero.bravedns.database.CustomIp
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.DomainRulesManager.isValidDomain
import com.celzero.bravedns.service.DomainRulesManager.isWildCardEntry
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.bottomsheet.CustomDomainRulesSheet
import com.celzero.bravedns.ui.bottomsheet.CustomIpRulesSheet
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.removeLeadingAndTrailingDots
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class CustomRulesActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()
    private val customDomainViewModel: CustomDomainViewModel by viewModel()
    private val customIpViewModel: CustomIpViewModel by viewModel()

    private var fragmentIndex = 0
    private var rulesType = RULES.APP_SPECIFIC_RULES.ordinal
    private var uid = UID_EVERYBODY
    private var rules = RULES.APP_SPECIFIC_RULES

    enum class Tabs(val screen: Int) {
        IP_RULES(0),
        DOMAIN_RULES(1);

        companion object {
            fun getCount(): Int {
                return entries.size
            }
        }
    }

    enum class RULES(val type: Int) {
        ALL_RULES(0),
        APP_SPECIFIC_RULES(1);

        companion object {
            fun getType(type: Int): RULES {
                return when (type) {
                    0 -> ALL_RULES
                    1 -> APP_SPECIFIC_RULES
                    else -> APP_SPECIFIC_RULES
                }
            }
        }
    }

    companion object {
        const val INTENT_RULES = "INTENT_RULES"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        fragmentIndex = intent.getIntExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, 0)
        rulesType = intent.getIntExtra(INTENT_RULES, RULES.APP_SPECIFIC_RULES.type)
        uid = intent.getIntExtra(Constants.INTENT_UID, UID_EVERYBODY)
        rules = RULES.getType(rulesType)

        setContent {
            RethinkTheme {
                CustomRulesContent(initialTab = fragmentIndex)
            }
        }

        observeAppState()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun CustomRulesContent(initialTab: Int) {
        val selectedTab = rememberSaveable {
            mutableStateOf(initialTab.coerceIn(0, Tabs.getCount() - 1))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab.value) {
                Tab(
                    text = { Text(text = getString(R.string.univ_view_blocked_ip)) },
                    selected = selectedTab.value == Tabs.IP_RULES.screen,
                    onClick = { selectedTab.value = Tabs.IP_RULES.screen }
                )
                Tab(
                    text = { Text(text = getString(R.string.dc_custom_block_heading)) },
                    selected = selectedTab.value == Tabs.DOMAIN_RULES.screen,
                    onClick = { selectedTab.value = Tabs.DOMAIN_RULES.screen }
                )
            }

            when (selectedTab.value) {
                Tabs.IP_RULES.screen -> CustomIpContent()
                Tabs.DOMAIN_RULES.screen -> CustomDomainContent()
            }
        }
    }

    @Composable
    private fun CustomDomainContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var appName by remember { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var showAddDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var editDomain by remember { mutableStateOf<CustomDomain?>(null) }
        var selectedDomain by remember { mutableStateOf<CustomDomain?>(null) }
        val selectedItems = remember { mutableStateListOf<CustomDomain>() }
        val selectionMode by remember { derivedStateOf { selectedItems.isNotEmpty() } }

        LaunchedEffect(rules, uid) {
            if (rules == RULES.APP_SPECIFIC_RULES) {
                customDomainViewModel.setUid(uid)
                appName = withContext(Dispatchers.IO) { FirewallManager.getAppNameByUid(uid) }
            }
        }

        LaunchedEffect(query) {
            customDomainViewModel.setFilter(query)
        }

        val domainCount by
            (if (rules == RULES.APP_SPECIFIC_RULES) {
                    customDomainViewModel.domainRulesCount(uid)
                } else {
                    customDomainViewModel.allDomainRulesCount()
                })
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = 0)

        val domainItems =
            (if (rules == RULES.APP_SPECIFIC_RULES) {
                    customDomainViewModel.customDomains
                } else {
                    customDomainViewModel.allDomainRules
                })
                .asFlow()
                .collectAsLazyPagingItems()

        Scaffold(
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (rules == RULES.APP_SPECIFIC_RULES) {
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fab_without_border),
                            contentDescription = getString(R.string.lbl_create)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (domainCount <= 0) {
                    NoRulesContent(text = getString(R.string.cd_no_rules_text))
                } else {
                    val placeholder =
                        if (rules == RULES.APP_SPECIFIC_RULES && !appName.isNullOrEmpty()) {
                            val name = appName?.take(10) ?: ""
                            getString(
                                R.string.two_argument_colon,
                                name,
                                getString(R.string.search_custom_domains)
                            )
                        } else {
                            getString(R.string.search_custom_domains)
                        }
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onDeleteClick = { showDeleteDialog = true },
                        placeholder = placeholder
                    )
                    CustomDomainList(
                        items = domainItems,
                        rules = rules,
                        selectionMode = selectionMode,
                        selectedItems = selectedItems,
                        onItemClick = { item ->
                            if (selectionMode) {
                                toggleSelection(selectedItems, item)
                            } else {
                                selectedDomain = item
                            }
                        },
                        onItemLongClick = { item ->
                            toggleSelection(selectedItems, item)
                        },
                        onEditClick = { editDomain = it },
                        onSeeMore = { targetUid ->
                            openAppWiseRulesActivity(
                                targetUid,
                                Tabs.DOMAIN_RULES.screen
                            )
                        }
                    )
                }
            }
        }

        selectedDomain?.let { domain ->
            CustomDomainRulesSheet(
                customDomain = domain,
                eventLogger = eventLogger,
                onDismiss = { selectedDomain = null },
                onDeleted = { selectedDomain = null }
            )
        }

        if (showAddDialog) {
            DomainRuleDialog(
                title = getString(R.string.cd_dialog_title),
                initialDomain = "",
                initialType = DomainRulesManager.DomainType.DOMAIN,
                onDismiss = { showAddDialog = false },
                onConfirm = { domain, type, status ->
                    scope.launch {
                        insertDomain(domain, type, status)
                        showAddDialog = false
                    }
                }
            )
        }

        editDomain?.let { domain ->
            DomainRuleDialog(
                title = getString(R.string.cd_dialog_title),
                initialDomain = domain.domain,
                initialType = DomainRulesManager.DomainType.getType(domain.type),
                onDismiss = { editDomain = null },
                onConfirm = { updatedDomain, type, status ->
                    scope.launch {
                        updateDomain(updatedDomain, type, domain, status)
                        editDomain = null
                    }
                }
            )
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = getString(R.string.univ_delete_domain_dialog_title),
                message = getString(R.string.univ_delete_domain_dialog_message),
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        if (selectedItems.isNotEmpty()) {
                            DomainRulesManager.deleteRules(selectedItems)
                            selectedItems.clear()
                            logDomainEvent("Deleted domains: $selectedItems, Rule: $rules, UID: $uid")
                        } else {
                            if (rules == RULES.APP_SPECIFIC_RULES) {
                                DomainRulesManager.deleteRulesByUid(uid)
                                logDomainEvent("Deleted all domains for UID: $uid")
                            } else {
                                DomainRulesManager.deleteAllRules()
                                logDomainEvent("Deleted all custom domain rules")
                            }
                        }
                        Utilities.showToastUiCentered(
                            this@CustomRulesActivity,
                            getString(R.string.cd_deleted_toast),
                            Toast.LENGTH_SHORT
                        )
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                    selectedItems.clear()
                }
            )
        }
    }

    @Composable
    private fun CustomIpContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var appName by remember { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var showAddDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var editIp by remember { mutableStateOf<CustomIp?>(null) }
        var selectedIp by remember { mutableStateOf<CustomIp?>(null) }
        val selectedItems = remember { mutableStateListOf<CustomIp>() }
        val selectionMode by remember { derivedStateOf { selectedItems.isNotEmpty() } }

        LaunchedEffect(rules, uid) {
            if (rules == RULES.APP_SPECIFIC_RULES) {
                customIpViewModel.setUid(uid)
                appName = withContext(Dispatchers.IO) { FirewallManager.getAppNameByUid(uid) }
            }
        }

        LaunchedEffect(query) {
            customIpViewModel.setFilter(query)
        }

        val ipCount by
            (if (rules == RULES.APP_SPECIFIC_RULES) {
                    customIpViewModel.ipRulesCount(uid)
                } else {
                    customIpViewModel.allIpRulesCount()
                })
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = 0)

        val ipItems =
            (if (rules == RULES.APP_SPECIFIC_RULES) {
                    customIpViewModel.customIpDetails
                } else {
                    customIpViewModel.allIpRules
                })
                .asFlow()
                .collectAsLazyPagingItems()

        Scaffold(
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (rules == RULES.APP_SPECIFIC_RULES) {
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fab_without_border),
                            contentDescription = getString(R.string.lbl_create)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (ipCount <= 0) {
                    NoRulesContent(text = getString(R.string.ci_no_rules_text))
                } else {
                    val placeholder =
                        if (rules == RULES.APP_SPECIFIC_RULES && !appName.isNullOrEmpty()) {
                            val name = appName?.take(10) ?: ""
                            getString(
                                R.string.two_argument_colon,
                                name,
                                getString(R.string.search_universal_ips)
                            )
                        } else {
                            getString(R.string.search_universal_ips)
                        }
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onDeleteClick = { showDeleteDialog = true },
                        placeholder = placeholder
                    )
                    CustomIpList(
                        items = ipItems,
                        rules = rules,
                        selectionMode = selectionMode,
                        selectedItems = selectedItems,
                        onItemClick = { item ->
                            if (selectionMode) {
                                toggleSelection(selectedItems, item)
                            } else {
                                selectedIp = item
                            }
                        },
                        onItemLongClick = { item ->
                            toggleSelection(selectedItems, item)
                        },
                        onEditClick = { editIp = it },
                        onSeeMore = { targetUid ->
                            openAppWiseRulesActivity(
                                targetUid,
                                Tabs.IP_RULES.screen
                            )
                        }
                    )
                }
            }
        }

        selectedIp?.let { ip ->
            CustomIpRulesSheet(
                customIp = ip,
                eventLogger = eventLogger,
                onDismiss = { selectedIp = null },
                onDeleted = { selectedIp = null }
            )
        }

        if (showAddDialog) {
            IpRuleDialog(
                title = getString(R.string.ci_dialog_title),
                initialIp = "",
                uid = uid,
                onDismiss = { showAddDialog = false },
                onConfirm = { ip, port, status ->
                    scope.launch {
                        insertCustomIp(ip, port, status)
                        showAddDialog = false
                    }
                }
            )
        }

        editIp?.let { ip ->
            val initialValue =
                if (ip.port != 0) {
                    IpRulesManager.joinIpNetPort(ip.ipAddress, ip.port)
                } else {
                    ip.ipAddress
                }
            IpRuleDialog(
                title = getString(R.string.ci_dialog_title),
                initialIp = initialValue,
                uid = ip.uid,
                onDismiss = { editIp = null },
                onConfirm = { ipAddr, port, status ->
                    scope.launch {
                        updateCustomIp(ip, ipAddr, port, status)
                        editIp = null
                    }
                }
            )
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = getString(R.string.univ_delete_firewall_dialog_title),
                message = getString(R.string.univ_delete_firewall_dialog_message),
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        if (selectedItems.isNotEmpty()) {
                            IpRulesManager.deleteRules(selectedItems)
                            selectedItems.clear()
                            logIpEvent("Deleted IP rules: $selectedItems")
                        } else {
                            if (rules == RULES.APP_SPECIFIC_RULES) {
                                IpRulesManager.deleteRulesByUid(uid)
                                logIpEvent("Deleted all IP rules for UID: $uid")
                            } else {
                                IpRulesManager.deleteAllAppsRules()
                                logIpEvent("Deleted all IP rules for all apps")
                            }
                        }
                        Utilities.showToastUiCentered(
                            this@CustomRulesActivity,
                            getString(R.string.univ_ip_delete_toast_success),
                            Toast.LENGTH_SHORT
                        )
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                    selectedItems.clear()
                }
            )
        }
    }

    @Composable
    private fun SearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        onDeleteClick: () -> Unit,
        placeholder: String
    ) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = placeholder) },
                    singleLine = true
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = getString(R.string.lbl_delete)
                    )
                }
            }
        }
    }

    @Composable
    private fun NoRulesContent(text: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(id = R.drawable.illustrations_no_record),
                contentDescription = null,
                modifier = Modifier.size(220.dp)
            )
        }
    }

    @Composable
    private fun CustomDomainList(
        items: LazyPagingItems<CustomDomain>,
        rules: RULES,
        selectionMode: Boolean,
        selectedItems: MutableList<CustomDomain>,
        onItemClick: (CustomDomain) -> Unit,
        onItemLongClick: (CustomDomain) -> Unit,
        onEditClick: (CustomDomain) -> Unit,
        onSeeMore: (Int) -> Unit
    ) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items.itemSnapshotList.items, key = { _, item -> "${item.domain}:${item.uid}" }) { index, item ->
                val showHeader =
                    rules == RULES.ALL_RULES &&
                        (index == 0 ||
                            items.itemSnapshotList.items.getOrNull(index - 1)?.uid != item.uid)
                if (showHeader) {
                    CustomRulesHeader(
                        uid = item.uid,
                        onSeeMore = onSeeMore
                    )
                }
                CustomDomainRow(
                    item = item,
                    selectionMode = selectionMode,
                    selected = selectedItems.contains(item),
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onEditClick = { onEditClick(item) }
                )
            }
        }
    }

    @Composable
    private fun CustomIpList(
        items: LazyPagingItems<CustomIp>,
        rules: RULES,
        selectionMode: Boolean,
        selectedItems: MutableList<CustomIp>,
        onItemClick: (CustomIp) -> Unit,
        onItemLongClick: (CustomIp) -> Unit,
        onEditClick: (CustomIp) -> Unit,
        onSeeMore: (Int) -> Unit
    ) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items.itemSnapshotList.items, key = { _, item -> "${item.ipAddress}:${item.uid}:${item.port}" }) { index, item ->
                val showHeader =
                    rules == RULES.ALL_RULES &&
                        (index == 0 ||
                            items.itemSnapshotList.items.getOrNull(index - 1)?.uid != item.uid)
                if (showHeader) {
                    CustomRulesHeader(
                        uid = item.uid,
                        onSeeMore = onSeeMore
                    )
                }
                CustomIpRow(
                    item = item,
                    selectionMode = selectionMode,
                    selected = selectedItems.contains(item),
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onEditClick = { onEditClick(item) }
                )
            }
        }
    }

    @Composable
    private fun CustomRulesHeader(uid: Int, onSeeMore: (Int) -> Unit) {
        val context = LocalContext.current
        var appName by remember(uid) { mutableStateOf("") }
        var appIcon by remember(uid) { mutableStateOf<Drawable?>(null) }

        LaunchedEffect(uid) {
            val (resolvedName, drawable) =
                withContext(Dispatchers.IO) {
                    val appNames = FirewallManager.getAppNamesByUid(uid)
                    val resolved = getAppName(context, uid, appNames)
                    val appInfo = FirewallManager.getAppInfoByUid(uid)
                    val icon =
                        Utilities.getIcon(
                            context,
                            appInfo?.packageName ?: "",
                            appInfo?.appName ?: ""
                        )
                    resolved to icon
                }
            appName = resolvedName
            appIcon = drawable
        }

        Row(
            modifier = Modifier
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = appIcon, size = 32.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSeeMore(uid) }) {
                Text(text = getString(R.string.ssv_see_more))
            }
        }
    }

    @Composable
    private fun CustomDomainRow(
        item: CustomDomain,
        selectionMode: Boolean,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onEditClick: () -> Unit
    ) {
        val context = LocalContext.current
        val status = DomainRulesManager.Status.getStatus(item.status)
        val statusUi = remember(item.status, item.modifiedTs) {
            domainStatusUi(context, status, item.modifiedTs)
        }
        val chipColors = domainStatusColors(context, status)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.domain, style = MaterialTheme.typography.bodyLarge)
                Text(text = statusUi.statusText, style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(
                label = statusUi.statusInitial,
                background = chipColors.bg,
                foreground = chipColors.text
            )
            IconButton(onClick = onEditClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit_icon_grey),
                    contentDescription = null
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_down_small),
                contentDescription = null
            )
        }
    }

    @Composable
    private fun CustomIpRow(
        item: CustomIp,
        selectionMode: Boolean,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onEditClick: () -> Unit
    ) {
        val context = LocalContext.current
        val status = IpRulesManager.IpRuleStatus.getStatus(item.status)
        val statusUi = remember(item.status, item.modifiedDateTime) {
            ipStatusUi(context, status, item.modifiedDateTime)
        }
        val chipColors = ipStatusColors(context, status)
        val flag = remember(item.ipAddress) { ipFlag(context, item) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = flag, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.getString(
                        R.string.ci_ip_label,
                        item.ipAddress,
                        item.port.toString()
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(text = statusUi.statusText, style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(
                label = statusUi.statusInitial,
                background = chipColors.bg,
                foreground = chipColors.text
            )
            IconButton(onClick = onEditClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit_icon_grey),
                    contentDescription = null
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_down_small),
                contentDescription = null
            )
        }
    }

    @Composable
    private fun StatusChip(label: String, background: Color, foreground: Color) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(background, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = label, color = foreground, style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun domainStatusUi(
        context: Context,
        status: DomainRulesManager.Status,
        modifiedTs: Long
    ): StatusUi {
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - modifiedTs
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        return when (status) {
            DomainRulesManager.Status.TRUST ->
                StatusUi(
                    statusInitial = context.getString(R.string.ci_trust_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.ci_trust_txt),
                            time
                        )
                )
            DomainRulesManager.Status.BLOCK ->
                StatusUi(
                    statusInitial = context.getString(R.string.cd_blocked_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.lbl_blocked),
                            time
                        )
                )
            DomainRulesManager.Status.NONE ->
                StatusUi(
                    statusInitial = context.getString(R.string.cd_no_rule_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.cd_no_rule_txt),
                            time
                        )
                )
        }
    }

    private fun ipStatusUi(
        context: Context,
        status: IpRulesManager.IpRuleStatus,
        modifiedTs: Long
    ): StatusUi {
        val now = System.currentTimeMillis()
        val uptime = System.currentTimeMillis() - modifiedTs
        val time =
            DateUtils.getRelativeTimeSpanString(
                now - uptime,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        return when (status) {
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL ->
                StatusUi(
                    statusInitial = context.getString(R.string.ci_bypass_universal_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.ci_bypass_universal_txt),
                            time
                        )
                )
            IpRulesManager.IpRuleStatus.BLOCK ->
                StatusUi(
                    statusInitial = context.getString(R.string.ci_blocked_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.lbl_blocked),
                            time
                        )
                )
            IpRulesManager.IpRuleStatus.NONE ->
                StatusUi(
                    statusInitial = context.getString(R.string.ci_no_rule_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.ci_no_rule_txt),
                            time
                        )
                )
            IpRulesManager.IpRuleStatus.TRUST ->
                StatusUi(
                    statusInitial = context.getString(R.string.ci_trust_initial),
                    statusText =
                        context.getString(
                            R.string.ci_desc,
                            context.getString(R.string.ci_trust_txt),
                            time
                        )
                )
        }
    }

    private fun domainStatusColors(
        context: Context,
        status: DomainRulesManager.Status
    ): StatusColors {
        return when (status) {
            DomainRulesManager.Status.NONE ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextNeutral)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorNeutral))
                )
            DomainRulesManager.Status.BLOCK ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextNegative)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorNegative))
                )
            DomainRulesManager.Status.TRUST ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextPositive)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorPositive))
                )
        }
    }

    private fun ipStatusColors(
        context: Context,
        status: IpRulesManager.IpRuleStatus
    ): StatusColors {
        return when (status) {
            IpRulesManager.IpRuleStatus.NONE ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextNeutral)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorNeutral))
                )
            IpRulesManager.IpRuleStatus.BLOCK ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextNegative)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorNegative))
                )
            IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL,
            IpRulesManager.IpRuleStatus.TRUST ->
                StatusColors(
                    text = Color(fetchColor(context, R.attr.chipTextPositive)),
                    bg = Color(fetchColor(context, R.attr.chipBgColorPositive))
                )
        }
    }

    private fun ipFlag(context: Context, ip: CustomIp): String {
        if (ip.wildcard) return ""
        val inetAddr = try {
            IPAddressString(ip.ipAddress).hostAddress.toInetAddress()
        } catch (e: Exception) {
            null
        }
        return Utilities.getFlag(Utilities.getCountryCode(inetAddr, context))
    }

    @Composable
    private fun AppIcon(drawable: Drawable?, size: androidx.compose.ui.unit.Dp) {
        val painter =
            remember(drawable) {
                drawable?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
            }
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.default_app_icon),
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        }
    }

    @Composable
    private fun DomainRuleDialog(
        title: String,
        initialDomain: String,
        initialType: DomainRulesManager.DomainType,
        onDismiss: () -> Unit,
        onConfirm: (String, DomainRulesManager.DomainType, DomainRulesManager.Status) -> Unit
    ) {
        var domain by remember(initialDomain) { mutableStateOf(initialDomain) }
        var selectedType by remember(initialType) { mutableStateOf(initialType) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        singleLine = true,
                        label = { Text(text = getString(R.string.lbl_domain)) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DomainTypeChip(
                            label = getString(R.string.lbl_domain),
                            selected = selectedType == DomainRulesManager.DomainType.DOMAIN,
                            onClick = { selectedType = DomainRulesManager.DomainType.DOMAIN }
                        )
                        DomainTypeChip(
                            label = getString(R.string.lbl_wildcard),
                            selected = selectedType == DomainRulesManager.DomainType.WILDCARD,
                            onClick = { selectedType = DomainRulesManager.DomainType.WILDCARD }
                        )
                    }
                    if (!errorText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorText ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            val result = validateDomain(domain, selectedType)
                            if (result.error != null) {
                                errorText = result.error
                                return@TextButton
                            }
                            onConfirm(result.value, selectedType, DomainRulesManager.Status.BLOCK)
                        }
                    ) {
                        Text(text = getString(R.string.lbl_blocked))
                    }
                    TextButton(
                        onClick = {
                            val result = validateDomain(domain, selectedType)
                            if (result.error != null) {
                                errorText = result.error
                                return@TextButton
                            }
                            onConfirm(result.value, selectedType, DomainRulesManager.Status.TRUST)
                        }
                    ) {
                        Text(text = getString(R.string.ci_trust_rule))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
            }
        )
    }

    @Composable
    private fun DomainTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
        val bg =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        val fg =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        TextButton(onClick = onClick) {
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = label, color = fg)
            }
        }
    }

    @Composable
    private fun IpRuleDialog(
        title: String,
        initialIp: String,
        uid: Int,
        onDismiss: () -> Unit,
        onConfirm: (IPAddress, Int?, IpRulesManager.IpRuleStatus) -> Unit
    ) {
        val scope = rememberCoroutineScope()
        var ipInput by remember(initialIp) { mutableStateOf(initialIp) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = {
                            ipInput = it
                            errorText = null
                        },
                        singleLine = true,
                        label = { Text(text = getString(R.string.lbl_ip)) }
                    )
                    if (!errorText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorText ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            validateIpInput(scope, ipInput) { result ->
                                if (result.error != null) {
                                    errorText = result.error
                                    return@validateIpInput
                                }
                                val ipValue = result.value ?: return@validateIpInput
                                onConfirm(
                                    ipValue,
                                    result.port,
                                    IpRulesManager.IpRuleStatus.BLOCK
                                )
                            }
                        }
                    ) {
                        Text(text = getString(R.string.lbl_blocked))
                    }
                    val trustLabel =
                        if (uid == UID_EVERYBODY) {
                            getString(R.string.bypass_universal)
                        } else {
                            getString(R.string.ci_trust_rule)
                        }
                    TextButton(
                        onClick = {
                            validateIpInput(scope, ipInput) { result ->
                                if (result.error != null) {
                                    errorText = result.error
                                    return@validateIpInput
                                }
                                val ipValue = result.value ?: return@validateIpInput
                                val status =
                                    if (uid == UID_EVERYBODY) {
                                        IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL
                                    } else {
                                        IpRulesManager.IpRuleStatus.TRUST
                                    }
                                onConfirm(ipValue, result.port, status)
                            }
                        }
                    ) {
                        Text(text = trustLabel)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
            }
        )
    }

    private fun validateDomain(
        input: String,
        selectedType: DomainRulesManager.DomainType
    ): ValidationResult {
        val url = input.trim()
        val extractedHost = DomainRulesManager.extractHost(url)
        if (extractedHost.isNullOrBlank()) {
            return ValidationResult(
                error = getString(R.string.cd_dialog_error_invalid_domain)
            )
        }

        when (selectedType) {
            DomainRulesManager.DomainType.WILDCARD -> {
                if (!isWildCardEntry(extractedHost)) {
                    return ValidationResult(
                        error = getString(R.string.cd_dialog_error_invalid_wildcard)
                    )
                }
            }
            DomainRulesManager.DomainType.DOMAIN -> {
                if (!isValidDomain(extractedHost)) {
                    return ValidationResult(
                        error = getString(R.string.cd_dialog_error_invalid_domain)
                    )
                }
            }
        }
        val cleaned = removeLeadingAndTrailingDots(extractedHost)
        return ValidationResult(value = cleaned)
    }

    private fun validateIpInput(
        scope: CoroutineScope,
        ipInput: String,
        onResult: (IpValidationResult) -> Unit
    ) {
        scope.launch {
            val ipString = removeLeadingAndTrailingDots(ipInput)
            val result =
                withContext(Dispatchers.IO) {
                    val ipPair = IpRulesManager.getIpNetPort(ipString)
                    if (ipPair.first == null || ipString.isBlank()) {
                        IpValidationResult(
                            error = getString(R.string.ci_dialog_error_invalid_ip)
                        )
                    } else {
                        IpValidationResult(value = ipPair.first, port = ipPair.second)
                    }
                }
            onResult(result)
        }
    }

    private fun insertDomain(
        domain: String,
        type: DomainRulesManager.DomainType,
        status: DomainRulesManager.Status
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            DomainRulesManager.addDomainRule(domain, status, type, uid = uid)
            logDomainEvent("Added domain: $domain, Type: $type, Status: $status, UID: $uid")
        }
        Utilities.showToastUiCentered(
            this,
            resources.getString(R.string.cd_toast_added),
            Toast.LENGTH_SHORT
        )
    }

    private fun updateDomain(
        domain: String,
        type: DomainRulesManager.DomainType,
        prevDomain: CustomDomain,
        status: DomainRulesManager.Status
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            DomainRulesManager.updateDomainRule(domain, status, type, prevDomain)
            logDomainEvent("Custom domain insert/update $domain, status: $status")
        }
        Utilities.showToastUiCentered(
            this,
            resources.getString(R.string.cd_toast_edit),
            Toast.LENGTH_SHORT
        )
    }

    private fun insertCustomIp(ip: IPAddress, port: Int?, status: IpRulesManager.IpRuleStatus) {
        lifecycleScope.launch(Dispatchers.IO) {
            IpRulesManager.addIpRule(uid, ip, port, status, proxyId = "", proxyCC = "")
            logIpEvent("Added IP rule: $ip, Port: $port, Status: $status, UID: $uid")
        }
        Utilities.showToastUiCentered(
            this,
            getString(R.string.ci_dialog_added_success),
            Toast.LENGTH_SHORT
        )
    }

    private fun updateCustomIp(
        prev: CustomIp,
        ip: IPAddress,
        port: Int?,
        status: IpRulesManager.IpRuleStatus
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            IpRulesManager.replaceIpRule(prev, ip, port, status, "", "")
            logIpEvent("Updated Custom IP rule: Prev[$prev], New[IP: $ip, Port: ${port ?: "0"}, Status: $status]")
        }
    }

    private fun logDomainEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom Domain", EventSource.UI, false, details)
    }

    private fun logIpEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom IP", EventSource.UI, false, details)
    }

    private fun openAppWiseRulesActivity(uid: Int, tab: Int) {
        val intent = Intent(this, CustomRulesActivity::class.java)
        intent.putExtra(Constants.VIEW_PAGER_SCREEN_TO_LOAD, tab)
        intent.putExtra(Constants.INTENT_UID, uid)
        startActivity(intent)
    }

    private fun toggleSelection(list: MutableList<CustomDomain>, item: CustomDomain) {
        if (list.contains(item)) {
            list.remove(item)
        } else {
            list.add(item)
        }
    }

    private fun toggleSelection(list: MutableList<CustomIp>, item: CustomIp) {
        if (list.contains(item)) {
            list.remove(item)
        } else {
            list.add(item)
        }
    }

    private fun getAppName(context: Context, uid: Int, appNames: List<String>): String {
        if (uid == UID_EVERYBODY) {
            return context
                .getString(R.string.firewall_act_universal_tab)
                .replaceFirstChar(Char::titlecase)
        }

        if (appNames.isEmpty()) {
            return context.getString(R.string.network_log_app_name_unknown) + " ($uid)"
        }

        val packageCount = appNames.count()
        return if (packageCount >= 2) {
            context.getString(
                R.string.ctbs_app_other_apps,
                appNames[0],
                packageCount.minus(1).toString()
            )
        } else {
            appNames[0]
        }
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it == BraveVPNService.State.PAUSED) {
                startActivity(Intent().setClass(this, PauseActivity::class.java))
                finish()
            }
        }
    }

    private data class StatusUi(val statusInitial: String, val statusText: String)

    private data class StatusColors(val text: Color, val bg: Color)

    private data class ValidationResult(val value: String = "", val error: String? = null)

    private data class IpValidationResult(
        val value: IPAddress? = null,
        val port: Int? = null,
        val error: String? = null
    )

    @Composable
    private fun ConfirmDeleteDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = getString(R.string.lbl_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
            }
        )
    }
}

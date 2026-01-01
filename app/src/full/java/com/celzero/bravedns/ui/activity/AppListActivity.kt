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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.lifecycle.asFlow
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.FirewallAppListAdapter
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.RefreshDatabase
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.bottomsheet.FirewallAppFilterDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.AppInfoViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppListActivity :
    AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private val appInfoViewModel: AppInfoViewModel by viewModel()
    private val refreshDatabase by inject<RefreshDatabase>()


    private var showBypassToolTip = true

    private var queryText by mutableStateOf("")
    private var filterLabelText by mutableStateOf<CharSequence>("")
    private var selectedFirewallFilter by mutableStateOf(FirewallFilter.ALL)
    private var isRefreshing by mutableStateOf(false)

    private var bulkWifi by mutableStateOf(false)
    private var bulkMobile by mutableStateOf(false)
    private var bulkBypass by mutableStateOf(false)
    private var bulkBypassDns by mutableStateOf(false)
    private var bulkExclude by mutableStateOf(false)
    private var bulkLockdown by mutableStateOf(false)

    companion object {
        val filters = MutableLiveData<Filters>()

        private const val ANIMATION_DURATION = 750L
        private const val REFRESH_TIMEOUT: Long = 4000
        private const val QUERY_TEXT_DELAY: Long = 1000
    }

    // enum class for bulk ui update
    enum class BlockType {
        UNMETER,
        METER,
        BYPASS,
        LOCKDOWN,
        EXCLUDE,
        BYPASS_DNS_FIREWALL
    }

    enum class TopLevelFilter(val id: Int) {
        ALL(0),
        INSTALLED(1),
        SYSTEM(2);

        fun getLabel(context: Context): String {
            return when (this) {
                ALL -> {
                    // getLabel is used only to show the filtered details in ui,
                    // no need to show "all" tag.
                    ""
                }
                INSTALLED -> {
                    context.getString(R.string.fapps_filter_parent_installed)
                }
                SYSTEM -> {
                    context.getString(R.string.fapps_filter_parent_system)
                }
            }
        }
    }

    @Suppress("MagicNumber")
    enum class FirewallFilter(val id: Int) {
        ALL(0),
        ALLOWED(1),
        BLOCKED(2),
        BLOCKED_WIFI(3),
        BLOCKED_MOBILE_DATA(4),
        BYPASS(5),
        EXCLUDED(6),
        LOCKDOWN(7);

        fun getFilter(): Set<Int> {
            return when (this) {
                ALL -> setOf(0, 1, 2, 3, 4, 5, 7)
                ALLOWED -> setOf(5)
                BLOCKED_WIFI -> setOf(5)
                BLOCKED_MOBILE_DATA -> setOf(5)
                BLOCKED -> setOf(5)
                BYPASS -> setOf(2, 7)
                EXCLUDED -> setOf(3)
                LOCKDOWN -> setOf(4)
            }
        }

        fun getConnectionStatusFilter(): Set<Int> {
            return when (this) {
                ALL -> setOf(0, 1, 2, 3)
                ALLOWED -> setOf(3)
                BLOCKED_WIFI -> setOf(1)
                BLOCKED_MOBILE_DATA -> setOf(2)
                BLOCKED -> setOf(0)
                BYPASS -> setOf(0, 1, 2, 3)
                EXCLUDED -> setOf(0, 1, 2, 3)
                LOCKDOWN -> setOf(0, 1, 2, 3)
            }
        }

        fun getLabel(context: Context): String {
            return when (this) {
                ALL -> context.getString(R.string.lbl_all)
                ALLOWED -> context.getString(R.string.lbl_allowed)
                BLOCKED_WIFI -> context.getString(R.string.two_argument_colon, context.getString(R.string.lbl_blocked), context.getString(R.string.firewall_rule_block_unmetered))
                BLOCKED_MOBILE_DATA -> context.getString(R.string.two_argument_colon, context.getString(R.string.lbl_blocked), context.getString(R.string.firewall_rule_block_metered))
                BLOCKED -> context.getString(R.string.lbl_blocked)
                BYPASS -> context.getString(R.string.fapps_firewall_filter_bypass_universal)
                EXCLUDED -> context.getString(R.string.fapps_firewall_filter_excluded)
                LOCKDOWN -> context.getString(R.string.fapps_firewall_filter_isolate)
            }
        }

        companion object {
            fun filter(id: Int): FirewallFilter {
                return when (id) {
                    ALL.id -> ALL
                    ALLOWED.id -> ALLOWED
                    BLOCKED_WIFI.id -> BLOCKED_WIFI
                    BLOCKED_MOBILE_DATA.id -> BLOCKED_MOBILE_DATA
                    BLOCKED.id -> BLOCKED
                    BYPASS.id -> BYPASS
                    EXCLUDED.id -> EXCLUDED
                    LOCKDOWN.id -> LOCKDOWN
                    else -> ALL
                }
            }
        }
    }

    class Filters {
        var categoryFilters: MutableSet<String> = mutableSetOf()
        var topLevelFilter = TopLevelFilter.ALL
        var firewallFilter = FirewallFilter.ALL
        var searchString: String = ""
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

        filters.value = Filters()
        initObserver()
        setQueryFilter()

        setContent {
            RethinkTheme {
                AppListScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        filters.value = filters.value ?: Filters()
    }

    private fun initObserver() {
        filters.observe(this) {
            if (it == null) return@observe

            appInfoViewModel.setFilter(it)
            updateFilterText(it)
            selectedFirewallFilter = it.firewallFilter
            queryText = it.searchString
        }
    }

    private fun updateFilterText(filter: Filters) {
        val filterLabel = filter.topLevelFilter.getLabel(this)
        val firewallLabel = filter.firewallFilter.getLabel(this)
        if (filter.categoryFilters.isEmpty()) {
            filterLabelText =
                UIUtils.htmlToSpannedText(
                    getString(
                        R.string.fapps_firewall_filter_desc,
                        firewallLabel.lowercase(),
                        filterLabel))
        } else {
            filterLabelText =
                UIUtils.htmlToSpannedText(
                    getString(
                        R.string.fapps_firewall_filter_desc_category,
                        firewallLabel.lowercase(),
                        filterLabel,
                        filter.categoryFilters))
        }
    }

    val searchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private fun setQueryFilter() {
        lifecycleScope.launch {
            searchQuery
                .debounce(QUERY_TEXT_DELAY)
                .distinctUntilChanged()
                .collect { query ->
                    addQueryToFilters(query)
                }
        }
    }

    private fun addQueryToFilters(query: String) {
        if (filters.value == null) {
            val f = Filters()
            f.searchString = query
            filters.postValue(f)
            return
        }

        filters.value?.searchString = query
        filters.postValue(filters.value)
    }

    private fun getBulkActionDialogTitle(type: BlockType): String {
        return when (type) {
            BlockType.UNMETER -> {
                if (!bulkWifi) {
                    getString(R.string.fapps_unmetered_block_dialog_title)
                } else {
                    getString(R.string.fapps_unmetered_unblock_dialog_title)
                }
            }
            BlockType.METER -> {
                if (!bulkMobile) {
                    getString(R.string.fapps_metered_block_dialog_title)
                } else {
                    getString(R.string.fapps_metered_unblock_dialog_title)
                }
            }
            BlockType.LOCKDOWN -> {
                if (!bulkLockdown) {
                    getString(R.string.fapps_isolate_block_dialog_title)
                } else {
                    getString(R.string.fapps_unblock_dialog_title)
                }
            }
            BlockType.BYPASS -> {
                if (!bulkBypass) {
                    getString(R.string.fapps_bypass_block_dialog_title)
                } else {
                    getString(R.string.fapps_unblock_dialog_title)
                }
            }
            BlockType.EXCLUDE -> {
                if (!bulkExclude) {
                    getString(R.string.fapps_exclude_block_dialog_title)
                } else {
                    getString(R.string.fapps_unblock_dialog_title)
                }
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                if (!bulkBypassDns) {
                    getString(R.string.fapps_bypass_dns_firewall_dialog_title)
                } else {
                    getString(R.string.fapps_unblock_dialog_title)
                }
            }
        }
    }

    private fun getBulkActionDialogMessage(type: BlockType): String {
        return when (type) {
            BlockType.UNMETER -> {
                if (!bulkWifi) {
                    getString(R.string.fapps_unmetered_block_dialog_message)
                } else {
                    getString(R.string.fapps_unmetered_unblock_dialog_message)
                }
            }
            BlockType.METER -> {
                if (!bulkMobile) {
                    getString(R.string.fapps_metered_block_dialog_message)
                } else {
                    getString(R.string.fapps_metered_unblock_dialog_message)
                }
            }
            BlockType.LOCKDOWN -> {
                if (!bulkLockdown) {
                    getString(R.string.fapps_isolate_block_dialog_message)
                } else {
                    getString(R.string.fapps_unblock_dialog_message)
                }
            }
            BlockType.BYPASS -> {
                if (!bulkBypass) {
                    getString(R.string.fapps_bypass_block_dialog_message)
                } else {
                    getString(R.string.fapps_unblock_dialog_message)
                }
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                if (!bulkBypassDns) {
                    getString(R.string.fapps_bypass_dns_firewall_dialog_message)
                } else {
                    getString(R.string.fapps_unblock_dialog_message)
                }
            }
            BlockType.EXCLUDE -> {
                if (!bulkExclude) {
                    getString(R.string.fapps_exclude_block_dialog_message)
                } else {
                    getString(R.string.fapps_unblock_dialog_message)
                }
            }
        }
    }

    private fun showBulkRulesUpdateDialog(title: String, message: String, type: BlockType) {
        val builder =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.lbl_apply)) { _, _ -> updateBulkRules(type) }
                .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
                .setCancelable(true)

        builder.create().show()
    }

    private fun updateBulkRules(type: BlockType) {
        when (type) {
            BlockType.UNMETER -> {
                updateUnmeteredBulk()
            }
            BlockType.METER -> {
                updateMeteredBulk()
            }
            BlockType.LOCKDOWN -> {
                updateLockdownBulk()
            }
            BlockType.BYPASS -> {
                updateBypassBulk()
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                updateBypassDnsFirewallBulk()
            }
            BlockType.EXCLUDE -> {
                updateExcludedBulk()
            }
        }
    }

    private fun showInfoDialog() {
        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                FirewallInfoDialogContent()
            }
        }
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(composeView)
        builder.setPositiveButton(getString(R.string.fapps_info_dialog_positive_btn)) { dialog, _ ->
            dialog.dismiss()
        }
        builder.setCancelable(true)
        builder.create().show()
    }

    @Composable
    private fun FirewallInfoDialogContent() {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = getString(R.string.fapps_info_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(fetchColor(this@AppListActivity, R.attr.primaryTextColor))
            )

            Spacer(modifier = Modifier.height(2.dp))

            Spacer(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(1.dp)
                        .background(Color(fetchColor(this@AppListActivity, R.attr.primaryLightColorText)))
            )

            InfoRow(
                icon = R.drawable.ic_firewall_wifi_on_grey,
                text = getString(R.string.fapps_info_unmetered_msg)
            )
            InfoRow(
                icon = R.drawable.ic_firewall_data_on_grey,
                text = getString(R.string.fapps_info_metered_msg)
            )
            InfoRow(
                icon = R.drawable.ic_firewall_bypass_off,
                text = getString(R.string.fapps_info_bypass_msg)
            )
            InfoRow(
                icon = R.drawable.ic_bypass_dns_firewall_off,
                text = getString(R.string.fapps_info_bypass_dns_firewall_msg)
            )
            InfoRow(
                icon = R.drawable.ic_firewall_exclude_off,
                text = getString(R.string.fapps_info_exclude_msg)
            )
            InfoRow(
                icon = R.drawable.ic_firewall_lockdown_off,
                text = getString(R.string.fapps_info_isolate_msg)
            )
        }
    }

    @Composable
    private fun InfoRow(icon: Int, text: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(fetchColor(this@AppListActivity, R.attr.primaryTextColor))
            )
        }
    }

    private fun applyFirewallFilter(firewallFilter: FirewallFilter) {
        if (filters.value == null) {
            val f = Filters()
            f.firewallFilter = firewallFilter
            filters.postValue(f)
            return
        }

        filters.value?.firewallFilter = firewallFilter
        filters.postValue(filters.value)
    }

    private fun updateMeteredBulk() {
        val metered = !bulkMobile
        bulkMobile = metered
        resetBulkStates(BlockType.METER)
        io { appInfoViewModel.updateMeteredStatus(metered) }
        logEvent("Bulk metered rule update performed, isMetered: $metered")
    }

    private fun updateUnmeteredBulk() {
        val unmeter = !bulkWifi
        bulkWifi = unmeter
        resetBulkStates(BlockType.UNMETER)
        io { appInfoViewModel.updateUnmeteredStatus(unmeter) }
        logEvent("Bulk unmetered rule update performed, isUnmetered: $unmeter")
    }

    private fun updateBypassBulk() {
        val bypass = !bulkBypass
        bulkBypass = bypass
        resetBulkStates(BlockType.BYPASS)
        io { appInfoViewModel.updateBypassStatus(bypass) }
        logEvent("Bulk bypass rule update performed, isBypass: $bypass")
    }

    private fun updateBypassDnsFirewallBulk() {
        val bypassDnsFirewall = !bulkBypassDns
        bulkBypassDns = bypassDnsFirewall
        resetBulkStates(BlockType.BYPASS_DNS_FIREWALL)
        io { appInfoViewModel.updateBypassDnsFirewall(bypassDnsFirewall) }
        logEvent("Bulk bypass DNS firewall rule update performed, isBypassDnsFirewall: $bypassDnsFirewall")
    }

    private fun updateExcludedBulk() {
        val exclude = !bulkExclude
        bulkExclude = exclude
        resetBulkStates(BlockType.EXCLUDE)
        io { appInfoViewModel.updateExcludeStatus(exclude) }
        logEvent("Bulk exclude rule update performed, isExclude: $exclude")
    }

    private fun updateLockdownBulk() {
        val lockdown = !bulkLockdown
        bulkLockdown = lockdown
        resetBulkStates(BlockType.LOCKDOWN)
        io { appInfoViewModel.updateLockdownStatus(lockdown) }
        logEvent("Bulk lockdown rule update performed, isLockdown: $lockdown")
    }

    private fun resetBulkStates(type: BlockType) {
        when (type) {
            BlockType.UNMETER -> {
                bulkMobile = false
                bulkBypass = false
                bulkBypassDns = false
                bulkExclude = false
                bulkLockdown = false
            }
            BlockType.METER -> {
                bulkWifi = false
                bulkBypass = false
                bulkBypassDns = false
                bulkExclude = false
                bulkLockdown = false
            }
            BlockType.LOCKDOWN -> {
                bulkWifi = false
                bulkMobile = false
                bulkBypass = false
                bulkBypassDns = false
                bulkExclude = false
            }
            BlockType.BYPASS -> {
                bulkWifi = false
                bulkMobile = false
                bulkBypassDns = false
                bulkExclude = false
                bulkLockdown = false
            }
            BlockType.BYPASS_DNS_FIREWALL -> {
                bulkWifi = false
                bulkMobile = false
                bulkBypass = false
                bulkExclude = false
                bulkLockdown = false
            }
            BlockType.EXCLUDE -> {
                bulkWifi = false
                bulkMobile = false
                bulkBypass = false
                bulkBypassDns = false
                bulkLockdown = false
            }
        }
    }

    private fun openFilterBottomSheet() {
        FirewallAppFilterDialog(this).show()
    }

    private fun refreshDatabase() {
        io { refreshDatabase.refresh(RefreshDatabase.ACTION_REFRESH_INTERACTIVE) }
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "App list, bulk change", EventSource.UI, false, details)
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    @Composable
    private fun AppListScreen() {
        val refreshRotation = rememberInfiniteTransition(label = "refresh").animateFloat(
            initialValue = 0f,
            targetValue = if (isRefreshing) 360f else 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = ANIMATION_DURATION.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshRotation"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = {
                                queryText = it
                                searchQuery.value = it
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(text = stringResourceCompat(R.string.search_firewall_all_apps)) }
                        )
                        IconButton(
                            onClick = { refreshAppList() },
                            enabled = !isRefreshing
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_refresh_white),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(if (isRefreshing) refreshRotation.value else 0f)
                            )
                        }
                        IconButton(onClick = { openFilterBottomSheet() }) {
                            Image(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(FirewallFilter.entries.size) { index ->
                            val filter = FirewallFilter.entries[index]
                            val label = filter.getLabel(this@AppListActivity)
                            if (label.isNotEmpty()) {
                                FilterChip(
                                    selected = selectedFirewallFilter == filter,
                                    onClick = { applyFirewallFilter(filter) },
                                    label = label
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FilterLabelText()
                }
            }
            BulkToggleRow()
            AppListRecycler()
        }
    }

    @Composable
    private fun FilterLabelText() {
        val context = LocalContext.current
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.TextView(ctx).apply {
                    isSelected = true
                    setTextColor(fetchColor(ctx, R.attr.primaryTextColor))
                }
            },
            update = { view ->
                view.text = filterLabelText
                view.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.default_font_text_view)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }

    @Composable
    private fun BulkToggleRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showInfoDialog() }) {
                Image(
                    painter = painterResource(R.drawable.ic_info_white),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            BulkIconButton(
                icon = if (bulkLockdown) R.drawable.ic_firewall_lockdown_on else R.drawable.ic_firewall_lockdown_off,
                onClick = {
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.LOCKDOWN),
                        getBulkActionDialogMessage(BlockType.LOCKDOWN),
                        BlockType.LOCKDOWN
                    )
                }
            )
            BulkIconButton(
                icon = if (bulkExclude) R.drawable.ic_firewall_exclude_on else R.drawable.ic_firewall_exclude_off,
                onClick = {
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.EXCLUDE),
                        getBulkActionDialogMessage(BlockType.EXCLUDE),
                        BlockType.EXCLUDE
                    )
                }
            )
            BulkIconButton(
                icon = if (bulkBypass) R.drawable.ic_firewall_bypass_on else R.drawable.ic_firewall_bypass_off,
                onClick = {
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.BYPASS),
                        getBulkActionDialogMessage(BlockType.BYPASS),
                        BlockType.BYPASS
                    )
                }
            )
            BulkIconButton(
                icon = if (bulkBypassDns) R.drawable.ic_bypass_dns_firewall_on else R.drawable.ic_bypass_dns_firewall_off,
                onClick = {
                    if (showBypassToolTip) {
                        showBypassToolTip = false
                        Utilities.showToastUiCentered(
                            this@AppListActivity,
                            getString(R.string.bypass_dns_firewall_tooltip, getString(R.string.bypass_dns_firewall)),
                            Toast.LENGTH_SHORT
                        )
                        return@BulkIconButton
                    }
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.BYPASS_DNS_FIREWALL),
                        getBulkActionDialogMessage(BlockType.BYPASS_DNS_FIREWALL),
                        BlockType.BYPASS_DNS_FIREWALL
                    )
                }
            )
            BulkIconButton(
                icon = if (bulkWifi) R.drawable.ic_firewall_wifi_off else R.drawable.ic_firewall_wifi_on_grey,
                onClick = {
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.UNMETER),
                        getBulkActionDialogMessage(BlockType.UNMETER),
                        BlockType.UNMETER
                    )
                }
            )
            BulkIconButton(
                icon = if (bulkMobile) R.drawable.ic_firewall_data_off else R.drawable.ic_firewall_data_on_grey,
                onClick = {
                    showBulkRulesUpdateDialog(
                        getBulkActionDialogTitle(BlockType.METER),
                        getBulkActionDialogMessage(BlockType.METER),
                        BlockType.METER
                    )
                }
            )
        }
    }

    @Composable
    private fun BulkIconButton(icon: Int, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    @Composable
    private fun AppListRecycler() {
        val adapter = remember { FirewallAppListAdapter(this@AppListActivity, this@AppListActivity, eventLogger) }
        val items = appInfoViewModel.appInfo.asFlow().collectAsLazyPagingItems()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items.itemCount) { index ->
                val item = items[index] ?: return@items
                adapter.FirewallAppRow(item)
            }
        }
    }

    private fun refreshAppList() {
        isRefreshing = true
        refreshDatabase()
        Utilities.delay(REFRESH_TIMEOUT, lifecycleScope) {
            if (!this.isFinishing) {
                isRefreshing = false
                Utilities.showToastUiCentered(
                    this, getString(R.string.refresh_complete), Toast.LENGTH_SHORT)
            }
        }
    }

    @Composable
    private fun FilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
        androidx.compose.material3.FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text = label) }
        )
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}

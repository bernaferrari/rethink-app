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

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConnectionTrackerAdapter
import com.celzero.bravedns.adapter.DnsLogAdapter
import com.celzero.bravedns.adapter.RethinkLogAdapter
import com.celzero.bravedns.adapter.WgNwStatsAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.data.DataUsageSummary
import com.celzero.bravedns.database.ConnectionTrackerRepository
import com.celzero.bravedns.database.DnsLogRepository
import com.celzero.bravedns.database.RethinkLogRepository
import com.celzero.bravedns.databinding.FragmentConnectionTrackerBinding
import com.celzero.bravedns.databinding.FragmentDnsLogsBinding
import com.celzero.bravedns.databinding.FragmentWgNwStatsBinding
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.FirewallRuleset
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.formatToRelativeTime
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel.TopLevelFilter
import com.celzero.bravedns.viewmodel.DnsLogViewModel
import com.celzero.bravedns.viewmodel.RethinkLogViewModel
import com.celzero.bravedns.viewmodel.WgNwActivityViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class NetworkLogsActivity : AppCompatActivity() {
    private var fragmentIndex = 0
    private var searchParam = ""
    private var isUnivNavigated = false
    private var isWireGuardLogs = false

    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val connectionTrackerRepository by inject<ConnectionTrackerRepository>()
    private val dnsLogRepository by inject<DnsLogRepository>()
    private val rethinkLogRepository by inject<RethinkLogRepository>()

    private val connectionTrackerViewModel: ConnectionTrackerViewModel by viewModel()
    private val dnsLogViewModel: DnsLogViewModel by viewModel()
    private val rethinkLogViewModel: RethinkLogViewModel by viewModel()
    private val wgNwActivityViewModel: WgNwActivityViewModel by viewModel()

    private var connectionBinding: FragmentConnectionTrackerBinding? = null
    private var dnsBinding: FragmentDnsLogsBinding? = null
    private var rethinkBinding: FragmentConnectionTrackerBinding? = null
    private var wgBinding: FragmentWgNwStatsBinding? = null

    private var connectionLayoutManager: RecyclerView.LayoutManager? = null
    private var dnsLayoutManager: RecyclerView.LayoutManager? = null

    private var connectionFilterQuery: String = ""
    private var connectionFilterCategories: MutableSet<String> = mutableSetOf()
    private var connectionFilterType: TopLevelFilter = TopLevelFilter.ALL
    private val connectionSearchQuery = MutableStateFlow("")
    private var connectionQueryCollectorStarted = false
    private var connectionFromWireGuardScreen: Boolean = false
    private var connectionFromUniversalFirewallScreen: Boolean = false

    private var dnsFilterValue: String = ""
    private var dnsFilterType: DnsLogViewModel.DnsLogFilter = DnsLogViewModel.DnsLogFilter.ALL
    private val dnsSearchQuery = MutableStateFlow("")
    private var dnsQueryCollectorStarted = false
    private var dnsFromWireGuardScreen: Boolean = false

    private val rethinkSearchQuery = MutableStateFlow("")
    private var rethinkQueryCollectorStarted = false

    private var wgId: String = ""

    private var tabs: List<TabSpec> = emptyList()

    enum class LogTab {
        CONNECTION,
        DNS,
        RETHINK,
        WG_STATS
    }

    data class TabSpec(val tab: LogTab, val label: String)

    enum class Tabs(val screen: Int) {
        NETWORK_LOGS(0),
        DNS_LOGS(1),
        RETHINK_LOGS(2),
        WIREGUARD_STATS(3)
    }

    companion object {
        const val RULES_SEARCH_ID_WIREGUARD = "W:"
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
        searchParam = intent.getStringExtra(Constants.SEARCH_QUERY) ?: ""
        if (searchParam.contains(UniversalFirewallSettingsActivity.RULES_SEARCH_ID)) {
            isUnivNavigated = true
        } else if (searchParam.contains(RULES_SEARCH_ID_WIREGUARD)) {
            isWireGuardLogs = true
        }

        connectionFromUniversalFirewallScreen = isUnivNavigated
        connectionFromWireGuardScreen = isWireGuardLogs
        dnsFromWireGuardScreen = isWireGuardLogs

        if (isWireGuardLogs) {
            wgId = searchParam.substringAfter(RULES_SEARCH_ID_WIREGUARD)
        }

        tabs = buildTabs()
        if (tabs.isEmpty()) {
            tabs = listOf(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
        }

        fragmentIndex = fragmentIndex.coerceIn(0, tabs.size - 1)

        setContent {
            RethinkTheme {
                NetworkLogsContent(initialTab = fragmentIndex)
            }
        }

        observeAppState()
    }

    override fun onResume() {
        super.onResume()
        refreshSearchView(connectionBinding?.connectionSearch)
        refreshSearchView(dnsBinding?.queryListSearch)
        refreshSearchView(rethinkBinding?.connectionSearch)
        connectionBinding?.connectionListRl?.requestFocus()
        dnsBinding?.topRl?.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionBinding = null
        dnsBinding = null
        rethinkBinding = null
        wgBinding = null
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun NetworkLogsContent(initialTab: Int) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val selectedTab = remember { mutableIntStateOf(initialTab.coerceIn(0, tabs.size - 1)) }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp),
                    selectedTabIndex = selectedTab.intValue
                ) {
                    tabs.forEachIndexed { index, spec ->
                        Tab(
                            selected = selectedTab.intValue == index,
                            onClick = { selectedTab.intValue = index },
                            text = { Text(text = spec.label) }
                        )
                    }
                }
                IconButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = { openConsoleLogActivity() }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_android_icon),
                        contentDescription = null
                    )
                }
            }

            when (tabs[selectedTab.intValue].tab) {
                LogTab.CONNECTION -> ConnectionLogsContent(lifecycleOwner)
                LogTab.DNS -> DnsLogsContent(lifecycleOwner)
                LogTab.RETHINK -> RethinkLogsContent(lifecycleOwner)
                LogTab.WG_STATS -> WgStatsContent(lifecycleOwner)
            }
        }
    }

    @Composable
    private fun ConnectionLogsContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding =
                    connectionBinding
                        ?: FragmentConnectionTrackerBinding.inflate(LayoutInflater.from(context))
                if (connectionBinding == null) {
                    connectionBinding = binding
                    initConnectionView(binding, lifecycleOwner)
                }
                binding.root
            }
        )
    }

    @Composable
    private fun DnsLogsContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding = dnsBinding ?: FragmentDnsLogsBinding.inflate(LayoutInflater.from(context))
                if (dnsBinding == null) {
                    dnsBinding = binding
                    initDnsLogView(binding, lifecycleOwner)
                }
                binding.root
            }
        )
    }

    @Composable
    private fun RethinkLogsContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding =
                    rethinkBinding
                        ?: FragmentConnectionTrackerBinding.inflate(LayoutInflater.from(context))
                if (rethinkBinding == null) {
                    rethinkBinding = binding
                    initRethinkLogView(binding, lifecycleOwner)
                }
                binding.root
            }
        )
    }

    @Composable
    private fun WgStatsContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding = wgBinding ?: FragmentWgNwStatsBinding.inflate(LayoutInflater.from(context))
                if (wgBinding == null) {
                    wgBinding = binding
                    initWgStatsView(binding, lifecycleOwner)
                }
                binding.root
            }
        )
    }

    private fun refreshSearchView(searchView: SearchView?) {
        if (searchView == null) return
        searchView.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(searchView)
    }

    private fun buildTabs(): List<TabSpec> {
        if (isUnivNavigated) {
            return listOf(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
        }
        if (isWireGuardLogs) {
            return listOf(
                TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)),
                TabSpec(LogTab.DNS, getString(R.string.dns_mode_info_title)),
                TabSpec(LogTab.WG_STATS, getString(R.string.title_statistics))
            )
        }

        val tabs = mutableListOf<TabSpec>()
        val braveMode = appConfig.getBraveMode()
        when {
            braveMode.isDnsMode() -> {
                tabs.add(TabSpec(LogTab.DNS, getString(R.string.dns_mode_info_title)))
            }
            braveMode.isFirewallMode() -> {
                tabs.add(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
            }
            else -> {
                tabs.add(TabSpec(LogTab.CONNECTION, getString(R.string.firewall_act_network_monitor_tab)))
                tabs.add(TabSpec(LogTab.DNS, getString(R.string.dns_mode_info_title)))
            }
        }

        if (persistentState.routeRethinkInRethink) {
            tabs.add(TabSpec(LogTab.RETHINK, getString(R.string.app_name)))
        }

        return tabs
    }

    private fun initConnectionView(binding: FragmentConnectionTrackerBinding, lifecycleOwner: LifecycleOwner) {
        if (!persistentState.logsEnabled) {
            binding.connectionListLogsDisabledTv.visibility = View.VISIBLE
            binding.connectionCardViewTop.visibility = View.GONE
            return
        }

        binding.connectionListLogsDisabledTv.visibility = View.GONE

        if (connectionFromWireGuardScreen || connectionFromUniversalFirewallScreen) {
            hideConnectionSearchLayout(binding)
        } else {
            binding.connectionCardViewTop.visibility = View.VISIBLE
        }

        binding.connectionSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                connectionSearchQuery.value = query
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                connectionSearchQuery.value = query
                return true
            }
        })

        setupConnectionRecyclerView(binding, lifecycleOwner)

        binding.connectionSearch.setOnClickListener {
            showConnectionParentChipsUi(binding)
            showConnectionChildChipsIfNeeded(binding)
            binding.connectionSearch.requestFocus()
            binding.connectionSearch.onActionViewExpanded()
        }

        binding.connectionFilterIcon.setOnClickListener { toggleConnectionParentChipsUi(binding) }

        binding.connectionDeleteIcon.setOnClickListener { showConnectionDeleteDialog() }

        remakeConnectionParentFilterChipsUi(binding)
        remakeConnectionChildFilterChipsUi(binding, FirewallRuleset.getBlockedRules())

        applyInitialConnectionFilters(binding)
        startConnectionQueryCollector()
    }

    private fun applyInitialConnectionFilters(binding: FragmentConnectionTrackerBinding) {
        if (searchParam.isEmpty()) return

        if (connectionFromUniversalFirewallScreen) {
            val rule = searchParam.split(UniversalFirewallSettingsActivity.RULES_SEARCH_ID)[1]
            connectionFilterCategories.add(rule)
            connectionFilterType = TopLevelFilter.BLOCKED
            connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
            hideConnectionSearchLayout(binding)
            return
        }

        if (connectionFromWireGuardScreen) {
            val rule = searchParam.split(RULES_SEARCH_ID_WIREGUARD)[1]
            connectionFilterQuery = rule
            connectionFilterType = TopLevelFilter.ALL
            connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
            hideConnectionSearchLayout(binding)
            return
        }

        binding.connectionSearch.setQuery(searchParam, true)
        connectionTrackerViewModel.setFilter(searchParam, connectionFilterCategories, connectionFilterType)
    }

    private fun setupConnectionRecyclerView(binding: FragmentConnectionTrackerBinding, lifecycleOwner: LifecycleOwner) {
        binding.recyclerConnection.setHasFixedSize(true)
        connectionLayoutManager = LinearLayoutManager(this)
        connectionLayoutManager?.isItemPrefetchEnabled = true
        binding.recyclerConnection.layoutManager = connectionLayoutManager

        val recyclerAdapter = ConnectionTrackerAdapter(this)
        recyclerAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.recyclerConnection.adapter = recyclerAdapter

        connectionTrackerViewModel.connectionTrackerList.observe(lifecycleOwner) { pagingData ->
            recyclerAdapter.submitData(lifecycleOwner.lifecycle, pagingData)
        }

        recyclerAdapter.addLoadStateListener { loadState ->
            val isEmpty = recyclerAdapter.itemCount < 1
            if (loadState.append.endOfPaginationReached && isEmpty) {
                if (connectionFromUniversalFirewallScreen || connectionFromWireGuardScreen) {
                    binding.connectionListLogsDisabledTv.text = getString(R.string.ada_ip_no_connection)
                    binding.connectionListLogsDisabledTv.visibility = View.VISIBLE
                    binding.connectionCardViewTop.visibility = View.GONE
                } else {
                    binding.connectionListLogsDisabledTv.visibility = View.GONE
                    binding.connectionCardViewTop.visibility = View.VISIBLE
                }
                connectionTrackerViewModel.connectionTrackerList.removeObservers(this)
                binding.recyclerConnection.visibility = View.GONE
            } else {
                binding.connectionListLogsDisabledTv.visibility = View.GONE
                if (!binding.recyclerConnection.isVisible) binding.recyclerConnection.visibility = View.VISIBLE
                if (connectionFromUniversalFirewallScreen || connectionFromWireGuardScreen) {
                    binding.connectionCardViewTop.visibility = View.GONE
                } else {
                    binding.connectionCardViewTop.visibility = View.VISIBLE
                }
            }
        }

        binding.recyclerConnection.post {
            try {
                if (recyclerAdapter.itemCount > 0) {
                    recyclerAdapter.stateRestorationPolicy =
                        RecyclerView.Adapter.StateRestorationPolicy.ALLOW
                }
            } catch (_: Exception) {
                Logger.e(LOG_TAG_UI, "ConnTrack; err in setting the recycler restoration policy")
            }
        }
        binding.recyclerConnection.layoutAnimation = null
        setupConnectionRecyclerScrollListener(binding)
    }

    private fun setupConnectionRecyclerScrollListener(binding: FragmentConnectionTrackerBinding) {
        val scrollListener =
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val firstChild = recyclerView.getChildAt(0)
                    if (firstChild == null) {
                        Logger.v(LOG_TAG_UI, "ConnTrack; err; no child views found in recyclerView")
                        return
                    }

                    val tag = firstChild.tag as? Long
                    if (tag == null) {
                        Logger.v(LOG_TAG_UI, "ConnTrack; err; tag is null for first child, rv")
                        return
                    }

                    binding.connectionListScrollHeader.text =
                        formatToRelativeTime(this@NetworkLogsActivity, tag)
                    binding.connectionListScrollHeader.visibility = View.VISIBLE
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        binding.connectionListScrollHeader.visibility = View.GONE
                    }
                }
            }
        binding.recyclerConnection.addOnScrollListener(scrollListener)
    }

    private fun hideConnectionSearchLayout(binding: FragmentConnectionTrackerBinding) {
        binding.connectionCardViewTop.visibility = View.GONE
    }

    private fun toggleConnectionParentChipsUi(binding: FragmentConnectionTrackerBinding) {
        if (binding.filterChipParentGroup.isVisible) {
            hideConnectionParentChipsUi(binding)
            hideConnectionChildChipsUi(binding)
        } else {
            showConnectionParentChipsUi(binding)
            showConnectionChildChipsIfNeeded(binding)
        }
    }

    private fun showConnectionChildChipsIfNeeded(binding: FragmentConnectionTrackerBinding) {
        when (connectionFilterType) {
            TopLevelFilter.ALL -> {
                hideConnectionChildChipsUi(binding)
            }
            TopLevelFilter.ALLOWED -> {
                showConnectionChildChipsUi(binding)
            }
            TopLevelFilter.BLOCKED -> {
                showConnectionChildChipsUi(binding)
            }
        }
    }

    private fun remakeConnectionParentFilterChipsUi(binding: FragmentConnectionTrackerBinding) {
        binding.filterChipParentGroup.removeAllViews()

        val all = makeConnectionParentChip(binding, TopLevelFilter.ALL.id, getString(R.string.lbl_all), true)
        val allowed =
            makeConnectionParentChip(binding, TopLevelFilter.ALLOWED.id, getString(R.string.lbl_allowed), false)
        val blocked =
            makeConnectionParentChip(binding, TopLevelFilter.BLOCKED.id, getString(R.string.lbl_blocked), false)

        binding.filterChipParentGroup.addView(all)
        binding.filterChipParentGroup.addView(allowed)
        binding.filterChipParentGroup.addView(blocked)
    }

    private fun makeConnectionParentChip(
        binding: FragmentConnectionTrackerBinding,
        id: Int,
        label: String,
        checked: Boolean
    ): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, binding.root, false) as Chip
        chip.tag = id
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applyConnectionParentFilter(binding, button.tag)
            } else {
                unselectConnectionParentsChipsUi(binding, button.tag)
            }
        }

        return chip
    }

    private fun makeConnectionChildChip(
        binding: FragmentConnectionTrackerBinding,
        id: String,
        titleResId: Int
    ): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, binding.root, false) as Chip
        chip.text = getString(titleResId)
        chip.chipIcon = ContextCompat.getDrawable(this, FirewallRuleset.getRulesIcon(id))
        chip.isCheckedIconVisible = false
        chip.tag = id

        chip.setOnCheckedChangeListener { compoundButton: CompoundButton, isSelected: Boolean ->
            applyConnectionChildFilter(compoundButton.tag, isSelected)
        }
        return chip
    }

    private fun applyConnectionParentFilter(binding: FragmentConnectionTrackerBinding, tag: Any) {
        when (tag) {
            TopLevelFilter.ALL.id -> {
                connectionFilterCategories.clear()
                connectionFilterType = TopLevelFilter.ALL
                connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
                hideConnectionChildChipsUi(binding)
            }
            TopLevelFilter.ALLOWED.id -> {
                connectionFilterCategories.clear()
                connectionFilterType = TopLevelFilter.ALLOWED
                connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
                remakeConnectionChildFilterChipsUi(binding, FirewallRuleset.getAllowedRules())
                showConnectionChildChipsUi(binding)
            }
            TopLevelFilter.BLOCKED.id -> {
                connectionFilterType = TopLevelFilter.BLOCKED
                connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
                remakeConnectionChildFilterChipsUi(binding, FirewallRuleset.getBlockedRules())
                showConnectionChildChipsUi(binding)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun startConnectionQueryCollector() {
        if (connectionQueryCollectorStarted) return
        connectionQueryCollectorStarted = true
        lifecycleScope.launch {
            connectionSearchQuery
                .debounce(1000)
                .distinctUntilChanged()
                .collect { query ->
                    connectionFilterQuery = query
                    connectionTrackerViewModel.setFilter(query, connectionFilterCategories, connectionFilterType)
                }
        }
    }

    private fun showConnectionDeleteDialog() {
        val rule = connectionFilterCategories.firstOrNull()
        if (connectionFromUniversalFirewallScreen && rule != null) {
            MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
                .setTitle(R.string.conn_track_clear_rule_logs_title)
                .setMessage(R.string.conn_track_clear_rule_logs_message)
                .setCancelable(true)
                .setPositiveButton(getString(R.string.dns_log_dialog_positive)) { _, _ ->
                    io { connectionTrackerRepository.clearLogsByRule(rule) }
                }
                .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
                .create()
                .show()
        } else {
            MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
                .setTitle(R.string.conn_track_clear_logs_title)
                .setMessage(R.string.conn_track_clear_logs_message)
                .setCancelable(true)
                .setPositiveButton(getString(R.string.dns_log_dialog_positive)) { _, _ ->
                    io { connectionTrackerRepository.clearAllData() }
                }
                .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
                .create()
                .show()
        }
    }

    private fun remakeConnectionChildFilterChipsUi(binding: FragmentConnectionTrackerBinding, categories: List<FirewallRuleset>) {
        with(binding.filterChipGroup) {
            removeAllViews()
            for (c in categories) {
                addView(makeConnectionChildChip(binding, c.id, c.title))
            }
        }
    }

    private fun applyConnectionChildFilter(tag: Any, show: Boolean) {
        if (show) {
            connectionFilterCategories.add(tag.toString())
        } else {
            connectionFilterCategories.remove(tag.toString())
        }
        connectionTrackerViewModel.setFilter(connectionFilterQuery, connectionFilterCategories, connectionFilterType)
    }

    private fun unselectConnectionParentsChipsUi(binding: FragmentConnectionTrackerBinding, tag: Any) {
        when (tag) {
            TopLevelFilter.ALL.id -> {
                showConnectionChildChipsUi(binding)
            }
        }
    }

    private fun showConnectionChildChipsUi(binding: FragmentConnectionTrackerBinding) {
        binding.filterChipGroup.visibility = View.VISIBLE
    }

    private fun hideConnectionChildChipsUi(binding: FragmentConnectionTrackerBinding) {
        binding.filterChipGroup.visibility = View.GONE
    }

    private fun showConnectionParentChipsUi(binding: FragmentConnectionTrackerBinding) {
        binding.filterChipParentGroup.visibility = View.VISIBLE
    }

    private fun hideConnectionParentChipsUi(binding: FragmentConnectionTrackerBinding) {
        binding.filterChipParentGroup.visibility = View.GONE
    }

    private fun initDnsLogView(binding: FragmentDnsLogsBinding, lifecycleOwner: LifecycleOwner) {
        if (!persistentState.logsEnabled) {
            binding.queryListLogsDisabledTv.visibility = View.VISIBLE
            binding.queryListLogsDisabledTv.text = getString(R.string.show_logs_disabled_dns_message)
            binding.queryListCardViewTop.visibility = View.GONE
            return
        }

        displayDnsLogsUi(binding, lifecycleOwner)
        setupDnsClickListeners(binding)
        remakeDnsFilterChipsUi(binding)
        startDnsQueryCollector()

        applyInitialDnsFilters(binding)
    }

    private fun applyInitialDnsFilters(binding: FragmentDnsLogsBinding) {
        if (searchParam.isEmpty()) return
        if (dnsFromWireGuardScreen) {
            hideDnsSearchLayout(binding)
            dnsLogViewModel.setIsWireGuardLogs(true, wgId)
            return
        }

        if (searchParam.contains(UniversalFirewallSettingsActivity.RULES_SEARCH_ID)) {
            return
        }

        binding.queryListSearch.setQuery(searchParam, true)
    }

    private fun hideDnsSearchLayout(binding: FragmentDnsLogsBinding) {
        binding.queryListCardViewTop.visibility = View.GONE
    }

    private fun setupDnsClickListeners(binding: FragmentDnsLogsBinding) {
        binding.queryListSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                dnsSearchQuery.value = query
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                dnsSearchQuery.value = query
                return true
            }
        })
        binding.queryListSearch.setOnClickListener {
            showDnsChipsUi(binding)
            binding.queryListSearch.requestFocus()
            binding.queryListSearch.onActionViewExpanded()
        }

        binding.queryListFilterIcon.setOnClickListener { toggleDnsChipsUi(binding) }

        binding.queryListDeleteIcon.setOnClickListener { showDnsLogsDeleteDialog() }
    }

    private fun displayDnsLogsUi(binding: FragmentDnsLogsBinding, lifecycleOwner: LifecycleOwner) {
        binding.queryListLogsDisabledTv.visibility = View.GONE
        binding.queryListCardViewTop.visibility = View.VISIBLE

        binding.recyclerQuery.setHasFixedSize(true)
        dnsLayoutManager = LinearLayoutManager(this)
        dnsLayoutManager?.isItemPrefetchEnabled = true
        binding.recyclerQuery.layoutManager = dnsLayoutManager

        val favIcon = persistentState.fetchFavIcon
        val isRethinkDns = appConfig.isRethinkDnsConnected()
        val recyclerAdapter = DnsLogAdapter(this, favIcon, isRethinkDns)
        recyclerAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.recyclerQuery.adapter = recyclerAdapter
        dnsLogViewModel.dnsLogsList.observe(lifecycleOwner) {
            recyclerAdapter.submitData(lifecycleOwner.lifecycle, it)
        }

        recyclerAdapter.addLoadStateListener { loadState ->
            val isEmpty = recyclerAdapter.itemCount < 1
            if (loadState.append.endOfPaginationReached && isEmpty) {
                dnsLogViewModel.dnsLogsList.removeObservers(this)
                binding.recyclerQuery.visibility = View.GONE
            } else {
                if (!binding.recyclerQuery.isVisible) binding.recyclerQuery.visibility = View.VISIBLE
            }
        }

        binding.recyclerQuery.post {
            try {
                if (recyclerAdapter.itemCount > 0) {
                    recyclerAdapter.stateRestorationPolicy =
                        RecyclerView.Adapter.StateRestorationPolicy.ALLOW
                }
            } catch (_: Exception) {
                Logger.e(LOG_TAG_UI, "DnsLogs; err in setting the recycler restoration policy")
            }
        }

        val scrollListener =
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val firstChild = recyclerView.getChildAt(0)
                    if (firstChild == null) {
                        Logger.w(LOG_TAG_UI, "DnsLogs; err; no child views found in recyclerView")
                        return
                    }

                    val tag = firstChild.tag as? Long
                    if (tag == null) {
                        Logger.w(LOG_TAG_UI, "DnsLogs; err; tag is null")
                        return
                    }

                    binding.queryListRecyclerScrollHeader.text = formatToRelativeTime(this@NetworkLogsActivity, tag)
                    binding.queryListRecyclerScrollHeader.visibility = View.VISIBLE
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        binding.queryListRecyclerScrollHeader.visibility = View.GONE
                    }
                }
            }
        binding.recyclerQuery.addOnScrollListener(scrollListener)
        binding.recyclerQuery.layoutAnimation = null
    }

    private fun remakeDnsFilterChipsUi(binding: FragmentDnsLogsBinding) {
        binding.filterChipGroup.removeAllViews()

        val all = makeDnsChip(binding, DnsLogViewModel.DnsLogFilter.ALL.id, getString(R.string.lbl_all), true)
        val allowed = makeDnsChip(binding, DnsLogViewModel.DnsLogFilter.ALLOWED.id, getString(R.string.lbl_allowed), false)
        val maybeBlocked =
            makeDnsChip(binding, DnsLogViewModel.DnsLogFilter.MAYBE_BLOCKED.id, getString(R.string.lbl_maybe_blocked), false)
        val blocked = makeDnsChip(binding, DnsLogViewModel.DnsLogFilter.BLOCKED.id, getString(R.string.lbl_blocked), false)
        val unknown =
            makeDnsChip(
                binding,
                DnsLogViewModel.DnsLogFilter.UNKNOWN_RECORDS.id,
                getString(R.string.network_log_app_name_unknown),
                false
            )

        binding.filterChipGroup.addView(all)
        binding.filterChipGroup.addView(allowed)
        binding.filterChipGroup.addView(maybeBlocked)
        binding.filterChipGroup.addView(blocked)
        binding.filterChipGroup.addView(unknown)
    }

    private fun makeDnsChip(
        binding: FragmentDnsLogsBinding,
        id: Int,
        label: String,
        checked: Boolean
    ): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, binding.root, false) as Chip
        chip.tag = id
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applyDnsFilter(button.tag)
            }
        }

        return chip
    }

    private fun toggleDnsChipsUi(binding: FragmentDnsLogsBinding) {
        if (binding.filterChipGroup.isVisible) {
            hideDnsChipsUi(binding)
        } else {
            showDnsChipsUi(binding)
        }
    }

    private fun applyDnsFilter(tag: Any) {
        when (tag) {
            DnsLogViewModel.DnsLogFilter.ALL.id -> {
                dnsFilterType = DnsLogViewModel.DnsLogFilter.ALL
                dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
            }
            DnsLogViewModel.DnsLogFilter.ALLOWED.id -> {
                dnsFilterType = DnsLogViewModel.DnsLogFilter.ALLOWED
                dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
            }
            DnsLogViewModel.DnsLogFilter.BLOCKED.id -> {
                dnsFilterType = DnsLogViewModel.DnsLogFilter.BLOCKED
                dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
            }
            DnsLogViewModel.DnsLogFilter.MAYBE_BLOCKED.id -> {
                dnsFilterType = DnsLogViewModel.DnsLogFilter.MAYBE_BLOCKED
                dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
            }
            DnsLogViewModel.DnsLogFilter.UNKNOWN_RECORDS.id -> {
                dnsFilterType = DnsLogViewModel.DnsLogFilter.UNKNOWN_RECORDS
                dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
            }
        }
    }

    private fun showDnsChipsUi(binding: FragmentDnsLogsBinding) {
        binding.filterChipGroup.visibility = View.VISIBLE
    }

    private fun hideDnsChipsUi(binding: FragmentDnsLogsBinding) {
        binding.filterChipGroup.visibility = View.GONE
    }

    private fun showDnsLogsDeleteDialog() {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(R.string.dns_query_clear_logs_title)
            .setMessage(R.string.dns_query_clear_logs_message)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.dns_log_dialog_positive)) { _, _ ->
                io {
                    Glide.get(this@NetworkLogsActivity).clearDiskCache()
                    dnsLogRepository.clearAllData()
                }
            }
            .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
            .create()
            .show()
    }

    @OptIn(FlowPreview::class)
    private fun startDnsQueryCollector() {
        if (dnsQueryCollectorStarted) return
        dnsQueryCollectorStarted = true
        lifecycleScope.launch {
            dnsSearchQuery
                .debounce(1000)
                .distinctUntilChanged()
                .collect { query ->
                    dnsFilterValue = query
                    dnsLogViewModel.setFilter(dnsFilterValue, dnsFilterType)
                }
        }
    }

    private fun initRethinkLogView(binding: FragmentConnectionTrackerBinding, lifecycleOwner: LifecycleOwner) {
        binding.connectionFilterIcon.visibility = View.GONE

        if (!persistentState.logsEnabled) {
            binding.connectionListLogsDisabledTv.visibility = View.VISIBLE
            binding.connectionCardViewTop.visibility = View.GONE
            return
        }

        binding.connectionListLogsDisabledTv.visibility = View.GONE
        binding.connectionCardViewTop.visibility = View.VISIBLE

        binding.recyclerConnection.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerConnection.layoutManager = layoutManager
        val recyclerAdapter = RethinkLogAdapter(this)
        rethinkLogViewModel.rlogList.observe(lifecycleOwner) {
            recyclerAdapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerConnection.adapter = recyclerAdapter
        binding.recyclerConnection.layoutAnimation = null

        setupRethinkRecyclerScrollListener(binding)
        binding.connectionSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                rethinkSearchQuery.value = query
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                rethinkSearchQuery.value = query
                return true
            }
        })
        startRethinkQueryCollector()

        binding.connectionDeleteIcon.setOnClickListener { showRethinkDeleteDialog() }

        if (searchParam.isNotEmpty()) {
            binding.connectionSearch.setQuery(searchParam, true)
        }
    }

    private fun setupRethinkRecyclerScrollListener(binding: FragmentConnectionTrackerBinding) {
        val scrollListener =
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val firstChild = recyclerView.getChildAt(0)
                    if (firstChild == null) {
                        Logger.w(LOG_TAG_UI, "RinRLogs; err; no child views found in recyclerView")
                        return
                    }

                    val tag = firstChild.tag as? Long
                    if (tag == null) {
                        Logger.w(LOG_TAG_UI, "RinRLogs; err; tag is null for first child, rv")
                        return
                    }

                    binding.connectionListScrollHeader.text =
                        formatToRelativeTime(this@NetworkLogsActivity, tag)
                    binding.connectionListScrollHeader.visibility = View.VISIBLE
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        binding.connectionListScrollHeader.visibility = View.GONE
                    }
                }
            }
        binding.recyclerConnection.addOnScrollListener(scrollListener)
    }

    @OptIn(FlowPreview::class)
    private fun startRethinkQueryCollector() {
        if (rethinkQueryCollectorStarted) return
        rethinkQueryCollectorStarted = true
        lifecycleScope.launch {
            rethinkSearchQuery
                .debounce(1000)
                .distinctUntilChanged()
                .collect { query ->
                    rethinkLogViewModel.setFilter(query)
                }
        }
    }

    private fun showRethinkDeleteDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.conn_track_clear_logs_title)
        builder.setMessage(R.string.conn_track_clear_logs_message)
        builder.setCancelable(true)
        builder.setPositiveButton(getString(R.string.dns_log_dialog_positive)) { _, _ ->
            io { rethinkLogRepository.clearAllData() }
        }

        builder.setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
        builder.create().show()
    }

    private fun initWgStatsView(binding: FragmentWgNwStatsBinding, lifecycleOwner: LifecycleOwner) {
        if (wgId.isEmpty() || !wgId.startsWith(ProxyManager.ID_WG_BASE)) {
            showWgErrorDialog()
            return
        }

        setTabbedViewTxt(binding)
        highlightToggleBtn(binding)
        setWgRecyclerView(binding, lifecycleOwner)
        setWgClickListeners(binding)
        handleTotalUsagesUi(binding)
    }

    private fun setWgClickListeners(binding: FragmentWgNwStatsBinding) {
        binding.toggleGroup.addOnButtonCheckedListener(wgToggleListener(binding))
    }

    private fun setTabbedViewTxt(binding: FragmentWgNwStatsBinding) {
        binding.tbRecentToggleBtn.text = getString(R.string.ci_desc, "1", getString(R.string.lbl_hour))
        binding.tbDailyToggleBtn.text = getString(R.string.ci_desc, "24", getString(R.string.lbl_hour))
        binding.tbWeeklyToggleBtn.text = getString(R.string.ci_desc, "7", getString(R.string.lbl_day))
    }

    private fun setWgRecyclerView(binding: FragmentWgNwStatsBinding, lifecycleOwner: LifecycleOwner) {
        val adapter = WgNwStatsAdapter(this)
        binding.statsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.statsRecyclerView.adapter = adapter

        wgNwActivityViewModel.setWgId(wgId)
        wgNwActivityViewModel.wgAppNwActivity.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }

        adapter.addLoadStateListener { loadState ->
            val isEmpty = adapter.itemCount < 1
            if (loadState.append.endOfPaginationReached && isEmpty) {
                binding.tbStatsCard.visibility = View.GONE
                binding.tbLogsDisabledTv.visibility = View.VISIBLE
                wgNwActivityViewModel.wgAppNwActivity.removeObservers(this)
            } else {
                binding.tbLogsDisabledTv.visibility = View.GONE
                binding.tbStatsCard.visibility = View.VISIBLE
            }
        }
    }

    private fun highlightToggleBtn(binding: FragmentWgNwStatsBinding) {
        val timeCategory = "0"
        val btn = binding.toggleGroup.findViewWithTag<MaterialButton>(timeCategory)
        btn.isChecked = true
        selectToggleBtnUi(binding, btn)
    }

    private fun wgToggleListener(binding: FragmentWgNwStatsBinding) =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            val mb: MaterialButton = binding.toggleGroup.findViewById(checkedId)
            if (isChecked) {
                selectToggleBtnUi(binding, mb)
                val tcValue = (mb.tag as String).toIntOrNull() ?: 0
                val timeCategory =
                    WgNwActivityViewModel.TimeCategory.fromValue(tcValue)
                        ?: WgNwActivityViewModel.TimeCategory.ONE_HOUR
                Logger.d(LOG_TAG_UI, "WgNwStats: time category changed to $timeCategory")
                wgNwActivityViewModel.timeCategoryChanged(timeCategory)
                handleTotalUsagesUi(binding)
                return@OnButtonCheckedListener
            }

            unselectToggleBtnUi(binding, mb)
        }

    private fun handleTotalUsagesUi(binding: FragmentWgNwStatsBinding) {
        io {
            val totalUsage = wgNwActivityViewModel.totalUsage(wgId)
            uiCtx { setTotalUsagesUi(binding, totalUsage) }
        }
    }

    private fun setTotalUsagesUi(binding: FragmentWgNwStatsBinding, dataUsage: DataUsageSummary) {
        val unmeteredUsage = (dataUsage.totalDownload + dataUsage.totalUpload)
        val totalUsage = unmeteredUsage + dataUsage.meteredDataUsage

        binding.fssUnmeteredDataUsage.text =
            getString(
                R.string.two_argument_colon,
                getString(R.string.ada_app_unmetered),
                Utilities.humanReadableByteCount(unmeteredUsage, true)
            )
        binding.fssMeteredDataUsage.text =
            getString(
                R.string.two_argument_colon,
                getString(R.string.ada_app_metered),
                Utilities.humanReadableByteCount(dataUsage.meteredDataUsage, true)
            )
        binding.fssTotalDataUsage.text =
            getString(
                R.string.two_argument_colon,
                getString(R.string.lbl_overall),
                Utilities.humanReadableByteCount(totalUsage, true)
            )
        binding.fssMeteredDataUsage.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.dot_accent,
            0,
            0,
            0
        )

        val alphaValue = 128
        val drawable = binding.fssMeteredDataUsage.compoundDrawables[0]
        drawable?.mutate()?.alpha = alphaValue

        val ump = calculatePercentage(unmeteredUsage, totalUsage)
        val mp = calculatePercentage(dataUsage.meteredDataUsage, totalUsage)
        val secondaryVal = ump + mp

        binding.fssProgressBar.max = secondaryVal
        binding.fssProgressBar.progress = ump
        binding.fssProgressBar.secondaryProgress = secondaryVal
    }

    private fun calculatePercentage(value: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((value.toDouble() / total.toDouble()) * 100).toInt()
    }

    private fun selectToggleBtnUi(binding: FragmentWgNwStatsBinding, btn: MaterialButton) {
        btn.backgroundTintList =
            ColorStateList.valueOf(
                UIUtils.fetchToggleBtnColors(this, R.color.accentGood)
            )
        btn.setTextColor(UIUtils.fetchColor(this, R.attr.homeScreenHeaderTextColor))
        binding.toggleGroup.checkedButtonId
    }

    private fun unselectToggleBtnUi(binding: FragmentWgNwStatsBinding, btn: MaterialButton) {
        btn.setTextColor(UIUtils.fetchColor(this, R.attr.primaryTextColor))
        btn.backgroundTintList =
            ColorStateList.valueOf(
                UIUtils.fetchToggleBtnColors(this, R.color.defaultToggleBtnBg)
            )
    }

    private fun showWgErrorDialog() {
        val dialog = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.lbl_wireguard))
            .setMessage(getString(R.string.config_invalid_desc))
            .setPositiveButton(R.string.fapps_info_dialog_positive_btn) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun openConsoleLogActivity() {
        val intent = Intent(this, ConsoleLogActivity::class.java)
        startActivity(intent)
    }

    private fun observeAppState() {
        VpnController.connectionStatus.observe(this) {
            if (it == BraveVPNService.State.PAUSED) {
                startActivity(Intent().setClass(this, PauseActivity::class.java))
                finish()
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

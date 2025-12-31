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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.CustomDomainAdapter
import com.celzero.bravedns.adapter.CustomIpAdapter
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.DialogAddCustomDomainBinding
import com.celzero.bravedns.databinding.DialogAddCustomIpBinding
import com.celzero.bravedns.databinding.FragmentCustomDomainBinding
import com.celzero.bravedns.databinding.FragmentCustomIpBinding
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.DomainRulesManager.isValidDomain
import com.celzero.bravedns.service.DomainRulesManager.isWildCardEntry
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.CustomLinearLayoutManager
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.removeLeadingAndTrailingDots
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.CustomDomainViewModel
import com.celzero.bravedns.viewmodel.CustomIpViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import inet.ipaddr.IPAddress
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

    private var customDomainBinding: FragmentCustomDomainBinding? = null
    private var customIpBinding: FragmentCustomIpBinding? = null

    private var domainLayoutManager: RecyclerView.LayoutManager? = null
    private lateinit var domainAdapter: CustomDomainAdapter

    private var ipLayoutManager: RecyclerView.LayoutManager? = null
    private lateinit var ipAdapter: CustomIpAdapter

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

    override fun onResume() {
        super.onResume()
        refreshSearchView(customDomainBinding?.cdaSearchView)
        refreshSearchView(customIpBinding?.cipSearchView)
    }

    override fun onDestroy() {
        super.onDestroy()
        customDomainBinding = null
        customIpBinding = null
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun CustomRulesContent(initialTab: Int) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val selectedTab = remember {
            mutableIntStateOf(initialTab.coerceIn(0, Tabs.getCount() - 1))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab.intValue) {
                Tab(
                    text = { Text(text = getString(R.string.univ_view_blocked_ip)) },
                    selected = selectedTab.intValue == Tabs.IP_RULES.screen,
                    onClick = { selectedTab.intValue = Tabs.IP_RULES.screen }
                )
                Tab(
                    text = { Text(text = getString(R.string.dc_custom_block_heading)) },
                    selected = selectedTab.intValue == Tabs.DOMAIN_RULES.screen,
                    onClick = { selectedTab.intValue = Tabs.DOMAIN_RULES.screen }
                )
            }

            when (selectedTab.intValue) {
                Tabs.IP_RULES.screen -> CustomIpContent(lifecycleOwner)
                Tabs.DOMAIN_RULES.screen -> CustomDomainContent(lifecycleOwner)
            }
        }
    }

    @Composable
    private fun CustomDomainContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding =
                    customDomainBinding
                        ?: FragmentCustomDomainBinding.inflate(LayoutInflater.from(context))
                customDomainBinding = binding
                initCustomDomainView(binding, lifecycleOwner)
                binding.root
            }
        )
    }

    @Composable
    private fun CustomIpContent(lifecycleOwner: LifecycleOwner) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val binding = customIpBinding ?: FragmentCustomIpBinding.inflate(LayoutInflater.from(context))
                customIpBinding = binding
                initCustomIpView(binding, lifecycleOwner)
                binding.root
            }
        )
    }

    private fun refreshSearchView(searchView: SearchView?) {
        if (searchView == null) return
        searchView.setQuery("", false)
        searchView.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(searchView)
    }

    private fun initCustomDomainView(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        binding.cdaSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                customDomainViewModel.setFilter(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                customDomainViewModel.setFilter(query)
                return true
            }
        })
        setupDomainRecyclerView(binding, lifecycleOwner)
        setupDomainClickListeners(binding)
        binding.cdaRecycler.requestFocus()
    }

    private fun setupDomainRecyclerView(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        domainLayoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.cdaRecycler.layoutManager = domainLayoutManager
        binding.cdaRecycler.setHasFixedSize(true)
        if (rules == RULES.APP_SPECIFIC_RULES) {
            binding.cdaAddFab.visibility = View.VISIBLE
            setupDomainRulesForApp(binding, lifecycleOwner)
        } else {
            binding.cdaAddFab.visibility = View.GONE
            setupDomainRulesForAll(binding, lifecycleOwner)
        }
    }

    private fun setupDomainRulesForApp(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        observeCustomDomainRules(binding, lifecycleOwner)
        domainAdapter = CustomDomainAdapter(this, supportFragmentManager, rules, eventLogger)
        binding.cdaRecycler.adapter = domainAdapter
        customDomainViewModel.setUid(uid)
        customDomainViewModel.customDomains.observe(lifecycleOwner) {
            domainAdapter.submitData(lifecycleOwner.lifecycle, it)
        }
        io {
            val appName = FirewallManager.getAppNameByUid(uid)
            if (!appName.isNullOrEmpty()) {
                uiCtx { updateDomainSearchHint(binding, appName) }
            }
        }
    }

    private fun updateDomainSearchHint(binding: FragmentCustomDomainBinding, appName: String) {
        val appNameTruncated = appName.substring(0, appName.length.coerceAtMost(10))
        val hint =
            getString(
                R.string.two_argument_colon,
                appNameTruncated,
                getString(R.string.search_custom_domains)
            )
        binding.cdaSearchView.queryHint = hint
        binding.cdaSearchView
            .findViewById<SearchView.SearchAutoComplete>(androidx.appcompat.R.id.search_src_text)
            .textSize = 14f
    }

    private fun setupDomainRulesForAll(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        observeAllDomainRules(binding, lifecycleOwner)
        domainAdapter = CustomDomainAdapter(this, supportFragmentManager, rules, eventLogger)
        binding.cdaRecycler.adapter = domainAdapter
        customDomainViewModel.allDomainRules.observe(lifecycleOwner) {
            domainAdapter.submitData(lifecycleOwner.lifecycle, it)
        }
    }

    private fun setupDomainClickListeners(binding: FragmentCustomDomainBinding) {
        binding.cdaAddFab.bringToFront()
        binding.cdaAddFab.setOnClickListener { showAddDomainDialog() }
        binding.cdaSearchDeleteIcon.setOnClickListener { showDomainRulesDeleteDialog() }
    }

    private fun observeCustomDomainRules(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        customDomainViewModel.domainRulesCount(uid).observe(lifecycleOwner) {
            if (it <= 0) {
                showNoDomainRulesUi(binding)
                hideDomainRulesUi(binding)
                return@observe
            }

            hideNoDomainRulesUi(binding)
            showDomainRulesUi(binding)
        }
    }

    private fun observeAllDomainRules(binding: FragmentCustomDomainBinding, lifecycleOwner: LifecycleOwner) {
        customDomainViewModel.allDomainRulesCount().observe(lifecycleOwner) {
            if (it <= 0) {
                showNoDomainRulesUi(binding)
                hideDomainRulesUi(binding)
                return@observe
            }

            hideNoDomainRulesUi(binding)
            showDomainRulesUi(binding)
        }
    }

    private fun hideDomainRulesUi(binding: FragmentCustomDomainBinding) {
        binding.cdaShowRulesRl.visibility = View.GONE
    }

    private fun showDomainRulesUi(binding: FragmentCustomDomainBinding) {
        binding.cdaShowRulesRl.visibility = View.VISIBLE
    }

    private fun hideNoDomainRulesUi(binding: FragmentCustomDomainBinding) {
        binding.cdaNoRulesRl.visibility = View.GONE
    }

    private fun showNoDomainRulesUi(binding: FragmentCustomDomainBinding) {
        binding.cdaNoRulesRl.visibility = View.VISIBLE
    }

    private fun showAddDomainDialog() {
        val dBind = DialogAddCustomDomainBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dBind.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        var selectedType: DomainRulesManager.DomainType = DomainRulesManager.DomainType.DOMAIN

        dBind.dacdDomainEditText.addTextChangedListener {
            if (it?.startsWith("*") == true || it?.startsWith(".") == true) {
                dBind.dacdWildcardChip.isChecked = true
            } else {
                dBind.dacdDomainChip.isChecked = true
            }
        }

        dBind.dacdDomainChip.setOnCheckedChangeListener { _, isSelected ->
            if (isSelected) {
                selectedType = DomainRulesManager.DomainType.DOMAIN
                dBind.dacdDomainEditText.hint =
                    resources.getString(
                        R.string.cd_dialog_edittext_hint,
                        getString(R.string.lbl_domain)
                    )
                dBind.dacdTextInputLayout.hint =
                    resources.getString(
                        R.string.cd_dialog_edittext_hint,
                        getString(R.string.lbl_domain)
                    )
            }
        }

        dBind.dacdWildcardChip.setOnCheckedChangeListener { _, isSelected ->
            if (isSelected) {
                selectedType = DomainRulesManager.DomainType.WILDCARD
                dBind.dacdDomainEditText.hint =
                    resources.getString(
                        R.string.cd_dialog_edittext_hint,
                        getString(R.string.lbl_wildcard)
                    )
                dBind.dacdTextInputLayout.hint =
                    resources.getString(
                        R.string.cd_dialog_edittext_hint,
                        getString(R.string.lbl_wildcard)
                    )
            }
        }

        dBind.dacdUrlTitle.text = getString(R.string.cd_dialog_title)
        dBind.dacdDomainEditText.hint =
            resources.getString(R.string.cd_dialog_edittext_hint, getString(R.string.lbl_domain))
        dBind.dacdTextInputLayout.hint =
            resources.getString(R.string.cd_dialog_edittext_hint, getString(R.string.lbl_domain))

        dBind.dacdBlockBtn.setOnClickListener {
            handleDomain(dBind, selectedType, DomainRulesManager.Status.BLOCK)
        }

        dBind.dacdTrustBtn.setOnClickListener {
            handleDomain(dBind, selectedType, DomainRulesManager.Status.TRUST)
        }

        dBind.dacdCancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun handleDomain(
        dBind: DialogAddCustomDomainBinding,
        selectedType: DomainRulesManager.DomainType,
        status: DomainRulesManager.Status
    ) {
        dBind.dacdFailureText.visibility = View.GONE
        val url = dBind.dacdDomainEditText.text.toString()
        val extractedHost = extractHost(url) ?: run {
            dBind.dacdFailureText.text =
                getString(R.string.cd_dialog_error_invalid_domain)
            dBind.dacdFailureText.visibility = View.VISIBLE
            return
        }
        when (selectedType) {
            DomainRulesManager.DomainType.WILDCARD -> {
                if (!isWildCardEntry(extractedHost)) {
                    dBind.dacdFailureText.text =
                        getString(R.string.cd_dialog_error_invalid_wildcard)
                    dBind.dacdFailureText.visibility = View.VISIBLE
                    return
                }
            }
            DomainRulesManager.DomainType.DOMAIN -> {
                if (!isValidDomain(extractedHost)) {
                    dBind.dacdFailureText.text = getString(R.string.cd_dialog_error_invalid_domain)
                    dBind.dacdFailureText.visibility = View.VISIBLE
                    return
                }
            }
        }

        insertDomain(removeLeadingAndTrailingDots(extractedHost), selectedType, status)
    }

    private fun extractHost(input: String): String? {
        return DomainRulesManager.extractHost(input)
    }

    private fun insertDomain(
        domain: String,
        type: DomainRulesManager.DomainType,
        status: DomainRulesManager.Status
    ) {
        io { DomainRulesManager.addDomainRule(domain, status, type, uid = uid) }
        Utilities.showToastUiCentered(
            this,
            resources.getString(R.string.cd_toast_added),
            Toast.LENGTH_SHORT
        )
        logDomainEvent("Added domain: $domain, Type: $type, Status: $status, UID: $uid")
    }

    private fun showDomainRulesDeleteDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(getString(R.string.univ_delete_domain_dialog_title))
        builder.setMessage(getString(R.string.univ_delete_domain_dialog_message))
        builder.setPositiveButton(getString(R.string.univ_ip_delete_dialog_positive)) { _, _ ->

            io {
                val selectedItems = domainAdapter.getSelectedItems()
                if (selectedItems.isNotEmpty()) {
                    uiCtx { domainAdapter.clearSelection() }
                    DomainRulesManager.deleteRules(selectedItems)
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
            }
            Utilities.showToastUiCentered(
                this,
                getString(R.string.cd_deleted_toast),
                Toast.LENGTH_SHORT
            )
        }

        builder.setNegativeButton(getString(R.string.lbl_cancel)) { _, _ ->
            domainAdapter.clearSelection()
        }

        builder.setCancelable(true)
        builder.create().show()
    }

    private fun logDomainEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom Domain", EventSource.UI, false, details)
    }

    private fun initCustomIpView(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        binding.cipSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                customIpViewModel.setFilter(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                customIpViewModel.setFilter(query)
                return true
            }
        })
        setupIpRecyclerView(binding, lifecycleOwner)
        setupIpClickListeners(binding)
        binding.cipRecycler.requestFocus()
    }

    private fun setupIpRecyclerView(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        ipLayoutManager = CustomLinearLayoutManager(this)
        binding.cipRecycler.layoutManager = ipLayoutManager
        binding.cipRecycler.setHasFixedSize(true)
        if (rules == RULES.APP_SPECIFIC_RULES) {
            binding.cipAddFab.visibility = View.VISIBLE
            setupIpAdapterForApp(binding, lifecycleOwner)
            io {
                val appName = FirewallManager.getAppNameByUid(uid)
                if (!appName.isNullOrEmpty()) {
                    uiCtx { updateIpSearchHint(binding, appName) }
                }
            }
        } else {
            binding.cipAddFab.visibility = View.GONE
            setupIpAdapterForAll(binding, lifecycleOwner)
        }
    }

    private fun updateIpSearchHint(binding: FragmentCustomIpBinding, appName: String) {
        val appNameTruncated = appName.substring(0, appName.length.coerceAtMost(10))
        val hint =
            getString(
                R.string.two_argument_colon,
                appNameTruncated,
                getString(R.string.search_universal_ips)
            )
        binding.cipSearchView.queryHint = hint
        binding.cipSearchView
            .findViewById<SearchView.SearchAutoComplete>(androidx.appcompat.R.id.search_src_text)
            .textSize = 14f
    }

    private fun setupIpAdapterForApp(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        observeAppSpecificIpRules(binding, lifecycleOwner)
        ipAdapter = CustomIpAdapter(this, RULES.APP_SPECIFIC_RULES, eventLogger)
        customIpViewModel.setUid(uid)
        customIpViewModel.customIpDetails.observe(lifecycleOwner) {
            ipAdapter.submitData(this.lifecycle, it)
        }
        binding.cipRecycler.adapter = ipAdapter
    }

    private fun setupIpAdapterForAll(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        observeAllAppsIpRules(binding, lifecycleOwner)
        ipAdapter = CustomIpAdapter(this, RULES.ALL_RULES, eventLogger)
        customIpViewModel.allIpRules.observe(lifecycleOwner) { ipAdapter.submitData(this.lifecycle, it) }
        binding.cipRecycler.adapter = ipAdapter
    }

    private fun setupIpClickListeners(binding: FragmentCustomIpBinding) {
        binding.cipAddFab.bringToFront()
        binding.cipAddFab.setOnClickListener { showAddIpDialog() }
        binding.cipSearchDeleteIcon.setOnClickListener { showIpRulesDeleteDialog() }
    }

    private fun observeAppSpecificIpRules(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        customIpViewModel.ipRulesCount(uid).observe(lifecycleOwner) {
            if (it <= 0) {
                showNoIpRulesUi(binding)
                hideIpRulesUi(binding)
                return@observe
            }

            hideNoIpRulesUi(binding)
            showIpRulesUi(binding)
        }
    }

    private fun observeAllAppsIpRules(binding: FragmentCustomIpBinding, lifecycleOwner: LifecycleOwner) {
        customIpViewModel.allIpRulesCount().observe(lifecycleOwner) {
            if (it <= 0) {
                showNoIpRulesUi(binding)
                hideIpRulesUi(binding)
                return@observe
            }

            hideNoIpRulesUi(binding)
            showIpRulesUi(binding)
        }
    }

    private fun hideIpRulesUi(binding: FragmentCustomIpBinding) {
        binding.cipShowRulesRl.visibility = View.GONE
    }

    private fun showIpRulesUi(binding: FragmentCustomIpBinding) {
        binding.cipShowRulesRl.visibility = View.VISIBLE
    }

    private fun hideNoIpRulesUi(binding: FragmentCustomIpBinding) {
        binding.cipNoRulesRl.visibility = View.GONE
    }

    private fun showNoIpRulesUi(binding: FragmentCustomIpBinding) {
        binding.cipNoRulesRl.visibility = View.VISIBLE
    }

    private fun showAddIpDialog() {
        val dBind = DialogAddCustomIpBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dBind.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        dBind.daciIpTitle.text = getString(R.string.ci_dialog_title)

        if (uid == UID_EVERYBODY) {
            dBind.daciTrustBtn.text = getString(R.string.bypass_universal)
        } else {
            dBind.daciTrustBtn.text = getString(R.string.ci_trust_rule)
        }

        dBind.daciIpEditText.addTextChangedListener {
            if (dBind.daciFailureTextView.isVisible) {
                dBind.daciFailureTextView.visibility = View.GONE
            }
        }

        dBind.daciBlockBtn.setOnClickListener {
            handleInsertIp(dBind, IpRulesManager.IpRuleStatus.BLOCK)
        }

        dBind.daciTrustBtn.setOnClickListener {
            if (uid == UID_EVERYBODY) {
                handleInsertIp(dBind, IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL)
            } else {
                handleInsertIp(dBind, IpRulesManager.IpRuleStatus.TRUST)
            }
        }
        adjustButtonLayoutOrientation(dBind.dialogButtonsContainer)
        dBind.daciCancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun adjustButtonLayoutOrientation(buttonContainer: LinearLayout) {
        buttonContainer.post {
            val totalButtonsWidth = (0 until buttonContainer.childCount).sumOf { index ->
                val child = buttonContainer.getChildAt(index)
                val margins = (child.layoutParams as? LinearLayout.LayoutParams)?.let {
                    it.marginStart + it.marginEnd
                } ?: 0
                child.measuredWidth + margins
            }

            val availableWidth = buttonContainer.width - buttonContainer.paddingStart - buttonContainer.paddingEnd

            if (totalButtonsWidth > availableWidth) {
                buttonContainer.orientation = LinearLayout.VERTICAL
                buttonContainer.gravity = android.view.Gravity.CENTER_HORIZONTAL
            } else {
                buttonContainer.orientation = LinearLayout.HORIZONTAL
                buttonContainer.gravity = android.view.Gravity.END
            }
        }
    }

    private fun handleInsertIp(
        dBind: DialogAddCustomIpBinding,
        status: IpRulesManager.IpRuleStatus
    ) {
        ui {
            val input = dBind.daciIpEditText.text.toString()
            val ipString = removeLeadingAndTrailingDots(input)
            var ip: IPAddress? = null
            var port = 0

            ioCtx {
                val ipPair = IpRulesManager.getIpNetPort(ipString)
                ip = ipPair.first
                port = ipPair.second
            }

            if (ip == null || ipString.isEmpty()) {
                dBind.daciFailureTextView.text = getString(R.string.ci_dialog_error_invalid_ip)
                dBind.daciFailureTextView.visibility = View.VISIBLE
                return@ui
            }

            dBind.daciIpEditText.text.clear()
            insertCustomIp(ip, port, status)
        }
    }

    private fun insertCustomIp(ip: IPAddress?, port: Int?, status: IpRulesManager.IpRuleStatus) {
        if (ip == null) return

        io { IpRulesManager.addIpRule(uid, ip, port, status, proxyId = "", proxyCC = "") }
        Utilities.showToastUiCentered(
            this,
            getString(R.string.ci_dialog_added_success),
            Toast.LENGTH_SHORT
        )
        logIpEvent("Added IP rule: $ip, Port: $port, Status: $status, UID: $uid")
    }

    private fun showIpRulesDeleteDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle(R.string.univ_delete_firewall_dialog_title)
        builder.setMessage(R.string.univ_delete_firewall_dialog_message)
        builder.setPositiveButton(getString(R.string.univ_ip_delete_dialog_positive)) { _, _ ->
            io {
                val selectedItems = ipAdapter.getSelectedItems()
                if (selectedItems.isNotEmpty()) {
                    IpRulesManager.deleteRules(selectedItems)
                    uiCtx { ipAdapter.clearSelection() }
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
            }
            Utilities.showToastUiCentered(
                this,
                getString(R.string.univ_ip_delete_toast_success),
                Toast.LENGTH_SHORT
            )
        }

        builder.setNegativeButton(getString(R.string.lbl_cancel)) { _, _ ->
            ipAdapter.clearSelection()
        }

        builder.setCancelable(true)
        builder.create().show()
    }

    private fun logIpEvent(details: String) {
        eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, "Custom IP", EventSource.UI, false, details)
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

    private fun ui(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) { f() }
    }

    private suspend fun ioCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

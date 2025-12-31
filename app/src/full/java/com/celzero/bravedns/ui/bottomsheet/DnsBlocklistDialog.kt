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
package com.celzero.bravedns.ui.bottomsheet

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ImageView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.Transition
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.FirewallStatusSpinnerAdapter
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.databinding.BottomSheetDnsLogBinding
import com.celzero.bravedns.databinding.DialogInfoRulesLayoutBinding
import com.celzero.bravedns.databinding.DialogIpDetailsLayoutBinding
import com.celzero.bravedns.glide.FavIconDownloader
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.activity.DomainConnectionsActivity
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.htmlToSpannedText
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DnsBlocklistDialog(
    private val activity: FragmentActivity,
    private val log: DnsLog
) : KoinComponent {
    private val binding = BottomSheetDnsLogBinding.inflate(LayoutInflater.from(activity))
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private var lastStatus: DomainRulesManager.Status = DomainRulesManager.Status.NONE

    init {
        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.useTransparentNoDimBackground()
            dialog.window?.let { window ->
                if (isAtleastQ()) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        initView()
    }

    fun show() {
        dialog.show()
    }

    private fun getThemeId(): Int {
        val isDark =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return Themes.getBottomsheetCurrentTheme(isDark, persistentState.theme)
    }

    private fun initView() {
        binding.bsdlDomainRuleDesc.text = htmlToSpannedText(activity.getString(R.string.bsdl_block_desc))
        binding.dnsBlockUrl.text = log.queryStr
        binding.dnsBlockIpAddress.text = getResponseIp()
        binding.dnsBlockConnectionFlag.text = log.flag
        binding.dnsBlockIpLatency.text =
            activity.getString(R.string.dns_btm_latency_ms, log.latency.toString())

        binding.dnsMessage.text = log.msg

        val region = log.region
        binding.dnsRegion.isVisible = region.isNotEmpty()
        binding.dnsRegion.text = region

        binding.dnsBlockedTarget.isVisible = log.blockedTarget.isNotEmpty()
        binding.dnsBlockedTarget.text = log.blockedTarget

        setupClickListeners()
        updateAppDetails()
        setupDomainRuleSpinner()

        binding.root.post {
            displayRecordTypeChip()
            displayBlockedSummary()
            displayBlocklistChips()
        }

        activity.lifecycleScope.launch {
            kotlinx.coroutines.delay(150)
            displayFavIcon()
        }
    }

    private fun setupDomainRuleSpinner() {
        val domain = log.queryStr
        if (domain.isEmpty()) {
            binding.bsdlDomainRuleSpinner.isEnabled = false
            return
        }

        binding.bsdlDomainRuleSpinner.adapter =
            FirewallStatusSpinnerAdapter(activity, DomainRulesManager.Status.getLabel(activity))
        val uid = getRuleUid()
        lastStatus = DomainRulesManager.getDomainRule(domain, uid)
        binding.bsdlDomainRuleSpinner.setSelection(lastStatus.id, false)

        var ignoreSelection = true
        binding.bsdlDomainRuleSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (ignoreSelection) {
                        ignoreSelection = false
                        return
                    }

                    val status = DomainRulesManager.Status.getStatus(position)
                    if (status == lastStatus) return
                    applyDomainRule(domain, uid, status)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun updateAppDetails() {
        if (log.appName.isNotEmpty() && log.packageName.isNotEmpty()) {
            binding.dnsAppNameHeader.isVisible = true
            binding.dnsAppName.text = log.appName
            binding.dnsAppIcon.setImageDrawable(getIcon(activity, log.packageName, log.appName))
            return
        }

        io {
            val appNames = FirewallManager.getAppNamesByUid(log.uid)
            if (appNames.isEmpty()) {
                uiCtx { binding.dnsAppNameHeader.isVisible = false }
                return@io
            }
            uiCtx {
                binding.dnsAppNameHeader.isVisible = true
                binding.dnsAppName.text = appNames[0]
                binding.dnsAppIcon.setImageDrawable(getIcon(activity, log.packageName, appNames[0]))
            }
        }
    }

    private fun setupClickListeners() {
        binding.dnsBlockUrl.setOnClickListener { openDomainConnections() }
        binding.dnsBlockIpAddress.setOnClickListener { showIpDetailsDialog() }
        binding.bsdlDomainRuleDesc.setOnClickListener { showRuleInfoDialog() }
        binding.dnsAppNameHeader.setOnClickListener {
            showInfoDialog(log.appName, log.packageName)
        }
    }

    private fun openDomainConnections() {
        val domain = log.queryStr
        if (domain.isEmpty()) return
        val intent = Intent(activity, DomainConnectionsActivity::class.java)
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_TYPE, DomainConnectionsActivity.InputType.DOMAIN.type)
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_DOMAIN, domain)
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_IS_BLOCKED, log.isBlocked)
        intent.putExtra(
            DomainConnectionsActivity.INTENT_EXTRA_TIME_CATEGORY,
            DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS.value
        )
        activity.startActivity(intent)
    }

    private fun showRuleInfoDialog() {
        val dialogBinding = DialogInfoRulesLayoutBinding.inflate(LayoutInflater.from(activity))
        val builder =
            MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        dialogBinding.infoRulesDialogRulesTitle.text = activity.getString(R.string.lbl_domain_rules)
        dialogBinding.infoRulesDialogRulesDesc.text =
            htmlToSpannedText(activity.getString(R.string.bsdl_block_desc))
        dialogBinding.infoRulesDialogRulesIcon.visibility = View.GONE

        dialogBinding.infoRulesDialogCancelImg.setOnClickListener { dialog.dismiss() }
        dialogBinding.infoRulesDialogOkBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun showInfoDialog(appName: String?, packageName: String?) {
        if (appName.isNullOrEmpty() && packageName.isNullOrEmpty()) return
        val dialogBinding = DialogInfoRulesLayoutBinding.inflate(LayoutInflater.from(activity))
        val builder =
            MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        dialogBinding.infoRulesDialogRulesTitle.text = appName
        dialogBinding.infoRulesDialogRulesDesc.text = packageName
        dialogBinding.infoRulesDialogRulesIcon.visibility = View.GONE

        dialogBinding.infoRulesDialogCancelImg.setOnClickListener { dialog.dismiss() }
        dialogBinding.infoRulesDialogOkBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun showIpDetailsDialog() {
        val dialogBinding = DialogIpDetailsLayoutBinding.inflate(LayoutInflater.from(activity))
        val builder =
            MaterialAlertDialogBuilder(activity, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        dialogBinding.ipDetailsFqdnTxt.text = log.queryStr
        dialogBinding.ipDetailsIpDetailsTxt.text = getResponseIps()

        dialogBinding.infoRulesDialogCancelImg.setOnClickListener { dialog.dismiss() }
    }

    private fun displayRecordTypeChip() {
        val typeName = log.typeName
        binding.dnsRecordTypeChip.isVisible = typeName.isNotEmpty()
        binding.dnsRecordTypeChip.text = typeName
    }

    private fun displayBlockedSummary() {
        if (!log.isBlocked && !log.upstreamBlock && log.blockLists.isEmpty()) {
            binding.dnsBlockBlockedDesc.isVisible = false
            return
        }

        val blockedBy = when {
            log.blockedTarget.isNotEmpty() -> log.blockedTarget
            log.blockLists.isNotEmpty() -> activity.getString(R.string.lbl_rules)
            log.proxyId.isNotEmpty() -> log.proxyId
            log.resolver.isNotEmpty() -> log.resolver
            else -> activity.getString(R.string.lbl_domain_rules)
        }

        binding.dnsBlockBlockedDesc.isVisible = true
        binding.dnsBlockBlockedDesc.text =
            activity.getString(R.string.bsdl_blocked_desc, log.queryStr, blockedBy)
    }

    private fun displayBlocklistChips() {
        val blocklists = log.getBlocklists().filter { it.isNotBlank() }
        if (blocklists.isNotEmpty()) {
            val countText = activity.getString(R.string.rsv_blocklist_count_text, blocklists.size)
            binding.dnsBlockBlocklistChip.text = countText
            binding.dnsBlockBlocklistChip.isVisible = true
        } else {
            binding.dnsBlockBlocklistChip.isVisible = false
        }

        val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (ips.isNotEmpty()) {
            val ipLabel = activity.getString(R.string.lbl_ip)
            binding.dnsBlockIpsChip.text =
                activity.getString(R.string.two_argument_colon, ipLabel, ips.size.toString())
            binding.dnsBlockIpsChip.isVisible = true
        } else {
            binding.dnsBlockIpsChip.isVisible = false
        }
    }

    private fun getResponseIp(): String {
        return log.responseIps.split(",").firstOrNull()?.trim().orEmpty()
    }

    private fun getResponseIps(): String {
        val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (ips.isEmpty()) {
            activity.getString(R.string.two_argument_colon, activity.getString(R.string.lbl_ip), "0")
        } else {
            ips.joinToString(separator = "\n")
        }
    }

    private fun displayFavIcon() {
        if (!persistentState.fetchFavIcon) {
            binding.dnsBlockFavIcon.visibility = View.GONE
            return
        }

        val domain = log.queryStr
        if (domain.isEmpty()) {
            binding.dnsBlockFavIcon.visibility = View.GONE
            return
        }

        val trim = domain.dropLastWhile { it == '.' }
        if (FavIconDownloader.isUrlAvailableInFailedCache(trim) != null) {
            binding.dnsBlockFavIcon.visibility = View.GONE
            return
        }

        val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        val nextDnsUrl = FavIconDownloader.constructFavIcoUrlNextDns(trim)
        val duckduckGoUrl = FavIconDownloader.constructFavUrlDuckDuckGo(trim)
        val duckduckgoDomainUrl = FavIconDownloader.getDomainUrlFromFdqnDuckduckgo(trim)

        Glide.with(activity.applicationContext)
            .load(nextDnsUrl)
            .onlyRetrieveFromCache(true)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .error(
                Glide.with(activity.applicationContext)
                    .load(duckduckGoUrl)
                    .onlyRetrieveFromCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .error(
                        Glide.with(activity.applicationContext)
                            .load(duckduckgoDomainUrl)
                            .onlyRetrieveFromCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    )
            )
            .transition(DrawableTransitionOptions.withCrossFade(factory))
            .into(object : CustomViewTarget<ImageView, Drawable>(binding.dnsBlockFavIcon) {
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    binding.dnsBlockFavIcon.visibility = View.GONE
                }

                override fun onResourceCleared(placeholder: Drawable?) {
                    binding.dnsBlockFavIcon.visibility = View.GONE
                    binding.dnsBlockFavIcon.setImageDrawable(null)
                }

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    binding.dnsBlockFavIcon.visibility = View.VISIBLE
                    binding.dnsBlockFavIcon.setImageDrawable(resource)
                }
            })
    }

    private fun applyDomainRule(domain: String, uid: Int, status: DomainRulesManager.Status) {
        io {
            DomainRulesManager.changeStatus(
                domain,
                uid,
                "",
                DomainRulesManager.DomainType.DOMAIN,
                status
            )
            lastStatus = status
            logEvent(domain, status)
        }
    }

    private fun logEvent(domain: String, status: DomainRulesManager.Status) {
        eventLogger.log(
            EventType.FW_RULE_MODIFIED,
            Severity.LOW,
            "DNS log rule",
            EventSource.UI,
            false,
            "Domain rule updated for $domain: ${status.name}"
        )
    }

    private fun getRuleUid(): Int {
        return when (log.uid) {
            Constants.INVALID_UID,
            Constants.MISSING_UID -> Constants.UID_EVERYBODY
            else -> log.uid
        }
    }

    private fun io(f: suspend () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

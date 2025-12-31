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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
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
import com.celzero.bravedns.database.DnsLog
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.glide.FavIconDownloader
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.activity.DomainConnectionsActivity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.getIcon
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.useTransparentNoDimBackground
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DnsBlocklistDialog(
    private val activity: FragmentActivity,
    private val log: DnsLog
) : KoinComponent {
    private val dialog = BottomSheetDialog(activity, getThemeId())

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private var lastStatus by mutableStateOf(DomainRulesManager.Status.NONE)
    private var showRuleInfo by mutableStateOf(false)
    private var showIpDetails by mutableStateOf(false)
    private var showAppInfo by mutableStateOf(false)
    private var ipDetailsText by mutableStateOf("")
    private var appInfoText by mutableStateOf("")

    init {
        val composeView = ComposeView(activity)
        composeView.setContent {
            RethinkTheme {
                DnsBlocklistContent()
            }
        }
        dialog.setContentView(composeView)
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

    @Composable
    private fun DnsBlocklistContent() {
        val borderColor = Color(UIUtils.fetchColor(activity, R.attr.border))
        val ruleLabels = remember { DomainRulesManager.Status.getLabel(activity).toList() }
        val ruleOptions =
            remember {
                listOf(
                    DomainRulesManager.Status.NONE,
                    DomainRulesManager.Status.BLOCK,
                    DomainRulesManager.Status.TRUST
                )
            }
        var ruleExpanded by remember { mutableStateOf(false) }
        val currentRule = remember { getRuleUid() }
        val selectedIndex = ruleOptions.indexOf(lastStatus).coerceAtLeast(0)
        var selectedLabel by remember { mutableStateOf(ruleLabels[selectedIndex]) }

        LaunchedEffect(Unit) {
            val domain = log.queryStr
            if (domain.isNotEmpty()) {
                lastStatus = DomainRulesManager.getDomainRule(domain, currentRule)
                selectedLabel = ruleLabels[lastStatus.id]
            }
            ipDetailsText = getResponseIps()
            appInfoText = log.packageName
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(60.dp)
                        .height(3.dp)
                        .background(borderColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (persistentState.fetchFavIcon) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                visibility = View.GONE
                            }
                        },
                        update = { view -> loadFavIcon(view) },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.queryStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { openDomainConnections() }
                    )
                    Text(
                        text = log.flag,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = activity.getString(
                            R.string.dns_btm_latency_ms,
                            log.latency.toString()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = getResponseIp(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { showIpDetails = true }
                    )
                }
            }

            HtmlText(
                html = activity.getString(R.string.bsdl_block_desc),
                modifier = Modifier.padding(horizontal = 10.dp)
            ) { showRuleInfo = true }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = activity.getString(R.string.lbl_domain_rules))
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { ruleExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = selectedLabel)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "▼")
                    }
                    DropdownMenu(
                        expanded = ruleExpanded,
                        onDismissRequest = { ruleExpanded = false }
                    ) {
                        ruleOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(ruleLabels[index]) },
                                onClick = {
                                    ruleExpanded = false
                                    if (option == lastStatus) return@DropdownMenuItem
                                    val domain = log.queryStr
                                    if (domain.isNotEmpty()) {
                                        selectedLabel = ruleLabels[index]
                                        applyDomainRule(domain, currentRule, option)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (log.msg.isNotEmpty()) {
                Text(
                    text = log.msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            if (log.region.isNotEmpty()) {
                Text(
                    text = log.region,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            if (log.blockedTarget.isNotEmpty()) {
                Text(
                    text = log.blockedTarget,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            BlockedSummary()

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val blocklists = log.getBlocklists().filter { it.isNotBlank() }
                if (blocklists.isNotEmpty()) {
                    val countText =
                        activity.getString(R.string.rsv_blocklist_count_text, blocklists.size)
                    ChipText(countText)
                }
                val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (ips.isNotEmpty()) {
                    val ipLabel = activity.getString(R.string.lbl_ip)
                    val text =
                        activity.getString(
                            R.string.two_argument_colon,
                            ipLabel,
                            ips.size.toString()
                        )
                    ChipText(text)
                }
                if (log.typeName.isNotEmpty()) {
                    ChipText(log.typeName)
                }
            }

            AppInfoRow()
        }

        if (showRuleInfo) {
            AlertDialog(
                onDismissRequest = { showRuleInfo = false },
                title = { Text(text = activity.getString(R.string.lbl_domain_rules)) },
                text = { HtmlText(html = activity.getString(R.string.bsdl_block_desc)) },
                confirmButton = {
                    TextButton(onClick = { showRuleInfo = false }) {
                        Text(text = activity.getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }

        if (showIpDetails) {
            AlertDialog(
                onDismissRequest = { showIpDetails = false },
                title = { Text(text = log.queryStr) },
                text = { Text(text = ipDetailsText) },
                confirmButton = {
                    TextButton(onClick = { showIpDetails = false }) {
                        Text(text = activity.getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }

        if (showAppInfo) {
            AlertDialog(
                onDismissRequest = { showAppInfo = false },
                title = { Text(text = log.appName) },
                text = { Text(text = appInfoText) },
                confirmButton = {
                    TextButton(onClick = { showAppInfo = false }) {
                        Text(text = activity.getString(R.string.hs_download_positive_default))
                    }
                }
            )
        }
    }

    @Composable
    private fun HtmlText(html: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
            },
            update = { view ->
                view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                view.setOnClickListener {
                    onClick?.invoke()
                }
            },
            modifier = modifier
        )
    }

    @Composable
    private fun ChipText(text: String) {
        Box(
            modifier =
                Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun BlockedSummary() {
        if (!log.isBlocked && !log.upstreamBlock && log.blockLists.isEmpty()) return

        val blockedBy =
            when {
                log.blockedTarget.isNotEmpty() -> log.blockedTarget
                log.blockLists.isNotEmpty() -> activity.getString(R.string.lbl_rules)
                log.proxyId.isNotEmpty() -> log.proxyId
                log.resolver.isNotEmpty() -> log.resolver
                else -> activity.getString(R.string.lbl_domain_rules)
            }
        Text(
            text = activity.getString(R.string.bsdl_blocked_desc, log.queryStr, blockedBy),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }

    @Composable
    private fun AppInfoRow() {
        if (log.appName.isEmpty() && log.packageName.isEmpty()) return

        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).clickable {
                    showAppInfo = true
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { ctx -> ImageView(ctx) },
                update = { view ->
                    view.setImageDrawable(getIcon(activity, log.packageName, log.appName))
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.appName, style = MaterialTheme.typography.bodyMedium)
        }
    }

    private fun openDomainConnections() {
        val domain = log.queryStr
        if (domain.isEmpty()) return
        val intent = Intent(activity, DomainConnectionsActivity::class.java)
        intent.putExtra(
            DomainConnectionsActivity.INTENT_EXTRA_TYPE,
            DomainConnectionsActivity.InputType.DOMAIN.type
        )
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_DOMAIN, domain)
        intent.putExtra(DomainConnectionsActivity.INTENT_EXTRA_IS_BLOCKED, log.isBlocked)
        intent.putExtra(
            DomainConnectionsActivity.INTENT_EXTRA_TIME_CATEGORY,
            DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS.value
        )
        activity.startActivity(intent)
    }

    private fun getResponseIp(): String {
        return log.responseIps.split(",").firstOrNull()?.trim().orEmpty()
    }

    private fun getResponseIps(): String {
        val ips = log.responseIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (ips.isEmpty()) {
            activity.getString(
                R.string.two_argument_colon,
                activity.getString(R.string.lbl_ip),
                "0"
            )
        } else {
            ips.joinToString(separator = "\n")
        }
    }

    private fun loadFavIcon(view: ImageView) {
        if (!persistentState.fetchFavIcon) {
            view.visibility = View.GONE
            return
        }

        val domain = log.queryStr
        if (domain.isEmpty()) {
            view.visibility = View.GONE
            return
        }

        val trim = domain.dropLastWhile { it == '.' }
        if (FavIconDownloader.isUrlAvailableInFailedCache(trim) != null) {
            view.visibility = View.GONE
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
            .into(
                object : CustomViewTarget<ImageView, Drawable>(view) {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        view.isVisible = false
                    }

                    override fun onResourceCleared(placeholder: Drawable?) {
                        view.isVisible = false
                        view.setImageDrawable(null)
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        view.isVisible = true
                        view.setImageDrawable(resource)
                    }
                }
            )
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
}

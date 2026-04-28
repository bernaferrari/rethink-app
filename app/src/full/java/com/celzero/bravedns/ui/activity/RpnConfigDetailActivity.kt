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
package com.celzero.bravedns.ui.activity

import Logger
import Logger.LOG_TAG_UI
import android.animation.ValueAnimator
import android.content.ClipData
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import androidx.core.graphics.withRotation
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.Toast
import com.celzero.bravedns.ui.BaseActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgIncludeAppsAdapter
import com.celzero.bravedns.data.SsidItem
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.databinding.ActivityRpnConfigDetailBinding
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.NetworkLogsActivity.Companion.RULES_SEARCH_ID_RPN
import com.celzero.bravedns.ui.activity.RpnConfigDetailActivity.Companion.STATS_POLL_MS
import com.celzero.bravedns.ui.dialog.CountrySsidDialog
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.ui.fragment.ServerSelectionFragment.Companion.AUTO_SERVER_ID
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.SsidPermissionManager
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.IPMetadata
import com.celzero.firestack.backend.RouterStats
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import kotlin.math.abs

/**
 * Detail screen for a server-provided WireGuard / WIN proxy.
 *
 * Hero banner: flag emoji + country name + city + server-key chip.
 *
 * Stats card: The poll runs every [STATS_POLL_MS] milliseconds while the activity
 * is resumed, and is canceled on pause so it does not drain battery in the background.
 */
class RpnConfigDetailActivity : BaseActivity(R.layout.activity_rpn_config_detail) {
    private val b by viewBinding(ActivityRpnConfigDetailBinding::bind)
    private val persistentState by inject<PersistentState>()
    private val mappingViewModel: ProxyAppsMappingViewModel by viewModel()

    private var configKey: String = ""
    private var countryConfig: CountryConfig? = null

    /** Coroutine that polls VpnController every [STATS_POLL_MS] ms. */
    private var statsJob: Job? = null
    /** Looping spin animator for the refresh chip icon. */
    private var chipAnimator: ValueAnimator? = null

    // SSID permission callback
    private val ssidPermissionCallback = object : SsidPermissionManager.PermissionCallback {
        override fun onPermissionsGranted() {
            lifecycleScope.launch { refreshSsidSection() }
        }
        override fun onPermissionsDenied() {
            lifecycleScope.launch {
                b.ssidCheck.isChecked = false
                refreshSsidSection()
            }
        }
        override fun onPermissionsRationale() {
            showSsidPermissionExplanationDialog()
        }
    }

    companion object {
        const val INTENT_EXTRA_FROM_SERVER_SELECTION = "FROM_SERVER_SELECTION"
        const val INTENT_EXTRA_CONFIG_KEY = "CONFIG_KEY"

        /** Polling interval for live stats. */
        private const val STATS_POLL_MS = 2_000L
    }

    private fun Context.isDarkThemeOn(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            WindowInsetsControllerCompat(window, window.decorView)
                .isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        configKey = intent.getStringExtra(INTENT_EXTRA_CONFIG_KEY) ?: ""

        setupCollapsingAnimation()
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    override fun onPause() {
        super.onPause()
        // Stop polling to conserve battery when screen is not visible.
        cancelStatsJob()
        chipAnimator?.cancel()
        chipAnimator = null
    }

    private fun init() {
        io {
            val proxy = if (configKey.isBlank() || configKey.contains(AUTO_SERVER_ID, ignoreCase = true))
                VpnController.getWinByKey("")
            else
                VpnController.getWinByKey(configKey)

            uiCtx {
                if (configKey.isBlank() || proxy == null) {
                    showInvalidConfigDialog()
                    return@uiCtx
                }
                populateHeroBanner()
                observeAppCount(configKey)
                loadConfigSettings(configKey)
            }
        }
    }

    /**
     * Populates flag emoji, country name, city subtitle and key chip in
     * the hero banner. Stats polling starts immediately; client IPs are
     * resolved separately in a background coroutine so VpnController
     * stats appear without delay.
     */
    private fun populateHeroBanner() {
        // Placeholder while we fetch from DB.
        b.configNameText.text = ""
        b.tvHeroCity.text     = ""
        b.tvHeroFlag.text     = "\uD83C\uDF10" // globe

        // Show inline shimmer for client IPs (stats table is already visible).
        showClientIpShimmer()

        io {
            val config = RpnProxyManager.getCountryConfigByKey(configKey)

            uiCtx {
                // Cache for use in the stats table and SSID.
                countryConfig = config

                // SSID section needs countryConfig.
                setupSsidSection(configKey)
                // Banner.
                if (config != null) {
                    b.tvHeroFlag.text = config.flagEmoji
                    b.configNameText.text = config.countryName
                    val city = config.city.ifBlank { config.serverLocation }
                    b.tvHeroCity.text = city.ifBlank { config.cc }
                } else {
                    b.tvHeroFlag.text = "\uD83C\uDF10"
                    b.configNameText.text = configKey.ifBlank { getString(R.string.lbl_server_config) }
                    b.tvHeroCity.text     = ""
                }

                startStatsPolling(configKey)
            }

            resolveClientIps(configKey)
        }
    }

    /**
     * Resolves client tunnel IPs (and all [IPMetadata]) on IO and applies them on the main thread.
     * Runs independently of stats polling so the table renders fast.
     */
    private suspend fun resolveClientIps(id: String) {
        var ip4Meta: IPMetadata? = null
        var ip6Meta: IPMetadata? = null
        var sinceTs = 0L
        try {
            val pid = if (id.contains(AUTO_SERVER_ID, ignoreCase = true)) {
                VpnController.getWinProxyId() ?: (Backend.RpnWin + "**")
            } else {
                Backend.RpnWin + id
            }
            sinceTs = VpnController.getProxyStats(pid)?.since ?: 0L
            val cachedSince = RpnProxyManager.getCachedSinceTs(id)
            val cached      = RpnProxyManager.getCachedIpMeta(id)

            // Cache hit: tunnel has not reconnected and we already have metadata.
            if (sinceTs > 0L && sinceTs == cachedSince && cached != null) {
                if (DEBUG) Logger.d(LOG_TAG_UI, "resolveClientIps[$id]: cache hit, since=$sinceTs")
                uiCtx { applyClientIps(cached.first, cached.second) }
                return
            }

            if (DEBUG) Logger.d(
                LOG_TAG_UI,
                "resolveClientIps[$id]: live fetch, sinceTs=$sinceTs cachedSince=$cachedSince"
            )
            // GoVpnAdapter handles AUTO and empty-string ids centrally; pass id as-is.
            val client = VpnController.getRpnClientInfoById(id)
            ip4Meta = client?.iP4()
            ip6Meta = client?.iP6()
            Logger.v(LOG_TAG_UI, "client ips resolved for $id: ip4=${ip4Meta?.ip} ip6=${ip6Meta?.ip}, sinceTs=$sinceTs")
        } catch (e: Exception) {
            Logger.w(LOG_TAG_UI, "failed to resolve client ips: ${e.message}")
        }
        // Persist to Manager cache; preserves selectedAt for this server key.
        RpnProxyManager.updateIpMeta(id, sinceTs, ip4Meta, ip6Meta)
        uiCtx { applyClientIps(ip4Meta, ip6Meta) }
    }

    /** Show inline shimmer placeholders for client IPv4/IPv6 value cells. */
    private fun showClientIpShimmer() {
        b.shimmerIpv4.visibility = View.VISIBLE
        b.shimmerIpv4.startShimmer()
        b.valueIpv4.visibility   = View.GONE

        b.shimmerIpv6.visibility = View.VISIBLE
        b.shimmerIpv6.startShimmer()
        b.valueIpv6.visibility   = View.GONE
    }

    /**
     * Replaces the inline shimmer with rich IP + metadata text.
     * ASN / location / providerUrl are embedded inside
     */
    private fun applyClientIps(ip4: IPMetadata?, ip6: IPMetadata?) {
        val na = getString(R.string.lbl_not_available_short)

        // IPv4
        b.shimmerIpv4.stopShimmer()
        b.shimmerIpv4.visibility = View.GONE
        b.valueIpv4.visibility   = View.VISIBLE
        b.valueIpv4.text = ip4
            ?.takeIf { it.ip?.isNotBlank() == true }
            ?.let { buildIpDetailSpan(it) }
            ?: na

        // ipv6, hide the entire row if unavailable
        b.shimmerIpv6.stopShimmer()
        b.shimmerIpv6.visibility = View.GONE
        val ip6Addr = ip6?.ip?.takeIf { it.isNotBlank() }
        if (ip6Addr != null && ip6 != null) {
            b.rowIpv6.visibility   = View.VISIBLE
            b.valueIpv6.visibility = View.VISIBLE
            b.valueIpv6.text       = buildIpDetailSpan(ip6)
        } else {
            b.rowIpv6.visibility = View.GONE
        }
    }

    /**
     * Builds a premium multi-line [SpannableStringBuilder] for a single [IPMetadata].
     *
     * ```
     * 10.0.0.1                                              ← monospace bold, 1.07×
     * ASN  AS13335 · Cloudflare Inc · net.cloudflare.com   ← only when any present
     * LOC  Frankfurt · 50.1109°, 8.6821°                   ← only when any present
     * via  cloudflare.com                                   ← only when present
     * ```
     *
     * Label tokens ("ASN", "LOC", "via") are 80 % size, bold, muted.
     * Lines whose value is entirely blank/zero are never appended.
     */
    private fun buildIpDetailSpan(meta: IPMetadata): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val labelColor = fetchColor(this, R.attr.primaryLightColorText)

        fun styleLabel(start: Int, end: Int) {
            sb.setSpan(ForegroundColorSpan(labelColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.80f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun appendLine(label: String, value: String) {
            if (value.isBlank()) return
            sb.append("\n")
            val ls = sb.length
            sb.append(label)
            styleLabel(ls, sb.length)
            sb.append("  $value")
        }

        // ip
        val ipStart = 0
        sb.append(meta.ip ?: "")
        val ipEnd = sb.length
        sb.setSpan(StyleSpan(Typeface.BOLD),  ipStart, ipEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(TypefaceSpan("monospace"), ipStart, ipEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(1.07f),   ipStart, ipEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // asn
        val asnParts = buildList {
            val asn = meta.asn ?: ""
            val org = meta.asnOrg ?: ""
            val dom = meta.asnDom ?: ""
            if (asn.isNotBlank()) add("AS$asn")
            if (org.isNotBlank()) add(org)
            if (dom.isNotBlank()) add(dom)
        }
        if (asnParts.isNotEmpty()) appendLine("ASN", asnParts.joinToString("  ·  "))

        // loc
        val locParts = buildList {
            val city = meta.city ?: ""
            val lat  = meta.lat
            val lon  = meta.lon
            if (city.isNotBlank()) add(city)
            if (lat != 0.0 || lon != 0.0) add(String.format(Locale.US, "%.4f°, %.4f°", lat, lon))
        }
        if (locParts.isNotEmpty()) appendLine("LOC", locParts.joinToString("  ·  "))

        // provider url
        val providerUrl = meta.providerURL ?: ""
        if (providerUrl.isNotBlank()) {
            val display = providerUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
            appendLine("VIA", display)
        }

        return sb
    }


    private fun cancelStatsJob() {
        if (statsJob?.isActive == true) statsJob?.cancel()
        statsJob = null
    }

    /**
     * Launches a coroutine that fetches and applies live stats every
     * [STATS_POLL_MS] ms. Client IPs are resolved separately by
     * [resolveClientIps]: this method only handles VpnController stats.
     */
    private fun startStatsPolling(id: String) {
        cancelStatsJob()
        statsJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    fetchAndApplyStats(id)
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_UI, "stats poll error: ${e.message}")
                }
                delay(STATS_POLL_MS)
            }
        }
    }

    private suspend fun fetchAndApplyStats(id: String) {
        // For AUTO use the live win proxy ID from the tunnel so the stats lookup targets the
        // real proxy entry rather than a hardcoded wildcard.  Fall back to the wildcard only
        val pid = if (id.contains(AUTO_SERVER_ID, ignoreCase = true)) {
            VpnController.getWinProxyId() ?: (Backend.RpnWin + "**")
        } else {
            Backend.RpnWin + id
        }
        val statusPair = VpnController.getProxyStatusById(pid)
        val stats = VpnController.getProxyStats(pid)
        val who = VpnController.getWin()?.who()
        val config = countryConfig
        // Use the time when this server key was selected by the user, not the VPN uptime.
        val selectedSinceTs = RpnProxyManager.getSelectedSinceTs(id)

        // Re-fetch client IPs when the tunnel has reconnected (stats.since changed).
        // stats.since is epoch-ms; a change means a new tunnel session started and the
        // assigned client IPs may have rotated.
        val currentSince = stats?.since ?: 0L
        if (currentSince > 0L && currentSince != RpnProxyManager.getCachedSinceTs(id)) {
            Logger.d(LOG_TAG_UI, "since changed to $currentSince for $id; re-fetching client IPs")
            resolveClientIps(id)
        }

        uiCtx {
            applyStats(statusPair, stats, config, who, selectedSinceTs)
        }
    }

    /**
     * Populates every row of the stats table from live data (except client
     * IPs which are populated by [applyClientIps]).  Runs on the main thread.
     *
     * @param selectedSinceTs epoch-ms when this server key was selected by the user
     *   (supplied by [RpnProxyManager.getSelectedSinceTs]); shown as the "active since" value.
     */
    private fun applyStats(
        statusPair: Pair<Long?, String>,
        stats: RouterStats?,
        config: CountryConfig?,
        who: String?,
        selectedSinceTs: Long
    ) {
        val ps = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
        val statusText  = buildStatusText(ps, stats, statusPair.second)
        val statusColor = buildStatusColor(ps, stats)
        b.valueStatus.text = statusText
        b.valueStatus.setTextColor(fetchColor(this, statusColor))

        if (who.isNullOrEmpty()) {
            b.rowWho.visibility = View.GONE
        } else {
            b.rowWho.visibility = View.VISIBLE
            b.valueWho.text = who
        }

        val rx = stats?.rx ?: 0L
        val tx = stats?.tx ?: 0L
        b.valueRx.text = Utilities.humanReadableByteCount(rx, true)
        b.valueTx.text = Utilities.humanReadableByteCount(tx, true)

        val lastOK = stats?.lastOK ?: 0L
        b.valueLastOk.text = if (lastOK > 0L)
            DateUtils.getRelativeTimeSpanString(
                lastOK, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
        else getString(R.string.lbl_never)

        // Show when the user selected this server, not the VPN tunnel's uptime.
        b.valueSince.text = if (selectedSinceTs > 0L)
            DateUtils.getRelativeTimeSpanString(
                selectedSinceTs, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
        else getString(R.string.lbl_never)

        val loadPct  = config?.load ?: 0
        val linkMbps = config?.link ?: 0
        b.valueLoad.text = buildLoadSpeedText(loadPct, linkMbps)

        // only shown when proxy is in a failing state.
        val isFailing = isFailing(ps, stats)
        if (isFailing && (rx == 0L && tx == 0L && selectedSinceTs > 0L)) {
            b.rowErrors.visibility = View.VISIBLE
            b.dividerErrors.visibility = View.VISIBLE
            b.valueErrors.text = getString(R.string.status_failing)
        } else {
            b.rowErrors.visibility = View.GONE
            b.dividerErrors.visibility = View.GONE
        }
    }


    private fun buildStatusText(
        status  : UIUtils.ProxyStatus?,
        stats   : RouterStats?,
        errMsg  : String?
    ): String {
        if (status == null) {
            return if (!errMsg.isNullOrEmpty())
                "${getString(R.string.status_waiting).replaceFirstChar(Char::titlecase)} ($errMsg)"
            else
                getString(R.string.status_waiting).replaceFirstChar(Char::titlecase)
        }
        if (status == UIUtils.ProxyStatus.TPU) {
            return getString(UIUtils.getProxyStatusStringRes(status.id))
                .replaceFirstChar(Char::titlecase)
        }
        if (isFailing(status, stats)) {
            return getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
        }
        val base = getString(UIUtils.getProxyStatusStringRes(status.id))
            .replaceFirstChar(Char::titlecase)
        return base
    }

    private fun buildStatusColor(status: UIUtils.ProxyStatus?, stats: RouterStats?): Int {
        return when {
            isFailing(status, stats) -> R.attr.chipTextNegative
            status == UIUtils.ProxyStatus.TOK -> R.attr.accentGood
            status == UIUtils.ProxyStatus.TUP ||
            status == UIUtils.ProxyStatus.TZZ ||
            status == UIUtils.ProxyStatus.TNT -> R.attr.chipTextNeutral
            status == null -> R.attr.primaryLightColorText
            else -> R.attr.chipTextNegative
        }
    }

    private fun isFailing(status: UIUtils.ProxyStatus?, stats: RouterStats?): Boolean {
        val now = System.currentTimeMillis()
        val lastOK = stats?.lastOK ?: 0L
        val since  = stats?.since  ?: 0L
        return now - since > WireguardManager.WG_UPTIME_THRESHOLD && lastOK == 0L
                && status != null && status != UIUtils.ProxyStatus.TPU
    }

    /**
     * Returns a human-readable combined load + speed string, e.g.
     * "35% · Normal  ·  1 Gbps · Fast"
     * If either piece is missing only the available one is shown.
     */
    private fun buildLoadSpeedText(loadPct: Int, linkMbps: Int): String {
        val parts = mutableListOf<String>()
        if (loadPct > 0) {
            val tier = when {
                loadPct <= 20 -> getString(R.string.server_load_light)
                loadPct <= 40 -> getString(R.string.server_load_normal)
                loadPct <= 60 -> getString(R.string.server_load_busy)
                loadPct <= 80 -> getString(R.string.server_load_very_busy)
                else -> getString(R.string.server_load_overloaded)
            }
            parts += "$loadPct% · $tier"
        }
        if (linkMbps > 0) {
            val formatted = when {
                linkMbps >= 10_000 -> String.format(Locale.US, "%.0f Gbps", linkMbps / 1_000.0)
                linkMbps >=  1_000 -> {
                    val g = linkMbps / 1_000.0
                    if (g == g.toLong().toDouble())
                        String.format(Locale.US, "%.0f Gbps", g)
                    else
                        String.format(Locale.US, "%.1f Gbps", g)
                }
                else -> "$linkMbps Mbps"
            }
            val tier = when {
                linkMbps >= 10_000 -> getString(R.string.server_speed_very_fast)
                linkMbps >= 1_000 -> getString(R.string.server_speed_fast)
                linkMbps >= 100 -> getString(R.string.server_speed_good)
                linkMbps >= 10 -> getString(R.string.server_speed_moderate)
                else -> getString(R.string.server_speed_slow)
            }
            parts += "$formatted · $tier"
        }
        return parts.joinToString("   ").ifBlank { "-" }
    }

    private fun observeAppCount(proxyId: String) {
        if (proxyId.isBlank()) return
        mappingViewModel.getAppCountById(proxyId).observe(this) { count ->
            // Don't override the "All apps" state when catch-all is active
            if (b.catchAllCheck.isChecked) return@observe
            val c = count ?: 0
            b.appsLabel.text = "Apps ($c)"
            b.appsLabel.setTextColor(
                fetchColor(this, if (c > 0) R.attr.accentGood else R.attr.accentBad)
            )
        }
    }

    private fun loadConfigSettings(key: String) {
        if (key.isBlank()) {
            b.otherSettingsCard.visibility = View.GONE
            b.mobileSsidSettingsCard.visibility = View.GONE
            return
        }
        io {
            val config = RpnProxyManager.getCountryConfigByKey(key)
            uiCtx {
                if (config != null) {
                    b.lockdownCheck.isChecked = config.lockdown
                    b.catchAllCheck.isChecked = config.catchAll
                    b.useMobileCheck.isChecked = config.mobileOnly
                    b.ssidCheck.isChecked = config.ssidBased
                    b.otherSettingsCard.visibility = View.VISIBLE
                    b.mobileSsidSettingsCard.visibility = View.VISIBLE
                    // Update apps section immediately based on catchAll state
                    if (config.catchAll) {
                        b.applicationsBtn.isEnabled = false
                        b.applicationsBtn.alpha = 0.5f
                        b.appsLabel.setTextColor(fetchColor(this, R.attr.primaryTextColor))
                        b.appsLabel.text = "All apps"
                    }
                } else {
                    showInvalidConfigDialog()
                }
                setupClickListeners(key)
            }
        }
    }

    private fun setupClickListeners(key: String) {
        b.applicationsBtn.setOnClickListener { openAppsDialog() }
        b.hopBtn.setOnClickListener         { openHopDialog() }
        b.logsBtn.setOnClickListener        { openLogsDialog(key) }

        b.configIdText.setOnClickListener {
            initiateReconnect(key)
        }
        b.valueWho.setOnClickListener {
            val text = b.valueWho.text?.toString().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("who", text))
            Utilities.showToastUiCentered(
                this,
                getString(R.string.copied_clipboard),
                Toast.LENGTH_SHORT
            )
        }

        if (configKey.isBlank()) {
            b.otherSettingsCard.visibility     = View.GONE
            b.mobileSsidSettingsCard.visibility = View.GONE
            return
        }

        b.catchAllCheck.setOnCheckedChangeListener { _, isChecked ->
            io {
                RpnProxyManager.setCatchAllForWinServer(configKey, isChecked)
                uiCtx {
                    // Update apps section immediately to reflect the new catch-all state
                    b.applicationsBtn.isEnabled = !isChecked
                    b.applicationsBtn.alpha = if (isChecked) 0.5f else 1.0f
                    if (isChecked) {
                        b.appsLabel.setTextColor(fetchColor(this, R.attr.primaryTextColor))
                        b.appsLabel.text = "All apps"
                    } else {
                        observeAppCount(configKey)
                    }
                    Utilities.showToastUiCentered(
                        this,
                        if (isChecked) "Catch all mode enabled" else "Catch all mode disabled",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }

        b.lockdownCheck.setOnCheckedChangeListener { _, isChecked ->
            io {
                RpnProxyManager.setLockdownForWinServer(configKey, isChecked)
                uiCtx {
                    Utilities.showToastUiCentered(
                        this,
                        if (isChecked) "Lockdown mode enabled" else "Lockdown mode disabled",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }

        b.useMobileCheck.setOnCheckedChangeListener { _, isChecked ->
            io {
                RpnProxyManager.setMobileOnlyForWinServer(configKey, isChecked)
                uiCtx {
                    Utilities.showToastUiCentered(
                        this,
                        if (isChecked) "Mobile data only enabled" else "Mobile data only disabled",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }

        b.ssidCheck.setOnCheckedChangeListener { _, isChecked ->
            io {
                RpnProxyManager.setSsidEnabledForWinServer(configKey, isChecked)
                uiCtx {
                    Utilities.showToastUiCentered(
                        this,
                        if (isChecked) "SSID based enabled" else "SSID based disabled",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }

        b.catchAllRl.setOnClickListener  { b.catchAllCheck.performClick() }
        b.useMobileRl.setOnClickListener { b.useMobileCheck.performClick() }
        b.ssidFilterRl.setOnClickListener{ b.ssidCheck.performClick() }
    }

    private fun initiateReconnect(key: String) {
        setRefreshReconnectEnabled(false)
        io {
            // GoVpnAdapter handles AUTO and empty-string ids centrally
            val reconnect = if (key.contains(AUTO_SERVER_ID, ignoreCase = true)) {
                VpnController.reconnectRpnProxy("")
            } else {
                VpnController.reconnectRpnProxy(key)
            }
            uiCtx {
                setRefreshReconnectEnabled(true)
                if (reconnect) {
                    Utilities.showToastUiCentered(this, getString(R.string.dc_refresh_toast), Toast.LENGTH_SHORT)
                } else {
                    Utilities.showToastUiCentered(this, "Failed to reconnect", Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private fun setRefreshReconnectEnabled(enabled: Boolean) {
        val targetAlpha = if (enabled) 1f else 0.40f
        b.configIdText.isEnabled = enabled
        b.configIdText.isClickable = enabled
        b.configIdText.animate().alpha(targetAlpha).setDuration(160).start()
        if (enabled) stopChipIconAnimation() else startChipIconAnimation()
    }

    /** Starts a continuous clockwise spin on the chip's icon only. */
    private fun startChipIconAnimation() {
        chipAnimator?.cancel()
        val raw = b.configIdText.chipIcon
        val spinning: RotatingDrawable = raw as? RotatingDrawable ?: RotatingDrawable(raw ?: return).also { b.configIdText.chipIcon = it }
        spinning.rotation = 0f
        chipAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { spinning.rotation = it.animatedValue as Float }
            start()
        }
    }

    /**
     * Stops the spin and eases the chip icon back to 0° so it doesn't
     * snap abruptly when the reconnect operation completes.
     */
    private fun stopChipIconAnimation() {
        chipAnimator?.cancel()
        chipAnimator = null
        val icon = b.configIdText.chipIcon as? RotatingDrawable ?: return
        ValueAnimator.ofFloat(icon.rotation, 0f).apply {
            duration = 200L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { icon.rotation = it.animatedValue as Float }
            start()
        }
    }

    /**
     * A [DrawableWrapper] that applies a canvas rotation around its own centre
     * during [draw], leaving the host view's background and tint untouched.
     */
    private class RotatingDrawable(drawable: Drawable) : DrawableWrapper(drawable.mutate()) {
        var rotation: Float = 0f
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            val b = bounds
            canvas.withRotation(rotation, b.exactCenterX(), b.exactCenterY()) {
                super@RotatingDrawable.draw(this)
            }
        }
    }

    private fun showInvalidConfigDialog() {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.lbl_wireguard))
            .setMessage(getString(R.string.config_invalid_desc))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.fapps_info_dialog_positive_btn)) { _, _ -> finish() }
            .create().show()
    }

    private fun openHopDialog() {
        Utilities.showToastUiCentered(this, "Configure Hops - Coming Soon", Toast.LENGTH_SHORT)
    }

    private fun openLogsDialog(proxyId: String) {
        val intent = Intent(this, NetworkLogsActivity::class.java)
        val query = RULES_SEARCH_ID_RPN + proxyId
        intent.putExtra(Constants.SEARCH_QUERY, query)
        startActivity(intent)
    }

    private fun openAppsDialog() {
        if (configKey.isBlank()) {
            Logger.e(LOG_TAG_UI, "openAppsDialog: configKey blank or proxy null")
            return
        }
        val proxyId   = configKey
        val proxyName = configKey
        val adapter   = WgIncludeAppsAdapter(this, proxyId, proxyName)
        mappingViewModel.apps.observe(this) { adapter.submitData(lifecycle, it) }
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) themeId = R.style.App_Dialog_NoDim
        val dlg = WgIncludeAppsDialog(this, adapter, mappingViewModel, themeId, proxyId, proxyName)
        dlg.setCanceledOnTouchOutside(false)
        dlg.show()
    }

    private fun setupCollapsingAnimation() {
        b.appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val pct = abs(verticalOffset).toFloat() / appBarLayout.totalScrollRange.toFloat()
            b.tvHeroFlag.alpha = 1f - pct
            b.configNameText.alpha = 1f - pct
            b.tvHeroCity.alpha = 1f - pct
            b.configIdText.alpha = 1f - pct
            val scale = 1f - pct * 0.08f
            b.configNameText.scaleX = scale
            b.configNameText.scaleY = scale
        }
    }

    private fun setupSsidSection(cc: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            countryConfig = RpnProxyManager.getCountryConfigByKey(cc)
            withContext(Dispatchers.Main) { setupSsidSectionUI(countryConfig) }
        }
    }

    private fun setupSsidSectionUI(config: CountryConfig?) {
        val sw = b.ssidCheck
        if (config == null) { sw.isEnabled = false; return }
        if (!SsidPermissionManager.isDeviceSupported(this)) {
            sw.isEnabled = false
            b.ssidFilterRl.visibility = View.GONE
            return
        }
        sw.isEnabled = true
        b.ssidFilterRl.visibility = View.VISIBLE
        val ssidItems = SsidItem.parseStorageList(config.ssids)
        sw.isChecked = config.ssidBased
        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !SsidPermissionManager.hasRequiredPermissions(this)) {
                SsidPermissionManager.checkAndRequestPermissions(this, ssidPermissionCallback)
                return@setOnCheckedChangeListener
            }
            if (isChecked && !SsidPermissionManager.isLocationEnabled(this)) {
                showLocationEnableDialog(); return@setOnCheckedChangeListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                RpnProxyManager.updateSsidBased(configKey, isChecked)
                withContext(Dispatchers.Main) { if (isChecked) openSsidDialog() }
            }
        }
    }

    private fun refreshSsidSection() {
        lifecycleScope.launch(Dispatchers.IO) {
            countryConfig = RpnProxyManager.getCountryConfigByKey(configKey)
            withContext(Dispatchers.Main) { setupSsidSectionUI(countryConfig) }
        }
    }

    private fun openSsidDialog() {
        if (configKey.isBlank() || countryConfig == null) return
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) themeId = R.style.App_Dialog_NoDim
        val dlg = CountrySsidDialog(
            this, themeId, configKey,
            countryConfig?.countryName ?: configKey,
            countryConfig?.ssids.orEmpty()
        ) { newSsids ->
            lifecycleScope.launch(Dispatchers.IO) {
                RpnProxyManager.updateSsids(configKey, newSsids)
                withContext(Dispatchers.Main) {
                    refreshSsidSection()
                    Utilities.showToastUiCentered(
                        this@RpnConfigDetailActivity,
                        "SSID settings saved",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }
        dlg.setCanceledOnTouchOutside(false)
        dlg.setOnDismissListener { refreshSsidSection() }
        dlg.show()
    }

    private fun showLocationEnableDialog() {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.ssid_location_error))
            .setMessage(getString(R.string.location_enable_explanation, getString(R.string.lbl_ssids)))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ssid_location_error_action)) { dlg, _ ->
                SsidPermissionManager.requestLocationEnable(this); dlg.dismiss()
            }
            .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ ->
                b.ssidCheck.isChecked = false
                lifecycleScope.launch(Dispatchers.IO) {
                    RpnProxyManager.updateSsidBased(configKey, false)
                }
            }
            .create().show()
    }

    private fun showSsidPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(getString(R.string.ssid_permission_error_action))
            .setMessage(getString(R.string.ssid_permission_explanation, getString(R.string.lbl_ssids)))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ssid_permission_error_action)) { dlg, _ ->
                SsidPermissionManager.requestSsidPermissions(this); dlg.dismiss()
            }
            .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ ->
                b.ssidCheck.isChecked = false
                lifecycleScope.launch(Dispatchers.IO) {
                    RpnProxyManager.updateSsidBased(configKey, false)
                }
            }
            .create().show()
    }

    override fun onRequestPermissionsResult(
        requestCode : Int,
        permissions : Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        SsidPermissionManager.handlePermissionResult(
            requestCode, permissions, grantResults, ssidPermissionCallback
        )
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}


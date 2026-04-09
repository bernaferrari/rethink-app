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
package com.celzero.bravedns.ui.adapter

import Logger.LOG_TAG_UI
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.databinding.ListItemVpnServerBinding
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.ServerWgConfigDetailActivity
import com.celzero.bravedns.ui.fragment.ServerSelectionFragment.Companion.AUTO_SERVER_ID
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.RouterStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Adapter for the list of currently-selected (active) VPN servers shown in
 * ServerSelectionFragment.  Mirrors WgConfigAdapter's live-stats pattern:
 * every 1.5 s it calls VpnController for proxy status, RouterStats and app-count
 * and updates the stats row in the item layout.
 */
class VpnServerAdapter(
    private val context: Context,
    private var serverGroups: List<ServerGroup>,
    private val listener: ServerSelectionListener
) : RecyclerView.Adapter<VpnServerAdapter.ServerViewHolder>() {

    private var lifecycleOwner: LifecycleOwner? = null

    /**
     * True when the RPN proxy has been deliberately stopped.
     * When set, every server item shows a "Stopped" status row and all taps
     * (detail, remove, reconnect, refresh) are redirected to [ServerSelectionListener.onProxyStoppedItemTapped]
     * so the user is guided to the settings sheet to restart.
     *
     * Using a plain private backing field (not `var isXxx: Boolean`) to avoid the
     * JVM declaration clash between the auto-generated `setProxyStopped` property
     * setter and the explicit [setProxyStopped] method.
     */
    private var proxyStopped = false

    /**
     * Keys of selected servers whose WIN tunnel is not yet available
     * (VpnController.getWinByKey returned null immediately after startProxy).
     *
     * While a key is in this set the corresponding adapter item shows a pulsing
     * "Connecting…" status row instead of live stats.  ServerSelectionFragment
     * drives these entries via [setLoadingTunnelKeys] / [clearLoadingTunnelKey]
     * from a short-lived polling job that runs for up to 10 s.
     */
    private val loadingTunnelKeys = mutableSetOf<String>()

    /**
     * Replaces the entire set of "tunnel not yet ready" keys and notifies all items
     * so they can switch between loading and live-stats rendering.
     */
    fun setLoadingTunnelKeys(keys: Set<String>) {
        if (loadingTunnelKeys == keys) return
        loadingTunnelKeys.clear()
        loadingTunnelKeys.addAll(keys)
        notifyItemRangeChanged(0, itemCount)
    }

    /**
     * Removes [key] from the loading set and triggers a targeted rebind on its item
     * so it transitions from "Connecting…" to live stats without a full list refresh.
     */
    fun clearLoadingTunnelKey(key: String) {
        if (loadingTunnelKeys.remove(key)) {
            val idx = serverGroups.indexOfFirst { it.key == key }
            if (idx >= 0) notifyItemChanged(idx)
        }
    }

    /**
     * Switches all currently-bound items to/from stopped mode.
     * Skips rebind if the flag did not change.
     * Clears [loadingTunnelKeys] when entering stopped mode — the stopped status
     * row takes precedence over the per-item tunnel-setup indicator.
     */
    fun setProxyStopped(stopped: Boolean) {
        if (proxyStopped == stopped) return
        proxyStopped = stopped
        if (stopped) loadingTunnelKeys.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        private const val STATS_POLL_MS = 1500L
    }

    data class ServerGroup(
        val key: String,
        val servers: List<CountryConfig>,
        val countryName: String,
        val flagEmoji: String,
        val cityName: String,
        val countryCode: String,
        val bestLinkSpeed: Int,
        val leastLoad: Int,
        val isActive: Boolean
    ) {
        val serverCount: Int get() = servers.size

        fun getBestServer(): CountryConfig = servers.minByOrNull { it.load } ?: servers.first()

        fun proxyId(): String = if (key.equals(AUTO_SERVER_ID, true)) "${Backend.RpnWin}**" else Backend.RpnWin + key
    }

    interface ServerSelectionListener {
        fun onServerGroupSelected(group: ServerGroup, isSelected: Boolean)
        fun onServerGroupRemoved(group: ServerGroup)
        /**
         * Called when any server item is tapped while the proxy is stopped.
         * The host should open the settings sheet so the user can restart the proxy.
         */
        fun onProxyStoppedItemTapped()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        if (lifecycleOwner == null) lifecycleOwner = parent.findViewTreeLifecycleOwner()
        val b = ListItemVpnServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(b)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(serverGroups[position])
    }

    override fun onViewDetachedFromWindow(holder: ServerViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.cancelStatsJob()
    }

    override fun getItemCount(): Int = serverGroups.size

    fun updateServerGroups(newGroups: List<ServerGroup>) {
        val old = serverGroups
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newGroups.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].key == newGroups[n].key
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newGroups[n]
        })
        serverGroups = newGroups.toList()
        diff.dispatchUpdatesTo(this)
    }

    fun updateServers(newServers: List<CountryConfig>) {
        val groups = newServers.groupBy { it.key }.map { (key, list) ->
            val rep = list.first()
            ServerGroup(
                key = key,
                servers = list,
                countryName = rep.countryName,
                flagEmoji = rep.flagEmoji,
                cityName = rep.serverLocation,
                countryCode = rep.cc,
                bestLinkSpeed = if (list.all { it.link > 0 }) list.maxOfOrNull { it.link } ?: 0 else 0,
                leastLoad = if (list.all { it.load > 0 }) list.minOfOrNull { it.load } ?: 0 else 0,
                isActive = list.any { it.isActive }
            )
        }.sortedBy { it.cityName.lowercase() }
        updateServerGroups(groups)
    }

    inner class ServerViewHolder(private val b: ListItemVpnServerBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val ctx: Context = b.root.context
        private var statsJob: Job? = null

        /**
         * Guards against repeated taps on the Refresh / Reconnect buttons while an IO
         * operation is already in-flight for this item.  Both flags are reset every time
         * [bind] is called so a recycled ViewHolder starts clean.
         */
        private var refreshInFlight   = false
        private var reconnectInFlight = false

        fun bind(group: ServerGroup) {
            // Reset per-item in-flight flags
            refreshInFlight   = false
            reconnectInFlight = false
            // Restore button alpha/enabled in case a previous bind left them dimmed.
            setRefreshReconnectEnabled(true)

            if (group.key.equals(AUTO_SERVER_ID, ignoreCase = true)) {
                b.infoIcon.visibility = View.GONE
            } else {
                b.infoIcon.visibility = View.VISIBLE
            }
            b.tvFlag.text = group.flagEmoji
            b.tvCountryName.text = group.countryName

            val locationText = if (group.serverCount > 1) {
                val cities = group.servers.map { it.serverLocation }.distinct()
                val cityText = if (cities.size <= 2) cities.joinToString(", ")
                else "${cities.first()} +${cities.size - 1} more"
                "$cityText • ${group.countryCode}"
            } else {
                ctx.getString(
                    R.string.server_location_format,
                    group.cityName,
                    group.countryCode
                )
            }
            b.tvServerLocation.text = locationText

            val hasSpeed = group.bestLinkSpeed > 0
            val hasLoad  = group.leastLoad > 0

            when {
                hasSpeed && hasLoad -> {
                    val speedStr = speedInfo(group.bestLinkSpeed).first
                    val (loadStr,  loadAttr) = loadInfo(group.leastLoad)
                    b.latencyBadge.text = ctx.getString(R.string.two_argument_dot, speedStr, loadStr)
                    b.latencyBadge.setTextColor(fetchColor(ctx, loadAttr))
                }
                hasSpeed -> {
                    val (speedStr, speedLabel, speedAttr) = speedInfo(group.bestLinkSpeed)
                    b.latencyBadge.text = ctx.getString(R.string.two_argument_dot, speedStr, speedLabel)
                    b.latencyBadge.setTextColor(fetchColor(ctx, speedAttr))
                }
                hasLoad -> {
                    val (loadStr, loadAttr) = loadInfo(group.leastLoad)
                    b.latencyBadge.text = loadStr
                    b.latencyBadge.setTextColor(fetchColor(ctx, loadAttr))
                }
                else -> {
                    b.latencyBadge.text = ctx.getString(R.string.lbl_not_available_short)
                    b.latencyBadge.visibility = View.GONE
                    b.latencyBadge.setTextColor(fetchColor(ctx, R.attr.primaryLightColorText))
                }
            }

            // Always cancel any running stats job before setting up the new state.
            cancelStatsJob()

            if (proxyStopped) {
                // Show a "Proxy Stopped" status row instead of live stats.
                showStoppedStatus()
                // Redirect every tap to the settings sheet so the user can restart.
                val stoppedClick = View.OnClickListener { listener.onProxyStoppedItemTapped() }
                b.serverCard.setOnClickListener(stoppedClick)
                b.infoIcon.setOnClickListener(stoppedClick)
                b.refreshTxt.setOnClickListener(stoppedClick)
                b.reconnectTxt.setOnClickListener(stoppedClick)
            } else if (loadingTunnelKeys.contains(group.key)) {
                // WIN tunnel for this server is still being set up (getWinByKey returned null).
                // Show a "Connecting…" indicator with a gentle pulse.
                // The stats loop is still started so it can take over naturally once the
                // tunnel is ready — the first successful applyStats() will stop the pulse.
                showTunnelLoadingStatus()
                b.infoIcon.setOnClickListener { listener.onServerGroupRemoved(group) }
                b.serverCard.setOnClickListener { openServerDetail(group.getBestServer()) }
                b.refreshTxt.setOnClickListener { refreshRpn(group.key) }
                b.reconnectTxt.setOnClickListener { reconnectRpn(group) }
                if (VpnController.hasTunnel()) {
                    statsJob = pollStatsLoop(group)
                }
            } else {
                b.infoIcon.setOnClickListener { listener.onServerGroupRemoved(group) }
                b.serverCard.setOnClickListener { openServerDetail(group.getBestServer()) }
                b.refreshTxt.setOnClickListener { refreshRpn(group.key) }
                b.reconnectTxt.setOnClickListener { reconnectRpn(group) }

                if (VpnController.hasTunnel()) {
                    statsJob = pollStatsLoop(group)
                } else {
                    hideStats()
                }
            }
        }

        /**
         * Displays a minimal "Proxy Stopped" status row.
         * Live stats are hidden since the proxy is not routing traffic.
         */
        private fun showStoppedStatus() {
            b.statsLayout.visibility = View.VISIBLE
            b.tvServerStatus.text = ctx.getString(R.string.server_settings_proxy_stopped)
            b.tvServerStatus.setTextColor(fetchColor(ctx, R.attr.chipTextNeutral))
            b.tvStatusSep.visibility = View.GONE
            b.tvAppsCount.visibility = View.GONE
            b.tvRxTx.visibility = View.GONE
            b.tvUptimeSep.visibility = View.GONE
            b.tvUptime.visibility = View.GONE
        }

        /**
         * Displays a pulsing "Connecting…" status row while the WIN tunnel for this
         * server is still being set up asynchronously after startProxy.
         *
         * The [pollStatsLoop] continues to run in parallel; the first successful
         * [applyStats] call will cancel the pulse and display real data.
         */
        private fun showTunnelLoadingStatus() {
            b.statsLayout.visibility = View.VISIBLE
            b.tvServerStatus.text = ctx.getString(R.string.lbl_connecting)
            b.tvServerStatus.setTextColor(fetchColor(ctx, R.attr.chipTextNeutral))
            b.tvStatusSep.visibility = View.GONE
            b.tvAppsCount.visibility = View.GONE
            b.tvRxTx.visibility = View.GONE
            b.tvUptimeSep.visibility = View.GONE
            b.tvUptime.visibility = View.GONE
            // Kick off a gentle alpha pulse so the user can tell this item is "live"
            b.tvServerStatus.animate().cancel()
            b.tvServerStatus.alpha = 1f
            pulseTvStatus()
        }

        /** Recursive alpha pulse on tvServerStatus. Stops when the view is detached. */
        private fun pulseTvStatus() {
            b.tvServerStatus.animate()
                .alpha(0.25f).setDuration(700)
                .withEndAction {
                    if (b.root.isAttachedToWindow) {
                        b.tvServerStatus.animate()
                            .alpha(1f).setDuration(700)
                            .withEndAction { if (b.root.isAttachedToWindow) pulseTvStatus() }
                            .start()
                    }
                }.start()
        }

        /**
         * Dims both action buttons (alpha → 0.40) and disables touches while [enabled]=false.
         * Restores them fully when [enabled]=true.  Animates the alpha change for polish.
         */
        private fun setRefreshReconnectEnabled(enabled: Boolean) {
            val targetAlpha = if (enabled) 1f else 0.40f
            b.refreshTxt.isEnabled   = enabled
            b.refreshTxt.isClickable = enabled
            b.reconnectTxt.isEnabled   = enabled
            b.reconnectTxt.isClickable = enabled
            b.refreshTxt.animate().alpha(targetAlpha).setDuration(160).start()
            b.reconnectTxt.animate().alpha(targetAlpha).setDuration(160).start()
        }

        /**
         * Refreshes all RPN proxies.  Guards against concurrent taps: the button pair is
         * disabled immediately and re-enabled once the IO operation completes.
         */
        private fun refreshRpn(id: String) {
            if (refreshInFlight || reconnectInFlight) return
            refreshInFlight = true
            setRefreshReconnectEnabled(false)
            io {
                // GoVpnAdapter handles AUTO and empty-string ids centrally
                val refreshed = VpnController.refreshRpnProxy(id)
                uiCtx {
                    refreshInFlight = false
                    setRefreshReconnectEnabled(true)
                    if (refreshed) {
                        Utilities.showToastUiCentered(context, "Refreshed", Toast.LENGTH_SHORT)
                    } else {
                        Utilities.showToastUiCentered(context, "Failed to refresh", Toast.LENGTH_SHORT)
                    }
                }
            }
        }

        /**
         * Reconnects (forks) the RPN proxy for this server group.  Guards against
         * concurrent taps the same way as [refreshRpn].
         */
        private fun reconnectRpn(group: ServerGroup) {
            if (reconnectInFlight || refreshInFlight) return
            reconnectInFlight = true
            setRefreshReconnectEnabled(false)
            io {
                // GoVpnAdapter handles AUTO and empty-string ids centrally
                val reconnect = VpnController.reconnectRpnProxy(group.key)
                uiCtx {
                    reconnectInFlight = false
                    setRefreshReconnectEnabled(true)
                    if (reconnect) {
                        Utilities.showToastUiCentered(context, "Reconnected", Toast.LENGTH_SHORT)
                    } else {
                        Utilities.showToastUiCentered(context, "Failed to reconnect", Toast.LENGTH_SHORT)
                    }
                }
            }
        }

        fun cancelStatsJob() {
            if (statsJob?.isActive == true) statsJob?.cancel()
            statsJob = null
            // Also clear in-flight flags — the ViewHolder is being detached, so any
            // pending IO completion will arrive into a recycled/detached state.
            // bind() will reset them again when the holder is reused.
            refreshInFlight   = false
            reconnectInFlight = false
        }

        private fun pollStatsLoop(group: ServerGroup): Job? {
            val lco = lifecycleOwner ?: return null
            // repeatOnLifecycle(STARTED) automatically suspends the inner block whenever
            // the lifecycle drops below STARTED (e.g. the user opens another activity) and
            // re-launches it once the lifecycle returns to STARTED.  This means the job is
            // never permanently canceled due to a background transition, no explicit
            // restart is required when the user navigates back.
            return lco.lifecycleScope.launch {
                lco.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        withContext(Dispatchers.IO) { fetchAndApplyStats(group) }
                        delay(STATS_POLL_MS)
                    }
                }
            }
        }

        private suspend fun fetchAndApplyStats(group: ServerGroup) {
            val config = RpnProxyManager.getCountryConfigByKey(group.key)
            val id = group.proxyId()
            val statusPair = VpnController.getProxyStatusById(id)
            val stats = VpnController.getProxyStats(id)
            val apps = ProxyManager.getAppCountForProxy(id)
            // show who, if not available show city name
            val who = if (group.key.equals(AUTO_SERVER_ID, ignoreCase = true)) {
                VpnController.getWin()?.who() ?: group.cityName
            } else {
                group.cityName
            }

            Logger.v(LOG_TAG_UI, "VpnServerAdapter fetchAndApplyStats for id: $id, config: $config, status: $statusPair, stats: $stats, apps/always-on: $apps/${config?.catchAll}")
            withContext(Dispatchers.Main) {
                if (group.key.equals(AUTO_SERVER_ID, ignoreCase = true)) {
                    val whoTrimmed = if (who.length > 10) who.take(10) else who
                    // update who in space of cc, for auto
                    val text = ctx.getString(
                        R.string.server_location_format,
                        group.cityName,
                        whoTrimmed
                    )
                    b.tvServerLocation.text = text
                }

                applyStats(config, statusPair, stats, apps)
            }
        }

        private fun applyStats(config: CountryConfig?, statusPair: Pair<Long?, String>, stats: RouterStats?, appsCount: Int) {
            if (config == null) {
                hideStats()
                return
            }
            // Stop any loading-pulse animation that may be running from showTunnelLoadingStatus().
            b.tvServerStatus.animate().cancel()
            b.tvServerStatus.alpha = 1f

            b.statsLayout.visibility = View.VISIBLE

            // Status chip
            val status = UIUtils.ProxyStatus.entries.find { it.id == statusPair.first }
            b.tvServerStatus.text = getStatusText(status, stats, statusPair.second)
            b.tvServerStatus.setTextColor(fetchColor(ctx, getStatusColor(status, stats)))

            // Apps count  (R.string.add_remove_apps = "Add / Remove (%1$s apps)")
            b.tvStatusSep.visibility = View.VISIBLE
            b.tvAppsCount.visibility = View.VISIBLE
            if (config.catchAll) {
                b.tvAppsCount.text = ctx.getString(R.string.routing_remaining_apps)
            } else {
                b.tvAppsCount.text = ctx.getString(R.string.add_remove_apps, appsCount)
            }
            b.tvAppsCount.setTextColor(
                fetchColor(ctx, if (appsCount > 0 || config.catchAll) R.attr.primaryLightColorText else R.attr.accentBad)
            )

            // Rx / Tx
            val rxtx = getRxTx(stats)
            b.tvRxTx.visibility = if (rxtx.isNotEmpty()) View.VISIBLE else View.GONE
            if (rxtx.isNotEmpty()) b.tvRxTx.text = rxtx

            // Uptime
            val uptime = getUpTime(stats)
            b.tvUptimeSep.visibility = if (uptime.isNotEmpty()) View.VISIBLE else View.GONE
            b.tvUptime.visibility = if (uptime.isNotEmpty()) View.VISIBLE else View.GONE
            if (uptime.isNotEmpty()) b.tvUptime.text = uptime
        }

        private fun hideStats() { b.statsLayout.visibility = View.GONE }

        private fun getStatusColor(status: UIUtils.ProxyStatus?, stats: RouterStats?): Int {
            val now = System.currentTimeMillis()
            val lastOk = stats?.lastOK ?: 0L
            val since = stats?.since  ?: 0L
            val failing = now - since > WireguardManager.WG_UPTIME_THRESHOLD && lastOk == 0L
            return when (status) {
                UIUtils.ProxyStatus.TOK ->
                    if (failing) R.attr.chipTextNegative else R.attr.accentGood
                UIUtils.ProxyStatus.TUP,
                UIUtils.ProxyStatus.TZZ,
                UIUtils.ProxyStatus.TNT -> R.attr.chipTextNeutral
                else -> R.attr.chipTextNegative
            }
        }

        private fun getStatusText(
            status: UIUtils.ProxyStatus?,
            stats: RouterStats?,
            errMsg: String?
        ): String {
            if (status == null) {
                val base = if (!errMsg.isNullOrEmpty())
                    ctx.getString(R.string.status_waiting) + " ($errMsg)"
                else
                    ctx.getString(R.string.status_waiting)
                return base.replaceFirstChar(Char::titlecase)
            }
            if (status == UIUtils.ProxyStatus.TPU) {
                return ctx.getString(UIUtils.getProxyStatusStringRes(status.id))
                    .replaceFirstChar(Char::titlecase)
            }
            val now = System.currentTimeMillis()
            val lastOk = stats?.lastOK ?: 0L
            val since = stats?.since  ?: 0L
            if (now - since > WireguardManager.WG_UPTIME_THRESHOLD && lastOk == 0L) {
                return ctx.getString(R.string.status_failing).replaceFirstChar(Char::titlecase)
            }
            return ctx.getString(UIUtils.getProxyStatusStringRes(status.id))
                .replaceFirstChar(Char::titlecase)
        }

        private fun getRxTx(stats: RouterStats?): String {
            if (stats == null || (stats.rx == 0L && stats.tx == 0L)) return ""
            val rx = ctx.getString(R.string.symbol_download,
                Utilities.humanReadableByteCount(stats.rx, true))
            val tx = ctx.getString(R.string.symbol_upload,
                Utilities.humanReadableByteCount(stats.tx, true))
            return ctx.getString(R.string.two_argument_space, tx, rx)
        }

        private fun getUpTime(stats: RouterStats?): CharSequence {
            if (stats == null || stats.since <= 0L) return ""
            return DateUtils.getRelativeTimeSpanString(
                stats.since, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
        }

        /**
         * Returns (formattedSpeed, tierLabel, textColorAttr) for [linkMbps].
         *
         * Tier thresholds (same as CountryServerAdapter):
         *   ≥ 10 000 Mbps → Very Fast   (chipTextPositive)
         *   ≥  1 000 Mbps → Fast        (accentGood)
         *   ≥    100 Mbps → Good        (chipTextNeutral)
         *   ≥     10 Mbps → Moderate    (chipTextNeutral)
         *   >      0 Mbps → Slow        (chipTextNegative)
         */
        private fun speedInfo(linkMbps: Int): Triple<String, String, Int> {
            val formatted: String
            val label: String
            val attr: Int
            when {
                linkMbps >= 10_000 -> {
                    formatted = String.format(Locale.US, "%.0f Gbps", linkMbps / 1_000.0)
                    label = ctx.getString(R.string.server_speed_very_fast)
                    attr = R.attr.chipTextPositive
                }
                linkMbps >= 1_000 -> {
                    val gbps  = linkMbps / 1_000.0
                    formatted = if (gbps == gbps.toLong().toDouble())
                        String.format(Locale.US, "%.0f Gbps", gbps)
                    else
                        String.format(Locale.US, "%.1f Gbps", gbps)
                    label = ctx.getString(R.string.server_speed_fast)
                    attr = R.attr.accentGood
                }
                linkMbps >= 100 -> {
                    formatted = "$linkMbps Mbps"
                    label = ctx.getString(R.string.server_speed_good)
                    attr = R.attr.chipTextNeutral
                }
                linkMbps >= 10 -> {
                    formatted = "$linkMbps Mbps"
                    label = ctx.getString(R.string.server_speed_moderate)
                    attr = R.attr.chipTextNeutral
                }
                else -> {
                    formatted = "$linkMbps Mbps"
                    label = ctx.getString(R.string.server_speed_slow)
                    attr = R.attr.chipTextNegative
                }
            }
            return Triple(formatted, label, attr)
        }

        /**
         * Returns (displayText, textColorAttr) for [loadPercent].
         *
         * Tier thresholds (same as CountryServerAdapter):
         *   ≤ 20 → Light      (chipTextPositive)
         *   ≤ 40 → Normal     (accentGood)
         *   ≤ 60 → Busy       (chipTextNeutral)
         *   ≤ 80 → Very Busy  (chipTextNegative)
         *   > 80 → Overloaded (chipTextNegative)
         */
        private fun loadInfo(loadPercent: Int): Pair<String, Int> {
            val label: String
            val attr: Int
            when {
                loadPercent <= 20 -> {
                    label = "$loadPercent% · ${ctx.getString(R.string.server_load_light)}"
                    attr = R.attr.chipTextPositive
                }
                loadPercent <= 40 -> {
                    label = "$loadPercent% · ${ctx.getString(R.string.server_load_normal)}"
                    attr = R.attr.accentGood
                }
                loadPercent <= 60 -> {
                    label = "$loadPercent% · ${ctx.getString(R.string.server_load_busy)}"
                    attr = R.attr.chipTextNeutral
                }
                loadPercent <= 80 -> {
                    label = "$loadPercent% · ${ctx.getString(R.string.server_load_very_busy)}"
                    attr = R.attr.chipTextNegative
                }
                else -> {
                    label = "$loadPercent% · ${ctx.getString(R.string.server_load_overloaded)}"
                    attr = R.attr.chipTextNegative
                }
            }
            return Pair(label, attr)
        }

        private fun openServerDetail(server: CountryConfig) {
            val intent = Intent(ctx, ServerWgConfigDetailActivity::class.java)
            intent.putExtra(ServerWgConfigDetailActivity.INTENT_EXTRA_SERVER_ID, server.id.hashCode())
            intent.putExtra(ServerWgConfigDetailActivity.INTENT_EXTRA_FROM_SERVER_SELECTION, true)
            intent.putExtra(ServerWgConfigDetailActivity.INTENT_EXTRA_CONFIG_KEY, server.key)
            ctx.startActivity(intent)
        }
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

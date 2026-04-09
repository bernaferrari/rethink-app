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
package com.celzero.bravedns.ui.fragment

import Logger
import Logger.LOG_TAG_UI
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.database.SubscriptionStatus
import com.celzero.bravedns.database.SubscriptionStatusDao
import com.celzero.bravedns.databinding.FragmentServerSelectionBinding
import com.celzero.bravedns.iab.InAppBillingHandler
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.activity.FragmentHostActivity
import com.celzero.bravedns.ui.adapter.CountryServerAdapter
import com.celzero.bravedns.ui.adapter.VpnServerAdapter
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.bottomsheet.ServerRemovalNotificationBottomSheet
import com.celzero.bravedns.ui.bottomsheet.ServerSettingsBottomSheet
import com.celzero.bravedns.util.SnackbarHelper
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.firestack.backend.Backend
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fragment for selecting VPN servers from a list.
 */
class ServerSelectionFragment : Fragment(R.layout.fragment_server_selection),
    VpnServerAdapter.ServerSelectionListener,
    CountryServerAdapter.CitySelectionListener {

    private val subscriptionStatusDao by inject<SubscriptionStatusDao>()
    private val persistentState by inject<PersistentState>()
    private val b by viewBinding(FragmentServerSelectionBinding::bind)

    private lateinit var serverAdapter: CountryServerAdapter
    private lateinit var selectedAdapter: VpnServerAdapter

    private val allServers = mutableListOf<CountryConfig>()
    private val unselectedServers = mutableListOf<CountryConfig>()
    private val selectedServers = mutableListOf<CountryConfig>()

    private var statusUpdateJob: Job? = null

    /** Job driving the registration / server-list polling loop. */
    private var serverLoadingJob: Job? = null
    /** Non-dismissable dialog shown while WIN registers / servers load. */
    private var serverLoadingDialog: android.app.Dialog? = null
    /**
     * Short-lived job that polls [VpnController.getWinByKey] for each selected server
     * whose WIN tunnel key was null immediately after [initServers] completed.
     * Runs for up to 10 s, clears the per-item loading indicator once the key appears,
     * and cancels itself when all keys are resolved or the deadline is reached.
     */
    private var tunnelWatchJob: Job? = null

    /** Guards against double-tapping the refresh button. */
    private var refreshServersInFlight = false
    /** Guards against double-tapping the FAB stop/start. */
    private var toggleProxyInFlight = false
    /** Looping spin animator attached to the refresh button while a refresh is in progress. */
    private var refreshAnimator: ObjectAnimator? = null
    /** Looping spin animator running on the FAB icon while stop/start is in progress. */
    private var fabLoadingAnimator: ObjectAnimator? = null

    private var isWinRegistered = false
    private var autoServer: CountryConfig? = null

    /** True from the moment onViewCreated fires until initServers finishes. */
    private var isLoading = true

    /**
     * True when the user has explicitly stopped the proxy via the settings
     * bottom sheet.  While stopped:
     * - Hero banner shows "Stopped" chip
     * - Server list + search are dimmed / non-interactive
     * - Status chip is still tappable → opens settings sheet to restart
     */
    private var isProxyStopped = false

    companion object {
        private const val TAG = "ServerSelectionFragment"
        const val AUTO_SERVER_ID   = "AUTO"
        const val AUTO_COUNTRY_CODE = "AUTO"

        /**
         * Maximum number of NON-AUTO servers the user can select simultaneously.
         * AUTO is always kept connected on top of this limit.
         */
        private const val MAX_SELECTIONS = 5

        /** UI connection states surfaced by [updateConnectionStatus]. */
        private enum class ConnectionUiState { DISCONNECTED, CONNECTING, CONNECTED }

        /** Maximum time the registration loading dialog will wait before giving up. */
        private const val LOADING_DIALOG_TIMEOUT_MS = 20_000L
        /** Interval between each registration / server-list poll within the dialog. */
        private const val LOADING_DIALOG_POLL_INTERVAL_MS = 1_500L
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isProxyStopped = RpnProxyManager.rpnMode().isNone()

        val fabExtraMarginPx = (16f * resources.displayMetrics.density + 0.5f).toInt()
        val fabEndMarginPx   = (16f * resources.displayMetrics.density + 0.5f).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(b.fabToggleProxy) { v, insets ->
            val navBarBottom  = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // nav_view is in the activity layout and is already measured by the time insets
            // are dispatched, so .height is reliable here.
            val navViewHeight = requireActivity().findViewById<View>(R.id.nav_view)?.height ?: 0
            (v.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = navBarBottom + navViewHeight + fabExtraMarginPx
                lp.marginEnd    = fabEndMarginPx
                v.layoutParams  = lp
            }
            insets
        }

        setupNavigationButtons()
        setupSearchBar()
        setupHeaderUI()
        setupRpnState()

        // Set initial FAB appearance to match current proxy state
        if (isProxyStopped) applyFabStoppedState() else applyFabRunningState()

        // Apply bottom padding so the last list item is not hidden behind the
        // BottomNavigationView (which floats over the fragment container).
        requireActivity().findViewById<View>(R.id.nav_view)?.let { navView ->
            navView.post {
                if (!isAdded) return@post
                b.serversScrollView.setPadding(
                    b.serversScrollView.paddingLeft,
                    b.serversScrollView.paddingTop,
                    b.serversScrollView.paddingRight,
                    navView.height + 300
                )
            }
        }

        animateHeaderEntry()
    }

    private fun setupRpnState() {
        setupRecyclerViews()
        setLoadingState(true)
        observeSubscription()
        observeServerRemovedEvents()

        io {
            if (!RpnProxyManager.isRpnActive()) {
                uiCtx {
                    setLoadingState(false)
                    dismissServerLoadingDialog()
                    applyProxyStoppedUi()
                }
            }

            isWinRegistered = VpnController.isWinRegistered()
            val selectedList = RpnProxyManager.getEnabledConfigs()
            Logger.v(LOG_TAG_UI, "$TAG; WIN registered: $isWinRegistered")

            val servers = RpnProxyManager.getWinServers()
            Logger.v(LOG_TAG_UI, "$TAG; fetched ${servers.size} servers from RPN")
            val hasRealServers = servers.any { it.id != AUTO_SERVER_ID }

            uiCtx {
                if (!isAdded) return@uiCtx
                when {
                    (!isWinRegistered || !hasRealServers) && RpnProxyManager.isRpnActive() -> {
                        // either registration is pending or the server list hasn't populated yet.
                        Logger.i(
                            LOG_TAG_UI,
                            "$TAG: WIN registered=$isWinRegistered, hasRealServers=$hasRealServers; showing loading dialog"
                        )
                        showServerLoadingDialog()
                    }
                    else -> initServers(servers, selectedList)
                }
            }
        }

    }

    override fun onResume() {
        Logger.vv(LOG_TAG_UI, "$TAG.onResume")
        super.onResume()
        redriveProxyStartStopState()
    }

    private fun redriveProxyStartStopState() {
        // rederive proxy stopped state on every resume, external changes (notification,
        // BraveVPNService) may have changed rpnMode without going through the bottom sheet listener.
        val wasProxyStopped = isProxyStopped
        isProxyStopped = RpnProxyManager.rpnMode().isNone()

        // If state changed while we were away, refresh the UI immediately.
        if (wasProxyStopped != isProxyStopped && !isLoading) {
            if (isProxyStopped) {
                dismissServerLoadingDialog()
                applyProxyStoppedUi()
            } else {
                applyProxyRunningUi()
            }
        }

        if (!RpnProxyManager.isRpnActive()) {
            if (!isLoading) {
                dismissServerLoadingDialog()
                if (!isProxyStopped) {
                    isProxyStopped = true
                    applyProxyStoppedUi()
                }
            }
        } else {
            io {
                val (refreshedServers, removedServers) = RpnProxyManager.refreshWinServers()

                if (removedServers.isNotEmpty()) {
                    Logger.w(
                        LOG_TAG_UI,
                        "$TAG.onResume: ${removedServers.size} selected servers removed"
                    )
                    uiCtx {
                        if (!isAdded || requireActivity().isFinishing) return@uiCtx
                        val removedNames = removedServers.joinToString(", ") { it.countryName }
                        Logger.i(LOG_TAG_UI, "$TAG.onResume: notifying removal of: $removedNames")
                        try {
                            showSettingsBottomSheet(removedServers, refreshedServers)
                        } catch (e: Exception) {
                            Logger.e(
                                LOG_TAG_UI,
                                "$TAG.onResume: error showing bottom sheet: ${e.message}",
                                e
                            )
                            if (isAdded && refreshedServers.isNotEmpty()) {
                                io {
                                    val sel = try { RpnProxyManager.getEnabledConfigs() } catch (_: Exception) { emptySet() }
                                    uiCtx { initServers(refreshedServers, sel) }
                                }
                            }
                        }
                    }
                } else if (refreshedServers.isNotEmpty()) {
                    Logger.v(
                        LOG_TAG_UI,
                        "$TAG.onResume: refreshed ${refreshedServers.size} servers, nothing removed"
                    )
                }
            }
        }

    }

    private fun showSettingsBottomSheet(
        removedServers: List<CountryConfig> = emptyList(),
        refreshedServers: List<CountryConfig> = emptyList(),
        selectedList: Set<CountryConfig> = emptySet()
    ) {
        val bs = ServerRemovalNotificationBottomSheet.newInstance(removedServers)
        bs.setOnDismissCallback {
            if (!isAdded || refreshedServers.isEmpty()) return@setOnDismissCallback
            io {
                val freshSelected = try {
                    RpnProxyManager.getEnabledConfigs().ifEmpty { selectedList }
                } catch (_: Exception) {
                    selectedList
                }
                uiCtx { initServers(refreshedServers, freshSelected) }
            }
        }
        bs.show(parentFragmentManager, "ServerRemovalNotification")
    }

    override fun onDestroyView() {
        // Cancel animations before the binding is torn down
        runCatching {
            fabLoadingAnimator?.cancel()
            fabLoadingAnimator = null
            refreshAnimator?.cancel()
            refreshAnimator = null
            b.fabToggleProxy.animate().cancel()
            b.statusIndicator.animate().cancel()
            b.statusCard.animate().cancel()
            b.searchCard.animate().cancel()
            b.searchClearBtn.animate().cancel()
        }
        // Release suppressLayout so RecyclerViews don't retain a frozen state if the
        // fragment is reattached (e.g. back-stack pop).
        runCatching {
            b.rvServers.suppressLayout(false)
            b.rvSelectedServers.suppressLayout(false)
        }
        statusUpdateJob?.cancel()
        statusUpdateJob = null
        tunnelWatchJob?.cancel()
        tunnelWatchJob = null
        dismissServerLoadingDialog()
        super.onDestroyView()
    }

    private fun setLoadingState(loading: Boolean) {
        if (!isAdded) return
        isLoading = loading

        if (loading) {
            // Header shimmer
            b.shimmerHeader.isVisible = true
            b.shimmerHeader.startShimmer()
            b.locationContent.isVisible = false
            b.heroIpRow.isVisible = false

            // hide real list and hint cards
            b.shimmerServerList.isVisible = true
            b.shimmerServerList.startShimmer()
            b.rvServers.isVisible = false
            b.emptySelectionCard.isVisible = false
            b.selectedServersCard.isVisible = false
            b.emptyStateLayout.isVisible = false
        } else {
            // Stop and hide header shimmer, reveal real content
            b.shimmerHeader.stopShimmer()
            b.shimmerHeader.isVisible = false
            b.locationContent.isVisible = true

            // Stop and hide list shimmer, reveal real list
            b.shimmerServerList.stopShimmer()
            b.shimmerServerList.isVisible = false
            b.rvServers.isVisible = true
        }
    }

    private fun initServers(servers: List<CountryConfig>, selectedList: Set<CountryConfig> = emptySet()) {
        io {
            Logger.v(LOG_TAG_UI, "$TAG.initServers: ${servers.size} servers, selected=${selectedList.size}, WIN=$isWinRegistered")

            val hasRealServers = servers.any { it.id != AUTO_SERVER_ID }

            if (!hasRealServers) {
                uiCtx {
                    if (!isAdded) return@uiCtx
                    setLoadingState(false)
                    showErrorState()
                }
                Logger.w(LOG_TAG_UI, "$TAG.initServers: no real servers available (hasRealServers=false, total=${servers.size})")
                return@io
            }

            uiCtx { hideErrorState() }

            if (isWinRegistered) {
                autoServer = RpnProxyManager.getAutoServer()
                if (autoServer == null) {
                    Logger.w(LOG_TAG_UI, "$TAG.initServers: AUTO not in DB, creating")
                    RpnProxyManager.ensureAutoServerExists()
                    autoServer = RpnProxyManager.getAutoServer()
                }
                //always activate AUTO in the backend regardless of prior state
                autoServer?.let { auto ->
                    if (!auto.isEnabled) {
                        auto.isEnabled = true
                        RpnProxyManager.updateAutoServerState(auto)
                        Logger.i(LOG_TAG_UI, "$TAG.initServers: AUTO was disabled, re-enabling")
                    }
                }
            }

            val localSelected = mutableListOf<CountryConfig>()
            // Keys where the WIN tunnel is not yet set up (getWinByKey returned null).
            val pendingTunnelKeys = mutableSetOf<String>()

            // AUTO is always first in the selected list
            autoServer?.let { auto ->
                auto.isActive  = true
                auto.isEnabled = true
                localSelected.add(auto)
            }

            if (selectedList.isNotEmpty()) {
                val selected = selectedList.map { it.key }
                val filtered = servers.filter { it.key in selected }
                // Exclude AUTO; it is always handled explicitly above and must never
                // appear twice in localSelected (treat the list as a set for AUTO).
                val groupedByKey = filtered
                    .filter { it.isActive && it.id != AUTO_SERVER_ID }
                    .groupBy { it.key }
                groupedByKey.forEach { (key, serversWithSameKey) ->
                    // Skip if already at the non-AUTO limit
                    val nonAutoCount = localSelected.count { it.id != AUTO_SERVER_ID }
                    if (nonAutoCount >= MAX_SELECTIONS) return@forEach
                    val best = serversWithSameKey.minByOrNull { it.load } ?: serversWithSameKey.first()
                    // Always add to localSelected based on DB state.
                    // Do NOT gate this on VpnController.getWinByKey(key): that call returns
                    // null immediately after startProxy() because tunnel setup is async.
                    // Blocking on it causes a race condition where the server misses the
                    // selected list and lands in unselected with its checkbox ticked.
                    // The live stats row (pollStatsLoop) will show actual tunnel readiness.
                    if (VpnController.getWinByKey(Backend.RpnWin + key) == null) {
                        Logger.w(LOG_TAG_UI, "$TAG.initServers: WIN tunnel for key=$key not yet available (still setting up)")
                        pendingTunnelKeys.add(key)
                    }
                    localSelected.add(best)
                }
            }

            val localUnselected = servers.filter { server ->
                server.isActive && localSelected.none { it.key == server.key }
            }.toMutableList()

            Logger.v(LOG_TAG_UI, "$TAG.initServers: selected=${localSelected.size} " +
                    "(AUTO=${localSelected.any { it.id == AUTO_SERVER_ID }}), " +
                    "unselected=${localUnselected.size}")

            uiCtx {
                if (!isAdded) return@uiCtx
                // Atomically replace shared lists on the main thread — safe because
                // the main thread is single-threaded and all other mutations
                // (onServerSelected / onServerDeselected) also run inside uiCtx.
                allServers.clear()
                allServers.addAll(servers)
                selectedServers.clear()
                selectedServers.addAll(localSelected)
                unselectedServers.clear()
                unselectedServers.addAll(localUnselected)

                selectedAdapter.updateServers(selectedServers)
                serverAdapter.updateCountries(buildCountries(unselectedServers))
                updateAllServersCount()
                updateSelectedSectionVisibility()
                updateSelectionCount()
                updateVpnStatus()
                setLoadingState(false)
                // Re-apply stopped UI on top of fully-loaded state
                if (isProxyStopped) applyProxyStoppedUi()
                // Notify adapter which server items are still waiting for tunnel setup,
                // then start a short-lived polling job to clear them as they come up.
                selectedAdapter.setLoadingTunnelKeys(pendingTunnelKeys)
                if (pendingTunnelKeys.isNotEmpty()) startTunnelWatchJob(pendingTunnelKeys)
                b.rvServers.post { b.rvServers.requestLayout() }
                b.rvSelectedServers.post { b.rvSelectedServers.requestLayout() }
            }
            Logger.v(LOG_TAG_UI, "$TAG.initServers: complete")
        }
    }

    private fun setupHeaderUI() {
        b.collapsingToolbar.title = getString(R.string.server_selection_title)
        b.collapsingToolbar.titleCollapseMode = CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_SCALE
        // Title is invisible while the header is expanded so it doesn't overlap the status
        // card content; it fades in only once the toolbar is fully collapsed.
        b.collapsingToolbar.setExpandedTitleColor(Color.TRANSPARENT)
        b.collapsingToolbar.setCollapsedTitleTextColor(resolveAttrColor(R.attr.primaryTextColor))

        // Fade out card content proportionally as the toolbar collapses so that only the
        // collapsed title is visible once the AppBar is fully pinned.
        // Start fading at 40 % scroll and be fully transparent by 75 %.
        b.appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            val scrollRange = appBar.totalScrollRange
            if (scrollRange == 0) return@addOnOffsetChangedListener
            val collapsedFraction = (-verticalOffset).toFloat() / scrollRange.toFloat()
            val contentAlpha = (1f - ((collapsedFraction - 0.40f) / 0.35f)).coerceIn(0f, 1f)
            b.statusCard.alpha = contentAlpha
        }

        populateHeroPlanAccountRow()
        // Only start the periodic status update; location is updated by initServers/onServerSelected
        statusUpdateJob = lifecycleScope.launch {
            while (true) {
                delay(3_000)
                if (isAdded && !isLoading) updateConnectionStatusOnly()
            }
        }
    }

    private fun populateHeroPlanAccountRow() {
        if (!isAdded) return
        val sub = RpnProxyManager.getSubscriptionData()?.subscriptionStatus
        if (sub == null || sub.purchaseToken.isEmpty()) {
            b.tvHeroPlanName.text = ""
            b.tvHeroAccountId.text = ""
            return
        }
        val raw = sub.productTitle.ifBlank { sub.planId.ifBlank { sub.productId } }
        val planLabel = when (raw) {
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> "One-Time 2 years"
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> "One-Time 5 years"
            InAppBillingHandler.SUBS_PRODUCT_YEARLY -> "Subscription Yearly"
            else -> getString(R.string.ping_server_name)
        }
        b.tvHeroPlanName.text = planLabel
        val accountId = sub.accountId.take(12)
        // Clear while we fetch the real device ID from SecureIdentityStore on IO.
        b.tvHeroAccountId.text = accountId.ifEmpty { "" }
        // Fetch real device ID asynchronously — sub.deviceId holds only the sentinel indicator.
        io {
            val realDeviceId = runCatching { InAppBillingHandler.getObfuscatedDeviceId() }.getOrDefault("")
            val deviceId = realDeviceId.take(4)
            uiCtx {
                if (!isAdded) return@uiCtx
                b.tvHeroAccountId.text = if (accountId.isNotEmpty()) "$accountId • $deviceId" else ""
            }
        }
    }

    /** Updates only the connection chip (colored dot + label). Safe to call frequently. */
    private fun updateConnectionStatusOnly() {
        if (!isAdded) return
        updateConnectionStatus(deriveConnectionUiState())
    }

    /** Derives the correct [ConnectionUiState] from live VPN adapter state. */
    private fun deriveConnectionUiState(): ConnectionUiState {
        if (isProxyStopped) return ConnectionUiState.DISCONNECTED
        val vpnState = VpnController.state()
        return when {
            // Fully connected tunnel
            vpnState.on -> ConnectionUiState.CONNECTED
            // VPN start has been requested but tunnel not yet up
            vpnState.activationRequested && !vpnState.on -> ConnectionUiState.CONNECTING
            // NEW state = tunnel was just created, still handshaking
            vpnState.connectionState == BraveVPNService.State.NEW -> ConnectionUiState.CONNECTING
            else -> ConnectionUiState.DISCONNECTED
        }
    }

    /**
     * Full header refresh: connection status + current location derived from [selectedServers].
     * Only called after data is loaded (not during loading).
     */
    private fun updateVpnStatus() {
        if (!isAdded) return
        updateConnectionStatus(deriveConnectionUiState())
        when {
            selectedServers.isEmpty() -> {
                updateCurrentLocation(
                    flag = "🌐",
                    countryName = if (isWinRegistered) AUTO_SERVER_ID else getString(R.string.vpn_status_disconnected),
                    location = if (isWinRegistered) getString(R.string.server_selection_tap_to_select)
                               else getString(R.string.server_selection_tap_to_select)
                )
            }
            selectedServers.size == 1 -> {
                val s = selectedServers.first()
                if (s.id.equals(AUTO_SERVER_ID, ignoreCase = true)) {
                    updateCurrentLocation("🌐", AUTO_SERVER_ID, getString(R.string.server_selection_tap_to_select))
                } else {
                    updateCurrentLocation(s.flagEmoji, s.countryName, s.serverLocation)
                }
            }
            else -> {
                val uniqueNames = selectedServers
                    .filter { !it.id.equals(AUTO_SERVER_ID, ignoreCase = true) }
                    .map { it.countryName }
                    .distinct()
                val namesText = uniqueNames.joinToString(", ")
                val locationText = selectedServers
                    .asSequence()
                    .filter { !it.id.equals(AUTO_SERVER_ID, ignoreCase = true) }
                    .map { it.serverLocation }
                    .distinct()
                    .take(2)
                    .joinToString(", ")
                updateCurrentLocation("🌐", namesText, locationText)
            }
        }
    }

    private fun updateConnectionStatus(uiState: ConnectionUiState) {
        if (!isAdded) return
        when (uiState) {
            ConnectionUiState.CONNECTED -> {
                b.tvConnectionStatus.text = getString(R.string.lbl_active)
                b.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accentGood))
                b.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accentGood)
                b.statusIndicator.animate().scaleX(1.3f).scaleY(1.3f).setDuration(500).withEndAction {
                    if (isAdded) b.statusIndicator.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
                }.start()
            }
            ConnectionUiState.CONNECTING -> {
                b.tvConnectionStatus.text = getString(R.string.lbl_connecting)
                b.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorAmber_900))
                b.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorAmber_900)
                // Pulse animation to indicate in-progress state
                b.statusIndicator.animate().scaleX(1.2f).scaleY(1.2f).setDuration(600).withEndAction {
                    if (isAdded) b.statusIndicator.animate().scaleX(0.8f).scaleY(0.8f).setDuration(600).withEndAction {
                        if (isAdded) b.statusIndicator.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                    }.start()
                }.start()
            }
            ConnectionUiState.DISCONNECTED -> {
                b.tvConnectionStatus.text = getString(R.string.lbl_inactive)
                b.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accentBad))
                b.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accentBad)
            }
        }
    }

    private fun updateCurrentLocation(flag: String, countryName: String, location: String) {
        if (!isAdded) return

        b.locationContent.visibility = View.VISIBLE
        b.tvCurrentCountryFlag.text = flag
        b.tvCurrentCountry.text = countryName
        b.tvCurrentLocation.text = location
        updateHeroIpRow()
    }

    private fun updateHeroIpRow() {
        io {
            // iP4() is a native (Go) method that performs network lookups.
            val ip4 = if (!isAdded) null else
                withTimeoutOrNull(5_000L) {
                    try {
                        VpnController.getWinByKey("")?.client()?.iP4()
                    } catch (t: Throwable) {
                        // Catches both Exception and native-bridged Error subclasses
                        // such as go.Universe$proxyerror from the Go runtime.
                        Logger.w(LOG_TAG_UI, "$TAG.updateHeroIpRow: iP4() threw: ${t.message}")
                        null
                    }
                }

            uiCtx {
                if (!isAdded) return@uiCtx
                if (ip4 != null && !ip4.ip.isNullOrEmpty()) {
                    b.heroIpRow.isVisible = true
                    b.heroIpText.text = ip4.ip
                    b.heroIspText.text = ip4.city?.ifEmpty { "" } ?: ""
                } else {
                    b.heroIpRow.isVisible = false
                }
            }
        }
    }

    private fun animateHeaderEntry() {
        if (!isAdded) return
        b.statusCard.alpha = 0f
        b.statusCard.scaleX = 0.9f
        b.statusCard.scaleY = 0.9f
        b.statusCard.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun setupNavigationButtons() {
        b.supportBtn.setOnClickListener { openHelpAndSupport() }
        b.settingsBtn.setOnClickListener { showServerSettingsBottomSheet() }
        b.refreshBtn.setOnClickListener { doRefreshServers() }
        b.fabToggleProxy.setOnClickListener { onToggleProxyFabClicked() }
        // Status chip: open settings when running, show a hint when stopped
        b.statusChip.setOnClickListener {
            if (isProxyStopped) {
                SnackbarHelper.show(
                    view     = b.root,
                    message  = getString(R.string.server_settings_tap_to_restart),
                    duration = Snackbar.LENGTH_SHORT
                )
            } else {
                showServerSettingsBottomSheet()
            }
        }
    }

    /** Opens the unified server-settings bottom sheet. */
    private fun showServerSettingsBottomSheet() {
        if (!isAdded) return
        // Guard: avoid stacking duplicate sheets
        if (parentFragmentManager.findFragmentByTag("ServerSettings") != null) return
        val sheet = ServerSettingsBottomSheet.newInstance(isProxyStopped)
        sheet.setOnSettingsChangedListener(object : ServerSettingsBottomSheet.OnSettingsChangedListener {
            override fun onDnsModeChanged(mode: RpnProxyManager.DnsMode) {
                io { VpnController.onRpnDnsChange() }
            }
            override fun onConfigChanged() {
                io {
                    VpnController.updateWin()
                }
            }
        })
        sheet.show(parentFragmentManager, "ServerSettings")
    }

    /** Starts a continuous (infinite) clockwise spin on the refresh button icon. */
    private fun startRefreshAnimation() {
        refreshAnimator?.cancel()
        b.refreshBtn.rotation = 0f
        refreshAnimator = ObjectAnimator.ofFloat(b.refreshBtn, View.ROTATION, 0f, 360f).apply {
            duration = 700L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * Stops the spin and resets icon rotation to 0° with a short ease-out so
     * the icon doesn't snap abruptly when the IO operation completes.
     */
    private fun stopRefreshAnimation() {
        refreshAnimator?.cancel()
        refreshAnimator = null
        b.refreshBtn.animate()
            .rotation(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun doRefreshServers() {
        if (isProxyStopped || refreshServersInFlight) return
        refreshServersInFlight = true
        b.refreshBtn.isClickable = false
        startRefreshAnimation()

        io {
            val selectedList = try {
                RpnProxyManager.getEnabledConfigs()
            } catch (e: Exception) {
                Logger.w(LOG_TAG_UI, "$TAG.doRefreshServers: getEnabledCCs failed: ${e.message}")
                emptySet()
            }
            val refreshed = try {
                RpnProxyManager.updateWinProxy(userRequest = true) ?: emptyList()
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "$TAG.doRefreshServers: error: ${e.message}", e)
                emptyList()
            }
            uiCtx {
                // Always stop animation and restore clickability — even if fragment is gone.
                stopRefreshAnimation()
                b.refreshBtn.isClickable = true
                refreshServersInFlight = false
                if (!isAdded) return@uiCtx
                if (refreshed.isNotEmpty()) {
                    initServers(refreshed, selectedList)
                } else {
                    showServerLoadingDialog()
                }
            }
        }
    }

    private fun onToggleProxyFabClicked() {
        if (toggleProxyInFlight) return
        toggleProxyInFlight = true
        b.fabToggleProxy.isClickable = false
        applyFabLoadingState()
        if (isProxyStopped) doStartProxy() else doStopProxy()
    }

    /**
     * Switches the FAB into a loading state:
     * 1. Quick overshoot-bounce (scale 1.0 → 1.12 → 1.0) for a crisp "tap registered" feel.
     * 2. Swaps the icon to [ic_refresh] and spins it continuously.
     *
     * No separate progress ring is shown — the spinning icon on the FAB itself is the
     * loading indicator.  This avoids any alignment or visibility issues.
     */
    private fun applyFabLoadingState() {
        if (!isAdded) return
        b.fabToggleProxy.animate().cancel()
        // Bounce: pop up slightly then settle back — gives tactile confirmation of the tap.
        b.fabToggleProxy.animate()
            .scaleX(1.12f).scaleY(1.12f)
            .setDuration(110)
            .setInterpolator(OvershootInterpolator(2.5f))
            .withEndAction {
                if (isAdded) {
                    b.fabToggleProxy.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }
            .start()
        // Swap icon to the refresh arrow and spin it clockwise continuously.
        b.fabToggleProxy.setImageResource(R.drawable.ic_refresh)
        fabLoadingAnimator?.cancel()
        fabLoadingAnimator = ObjectAnimator.ofFloat(b.fabToggleProxy, View.ROTATION, 0f, 360f).apply {
            duration = 850L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * Cancels the FAB loading animation, snaps rotation and scale back to neutral,
     * and clears [toggleProxyInFlight] so the button can be tapped again.
     *
     * Must be called **before** [applyFabRunningState] / [applyFabStoppedState] so
     * the icon swap they perform isn't immediately overwritten by the animator.
     */
    private fun stopFabLoadingState() {
        fabLoadingAnimator?.cancel()
        fabLoadingAnimator = null
        b.fabToggleProxy.animate().cancel()
        b.fabToggleProxy.rotation = 0f
        b.fabToggleProxy.scaleX = 1f
        b.fabToggleProxy.scaleY = 1f
        b.fabToggleProxy.isClickable = true
        toggleProxyInFlight = false
    }

    private fun doStopProxy() {
        io {
            val success = try {
                RpnProxyManager.stopProxy()
                true
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "$TAG: stop proxy error: ${e.message}", e)
                false
            }
            uiCtx {
                // Always restore FAB state first — even if the fragment is being torn down —
                // so the button is never left permanently disabled.
                stopFabLoadingState()
                if (!isAdded) return@uiCtx
                if (success) {
                    isProxyStopped = true
                    applyProxyStoppedUi()
                    Logger.i(LOG_TAG_UI, "$TAG: proxy stopped")
                } else {
                    // Restore to the pre-click appearance on failure.
                    applyFabRunningState()
                }
            }
        }
    }

    private fun doStartProxy() {
        io {
            RpnProxyManager.startProxy()
            uiCtx {
                stopFabLoadingState()
                if (!isAdded) return@uiCtx
                isProxyStopped = false
                applyProxyRunningUi()
                Logger.i(LOG_TAG_UI, "$TAG: proxy started")
            }
        }
    }

    /** Switches the FAB to "stop" appearance (red, stop icon). */
    private fun applyFabRunningState() {
        if (!isAdded) return
        b.fabToggleProxy.setImageResource(R.drawable.ic_stop)
        b.fabToggleProxy.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.colorRed_A400)
    }

    /** Switches the FAB to "start" appearance (green, VPN/play icon). */
    private fun applyFabStoppedState() {
        if (!isAdded) return
        b.fabToggleProxy.setImageResource(R.drawable.ic_vpn)
        b.fabToggleProxy.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.accentGood)
    }

    /**
     * Polls [VpnController.getWinByKey] for each key in [pendingKeys] every 1.5 s for
     * up to 10 s.  As each key resolves the per-item loading indicator in the
     * selected-server adapter is cleared.  After the deadline all remaining keys are
     * force-cleared so the UI never stays stuck in a loading state.
     */
    private fun startTunnelWatchJob(pendingKeys: Set<String>) {
        tunnelWatchJob?.cancel()
        val remaining = pendingKeys.toMutableSet()
        tunnelWatchJob = lifecycleScope.launch {
            val deadline = System.currentTimeMillis() + 10_000L
            while (remaining.isNotEmpty() && System.currentTimeMillis() < deadline) {
                delay(1_500L)
                val resolved = withContext(Dispatchers.IO) {
                    remaining.filter { VpnController.getWinByKey(it) != null }.toSet()
                }
                if (resolved.isNotEmpty()) {
                    remaining.removeAll(resolved)
                    resolved.forEach { key ->
                        if (isAdded) selectedAdapter.clearLoadingTunnelKey(key)
                    }
                    Logger.i(LOG_TAG_UI, "$TAG: tunnelWatchJob resolved=$resolved, pending=$remaining")
                }
            }
            // Deadline reached or all resolved; clear whatever is left.
            if (isAdded && remaining.isNotEmpty()) {
                Logger.w(LOG_TAG_UI, "$TAG: tunnelWatchJob: timed out with ${remaining.size} unresolved — clearing")
                selectedAdapter.setLoadingTunnelKeys(emptySet<String>())
            }
            tunnelWatchJob = null
        }
    }

    /**
     * Called when the user stops the proxy via the settings sheet.
     *
     * Visual changes:
     * - Hero status chip → amber "Proxy Stopped"
     * - Location hint → "Tap status to restart proxy"
     * - Server lists dimmed to 50 % alpha
     * - Search bar disabled
     * - Both adapters enter stopped mode: selected servers show "Proxy Stopped"
     *   status rows; unselected server items redirect all taps to the settings
     *   sheet rather than allowing new selections.
     *
     * NOTE: RecyclerView.suppressLayout does NOT intercept touch events, so we
     * rely entirely on adapter-level click-handler replacement to block interaction.
     */
    private fun applyProxyStoppedUi() {
        if (!isAdded) return

        b.tvConnectionStatus.text = getString(R.string.server_settings_proxy_stopped)
        b.tvConnectionStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.colorAmber_900)
        )
        b.statusIndicator.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.colorAmber_900)

        // Hint under the flag/country row
        b.tvCurrentLocation.text = getString(R.string.server_settings_tap_to_restart)

        val stoppedAlpha = 0.5f
        b.rvServers.alpha         = stoppedAlpha
        b.rvSelectedServers.alpha = stoppedAlpha
        b.searchCard.alpha        = stoppedAlpha
        b.searchCard.isEnabled    = false
        b.searchBar.isEnabled     = false
        b.searchBar.isFocusable   = false

        // Dim and disable action buttons — they have no effect while stopped
        b.refreshBtn.alpha      = stoppedAlpha
        b.refreshBtn.isClickable = false
        b.settingsBtn.alpha     = stoppedAlpha

        // Adapters replace click handlers so tapping any server item opens the
        // settings sheet instead of selecting/deselecting or opening detail.
        selectedAdapter.setProxyStopped(true)
        serverAdapter.setProxyStopped(true)

        // FAB: switch to "Start" (green VPN icon)
        applyFabStoppedState()
    }

    /**
     * Called when the user restarts the proxy via the settings sheet.
     * Reverts all changes made by [applyProxyStoppedUi] and re-fetches the
     * server list in the background.
     *
     * Deliberately does NOT call [setupRpnState] or [redriveProxyStartStopState]:
     * those would recreate the adapters (losing state) and register duplicate
     * coroutine observers on the lifecycle scope.
     */
    private fun applyProxyRunningUi() {
        if (!isAdded) return

        b.rvServers.alpha              = 1f
        b.rvSelectedServers.alpha      = 1f
        b.searchCard.alpha             = 1f
        b.searchCard.isEnabled         = true
        b.searchBar.isEnabled          = true
        b.searchBar.isFocusable        = true
        b.searchBar.isFocusableInTouchMode = true

        // Restore action buttons
        b.refreshBtn.alpha       = 1f
        b.refreshBtn.isClickable = true
        b.settingsBtn.alpha      = 1f

        selectedAdapter.setProxyStopped(false)
        serverAdapter.setProxyStopped(false)

        updateConnectionStatus(deriveConnectionUiState())

        // FAB: switch to "Stop" (red stop icon)
        applyFabRunningState()

        // refetch WIN registration state and server list on a background thread.
        io {
            isWinRegistered = VpnController.isWinRegistered()
            val servers      = RpnProxyManager.getWinServers()
            val selectedList = RpnProxyManager.getEnabledConfigs()
            val hasRealServers = servers.any { it.id != AUTO_SERVER_ID }

            uiCtx {
                if (!isAdded) return@uiCtx
                if (!isWinRegistered || !hasRealServers) {
                    // WIN is not yet registered or the server list is not populated
                    // immediately after proxy start (can happen on first launch after
                    // reinstall).  The polling dialog will keep retrying until both
                    // conditions are satisfied, then call initServers().
                    showServerLoadingDialog()
                } else {
                    initServers(servers, selectedList)
                }
            }
        }
    }

    private fun openHelpAndSupport() {
        val args = Bundle().apply { putString("ARG_KEY", "Launch_Rethink_Support_Dashboard") }
        startActivity(
            FragmentHostActivity.createIntent(
                context = requireContext(),
                fragmentClass = RethinkPlusDashboardFragment::class.java,
                args = args
            )
        )
    }

    private fun setupRecyclerViews() {
        b.rvServers.layoutManager = LinearLayoutManager(requireContext())
        serverAdapter = CountryServerAdapter(buildCountries(unselectedServers), this)
        b.rvServers.adapter = serverAdapter
        b.rvServers.itemAnimator?.apply { changeDuration = 200; moveDuration = 200; addDuration = 200; removeDuration = 200 }

        b.rvSelectedServers.layoutManager = LinearLayoutManager(requireContext())
        selectedAdapter = VpnServerAdapter(requireContext(), buildSelectedServerGroups(selectedServers), this)
        b.rvSelectedServers.adapter = selectedAdapter
        b.rvSelectedServers.itemAnimator?.apply { changeDuration = 200; moveDuration = 200; addDuration = 200; removeDuration = 200 }
    }

    private fun setupSearchBar() {
        b.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterServers(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        b.searchClearBtn.setOnClickListener {
            b.searchBar.text?.clear()
            animateSearchClearButton(false)
        }
        b.searchBar.setOnFocusChangeListener { _, hasFocus ->
            b.searchCard.animate().scaleX(if (hasFocus) 1.02f else 1f).scaleY(if (hasFocus) 1.02f else 1f).setDuration(150).start()
        }
    }

    private fun animateSearchClearButton(show: Boolean) {
        if (!isAdded) return
        if (show && b.searchClearBtn.visibility != View.VISIBLE) {
            b.searchClearBtn.visibility = View.VISIBLE
            b.searchClearBtn.alpha = 0f; b.searchClearBtn.scaleX = 0.5f; b.searchClearBtn.scaleY = 0.5f
            b.searchClearBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        } else if (!show && b.searchClearBtn.isVisible) {
            b.searchClearBtn.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { if (isAdded) b.searchClearBtn.visibility = View.GONE }
                .start()
        }
    }

    private fun updateSelectedSectionVisibility() {
        if (!isAdded) return
        val hasSelection = selectedServers.isNotEmpty()
        b.selectedServersCard.isVisible = hasSelection
        b.rvSelectedServers.isVisible = hasSelection
        // BUG FIX: only show emptySelectionCard when NOT loading
        b.emptySelectionCard.isVisible = !hasSelection && !isLoading
    }

    private fun updateAllServersCount() {
        if (!isAdded) return
        val count = unselectedServers.distinctBy { it.key }.size
        b.tvServerCount.text = if (count == 0) ""
        else resources.getQuantityString(R.plurals.server_count, count, count)
    }

    private fun updateSelectionCount() {
        if (!isAdded) return
        // Non-AUTO selections count toward the limit
        val nonAutoSelected = selectedServers.count { it.id != AUTO_SERVER_ID }
        b.tvSelectedCount.text = if (nonAutoSelected == 0)
            getString(R.string.server_selection_none_selected)
        else
            getString(
                R.string.server_selection_selected_count,
                resources.getQuantityString(R.plurals.server_selection_count, nonAutoSelected, nonAutoSelected),
                resources.getQuantityString(R.plurals.server_count, MAX_SELECTIONS, MAX_SELECTIONS)
            )
    }

    private fun showErrorState() {
        if (!isAdded) return
        b.rvServers.isVisible = false
        b.searchCard.isVisible = false
        b.supportBtn.isVisible = false
        b.settingsBtn.isVisible = false
        b.refreshBtn.isVisible = false
        b.statusCard.isVisible = false
        b.tvServerHeader.isVisible = false

        b.selectedServersCard.isVisible = false
        b.emptySelectionCard.isVisible = false

        // Animate the container sliding up from below
        b.errorStateContainer.visibility = View.VISIBLE
        b.errorStateContainer.alpha = 0f
        b.errorStateContainer.translationY = 60f
        b.errorStateContainer.animate()
            .alpha(1f).translationY(0f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Pulse the illustration icon once it's visible
        b.errorIllustration.alpha = 0f
        b.errorIllustration.scaleX = 0.6f
        b.errorIllustration.scaleY = 0.6f
        b.errorIllustration.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(180)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        b.errorRetryBtn.isEnabled = true
        b.errorRetryBtn.text = getString(R.string.server_selection_error_retry)
        b.errorRetryBtn.setOnClickListener { retryLoadingServers() }
    }

    private fun hideErrorState() {
        if (!isAdded) return
        if (b.errorStateContainer.isVisible) {
            b.errorStateContainer.animate()
                .alpha(0f).translationY(-40f)
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { if (isAdded) b.errorStateContainer.visibility = View.GONE }
                .start()
        }
        b.rvServers.isVisible = true
        b.searchCard.isVisible = true
        b.supportBtn.isVisible = true
        b.settingsBtn.isVisible = true
        b.refreshBtn.isVisible = true
        b.statusCard.isVisible = true
        b.tvServerHeader.isVisible = true
    }

    /**
     * Retry handler wired to the error-state "Try Again" button and the snackbar action.
     *
     * Delegates to [RpnProxyManager.updateWinProxy] — the canonical WIN proxy refresh.
     *
     */
    private fun retryLoadingServers() {
        if (!isAdded) return
        b.errorRetryBtn.isEnabled = false
        b.errorRetryBtn.text = getString(R.string.lbl_connecting)

        // Dismiss stale Snackbar immediately — user tapped retry, no need to keep showing the error.
        SnackbarHelper.dismiss()

        // Hide error container and show shimmer for immediate visual feedback.
        b.errorStateContainer.animate()
            .alpha(0f).setDuration(200)
            .withEndAction { if (isAdded) b.errorStateContainer.visibility = View.GONE }
            .start()
        setLoadingState(true)

        io {
            isWinRegistered = VpnController.isWinRegistered()
            Logger.v(LOG_TAG_UI, "$TAG.retryLoadingServers: WIN registered=$isWinRegistered")

            val selectedList = try {
                RpnProxyManager.getEnabledConfigs()
            } catch (e: Exception) {
                Logger.w(LOG_TAG_UI, "$TAG.retryLoadingServers: getEnabledCCs failed: ${e.message}")
                emptySet()
            }
            val servers = try {
                val refreshed = RpnProxyManager.updateWinProxy(userRequest = true)
                if (!refreshed.isNullOrEmpty()) refreshed
                else RpnProxyManager.getWinServers()
            } catch (t: Throwable) {
                Logger.e(LOG_TAG_UI, "$TAG.retryLoadingServers: tunnel error (non-fatal): ${t.message}")
                // Fall back to whatever is in the DB/cache so the screen is not blank.
                try { RpnProxyManager.getWinServers() } catch (_: Exception) { emptyList() }
            }

            val hasRealServers = servers.any { it.id != AUTO_SERVER_ID }

            uiCtx {
                if (!isAdded) return@uiCtx
                b.errorRetryBtn.isEnabled = true
                b.errorRetryBtn.text = getString(R.string.server_selection_error_retry)
                when {
                    // WIN not yet registered or server list still empty — hand off to the
                    // polling dialog (same path as setupRpnState / applyProxyRunningUi).
                    (!isWinRegistered || !hasRealServers) && RpnProxyManager.isRpnActive() -> {
                        Logger.i(
                            LOG_TAG_UI,
                            "$TAG.retryLoadingServers: WIN registered=$isWinRegistered, " +
                                "hasRealServers=$hasRealServers — showing loading dialog"
                        )
                        setLoadingState(false)
                        showServerLoadingDialog()
                    }
                    else -> initServers(servers, selectedList)
                }
            }
        }
    }


    private fun buildCountries(servers: List<CountryConfig>): List<CountryServerAdapter.CountryItem> {
        if (servers.isEmpty()) return emptyList()
        // AUTO is always kept exclusively in the selected list; filter it out here
        // as a defensive measure so it can never appear as a selectable country row
        // even if initServers() has a bug that leaves it in unselectedServers.
        return servers
            .filter { !it.id.equals(AUTO_SERVER_ID, ignoreCase = true) }
            .groupBy { it.cc }
            .map { (cc, list) ->
                val sample = list.first()
                val groups = list.groupBy { it.key }.map { (key, grouped) ->
                    val rep = grouped.first()
                    val leastLoad = if (grouped.all { it.load > 0 }) grouped.minOfOrNull { it.load } ?: 0 else 0
                    val bestLink  = if (grouped.all { it.link > 0 }) grouped.maxOfOrNull { it.link } ?: 0 else 0
                    CountryServerAdapter.ServerGroup(key, grouped, rep.serverLocation, leastLoad, bestLink, grouped.any { it.isEnabled })
                }.sortedBy { it.city.lowercase() }
                CountryServerAdapter.CountryItem(cc, sample.countryName, sample.flagEmoji, groups)
            }.sortedBy { it.countryName.lowercase() }
    }

    private fun buildSelectedServerGroups(servers: List<CountryConfig>): List<VpnServerAdapter.ServerGroup> {
        if (servers.isEmpty()) return emptyList()
        return servers.groupBy { it.key }.map { (key, grouped) ->
            val rep = grouped.first()
            val leastLoad = if (grouped.all { it.load > 0 }) grouped.minOfOrNull { it.load } ?: 0 else 0
            val bestLink  = if (grouped.all { it.link > 0 }) grouped.maxOfOrNull { it.link } ?: 0 else 0
            VpnServerAdapter.ServerGroup(key, grouped, rep.countryName, rep.flagEmoji, rep.serverLocation, rep.cc, bestLink, leastLoad, grouped.any { it.isActive })
        }.sortedBy { it.cityName.lowercase() }
    }

    private fun filterServers(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            serverAdapter.updateCountries(buildCountries(unselectedServers))
            animateSearchClearButton(false)
            return
        }
        val filtered = unselectedServers.filter { s ->
            s.countryName.lowercase().contains(q) ||
            s.serverLocation.lowercase().contains(q) ||
            s.cc.lowercase().contains(q)
        }
        serverAdapter.updateCountries(buildCountries(filtered))
        animateSearchClearButton(true)
    }


    private fun onServerSelected(server: CountryConfig, isEnabled: Boolean) {
        if (isEnabled) {
            if (server.id == AUTO_SERVER_ID) {
                // AUTO is always kept enabled — silently ignore attempts to "re-add" it
                if (selectedServers.any { it.id == AUTO_SERVER_ID }) return
                io {
                    val auto = RpnProxyManager.getAutoServer() ?: return@io
                    auto.isEnabled = true
                    RpnProxyManager.updateAutoServerState(auto)
                    uiCtx {
                        if (!isAdded) return@uiCtx

                        server.isEnabled = true
                        selectedServers.add(0, server) // AUTO always first
                        unselectedServers.removeAll { it.id == AUTO_SERVER_ID }
                        refreshAfterSelectionChange()
                    }
                }
                return
            }

            if (selectedServers.any { it.key == server.key }) {
                showToast("${server.serverLocation} is already selected")
                return
            }

            val nonAutoCount = selectedServers.count { it.id != AUTO_SERVER_ID }
            if (nonAutoCount >= MAX_SELECTIONS) {
                showToast(getString(R.string.server_selection_max_reached, MAX_SELECTIONS))
                return
            }

            io {
                val res = RpnProxyManager.enableWinServer(server.key)
                uiCtx {
                    if (!isAdded) return@uiCtx

                    if (!res.first) {
                        showToast("Failed to add ${server.countryName}: ${res.second}")
                        return@uiCtx
                    }
                    val grouped = allServers.filter { it.key == server.key }
                    val best = grouped.minByOrNull { it.load } ?: server
                    grouped.forEach { it.isActive = true }
                    Logger.v(LOG_TAG_UI, "$TAG.onServerSelected: best: $best, grouped: $grouped")
                    if (!selectedServers.any { it.key == best.key }) {
                        selectedServers.add(best)
                    }

                    unselectedServers.removeAll { it.key == server.key }
                    refreshAfterSelectionChange()
                }
            }
        } else {
            if (server.id == AUTO_SERVER_ID) {
                showToast(getString(R.string.server_selection_auto_always_on))
                return
            }

            val key = server.key
            io {
                val res = RpnProxyManager.disableWinServer(key)
                uiCtx {
                    if (!isAdded) return@uiCtx

                    if (!res.first) {
                        showToast("Failed to remove ${server.countryName}: ${res.second}")
                        return@uiCtx
                    }
                    // Mark inactive and move from selected → unselected
                    val removedServers = selectedServers.filter { it.key == key }
                    removedServers.forEach { s ->
                        s.isActive = false
                        s.isEnabled = false
                    }
                    selectedServers.removeAll { it.key == key }

                    allServers.filter { it.key == key }.forEach { s ->
                        s.isActive = false
                        s.isEnabled = false
                        if (!unselectedServers.any { it.key == s.key }) {
                            unselectedServers.add(s)
                        }
                    }
                    refreshAfterSelectionChange()
                }
            }
        }
    }

    /**
     * Single call to refresh adapters + header + counts after any selection change.
     */
    private fun refreshAfterSelectionChange() {
        selectedAdapter.updateServers(selectedServers)
        serverAdapter.updateCountries(buildCountries(unselectedServers))
        updateAllServersCount()
        updateSelectedSectionVisibility()
        updateSelectionCount()
        updateVpnStatus()
    }

    override fun onCitySelected(server: CountryConfig, isEnabled: Boolean) {
        onServerSelected(server, isEnabled)
    }

    override fun onServerGroupSelected(group: VpnServerAdapter.ServerGroup, isSelected: Boolean) {
        onServerSelected(group.getBestServer(), isSelected)
    }

    /**
     * Satisfies both [VpnServerAdapter.ServerSelectionListener] and
     * [CountryServerAdapter.CitySelectionListener].
     *
     * When the proxy is stopped, any tap on a server item (selected or unselected)
     * calls this method.  We show a brief hint pointing the user to the FAB.
     */
    override fun onProxyStoppedItemTapped() {
        SnackbarHelper.show(
            view     = b.root,
            message  = getString(R.string.server_settings_tap_to_restart),
            duration = Snackbar.LENGTH_SHORT
        )
    }

    override fun onServerGroupRemoved(group: VpnServerAdapter.ServerGroup) {
        // AUTO is always kept connected — removal is blocked
        if (group.key == AUTO_SERVER_ID || group.servers.any { it.id == AUTO_SERVER_ID }) {
            showToast(getString(R.string.server_selection_auto_always_on))
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.server_selection_remove_title))
            .setMessage(getString(R.string.server_selection_remove_message, group.countryName, group.cityName))
            .setPositiveButton(getString(R.string.lbl_remove)) { _, _ ->
                io {
                    val res = RpnProxyManager.disableWinServer(group.key)
                    uiCtx {
                        if (!isAdded) return@uiCtx

                        if (!res.first) {
                            showToast("Failed to remove ${group.countryName}: ${res.second}")
                            return@uiCtx
                        }
                        val key = group.key
                        selectedServers.filter { it.key == key }.forEach { s ->
                            s.isActive = false
                            s.isEnabled = false
                        }
                        selectedServers.removeAll { it.key == key }

                        // Restore to unselected
                        allServers.filter { it.key == key }.forEach { s ->
                            s.isActive = false
                            s.isEnabled = false
                            if (!unselectedServers.any { it.id == s.id }) unselectedServers.add(s)
                        }
                        refreshAfterSelectionChange()
                        showToast("${group.countryName};${group.cityName} removed")
                    }
                }
            }
            .setNegativeButton(getString(R.string.lbl_cancel), null)
            .show()
    }

    private fun showToast(msg: String) {
        if (isAdded) Utilities.showToastUiCentered(requireContext(), msg, Toast.LENGTH_SHORT)
    }

    /**
     * Observes [SubscriptionStatusDao.observeCurrentSubscription] via a Room Flow so the
     * banner always reflects the live DB value — no polling needed.
     */
    private fun observeSubscription() {
        // Show shimmer banner while data hasn't arrived yet
        b.shimmerSubscriptionBanner.isVisible = true
        b.shimmerSubscriptionBanner.startShimmer()
        b.subscriptionBanner.isVisible = false

        lifecycleScope.launch {
            subscriptionStatusDao.observeCurrentSubscription().collectLatest { sub ->
                if (!isAdded) return@collectLatest
                uiCtx {
                    b.shimmerSubscriptionBanner.stopShimmer()
                    b.shimmerSubscriptionBanner.isVisible = false
                    updateSubscriptionBanner(sub)
                }
            }
        }
    }

    /**
     * Populates (or hides) the subscription details banner from [sub].
     */
    private fun updateSubscriptionBanner(sub: SubscriptionStatus?) {
        if (sub == null || sub.purchaseToken.isEmpty()) {
            b.subscriptionBanner.isVisible = false
            return
        }

        val now           = System.currentTimeMillis()
        val billingExpiry = sub.billingExpiry
        val hasExpiry     = billingExpiry > 0L && billingExpiry != Long.MAX_VALUE

        // Determine product type (one-time vs recurring subscription)
        val isOneTime = sub.productId.contains("onetime", ignoreCase = true) ||
                sub.productId.contains("inapp", ignoreCase = true)

        // For SUBS: NEVER derive expired from local clock
        // For INAPP: local expiry IS authoritative.
        val isLocallyExpired = isOneTime && hasExpiry && billingExpiry < now

        // DB status tells us the true state (written by state machine after Play reconcile)
        val statusState = SubscriptionStatus.SubscriptionState.fromId(sub.status)
        // For SUBS use DB status for expired check; for INAPP use local clock
        val isEffectivelyExpired = when {
            isOneTime -> isLocallyExpired
            else -> statusState == SubscriptionStatus.SubscriptionState.STATE_EXPIRED ||
                    statusState == SubscriptionStatus.SubscriptionState.STATE_REVOKED
        }

        val showDateRow = when {
            isEffectivelyExpired && !isOneTime -> false   // SUBS expired: badge is enough
            isOneTime -> true                             // always for one-time
            statusState == SubscriptionStatus.SubscriptionState.STATE_CANCELLED ||
            statusState == SubscriptionStatus.SubscriptionState.STATE_GRACE ||
            statusState == SubscriptionStatus.SubscriptionState.STATE_ON_HOLD ||
            statusState == SubscriptionStatus.SubscriptionState.STATE_PAUSED -> true  // show end date
            else -> false  // Active SUBS: hide date row
        }

        if (!showDateRow) {
            // Active SUBS: replace date with a clean "managed by Play" hint
            b.tvExpiryLabel.text = getString(R.string.subscription_label)
            b.tvExpiryDate.text  = getString(R.string.server_selection_sub_managed_by_play)
            b.tvDaysRemaining.isVisible = false
        } else if (!hasExpiry) {
            // Date row needed but expiry not yet known
            b.tvExpiryDate.text = "—"
            b.tvExpiryLabel.text = if (isOneTime)
                getString(R.string.server_selection_sub_expires_on)
            else
                getString(R.string.server_selection_sub_ends_on)
            b.tvDaysRemaining.isVisible = false
        } else {
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(billingExpiry))

            val expiryLabelRes = when {
                isEffectivelyExpired -> R.string.server_selection_sub_expired_on
                isOneTime            -> R.string.server_selection_sub_expires_on
                statusState == SubscriptionStatus.SubscriptionState.STATE_CANCELLED ||
                statusState == SubscriptionStatus.SubscriptionState.STATE_GRACE ->
                    R.string.server_selection_sub_ends_on
                else                 -> R.string.server_selection_sub_ends_on
            }
            b.tvExpiryLabel.text = getString(expiryLabelRes)
            b.tvExpiryDate.text  = dateStr

            if (!isEffectivelyExpired) {
                val daysLeft = TimeUnit.MILLISECONDS.toDays(billingExpiry - now)
                if (daysLeft >= 0) {
                    b.tvDaysRemaining.text = if (daysLeft == 0L)
                        getString(R.string.server_selection_sub_expires_today)
                    else
                        getString(R.string.server_selection_sub_days_left, daysLeft)
                    b.tvDaysRemaining.setTextColor(
                        if (daysLeft <= 7)
                            resolveAttrColor(R.attr.colorGolden)
                        else
                            resolveAttrColor(R.color.primaryText)
                    )
                    b.tvDaysRemaining.isVisible = true
                } else {
                    b.tvDaysRemaining.isVisible = false
                }
            } else {
                b.tvDaysRemaining.isVisible = false
            }
        }

        // Animate banner in if it was previously hidden
        if (!b.subscriptionBanner.isVisible) {
            b.subscriptionBanner.alpha = 0f
            b.subscriptionBanner.isVisible = true
            b.subscriptionBanner.animate()
                .alpha(1f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    /**
     * Collects [RpnProxyManager.serverRemovedEvent] — a SharedFlow emitted by
     * [RpnProxyManager.updateWinProxy] whenever selected servers are found to be
     * absent from the fresh server list returned by the tunnel.
     *
     * Using [kotlinx.coroutines.flow.collect] (not collectLatest) so every removal
     * event is fully processed even if two events arrive in quick succession.
     */
    private fun observeServerRemovedEvents() {
        lifecycleScope.launch {
            RpnProxyManager.serverRemovedEvent.collect { removedConfigs ->
                if (!isAdded || requireActivity().isFinishing) return@collect
                Logger.w(
                    LOG_TAG_UI,
                    "$TAG.observeServerRemovedEvents: ${removedConfigs.size} server(s) removed from tunnel list"
                )
                // Fetch the refreshed list (already synced to DB+cache by updateWinProxy)
                val refreshedServers = try {
                    withContext(Dispatchers.IO) { RpnProxyManager.getWinServers() }
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_UI, "$TAG.observeServerRemovedEvents: could not fetch updated servers: ${e.message}")
                    emptyList()
                }
                val selectedList = try {
                    withContext(Dispatchers.IO) { RpnProxyManager.getEnabledConfigs() }
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_UI, "$TAG.observeServerRemovedEvents: could not fetch selectedList: ${e.message}")
                    emptySet()
                }

                uiCtx {
                    if (!isAdded || requireActivity().isFinishing) return@uiCtx
                    // Guard: don't stack duplicate sheets
                    if (parentFragmentManager.findFragmentByTag("ServerRemovalNotification") != null) {
                        Logger.d(LOG_TAG_UI, "$TAG.observeServerRemovedEvents: sheet already showing, skipping")
                        // Still refresh the list even if the sheet is already up
                        if (refreshedServers.isNotEmpty()) initServers(refreshedServers, selectedList)
                        return@uiCtx
                    }
                    try {
                        showSettingsBottomSheet(
                            removedServers   = removedConfigs,
                            refreshedServers = refreshedServers,
                            selectedList     = selectedList
                        )
                    } catch (e: Exception) {
                        Logger.e(LOG_TAG_UI, "$TAG.observeServerRemovedEvents: error showing sheet: ${e.message}", e)
                        // Fall back: just refresh the list so removed servers are gone from UI
                        if (refreshedServers.isNotEmpty()) initServers(refreshedServers, selectedList)
                    }
                }
            }
        }
    }

    /**
     * Shows a non-dismissable, informative dialog while WIN registration or the
     * server-list fetch is in progress.
     *
     * Behaviour:
     * - Polls [VpnController.isWinRegistered] + [RpnProxyManager.getWinServers] every
     *   [LOADING_DIALOG_POLL_INTERVAL_MS] milliseconds.
     * - Automatically dismisses and calls [initServers] as soon as both conditions
     *   are satisfied (registered AND at least one real server present).
     * - Hard timeout of [LOADING_DIALOG_TIMEOUT_MS] ms; on expiry the dialog dismisses
     *   and calls [initServers] with whatever is cached, which may surface the error
     *   state if servers are still unavailable.
     * - Calling this while a dialog is already showing is a no-op (safe to call
     *   from multiple entry points).
     */
    private fun showServerLoadingDialog() {
        if (!isAdded) return
        // Guard: don't stack multiple dialogs
        if (serverLoadingDialog?.isShowing == true) {
            Logger.d(LOG_TAG_UI, "$TAG: loading dialog already showing — skipping duplicate call")
            return
        }
        dismissServerLoadingDialog()

        val dialogView = layoutInflater.inflate(R.layout.dialog_server_loading, null)
        val tvStatus = dialogView.findViewById<android.widget.TextView>(R.id.tv_server_loading_status)
        val timeoutBar = dialogView.findViewById<LinearProgressIndicator>(R.id.server_loading_timeout_bar)
        timeoutBar.max = LOADING_DIALOG_TIMEOUT_MS.toInt()
        timeoutBar.setProgressCompat(0, false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        serverLoadingDialog = dialog

        Logger.i(
            LOG_TAG_UI,
            "$TAG: showServerLoadingDialog; waiting up to ${LOADING_DIALOG_TIMEOUT_MS / 1000}s"
        )

        val statusMessages = listOf(
            getString(R.string.server_loading_dialog_status_checking),
            getString(R.string.server_loading_dialog_status_connecting),
            getString(R.string.server_loading_dialog_status_fetching),
            getString(R.string.server_loading_dialog_status_almost),
        )

        serverLoadingJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            var msgIdx = 0

            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= LOADING_DIALOG_TIMEOUT_MS) break

                // Decide which status message to show
                val statusMsg = when {
                    elapsed > LOADING_DIALOG_TIMEOUT_MS * 0.75 ->
                        getString(R.string.server_loading_dialog_status_timeout)
                    else -> statusMessages[msgIdx % statusMessages.size]
                }

                // cross-fade the status text for a polished feel
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    // Animate old text out, swap, animate new text in
                    tvStatus.animate().alpha(0f).setDuration(120).withEndAction {
                        if (isAdded) {
                            tvStatus.text = statusMsg
                            tvStatus.animate().alpha(0.65f).setDuration(120).start()
                        }
                    }.start()
                    timeoutBar.setProgressCompat(elapsed.coerceAtMost(LOADING_DIALOG_TIMEOUT_MS).toInt(), true)
                }

                // Poll registration status and server list on the IO thread
                val winRegistered = withContext(Dispatchers.IO) {
                    try { VpnController.isWinRegistered() } catch (_: Exception) { false }
                }
                val servers = withContext(Dispatchers.IO) {
                    try { RpnProxyManager.getWinServers() } catch (_: Exception) { emptyList() }
                }
                val hasRealServers = servers.any { it.id != AUTO_SERVER_ID }

                Logger.d(
                    LOG_TAG_UI,
                    "$TAG: loading dialog poll — winRegistered=$winRegistered, " +
                        "realServers=${servers.count { it.id != AUTO_SERVER_ID }}, elapsed=${elapsed}ms"
                )

                if (winRegistered && hasRealServers) {
                    // Registration complete and servers available, load the list
                    val selectedList = withContext(Dispatchers.IO) {
                        try { RpnProxyManager.getEnabledConfigs() } catch (_: Exception) { emptySet() }
                    }
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        Logger.i(
                            LOG_TAG_UI,
                            "$TAG: loading dialog — registration complete (elapsed=${elapsed}ms)"
                        )
                        isWinRegistered = true
                        dismissServerLoadingDialog()
                        initServers(servers, selectedList)
                    }
                    return@launch
                }

                delay(LOADING_DIALOG_POLL_INTERVAL_MS)
                msgIdx++
            }

            // Timed out; dismiss dialog and surface whatever state we have.
            // If the proxy is not active by now, apply the stopped UI so the FAB is visible.
            Logger.w(
                LOG_TAG_UI,
                "$TAG: loading dialog timed out after ${LOADING_DIALOG_TIMEOUT_MS / 1000}s"
            )
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                dismissServerLoadingDialog()
                setLoadingState(false)
                if (!RpnProxyManager.isRpnActive()) {
                    isProxyStopped = true
                    applyProxyStoppedUi()
                } else {
                    showErrorState()
                }
            }
        }
    }

    /**
     * Cancels the polling job and safely dismisses the loading dialog.
     * Safe to call when no dialog is showing.
     */
    private fun dismissServerLoadingDialog() {
        serverLoadingJob?.cancel()
        serverLoadingJob = null
        runCatching {
            if (serverLoadingDialog?.isShowing == true) serverLoadingDialog?.dismiss()
        }
        serverLoadingDialog = null
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun resolveAttrColor(attrRes: Int): Int {
        UIUtils.fetchColor(requireContext(), attrRes).let { return it }
    }
}

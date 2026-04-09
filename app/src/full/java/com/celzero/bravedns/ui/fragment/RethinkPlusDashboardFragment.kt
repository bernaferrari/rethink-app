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
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.database.SubscriptionStatus
import com.celzero.bravedns.database.SubscriptionStatusDao
import com.celzero.bravedns.databinding.ActivityRethinkPlusDashboardBinding
import com.celzero.bravedns.iab.InAppBillingHandler
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.subscription.SubscriptionStateMachineV2
import com.celzero.bravedns.ui.activity.CustomerSupportActivity
import com.celzero.bravedns.ui.activity.FragmentHostActivity
import com.celzero.bravedns.ui.activity.PingTestActivity
import com.celzero.bravedns.ui.activity.PurchaseHistoryActivity
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RethinkPlusDashboardFragment : Fragment(R.layout.activity_rethink_plus_dashboard) {
    private val b by viewBinding(ActivityRethinkPlusDashboardBinding::bind)

    private val subscriptionStatusDao by inject<SubscriptionStatusDao>()

    companion object {
        private const val TAG = "RPNDashFrag"
        /** Show "expiring soon" banner when fewer than this many days remain for an INAPP purchase. */
        private const val EXPIRING_SOON_THRESHOLD_DAYS = 30L
    }

    private fun safeNavigate(actionId: Int) {
        try {
            findNavController().navigate(actionId)
        } catch (_: IllegalStateException) {
            Logger.w(LOG_TAG_UI, "$TAG safeNavigate: no NavController (action=$actionId)")
            requireActivity().finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isAdded) return
        setupToolbar()
        setupClickListeners()
        loadSubscriptionBanner()
        observeSubscriptionState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh banner on resume so changes from ManageSubscription are reflected
        if (isAdded) loadSubscriptionBanner()
    }

    private fun setupToolbar() {
        // Set the collapsing title — shown in both expanded and collapsed states.
        b.collapsingToolbar.title = getString(R.string.rethink_plus_title)
    }

    /**
     * Load the current subscription from DB and populate the collapsing header
     * and the details card below.  Runs on IO; posts to Main.
     */
    private fun loadSubscriptionBanner() {
        io {
            val sub = runCatching { subscriptionStatusDao.getCurrentSubscription() }.getOrNull()
            val state = RpnProxyManager.getSubscriptionState()
            // SubscriptionStatus.deviceId which holds only the sentinel "pip/identity.json".
            val deviceId = runCatching { InAppBillingHandler.getObfuscatedDeviceId() }.getOrDefault("")
            uiCtx { populateBanner(sub, state, deviceId) }
        }
    }

    private fun populateBanner(
        sub: SubscriptionStatus?,
        state: SubscriptionStateMachineV2.SubscriptionState,
        realDeviceId: String = ""
    ) {
        if (!isAdded) return

        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        // Resolve friendly plan name
        val rawPlan = sub?.let {
            it.productTitle.ifBlank { it.planId.ifBlank { it.productId } }
        } ?: ""
        val planName = resolvePlanName(rawPlan)

        // Hero subtitle: "RPN Standard  ·  ID: 74b4c00217"
        val accountId = sub?.accountId?.take(12) ?: ""
        // Use the real device ID fetched from SecureIdentityStore (never sub.deviceId directly).
        val deviceId = realDeviceId.take(4)
        val id = "$accountId •  $deviceId"
        b.tvHeroSubtitle.text = when {
            planName.isNotEmpty() && accountId.isNotEmpty() ->
                getString(R.string.hero_plan_and_account, planName, id)
            planName.isNotEmpty() -> planName
            accountId.isNotEmpty() -> id
            else -> getString(R.string.rethink_plus_title)
        }

        val displayPlan = planName.ifBlank { getString(R.string.no_active_subscription) }
        b.tvDetailPlan.text = displayPlan

        b.tvDetailActivated.text = if (sub != null && sub.purchaseTime > 0)
            fmt.format(Date(sub.purchaseTime))
        else getString(R.string.placeholder_dash)

        val isInApp = sub != null && isInAppProduct(sub.productId, sub.planId)
        val isRevoked = state is SubscriptionStateMachineV2.SubscriptionState.Revoked
        val hasKnownExpiry = !isRevoked &&
                sub != null && sub.billingExpiry > 0 &&
                sub.billingExpiry != Long.MAX_VALUE &&
                (isInApp ||
                 state is SubscriptionStateMachineV2.SubscriptionState.Expired ||
                 state is SubscriptionStateMachineV2.SubscriptionState.Cancelled)

        b.dividerExpiry.isVisible = hasKnownExpiry
        b.rowDetailExpiry.isVisible = hasKnownExpiry
        if (hasKnownExpiry && sub != null) {
            b.tvDetailExpiry.text = fmt.format(Date(sub.billingExpiry))
        }

        // Renew CTA
        b.renewButton.isVisible = !state.hasValidSubscription

        // Expiring-soon banner — only for active INAPP purchases within 30 days of expiry
        updateExpiringBanner(sub, state)
    }

    /**
     * Shows a "your access expires in N days" banner when an INAPP purchase is within
     * 30 days of its access-window end.  Clicking the banner navigates to the purchase
     * flow so the user can buy again.
     *
     * Silently no-ops when the current product is a subscription (auto-renews) or when
     * expiry information is unavailable.
     */
    private fun updateExpiringBanner(
        sub: SubscriptionStatus?,
        state: SubscriptionStateMachineV2.SubscriptionState
    ) {
        try {
            val isInApp = sub != null && isInAppProduct(sub.productId, sub.planId)
            if (!isInApp || !state.hasValidSubscription) {
                b.expiringBannerCard.isVisible = false
                return
            }
            val remainingDays = InAppBillingHandler.getRemainingDaysForInApp() ?: run {
                b.expiringBannerCard.isVisible = false
                return
            }
            val isExpiringSoon = remainingDays in 0..EXPIRING_SOON_THRESHOLD_DAYS
            b.expiringBannerCard.isVisible = isExpiringSoon
            if (isExpiringSoon) {
                b.tvExpiringBanner.text = getString(R.string.inapp_expiry_soon, remainingDays.coerceAtLeast(0L))
                // Clicking the banner takes the user directly to the purchase flow
                b.expiringBannerCard.setOnClickListener {
                    safeNavigate(R.id.action_rethinkPlusDashboard_to_rethinkPlus)
                }
                Logger.i(LOG_TAG_UI, "$TAG expiring banner shown: remainingDays=$remainingDays")
            }
        } catch (e: Exception) {
            Logger.w(LOG_TAG_UI, "$TAG updateExpiringBanner error (non-fatal): ${e.message}")
        }
    }

    /** Maps a raw product title/id to a friendly display name. */
    private fun resolvePlanName(raw: String): String = when {
        raw.contains("standard", ignoreCase = true) -> getString(R.string.plan_rpn_standard)
        raw.contains("plus",     ignoreCase = true) -> getString(R.string.plan_rpn_one_time)
        raw.isNotBlank() -> raw
        else -> ""
    }

    private fun resolveAttrColor(attrRes: Int): Int {
        val ta = requireContext().obtainStyledAttributes(intArrayOf(attrRes))
        return try { ta.getColor(0, 0) } finally { ta.recycle() }
    }

    /** Returns true if the given productId/planId belongs to a one-time INAPP purchase. */
    private fun isInAppProduct(productId: String, planId: String): Boolean {
        val inAppIds = setOf(
            InAppBillingHandler.ONE_TIME_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS,
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS,
            InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID
        )
        return productId in inAppIds || planId in inAppIds
    }

    private fun observeSubscriptionState() {
        io {
            RpnProxyManager.collectSubscriptionState().collect { state ->
                val sub = runCatching { subscriptionStatusDao.getCurrentSubscription() }.getOrNull()
                val deviceId = runCatching { InAppBillingHandler.getObfuscatedDeviceId() }.getOrDefault("")
                uiCtx {
                    populateBanner(sub, state, deviceId)
                    handleStateChange(state)
                }
            }
        }
    }

    private fun handleStateChange(state: SubscriptionStateMachineV2.SubscriptionState) {
        when (state) {
            is SubscriptionStateMachineV2.SubscriptionState.Active,
            is SubscriptionStateMachineV2.SubscriptionState.Grace -> {
                b.renewButton.isVisible = false
            }
            is SubscriptionStateMachineV2.SubscriptionState.Cancelled -> {
                b.renewButton.isVisible = true
            }
            is SubscriptionStateMachineV2.SubscriptionState.Revoked -> {
                b.renewButton.isVisible = true
            }
            is SubscriptionStateMachineV2.SubscriptionState.Expired -> {
                b.renewButton.isVisible = true
            }
            is SubscriptionStateMachineV2.SubscriptionState.Uninitialized,
            is SubscriptionStateMachineV2.SubscriptionState.Initial -> {
                // transient — ignore
            }
            else -> Logger.d(LOG_TAG_UI, "$TAG state: ${state.javaClass.simpleName}")
        }
    }

    private fun setupClickListeners() {
        b.pingTestRl.setOnClickListener {
            startActivity(Intent(requireContext(), PingTestActivity::class.java))
        }
        b.manageSubsRl.setOnClickListener { managePlayStoreSubs() }
        b.paymentHistoryRl.setOnClickListener { openBillingHistory() }
        b.reportIssueRl.setOnClickListener { CustomerSupportActivity.start(requireContext()) }
        b.renewButton.setOnClickListener {
            safeNavigate(R.id.action_rethinkPlusDashboard_to_rethinkPlus)
        }
    }

    private fun managePlayStoreSubs() {
        val args = Bundle().apply { putString("ARG_KEY", "Launch_Manage_Subscriptions") }
        startActivity(
            FragmentHostActivity.createIntent(
                context = requireContext(),
                fragmentClass = ManagePurchaseFragment::class.java,
                args = args
            )
        )
    }

    private fun openBillingHistory() {
        try {
            startActivity(Intent(requireContext(), PurchaseHistoryActivity::class.java))
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG openBillingHistory error: ${e.message}", e)
            showToastUiCentered(requireContext(), getString(R.string.payment_history_open_error), Toast.LENGTH_SHORT)
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) = withContext(Dispatchers.Main) { f() }
    private fun io(f: suspend () -> Unit) = lifecycleScope.launch(Dispatchers.IO) { f() }
}

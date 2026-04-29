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
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.FragmentManagePurchaseBinding
import com.celzero.bravedns.iab.InAppBillingHandler
import com.celzero.bravedns.iab.InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_2YRS_DAYS
import com.celzero.bravedns.iab.InAppBillingHandler.REVOKE_WINDOW_ONE_TIME_5YRS_DAYS
import com.celzero.bravedns.iab.InAppBillingHandler.REVOKE_WINDOW_SUBS_MONTHLY_DAYS
import com.celzero.bravedns.iab.InAppBillingHandler.REVOKE_WINDOW_SUBS_YEARLY_DAYS
import com.celzero.bravedns.iab.PurchaseConflictNotifier
import com.celzero.bravedns.iab.DeviceNotRegisteredNotifier
import com.celzero.bravedns.iab.ServerApiError
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.rpnproxy.SubscriptionStateMachineV2
import com.celzero.bravedns.ui.activity.FragmentHostActivity
import com.celzero.bravedns.ui.bottomsheet.PurchaseConflictBottomSheet
import com.celzero.bravedns.ui.bottomsheet.DeviceAuthErrorBottomSheet
import com.celzero.bravedns.ui.bottomsheet.DeviceNotRegisteredBottomSheet
import com.celzero.bravedns.ui.bottomsheet.ResubscribeBottomSheet
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.openUrl
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.celzero.bravedns.viewmodel.ManagePurchaseViewModel
import com.celzero.bravedns.viewmodel.ManagePurchaseViewModel.OperationState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

class ManagePurchaseFragment : Fragment(R.layout.fragment_manage_purchase) {
    private val b by viewBinding(FragmentManagePurchaseBinding::bind)

    /**
     * The ViewModel owns the cancel/revoke coroutine.
     */
    private val viewModel: ManagePurchaseViewModel by viewModel()

    /**
     * Enabled only while an operation is in progress so the user cannot navigate away
     * mid-cancel/revoke and leave local state out of sync with the server.
     */
    private val blockBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Logger.d(LOG_TAG_UI, "$TAG: back press blocked, operation in progress")
        }
    }

    companion object {
        private const val TAG = "ManSubFragment"
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        /** Show "expiring soon" banner when fewer than this many days remain for an INAPP purchase. */
        private const val EXPIRING_SOON_THRESHOLD_DAYS = 30L
        private const val SUPPORT_EMAIL = "hello@celzero.com"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, blockBackCallback)
        applyNavigationBarInset()
        initView()
        setupClickListeners()
        setupServerErrorObserver()
        observeOperationState()
    }

    private fun applyNavigationBarInset() {
        b.nestedScroll.doOnAttach { view ->
            val navBarHeight = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom ?: 0
            view.doOnNextLayout {
                view.updatePadding(top = 0, bottom = navBarHeight)
            }
        }
    }

    /**
     * Collects [ManagePurchaseViewModel.operationState] and drives the progress UI.
     */
    private fun observeOperationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.operationState.collect { state ->
                    when (state) {
                        is OperationState.Idle -> hideProgressOverlay()

                        is OperationState.InProgress -> {
                            showProgressOverlay(state)
                        }

                        is OperationState.Success -> {
                            hideProgressOverlay()
                            showToastUiCentered(requireContext(), state.message, Toast.LENGTH_SHORT)
                            initView()
                            viewModel.resetOperationState()
                        }

                        is OperationState.Failure -> {
                            hideProgressOverlay()
                            showToastUiCentered(requireContext(), state.message, Toast.LENGTH_LONG)
                            viewModel.resetOperationState()
                        }
                    }
                }
            }
        }
    }

    /**
     * Shows the step-progress overlay and updates it to reflect [state].
     */
    private fun showProgressOverlay(state: OperationState.InProgress) {
        b.loadingOverlay.isVisible = true
        // Block back navigation while the operation runs.
        blockBackCallback.isEnabled = true

        val opLabel = if (state.isCancel)
            getString(R.string.manage_sub_cancelling)
        else
            getString(R.string.manage_sub_revoking)

        b.tvLoadingMessage.text = opLabel
        b.tvLoadingSubMessage.text = getString(R.string.progress_do_not_close)
        
        val currentOrdinal = state.step.ordinal  // VALIDATING=0, SERVER=1, LOCAL=2, REFRESH=3, DONE=4

        // Map each Step to its icon + label views.
        data class StepViews(val icon: AppCompatImageView, val label: AppCompatTextView)

        val steps = listOf(
            StepViews(b.stepIconValidating, b.stepLabelValidating),
            StepViews(b.stepIconServer,     b.stepLabelServer),
            StepViews(b.stepIconLocal,      b.stepLabelLocal),
            StepViews(b.stepIconRefresh,    b.stepLabelRefresh)
        )

        val colorDone = UIUtils.fetchColor(requireContext(), R.attr.accentGood)
        val colorPending = UIUtils.fetchColor(requireContext(), R.attr.primaryTextColor)

        steps.forEachIndexed { index, sv ->
            val isDone    = index < currentOrdinal
            val isCurrent = index == currentOrdinal

            // Icon tint: green for done/current, dim for future.
            val tint = if (isDone || isCurrent) colorDone else colorPending
            sv.icon.setColorFilter(tint)

            // Text style: bold + primary colour for done/current, light for future.
            if (isDone || isCurrent) {
                sv.label.setTextColor(UIUtils.fetchColor(requireContext(), R.attr.primaryTextColor))
                sv.label.alpha = 1f
            } else {
                sv.label.setTextColor(colorPending)
                sv.label.alpha = 0.5f
            }
        }
    }

    private fun hideProgressOverlay() {
        b.loadingOverlay.isVisible = false
        // Re-enable navigation once the operation has finished.
        blockBackCallback.isEnabled = false
    }

    private fun setupToolbar(subscriptionData: SubscriptionStateMachineV2.SubscriptionData, deviceId: String) {
        b.collapsingToolbar.title = getString(R.string.manage_purchase_title)
        b.tvHeroSubtitle.text = buildHeroSubtitle(subscriptionData, deviceId)
    }

    private fun buildHeroSubtitle(subscriptionData: SubscriptionStateMachineV2.SubscriptionData, realDeviceId: String): String {
        val sub = subscriptionData.subscriptionStatus
        // Purchase token (show first 12 chars)
        var token = sub.purchaseToken
        token = if (token.length > 12) token.take(12) + "…" else token.ifBlank { "" }
        val accountId = sub.accountId.take(12).ifBlank { return token }
        // Hero subtitle: "RPN Standard · 74b4c00217 · 1234"
        // Use the real device ID fetched from SecureIdentityStore (never sub.deviceId directly).
        val deviceId = realDeviceId.take(4)
        val id = "$accountId • $deviceId"
        b.tvHeroSubtitle.text = when {
            token.isNotEmpty() && accountId.isNotEmpty() ->
                getString(R.string.hero_plan_and_account, token, id)
            token.isNotEmpty() -> token
            accountId.isNotEmpty() -> id
            else -> getString(R.string.rethink_plus_title)
        }
        return if (token.isNotEmpty()) "$token \u00B7 $id" else id
    }

    private fun resolvePlanName(subscriptionData: SubscriptionStateMachineV2.SubscriptionData): String {
        val productId = subscriptionData.purchaseDetail?.productId.orEmpty()
        return when (productId) {
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> getString(R.string.plan_2yr)
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> getString(R.string.plan_5yr)
            InAppBillingHandler.SUBS_PRODUCT_YEARLY -> getString(R.string.billing_yearly)
            InAppBillingHandler.SUBS_PRODUCT_MONTHLY -> getString(R.string.monthly_plan)
            else -> subscriptionData.purchaseDetail?.productTitle?.ifEmpty { productId } ?: productId
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.operationState.value is OperationState.Idle) {
            initView()
        }
    }

    private fun initView() {
        try {
            val subscriptionData  = RpnProxyManager.getSubscriptionData()
            val subscriptionState = RpnProxyManager.getSubscriptionState()

            val hasSubscription = subscriptionData != null && subscriptionState.hasValidSubscription
            val isKnownExpiredOrCancelled = !hasSubscription &&
                    subscriptionData != null &&
                    (subscriptionState.state().isExpired || subscriptionState.state().isCancelled)

            if (!hasSubscription && !isKnownExpiredOrCancelled) {
                showNoSubscriptionState()
                return
            }

            io {
                val deviceId = InAppBillingHandler.getObfuscatedDeviceId()
                uiCtx {
                    setupToolbar(subscriptionData, deviceId)
                    showSubscriptionState(subscriptionData, deviceId)
                }
            }
            updateStatusBadge(subscriptionState)
            updatePlanDetails(subscriptionData)
            updateFeaturesList(subscriptionData)
            updateBillingCycle(subscriptionData)
            updateDates(subscriptionData, subscriptionState)
            updateExpiringBanner(subscriptionData, subscriptionState)
            showCancelOrRevokeButton()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error initializing view: ${e.message}", e)
            showNoSubscriptionState()
            showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
        }
    }

    /**
     * Shows the premium "no subscription" empty state.
     * Hides the subscription detail card and features card completely.
     */
    private fun showNoSubscriptionState() {
        // Show the empty-state card
        b.noSubscriptionContainer.isVisible = true

        // Hide all subscription-specific content
        b.subscriptionDetailsCard.isVisible = false
        b.featuresCard.isVisible = false
        b.actionButtonsContainer.isVisible = false

        // Update the hero subtitle for new users
        b.tvHeroSubtitle.text = getString(R.string.manage_sub_no_sub_hero_subtitle)
    }

    /**
     * Shows normal subscription content cards (used when subscription exists).
     */
    private fun showSubscriptionState(subscriptionData: SubscriptionStateMachineV2.SubscriptionData, deviceId: String) {
        b.noSubscriptionContainer.isVisible = false
        b.subscriptionDetailsCard.isVisible = true
        b.featuresCard.isVisible = true
        b.actionButtonsContainer.isVisible = true
        // Restore hero subtitle with actual plan/account info
        b.tvHeroSubtitle.text = buildHeroSubtitle(subscriptionData, deviceId)
    }

    private fun updateDates(
        subscriptionData: SubscriptionStateMachineV2.SubscriptionData?,
        state: SubscriptionStateMachineV2.SubscriptionState
    ) {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val sub = subscriptionData?.subscriptionStatus

        // Activation date
        b.tvActivationDate.text = if (sub != null && sub.purchaseTime > 0)
            fmt.format(Date(sub.purchaseTime))
        else "-"

        val isInApp = sub != null && isInAppProduct(sub.productId, sub.planId)
        val isRevoked = state is SubscriptionStateMachineV2.SubscriptionState.Revoked
        val hasKnownExpiry = !isRevoked &&
                sub != null && sub.billingExpiry > 0 && sub.billingExpiry != Long.MAX_VALUE &&
                (isInApp ||
                 state is SubscriptionStateMachineV2.SubscriptionState.Expired ||
                 state is SubscriptionStateMachineV2.SubscriptionState.Cancelled)

        b.dividerExpiry.isVisible   = hasKnownExpiry
        b.labelExpiryDate.isVisible = hasKnownExpiry
        b.tvExpiryDate.isVisible    = hasKnownExpiry

        if (hasKnownExpiry) {
            b.tvExpiryDate.text = fmt.format(Date(sub.billingExpiry))
            // For a canceled subscription the user still has access until billingExpiry,
            // so label the field "Access Until" instead of the generic "Expires" to make
            // the distinction clear.
            b.labelExpiryDate.text = if (state is SubscriptionStateMachineV2.SubscriptionState.Cancelled) {
                getString(R.string.label_access_until)
            } else {
                getString(R.string.dashboard_expiry_label)
            }
        }
    }

    /**
     * Shows a renewal banner when an INAPP purchase is expiring within 30 days.
     *
     * The banner is shown only for one-time (INAPP) purchases, subscriptions auto-renew
     * so they never need a manual renewal prompt. The threshold is 30 days to give users
     * enough time to repurchase before losing access.
     */
    private fun updateExpiringBanner(
        subscriptionData: SubscriptionStateMachineV2.SubscriptionData?,
        state: SubscriptionStateMachineV2.SubscriptionState
    ) {
        try {
            val sub = subscriptionData?.subscriptionStatus ?: return
            val isInApp = isInAppProduct(sub.productId, sub.planId)

            // Only show for active INAPP purchases
            if (!isInApp || !state.hasValidSubscription) {
                b.expiringBannerCard.isVisible = false
                return
            }

            val billingExpiry = sub.billingExpiry
            if (billingExpiry > 0L && billingExpiry != Long.MAX_VALUE) {
                val days = (billingExpiry - System.currentTimeMillis()) / ONE_DAY_MS
                if (days in 0..EXPIRING_SOON_THRESHOLD_DAYS) {
                    b.expiringBannerCard.isVisible = true
                    b.tvExpiringBanner.text = getString(R.string.inapp_expiry_soon, days.coerceAtLeast(0L))
                    b.btnExtendAccess.setOnClickListener { navigateToOneTimePurchase() }
                }
            }

            io {
                val remainingDays = InAppBillingHandler.getRemainingDaysForInAppSuspend()
                uiCtx {
                    if (remainingDays == null) return@uiCtx
                    val isExpiringSoon = remainingDays in 0..EXPIRING_SOON_THRESHOLD_DAYS
                    b.expiringBannerCard.isVisible = isExpiringSoon || true
                    if (isExpiringSoon || true) {
                        val days = remainingDays.coerceAtLeast(0L)
                        b.tvExpiringBanner.text = getString(R.string.inapp_expiry_soon, days)
                        b.btnExtendAccess.setOnClickListener { navigateToOneTimePurchase() }
                        Logger.i(LOG_TAG_UI, "$TAG expiring banner shown: remainingDays=$remainingDays")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(LOG_TAG_UI, "$TAG updateExpiringBanner error (non-fatal): ${e.message}")
        }
    }

    private fun updateStatusBadge(state: SubscriptionStateMachineV2.SubscriptionState) {
        try {
            val (statusText, statusColor, statusDescription) = when {
                state.state().isActive    -> Triple(
                    getString(R.string.lbl_active),
                    themeColor(R.attr.accentGood),
                    getString(R.string.status_active_description))
                state.state().isCancelled -> Triple(
                    getString(R.string.status_cancelled),
                    themeColor(R.attr.accentWarning),
                    getString(R.string.status_cancelled_description))
                state.state().isExpired   -> Triple(
                    getString(R.string.status_expired),
                    themeColor(R.attr.accentBad),
                    getString(R.string.status_expired_description))
                // Revoked: access removed immediately (refund / policy violation).
                state.state().isRevoked   -> Triple(
                    getString(R.string.status_revoked),
                    themeColor(R.attr.accentBad),
                    getString(R.string.status_revoked_description))
                // OnHold: payment is failing; Google Play is retrying charges.
                state is SubscriptionStateMachineV2.SubscriptionState.OnHold -> Triple(
                    getString(R.string.status_on_hold),
                    themeColor(R.attr.accentWarning),
                    getString(R.string.status_on_hold_description))
                // Paused: user voluntarily paused the subscription via Google Play.
                state is SubscriptionStateMachineV2.SubscriptionState.Paused -> Triple(
                    getString(R.string.status_paused),
                    themeColor(R.attr.accentWarning),
                    getString(R.string.status_paused_description))
                else -> Triple(
                    getString(R.string.lbl_inactive),
                    themeColor(R.attr.primaryTextColor),
                    getString(R.string.no_active_subscription))
            }
            b.tvStatus.text = statusText
            b.statusBadge.setCardBackgroundColor(statusColor)
            b.tvStatusDescription.text = statusDescription
            b.tvStatusDescription.visibility = View.VISIBLE
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error updating status badge: ${e.message}", e)
            b.tvStatus.text = getString(R.string.lbl_inactive)
            b.statusBadge.setCardBackgroundColor(themeColor(R.attr.primaryTextColor))
            b.tvStatusDescription.visibility = View.GONE
        }
    }

    /** Resolves a theme colour attribute to an ARGB int, with a safe fallback. */
    private fun themeColor(attr: Int): Int = UIUtils.fetchColor(requireContext(), attr)

    private fun updatePlanDetails(subscriptionData: SubscriptionStateMachineV2.SubscriptionData?) {
        try {
            if (subscriptionData == null) {
                b.divider1.visibility = View.GONE
                b.labelCurrentPlan.visibility = View.GONE
                b.tvPlan.visibility = View.GONE
                return
            }

            b.divider1.visibility = View.VISIBLE
            b.labelCurrentPlan.visibility = View.VISIBLE
            b.tvPlan.visibility = View.VISIBLE
            b.tvPlan.text = resolvePlanName(subscriptionData)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error updating plan details: ${e.message}", e)
            b.divider1.visibility = View.GONE
            b.labelCurrentPlan.visibility = View.GONE
            b.tvPlan.visibility = View.GONE
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateFeaturesList(subscriptionData: SubscriptionStateMachineV2.SubscriptionData?) {
        try {
            val features = listOf(
                getString(R.string.feature_unlimited_bandwidth),
                getString(R.string.feature_all_servers),
                getString(R.string.feature_ad_free),
                getString(R.string.feature_priority_support),
                getString(R.string.feature_multi_device)
            )
            b.tvFeatures.text = features.joinToString(separator = "\n") { "✓ $it" }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error updating features: ${e.message}", e)
            b.tvFeatures.text = getString(R.string.subscription_features_unavailable)
        }
    }

    private fun updateBillingCycle(subscriptionData: SubscriptionStateMachineV2.SubscriptionData?) {
        try {
            val planId = subscriptionData?.purchaseDetail?.planId.orEmpty()
            val txt = when (planId)  {
                InAppBillingHandler.SUBS_PRODUCT_YEARLY -> getString(R.string.billing_yearly)
                InAppBillingHandler.SUBS_PRODUCT_MONTHLY -> getString(R.string.monthly_plan)
                else -> ""
            }
            if (txt.isEmpty()) {
                b.divider2.visibility = View.GONE
                b.tvBillingCycle.visibility = View.GONE
                b.labelBillingCycle.visibility = View.GONE
            } else {
                b.tvBillingCycle.text = txt
            }

        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error updating billing cycle: ${e.message}", e)
            b.divider2.visibility = View.GONE
            b.tvBillingCycle.visibility = View.GONE
            b.labelBillingCycle.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        b.tvManageSubscriptionOnGooglePlay.apply {
            underline()
            setOnClickListener { managePlayStoreSubs() }
        }
        b.btnRevoke.setOnClickListener { showDialogConfirmCancelOrRevoke(isCancel = false) }
        b.btnCancel.setOnClickListener { showDialogConfirmCancelOrRevoke(isCancel = true) }
        b.btnRenew.setOnClickListener  { navigateToRethinkPlus() }
        b.btnResubscribe.setOnClickListener { showResubscribeBottomSheet() }
        b.btnExplorePlans.setOnClickListener   { navigateToRethinkPlus() }
        b.btnRestorePurchase.setOnClickListener { restorePurchase() }
        b.btnConsumePurchase.setOnClickListener { io { RpnProxyManager.consumePurchaseIfTest() } }
        b.contactSupportCard.setOnClickListener { openSupportEmail() }
    }

    /**
     * Opens the device's email client pre-filled with the support address and a
     * generic subscription-support subject / body.  Falls back to a mailto URI
     * when no email app is installed.
     */
    private fun openSupportEmail() {
        try {
            val subject = getString(R.string.contact_support_email_subject)
            val body    = getString(R.string.contact_support_email_body)
            val intent  = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL,   arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT,    body)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW,
                    "mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}".toUri()))
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG: failed to open support email: ${e.message}", e)
            showToastUiCentered(requireContext(),
                getString(R.string.subscription_action_failed), Toast.LENGTH_SHORT)
        }
    }

    private fun showCancelOrRevokeButton() {
        try {
            io {
                val isTestEntitlement = RpnProxyManager.getIsTestEntitlement()
                uiCtx {
                    if (isTestEntitlement) {
                        b.btnConsumePurchase.visibility = View.VISIBLE
                    } else {
                        b.btnConsumePurchase.visibility = View.GONE
                    }
                }
            }
            val state = RpnProxyManager.getSubscriptionState()
            val subscriptionData = RpnProxyManager.getSubscriptionData()

            // Reset all buttons first
            b.btnCancel.visibility       = View.GONE
            b.btnRevoke.visibility       = View.GONE
            b.btnRenew.visibility        = View.GONE
            b.btnResubscribe.visibility  = View.GONE
            b.cancelNoteCard.visibility  = View.GONE

            val planId = subscriptionData?.purchaseDetail?.planId.orEmpty()
            val isInApp = isInAppProduct(subscriptionData?.purchaseDetail?.productId.orEmpty(), planId)

            if (!state.state().isActive) {
                when {
                    // Canceled SUBS: subscription still valid but auto-renewal is off.
                    // Show a targeted "Resubscribe" button to re-enable the same plan without
                    // presenting all purchase options.
                    state.state().isCancelled && !isInApp -> {
                        b.btnResubscribe.visibility = View.VISIBLE
                    }
                    // Expired or no valid subscription: let the user pick any plan.
                    state.state().isExpired || !state.hasValidSubscription -> {
                        b.btnRenew.visibility = View.VISIBLE
                    }
                }
                return
            }

            // show manage from play store only for subs
            if (isInApp) {
                b.tvManageSubscriptionOnGooglePlay.visibility = View.GONE
            } else {
                b.tvManageSubscriptionOnGooglePlay.visibility = View.VISIBLE
            }

            val canRevoke = canRevoke(subscriptionData)
            if (canRevoke) {
                // within revocation window, show revoke button
                showRevokeButton()
            } else {
                // show cancel button only for subs, outside revocation window
                if (isInApp) {
                    return
                }
                // subscription
                showCancelButton()
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error showing cancel/revoke button: ${e.message}", e)
            // Default to hiding all buttons on error
            b.btnCancel.visibility      = View.GONE
            b.btnRevoke.visibility      = View.GONE
            b.btnRenew.visibility       = View.GONE
            b.cancelNoteCard.visibility = View.GONE
        }
    }

    private fun showRevokeButton() {
        b.btnRevoke.visibility      = View.VISIBLE
        b.cancelNoteCard.visibility = View.VISIBLE
        b.tvCancelNote.text         = getString(R.string.revoke_subscription_note)
    }

    private fun showCancelButton() {
        b.btnCancel.visibility      = View.VISIBLE
        b.cancelNoteCard.visibility = View.VISIBLE
        b.tvCancelNote.text         = getString(R.string.cancel_subscription_note_future)
    }

    private fun canRevoke(subscriptionData: SubscriptionStateMachineV2.SubscriptionData?): Boolean {
        val purchaseTs = subscriptionData?.subscriptionStatus?.purchaseTime
        if (purchaseTs == null || purchaseTs <= 0) {
            Logger.w(LOG_TAG_UI, "$TAG purchase time is invalid, cannot determine revocation eligibility")
            return false
        }
        val planId = subscriptionData.purchaseDetail?.planId.orEmpty()
        val revokeWindowMs = when (planId) {
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS -> REVOKE_WINDOW_ONE_TIME_2YRS_DAYS * ONE_DAY_MS
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS -> REVOKE_WINDOW_ONE_TIME_5YRS_DAYS * ONE_DAY_MS
            InAppBillingHandler.SUBS_PRODUCT_YEARLY -> REVOKE_WINDOW_SUBS_YEARLY_DAYS * ONE_DAY_MS
            else -> REVOKE_WINDOW_SUBS_MONTHLY_DAYS * ONE_DAY_MS
        }
        return (System.currentTimeMillis() - purchaseTs) < revokeWindowMs
    }

    private fun showDialogConfirmCancelOrRevoke(isCancel: Boolean) {
        try {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (isCancel) getString(R.string.confirm_cancel_title) else getString(R.string.confirm_revoke_title))
                .setMessage(if (isCancel) getString(R.string.confirm_cancel_message) else getString(R.string.confirm_revoke_message))
                .setPositiveButton(if (isCancel) getString(R.string.cancel_subscription) else getString(R.string.revoke_subscription)) { _, _ ->
                    if (isCancel) viewModel.cancelSubscription() else viewModel.revokeSubscription()
                }
                .setNegativeButton(getString(R.string.lbl_cancel), null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error showing confirmation dialog: ${e.message}", e)
        }
    }

    private fun managePlayStoreSubs() {
        try {
            val productId = RpnProxyManager.getRpnProductId()
            if (productId.isEmpty()) {
                showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
                return
            }
            val link = InAppBillingHandler.PLAY_SUBS_LINK
                .replace("$1", productId)
                .replace("$2", requireContext().packageName)
            openUrl(requireContext(), link)
            InAppBillingHandler.fetchPurchases(
                    listOf(InAppBillingHandler.PRODUCT_TYPE_SUBS, InAppBillingHandler.PRODUCT_TYPE_INAPP)
                )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error managing play store subs: ${e.message}", e)
            showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
        }
    }

    private fun navigateToRethinkPlus() {
        try {
            val intent = FragmentHostActivity.createIntent(
                context = requireContext(),
                fragmentClass = RethinkPlusFragment::class.java,
                args = Bundle().apply { putString("ARG_KEY", "Launch_Rethink_Plus") }
            )
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error navigating to Rethink Plus: ${e.message}", e)
            showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
        }
    }

    /**
     * Navigates to [RethinkPlusFragment] in **extend mode**: ONE_TIME tab is pre-selected and the
     * "already subscribed" guard is bypassed so the user can purchase an additional one-time plan
     * while their current one-time access is still active but expiring soon.
     */
    private fun navigateToOneTimePurchase() {
        try {
            val intent = FragmentHostActivity.createIntent(
                context = requireContext(),
                fragmentClass = RethinkPlusFragment::class.java,
                args = Bundle().apply {
                    putString("ARG_KEY", "Launch_Rethink_Plus_Extend")
                    putBoolean("arg_extend_mode", true)
                }
            )
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error navigating to one-time purchase: ${e.message}", e)
            showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
        }
    }

    /**
     * Shows a targeted [ResubscribeBottomSheet] for the user's current plan.
     *
     * Called when the subscription is in the **Canceled** state (auto-renewal disabled but
     * still active). The sheet re-uses the existing [productId]/[planId] so the user is not
     * presented with all purchase options, they simply re-enable the same plan.
     */
    private fun showResubscribeBottomSheet() {
        if (!isAdded || isStateSaved) return
        if (childFragmentManager.findFragmentByTag("resubscribe") != null) return
        try {
            val subscriptionData = RpnProxyManager.getSubscriptionData()
            val purchaseDetail = subscriptionData?.purchaseDetail
            if (purchaseDetail == null) {
                showToastUiCentered(requireContext(), getString(R.string.purchase_detail_unavailable), Toast.LENGTH_SHORT)
                return
            }
            val sheet = ResubscribeBottomSheet.newInstance(
                productId = purchaseDetail.productId,
                planId = purchaseDetail.planId,
                planDisplayName = resolvePlanName(subscriptionData)
            )
            sheet.show(childFragmentManager, "resubscribe")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_UI, "$TAG error showing resubscribe bottom sheet: ${e.message}", e)
            showToastUiCentered(requireContext(), getString(R.string.error_loading_manage_subscription), Toast.LENGTH_SHORT)
        }
    }

    private fun restorePurchase() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    b.loadingOverlay.isVisible = true
                    b.tvLoadingMessage.text    = getString(R.string.manage_sub_restoring)
                    b.tvLoadingSubMessage.text = getString(R.string.manage_sub_please_wait)

                    b.stepRowValidating.isVisible = false
                    b.stepRowServer.isVisible     = false
                    b.stepRowLocal.isVisible      = false
                    b.stepRowRefresh.isVisible    = false
                }

                InAppBillingHandler.fetchPurchases(
                    listOf(InAppBillingHandler.PRODUCT_TYPE_SUBS, InAppBillingHandler.PRODUCT_TYPE_INAPP)
                )
                kotlinx.coroutines.delay(2_500L)

                val hasValid = RpnProxyManager.getSubscriptionState().hasValidSubscription
                withContext(Dispatchers.Main) {
                    b.loadingOverlay.isVisible = false

                    b.stepRowValidating.isVisible = true
                    b.stepRowServer.isVisible     = true
                    b.stepRowLocal.isVisible      = true
                    b.stepRowRefresh.isVisible    = true
                    showToastUiCentered(
                        requireContext(),
                        if (hasValid) getString(R.string.manage_sub_restore_done)
                        else getString(R.string.manage_sub_restore_none),
                        if (hasValid) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    )
                    if (hasValid) initView()
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "$TAG error restoring purchase: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    b.loadingOverlay.isVisible    = false
                    b.stepRowValidating.isVisible = true
                    b.stepRowServer.isVisible     = true
                    b.stepRowLocal.isVisible      = true
                    b.stepRowRefresh.isVisible    = true
                    showToastUiCentered(requireContext(), getString(R.string.subscription_action_failed), Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private fun setupServerErrorObserver() {
        InAppBillingHandler.serverApiErrorLiveData.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            InAppBillingHandler.serverApiErrorLiveData.value = null
            when (error) {
                is ServerApiError.Conflict409 -> showConflictBottomSheet(error)
                is ServerApiError.Unauthorized401 -> showDeviceAuthErrorBottomSheet(error)
                is ServerApiError.DeviceNotRegistered -> showDeviceNotRegisteredBottomSheet(error)
                is ServerApiError.GenericError -> showToastUiCentered(requireContext(), error.message, Toast.LENGTH_LONG)
                is ServerApiError.NetworkError -> showToastUiCentered(
                    requireContext(),
                    error.message ?: getString(R.string.subscription_action_failed),
                    Toast.LENGTH_LONG)
                is ServerApiError.None -> { /* no-op */ }
            }
        }
    }

    private fun showDeviceNotRegisteredBottomSheet(error: ServerApiError.DeviceNotRegistered) {
        if (!isAdded || isStateSaved) return
        DeviceNotRegisteredNotifier.cancel(requireContext())
        val sheet = DeviceNotRegisteredBottomSheet.newInstance(error)
        sheet.show(childFragmentManager, "deviceNotRegistered")
    }

    private fun showDeviceAuthErrorBottomSheet(error: ServerApiError.Unauthorized401) {
        if (!isAdded || isStateSaved) return
        val sheet = DeviceAuthErrorBottomSheet.newInstance(error)
        sheet.show(childFragmentManager, "deviceAuthError401")
    }

    private fun showConflictBottomSheet(error: ServerApiError.Conflict409) {
        if (!isAdded || isStateSaved) return
        PurchaseConflictNotifier.cancel(requireContext())
        val sheet = PurchaseConflictBottomSheet.newInstance(error)
        sheet.onRefundResult = { success, _ ->
            if (success) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) { initView() }
            }
        }
        sheet.show(childFragmentManager, "conflict409")
    }

    private fun isInAppProduct(productId: String, planId: String): Boolean {
        val inAppIds = setOf(
            InAppBillingHandler.ONE_TIME_PRODUCT_ID,
            InAppBillingHandler.ONE_TIME_PRODUCT_2YRS,
            InAppBillingHandler.ONE_TIME_PRODUCT_5YRS,
            InAppBillingHandler.ONE_TIME_TEST_PRODUCT_ID
        )
        return productId in inAppIds || planId in inAppIds
    }

    private fun AppCompatTextView.underline() {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }
    private suspend fun uiCtx(f: suspend () -> Unit) = withContext(Dispatchers.Main) { f() }
    private fun io(f: suspend () -> Unit) = lifecycleScope.launch(Dispatchers.IO) { f() }
}

/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.BottomsheetResubscribeBinding
import com.celzero.bravedns.iab.InAppBillingHandler
import com.celzero.bravedns.util.Utilities.showToastUiCentered
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet shown to users whose subscription is in the **Canceled** state
 * (auto-renewal disabled, but still active until the billing period ends).
 *
 * Tapping "Resubscribe" launches the standard Google Play billing flow for the
 * same plan - Google Play will re-enable auto-renewal without an immediate charge;
 * the subscription continues from the current billing period.
 *
 */
class ResubscribeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetResubscribeBinding? = null
    private val binding
        get() = requireNotNull(_binding)
        { "Binding accessed outside of view lifecycle" }

    companion object {
        private const val TAG = "ResubscribeBS"
        private const val ARG_PRODUCT_ID       = "product_id"
        private const val ARG_PLAN_ID          = "plan_id"
        private const val ARG_PLAN_DISPLAY_NAME = "plan_display_name"

        fun newInstance(
            productId: String,
            planId: String,
            planDisplayName: String
        ): ResubscribeBottomSheet {
            return ResubscribeBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRODUCT_ID, productId)
                    putString(ARG_PLAN_ID, planId)
                    putString(ARG_PLAN_DISPLAY_NAME, planDisplayName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetResubscribeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args            = requireArguments()
        val productId       = args.getString(ARG_PRODUCT_ID, "")
        val planId          = args.getString(ARG_PLAN_ID, "")
        val planDisplayName = args.getString(ARG_PLAN_DISPLAY_NAME, "")

        binding.tvPlanName.text = planDisplayName.ifEmpty { productId }

        binding.btnResubscribe.setOnClickListener {
            launchResubscribe(productId, planId)
        }

        binding.btnNotNow.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun launchResubscribe(productId: String, planId: String) {
        if (productId.isBlank() || planId.isBlank()) {
            Logger.w(LOG_TAG_UI, "$TAG: cannot resubscribe, missing productId or planId")
            showToastUiCentered(
                requireContext(),
                getString(R.string.resubscribe_error),
                Toast.LENGTH_SHORT
            )
            return
        }

        setInFlight(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Logger.i(LOG_TAG_UI, "$TAG: launching resubscription for productId=$productId, planId=$planId")
                // purchaseSubs() uses the existing purchase token if auto-renewal was off.
                // Google Play shows a targeted "resubscribe" billing sheet for a canceled
                // (isAutoRenewing=false) subscription: no SubscriptionUpdateParams needed.
                // forceResubscribe=true bypasses the canMakePurchase() guard because the
                // subscription may still be Active (canceled but not yet expired), which is
                // the exact case this sheet is designed to handle.
                InAppBillingHandler.purchaseSubs(
                    activity = requireActivity(),
                    productId = productId,
                    planId = planId,
                    forceResubscribe = true
                )
                withContext(Dispatchers.Main) {
                    // Billing UI is now overlaid on the activity; dismiss the sheet.
                    dismissAllowingStateLoss()
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_UI, "$TAG: failed to launch resubscription: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setInFlight(false)
                    showToastUiCentered(
                        requireContext(),
                        getString(R.string.resubscribe_error),
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }
    }

    private fun setInFlight(inFlight: Boolean) {
        binding.progressResubscribe.isVisible = inFlight
        binding.btnResubscribe.isEnabled = !inFlight
        binding.btnNotNow.isEnabled = !inFlight
        isCancelable = !inFlight
    }

    override fun dismiss() {
        if (isAdded && !isStateSaved) super.dismiss()
    }

    override fun dismissAllowingStateLoss() {
        if (isAdded) super.dismissAllowingStateLoss()
    }
}


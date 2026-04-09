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
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.BottomsheetServerSettingsBinding
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Themes.Companion.getBottomsheetCurrentTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.android.ext.android.inject

/**
 * Premium bottom sheet combining DNS filter settings (previously in dialog_dns_mode) and
 * the new Configuration Handling section.
 *
 * Layout sections
 * ───────────────
 * 1. DNS Filter – mirrors the former showDnsDialog() content exactly.
 * 2. Configuration Handling – master AUTO/MANUAL toggle, plus three child settings
 *    (always change identity, connection port, permanent configuration) that are
 *    enabled only when the user selects MANUAL mode.
 *
 * All state is persisted to [PersistentState]; no data is held in-memory across
 * fragment recreations.
 */
class ServerSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetServerSettingsBinding? = null
    private val binding get() = _binding!!

    private val persistentState by inject<PersistentState>()

    /** True while the proxy is stopped; some controls are additionally locked. */
    private var isProxyStopped: Boolean = false

    companion object {
        private const val TAG = "ServerSettingsBS"
        private const val ARG_PROXY_STOPPED = "proxy_stopped"

        /**
         * Available port choices shown in the port-selection dialog.
         * Index 0 → AUTO (stored as 0); other indices map 1-to-1 with [PORT_VALUES].
         * 443, 80, 53, 123, 1194, 65142 are the most common ports which was seen from win-api
         */
        private val PORT_LABELS = arrayOf("AUTO", "80", "443", "53", "123", "1194", "65142")
        private val PORT_VALUES = intArrayOf(0, 80, 443, 53, 123, 1194, 65142)

        fun newInstance(isProxyStopped: Boolean): ServerSettingsBottomSheet {
            return ServerSettingsBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_PROXY_STOPPED, isProxyStopped)
                }
            }
        }
    }

    /**
     * Callback interface for settings changes that require tunnel IO.
     *
     * Callbacks are delivered on the **main thread**. The receiving fragment is
     * responsible for dispatching tunnel work onto a background dispatcher so the
     * operation survives bottom-sheet dismissal.
     */
    interface OnSettingsChangedListener {
        /** Fired immediately each time the user picks a new DNS filter mode. */
        fun onDnsModeChanged(mode: RpnProxyManager.DnsMode)
        /**
         * Fired once when the sheet is dismissed (Done tap or swipe-away), but
         * **only** if at least one of the four configuration values changed since
         * the sheet was opened. The caller reads the final values from
         * [PersistentState] directly.
         */
        fun onConfigChanged()
    }

    private var listener: OnSettingsChangedListener? = null

    /** Attach a [OnSettingsChangedListener]; call before [show]. */
    fun setOnSettingsChangedListener(l: OnSettingsChangedListener) {
        listener = l
    }

    // Used by hasConfigChanged() to decide whether to fire onConfigChanged().
    private var snapshotConfigManual: Boolean = false
    private var snapshotAlwaysChangeIdentity: Boolean = false
    private var snapshotPort: Int = 0
    private var snapshotPermanentConfig: Boolean = false

    /** Returns true if any of the four config values differ from their opening snapshot. */
    private fun hasConfigChanged(): Boolean =
        persistentState.rpnConfigHandlingManual  != snapshotConfigManual         ||
        persistentState.rpnAlwaysChangeIdentity  != snapshotAlwaysChangeIdentity ||
        persistentState.rpnPort                  != snapshotPort                 ||
        persistentState.rpnUsePermanentConfig    != snapshotPermanentConfig

    private fun isDarkThemeOn(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

    override fun getTheme(): Int =
        getBottomsheetCurrentTheme(isDarkThemeOn(), persistentState.theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        isCancelable = true
        isProxyStopped = arguments?.getBoolean(ARG_PROXY_STOPPED, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetServerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep nav bar transparent / dark on Q+
        dialog?.window?.let { window ->
            if (isAtleastQ()) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightNavigationBars = false
                window.isNavigationBarContrastEnforced = false
            }
        }

        // Snapshot config values before any UI interaction so hasConfigChanged() is accurate.
        snapshotConfigManual          = persistentState.rpnConfigHandlingManual
        snapshotAlwaysChangeIdentity  = persistentState.rpnAlwaysChangeIdentity
        snapshotPort                  = persistentState.rpnPort
        snapshotPermanentConfig       = persistentState.rpnUsePermanentConfig

        setupDnsSection()
        setupConfigHandlingSection()

        binding.btnDone.setOnClickListener { dismiss() }

        Logger.i(LOG_TAG_UI, "$TAG: view created, proxyStopped=$isProxyStopped")
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        // Fire onConfigChanged once if any config value was mutated during this session.
        // onDismiss fires before onDestroyView, so listener is still non-null here.
        if (hasConfigChanged()) {
            Logger.i(LOG_TAG_UI, "$TAG: config changed on dismiss, notifying listener")
            listener?.onConfigChanged()
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        listener = null   // prevent fragment leak via callback reference
        _binding = null
        super.onDestroyView()
    }

    /**
     * Sets up the DNS filter section — mirrors the former inline DNS dialog that lived
     * in ServerSelectionFragment, now unified into this settings sheet.
     */
    private fun setupDnsSection() {
        val currentMode = RpnProxyManager.DnsMode.fromUrl(persistentState.rpnDnsUrl)
        val radioId = when (currentMode) {
            RpnProxyManager.DnsMode.DEFAULT  -> R.id.rb_dns_default
            RpnProxyManager.DnsMode.ANTI_AD  -> R.id.rb_dns_antiad
            RpnProxyManager.DnsMode.PARENTAL -> R.id.rb_dns_parental
            RpnProxyManager.DnsMode.SECURITY -> R.id.rb_dns_security
        }
        binding.dnsRadioGroup.check(radioId)

        val splitEnabled = persistentState.splitDns
        binding.splitDnsBanner.visibility = if (splitEnabled) View.GONE else View.VISIBLE
        setDnsRadioGroupEnabled(splitEnabled && !isProxyStopped)

        binding.splitDnsEnableBtn.setOnClickListener {
            persistentState.splitDns = true
            binding.splitDnsBanner.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (isAdded) {
                        binding.splitDnsBanner.visibility = View.GONE
                        binding.splitDnsBanner.alpha = 1f
                        setDnsRadioGroupEnabled(!isProxyStopped)
                    }
                }
                .start()
        }

        binding.dnsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!persistentState.splitDns || isProxyStopped) return@setOnCheckedChangeListener
            val newMode = when (checkedId) {
                R.id.rb_dns_default  -> RpnProxyManager.DnsMode.DEFAULT
                R.id.rb_dns_antiad   -> RpnProxyManager.DnsMode.ANTI_AD
                R.id.rb_dns_parental -> RpnProxyManager.DnsMode.PARENTAL
                R.id.rb_dns_security -> RpnProxyManager.DnsMode.SECURITY
                else                 -> return@setOnCheckedChangeListener
            }
            if (newMode == currentMode) return@setOnCheckedChangeListener
            persistentState.rpnDnsUrl = newMode.url
            listener?.onDnsModeChanged(newMode)
            Logger.i(LOG_TAG_UI, "$TAG: DNS mode → $newMode")
        }
    }

    private fun setDnsRadioGroupEnabled(enabled: Boolean) {
        binding.dnsRadioGroup.alpha = if (enabled) 1f else 0.38f
        for (i in 0 until binding.dnsRadioGroup.childCount) {
            binding.dnsRadioGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun setupConfigHandlingSection() {
        val isManual = persistentState.rpnConfigHandlingManual

        // Initialise toggle without firing the listener
        val initialChecked = if (isManual) R.id.btn_config_manual else R.id.btn_config_auto
        binding.configModeToggle.check(initialChecked)
        applyManualModeUi(isManual, animate = false)

        // Initialise child toggle states from persisted values
        binding.identitySwitch.isChecked = persistentState.rpnAlwaysChangeIdentity
        updatePortValueLabel(persistentState.rpnPort)
        binding.permanentConfigSwitch.isChecked = persistentState.rpnUsePermanentConfig

        // Set initial toggle text colors
        updateToggleTextColors(isManual)

        // AUTO / MANUAL toggle
        binding.configModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val manual = checkedId == R.id.btn_config_manual
            persistentState.rpnConfigHandlingManual = manual
            applyManualModeUi(manual, animate = true)
            updateToggleTextColors(manual)
            Logger.i(LOG_TAG_UI, "$TAG: config mode → ${if (manual) "MANUAL" else "AUTO"}")
        }

        // Always Change Identity
        binding.identitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!persistentState.rpnConfigHandlingManual) return@setOnCheckedChangeListener
            persistentState.rpnAlwaysChangeIdentity = isChecked
            Logger.i(LOG_TAG_UI, "$TAG: alwaysChangeIdentity → $isChecked")
        }

        // Port row (click to open selection dialog)
        binding.portRow.setOnClickListener {
            if (!persistentState.rpnConfigHandlingManual) return@setOnClickListener
            showPortSelectionDialog()
        }

        // Permanent Configuration
        binding.permanentConfigSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!persistentState.rpnConfigHandlingManual) return@setOnCheckedChangeListener
            persistentState.rpnUsePermanentConfig = isChecked
            Logger.i(LOG_TAG_UI, "$TAG: permanentConfig → $isChecked")
        }
    }

    /**
     * Applies text colors to the AUTO/MANUAL toggle buttons.
     * The selected button uses [R.attr.secondaryTextColor]; the unselected one
     * uses [R.attr.primaryTextColor].
     */
    private fun updateToggleTextColors(isManual: Boolean) {
        val selectedColor = UIUtils.fetchColor(requireContext(), R.attr.secondaryTextColor)
        val unselectedColor = UIUtils.fetchColor(requireContext(), R.attr.primaryTextColor)
        binding.btnConfigManual.setTextColor(if (isManual) selectedColor else unselectedColor)
        binding.btnConfigAuto.setTextColor(if (isManual) unselectedColor else selectedColor)
    }

    /**
     * Enables or disables the three manual-only settings rows.
     *
     * Visual treatment for disabled rows:
     * - alpha 0.35 (dimmed but readable)
     * - switches and click listeners disabled
     * - port row not clickable
     *
     * The [animate] flag controls whether the transition is instant or eased.
     */
    private fun applyManualModeUi(isManual: Boolean, animate: Boolean) {
        // Update hint text
        binding.tvConfigModeHint.text = getString(
            if (isManual) R.string.server_settings_config_manual_hint
            else R.string.server_settings_config_auto_hint
        )

        val targetAlpha = if (isManual) 1f else 0.35f
        val duration = if (animate) 220L else 0L

        // Identity row
        if (animate) {
            binding.identityRow.animate()
                .alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            binding.identityRow.alpha = targetAlpha
        }
        binding.identitySwitch.isEnabled = isManual

        // Port row
        if (animate) {
            binding.portRow.animate()
                .alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            binding.portRow.alpha = targetAlpha
        }
        binding.portRow.isClickable = isManual
        binding.portRow.isFocusable = isManual

        // Permanent config row
        if (animate) {
            binding.permanentConfigRow.animate()
                .alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            binding.permanentConfigRow.alpha = targetAlpha
        }
        binding.permanentConfigSwitch.isEnabled = isManual
    }

    /**
     * Shows a [MaterialAlertDialogBuilder] single-choice dialog for selecting
     * the connection port.  The current selection is pre-checked.
     */
    private fun showPortSelectionDialog() {
        if (!isAdded) return

        val currentPort = persistentState.rpnPort
        val selectedIndex = PORT_VALUES.indexOfFirst { it == currentPort }.let {
            if (it < 0) 0 else it  // fall back to AUTO if stored value is unknown
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.server_settings_port_dialog_title))
            .setSingleChoiceItems(PORT_LABELS, selectedIndex) { dialog, which ->
                val newPort = PORT_VALUES[which]
                persistentState.rpnPort = newPort
                updatePortValueLabel(newPort)
                Logger.i(LOG_TAG_UI, "$TAG: port selected → $newPort")
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.lbl_cancel), null)
            .show()
    }

    /** Updates the port display label in the port row. */
    private fun updatePortValueLabel(port: Int) {
        binding.tvPortValue.text = if (port == 0) {
            getString(R.string.server_settings_port_auto)
        } else {
            port.toString()
        }
    }
}




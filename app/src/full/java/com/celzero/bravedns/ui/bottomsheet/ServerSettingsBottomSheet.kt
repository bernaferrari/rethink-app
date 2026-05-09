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
 * bottom sheet combining DNS filter settings and new Configuration Handling section.
 */
class ServerSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetServerSettingsBinding? = null
    private val binding
        get() = checkNotNull(_binding)
        { "Binding accessed outside of view lifecycle" }

    private val persistentState by inject<PersistentState>()

    /** True while the proxy is stopped; some controls are additionally locked. */
    private var isProxyStopped: Boolean = false

    companion object {
        private const val TAG = "ServerSettingsBS"
        private const val ARG_PROXY_STOPPED = "proxy_stopped"

        /**
         * Available port values shown in the port-selection dialog.
         * Index 0 → random (stored as 0); other indices are literal port numbers.
         * 443, 80, 53, 123, 1194, 65142 are the most common ports which was seen from win-api
         */
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
     * The receiving fragment is responsible for dispatching tunnel work onto a
     * background dispatcher so the operation survives bottom-sheet dismissal.
     */
    interface OnSettingsChangedListener {
        /**
         * Fired immediately each time the user changes the DNS filter selection.
         * [tunTypes] is a comma-separated string of [RpnProxyManager.DnsMode.tunType] values
         * representing all currently active filters (e.g. `"privacy,family"`).
         */
        fun onDnsModeChanged(tunTypes: String)
        /**
         * Fired once when the sheet is dismissed (Done tap or swipe-away), but
         * **only** if at least one of the four configuration values changed since
         * the sheet was opened. The caller reads the final values from
         * [PersistentState] directly.
         */
        fun onConfigChanged()
        fun onReset()
    }

    private var listener: OnSettingsChangedListener? = null

    /** Attach a [OnSettingsChangedListener]; call before [show]. */
    fun setOnSettingsChangedListener(l: OnSettingsChangedListener) {
        listener = l
    }

    /**
     * Tracks the last comma-separated tunType string emitted to the listener so we can
     * guard against spurious re-emissions when checkboxes are programmatically initialised.
     */
    private var lastEmittedTunTypes: String = RpnProxyManager.DnsMode.DEFAULT.tunType

    // Used by hasConfigChanged() to decide whether to fire onConfigChanged().
    private var snapshotConfigManual: Boolean = false
    private var snapshotAlwaysChangeIdentity: Boolean = false
    private var snapshotPort: Int = 0
    private var snapshotPermanentConfig: Boolean = false

    /** Returns true if any of the four config values differ from their opening snapshot. */
    private fun hasConfigChanged(): Boolean =
        persistentState.rpnConfigHandlingManual != snapshotConfigManual ||
        persistentState.rpnAlwaysChangeIdentity != snapshotAlwaysChangeIdentity ||
        persistentState.rpnPort != snapshotPort ||
        persistentState.rpnUsePermanentConfig != snapshotPermanentConfig

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
        snapshotConfigManual = persistentState.rpnConfigHandlingManual
        snapshotAlwaysChangeIdentity  = persistentState.rpnAlwaysChangeIdentity
        snapshotPort = persistentState.rpnPort
        snapshotPermanentConfig = persistentState.rpnUsePermanentConfig

        setupDnsSection()
        setupConfigHandlingSection()

        binding.btnDone.setOnClickListener { dismiss() }
        binding.btnResetRpn.setOnClickListener { showResetConfirmationDialog() }

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
        super.onDestroyView()
        listener = null // prevent fragment leak via callback reference
        _binding = null
    }

    /**
     * Sets up the DNS filter section using four independent [AppCompatCheckBox] rows.
     * Any combination of Default / Privacy / Family / Security may be selected.
     *
     * State is persisted in two [PersistentState] fields:
     * - [PersistentState.rpnDnsTunTypes]: CSV of active [RpnProxyManager.DnsMode.tunType] values
     *
     * [OnSettingsChangedListener.onDnsModeChanged] is fired on every real change with the
     * updated CSV string so the caller can propagate the change to the tunnel.
     */
    private fun setupDnsSection() {
        // restore initial selection
        val activeModes = getActiveModesFromState()
        lastEmittedTunTypes = RpnProxyManager.DnsMode.tunTypesFromSet(activeModes)

        // Initialise checkboxes without triggering listeners (listeners are registered below).
        setCheckboxesQuietly(activeModes)

        val splitEnabled = persistentState.splitDns
        binding.splitDnsBanner.visibility = if (splitEnabled) View.GONE else View.VISIBLE
        setDnsCheckboxesEnabled(splitEnabled && !isProxyStopped)

        binding.splitDnsEnableBtn.setOnClickListener {
            persistentState.splitDns = true
            binding.splitDnsBanner.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (isAdded) {
                        binding.splitDnsBanner.visibility = View.GONE
                        binding.splitDnsBanner.alpha = 1f
                        setDnsCheckboxesEnabled(!isProxyStopped)
                    }
                }
                .start()
        }

        val rows = listOf(
            binding.dnsRowDefault  to binding.cbDnsDefault,
            binding.dnsRowPrivacy  to binding.cbDnsPrivacy,
            binding.dnsRowFamily   to binding.cbDnsFamily,
            binding.dnsRowSecurity to binding.cbDnsSecurity,
        )

        rows.forEach { (row, checkbox) ->
            row.setOnClickListener {
                if (!persistentState.splitDns || isProxyStopped) return@setOnClickListener
                checkbox.isChecked = !checkbox.isChecked
            }
        }

        val changeListener = { _: android.widget.CompoundButton, _: Boolean ->
            if (persistentState.splitDns && !isProxyStopped) {
                onDnsCheckboxChanged()
            }
        }
        binding.cbDnsDefault .setOnCheckedChangeListener(changeListener)
        binding.cbDnsPrivacy .setOnCheckedChangeListener(changeListener)
        binding.cbDnsFamily  .setOnCheckedChangeListener(changeListener)
        binding.cbDnsSecurity.setOnCheckedChangeListener(changeListener)
    }

    /**
     * Called whenever any DNS checkbox state changes.
     * Derives the new active set, enforces the "at least one selected" invariant,
     * persists both [PersistentState.rpnDnsTunTypes]
     * and fires [OnSettingsChangedListener.onDnsModeChanged] only on a real change.
     */
    private fun onDnsCheckboxChanged() {
        val selected = buildSelectedModes()

        // Enforce at least one selection — silently re-check Default if everything is cleared.
        val effective: Set<RpnProxyManager.DnsMode> = selected.ifEmpty {
            // Re-check Default without triggering the listener again.
            binding.cbDnsDefault.setOnCheckedChangeListener(null)
            binding.cbDnsDefault.isChecked = true
            binding.cbDnsDefault.setOnCheckedChangeListener { _, _ ->
                if (persistentState.splitDns && !isProxyStopped) onDnsCheckboxChanged()
            }
            setOf(RpnProxyManager.DnsMode.DEFAULT)
        }

        val newTunTypes = RpnProxyManager.DnsMode.tunTypesFromSet(effective)

        // Guard against spurious re-emissions during programmatic initialisation.
        if (newTunTypes == lastEmittedTunTypes) return
        lastEmittedTunTypes = newTunTypes

        // Persist: CSV tunTypes for the filter; primary URL for GoVpnAdapter DNS routing.
        persistentState.rpnDnsTunTypes = newTunTypes

        listener?.onDnsModeChanged(newTunTypes)
        Logger.i(LOG_TAG_UI, "$TAG: DNS filter → $newTunTypes")
    }

    /**
     * Reads the current checkbox states and returns the corresponding [RpnProxyManager.DnsMode] set.
     */
    private fun buildSelectedModes(): Set<RpnProxyManager.DnsMode> {
        val result = mutableSetOf<RpnProxyManager.DnsMode>()
        if (binding.cbDnsDefault .isChecked) result += RpnProxyManager.DnsMode.DEFAULT
        if (binding.cbDnsPrivacy .isChecked) result += RpnProxyManager.DnsMode.ANTI_AD
        if (binding.cbDnsFamily  .isChecked) result += RpnProxyManager.DnsMode.PARENTAL
        if (binding.cbDnsSecurity.isChecked) result += RpnProxyManager.DnsMode.SECURITY
        return result
    }

    /**
     * Sets all four checkboxes to match [modes] **without** triggering their change-listeners
     * (used during initialisation to avoid spurious emissions).
     */
    private fun setCheckboxesQuietly(modes: Set<RpnProxyManager.DnsMode>) {
        binding.cbDnsDefault .setOnCheckedChangeListener(null)
        binding.cbDnsPrivacy .setOnCheckedChangeListener(null)
        binding.cbDnsFamily  .setOnCheckedChangeListener(null)
        binding.cbDnsSecurity.setOnCheckedChangeListener(null)

        binding.cbDnsDefault .isChecked = RpnProxyManager.DnsMode.DEFAULT  in modes
        binding.cbDnsPrivacy .isChecked = RpnProxyManager.DnsMode.ANTI_AD  in modes
        binding.cbDnsFamily  .isChecked = RpnProxyManager.DnsMode.PARENTAL in modes
        binding.cbDnsSecurity.isChecked = RpnProxyManager.DnsMode.SECURITY in modes
        // Listeners are wired in setupDnsSection() after this call.
    }

    /**
     * Resolves the active [RpnProxyManager.DnsMode] set from [PersistentState].
     *
     */
    private fun getActiveModesFromState(): Set<RpnProxyManager.DnsMode> {
        return RpnProxyManager.DnsMode.setFromCsv(persistentState.rpnDnsTunTypes)
    }

    /**
     * Enables or disables all four DNS checkbox rows.
     * The container alpha provides a clear disabled affordance without hiding controls.
     */
    private fun setDnsCheckboxesEnabled(enabled: Boolean) {
        binding.dnsCheckboxContainer.alpha = if (enabled) 1f else 0.38f
        listOf(
            binding.dnsRowDefault, binding.dnsRowPrivacy,
            binding.dnsRowFamily,  binding.dnsRowSecurity,
        ).forEach {
            it.isClickable = enabled
            it.isFocusable  = enabled
        }
        listOf(
            binding.cbDnsDefault, binding.cbDnsPrivacy,
            binding.cbDnsFamily,  binding.cbDnsSecurity,
        ).forEach { it.isEnabled = enabled }
    }

    private fun setupConfigHandlingSection() {
        val isManual = persistentState.rpnConfigHandlingManual

        // Initialize toggle without firing the listener
        val initialChecked = if (isManual) R.id.btn_config_manual else R.id.btn_config_auto
        binding.configModeToggle.check(initialChecked)
        applyManualModeUi(isManual, animate = false)

        // Initialize child toggle states from persisted values
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

        // Port row (opens selection dialog)
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
     * the connection port. The current selection is pre-checked.
     */
    private fun showPortSelectionDialog() {
        if (!isAdded) return

        // lbl_random string resource as updatePortValueLabel()
        // replace 0 to "RANDOM" in the dialog list
        val randomLabel = getString(R.string.lbl_random).trim('(', ')').uppercase()
        val portLabels  = arrayOf(randomLabel, "80", "443", "53", "123", "1194", "65142")

        val currentPort = persistentState.rpnPort
        val selectedIndex = PORT_VALUES.indexOfFirst { it == currentPort }.let {
            if (it < 0) 0 else it  // fall back to random if stored value is unknown
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.server_settings_port_dialog_title))
            .setSingleChoiceItems(portLabels, selectedIndex) { dialog, which ->
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
            // Use lbl_random ("(random)"), strip the parentheses, and display in caps → "RANDOM"
            getString(R.string.lbl_random).trim('(', ')').uppercase()
        } else {
            port.toString()
        }
    }

    /**
     * Shows a confirmation dialog before executing the RPN reset.
     */
    private fun showResetConfirmationDialog() {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.rpn_restore_confirm_title))
            .setMessage(getString(R.string.rpn_restore_confirm_message))
            .setPositiveButton(getString(R.string.rpn_restore_confirm_action)) { dialog, _ ->
                dialog.dismiss()
                dismiss() // dismiss the bottom sheet first
                listener?.onReset() // then trigger reset in the parent fragment
            }
            .setNegativeButton(getString(R.string.lbl_cancel), null)
            .show()
    }
}

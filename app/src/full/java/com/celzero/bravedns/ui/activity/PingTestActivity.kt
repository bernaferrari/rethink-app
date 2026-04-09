/*
 * Copyright 2024 RethinkDNS and its authors
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
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.databinding.ActivityPingTestBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.util.SnackbarHelper
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.firestack.backend.Backend
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class PingTestActivity : AppCompatActivity(R.layout.activity_ping_test) {
    private val b by viewBinding(ActivityPingTestBinding::bind)

    private val persistentState by inject<PersistentState>()

    companion object {
        private const val TAG = "PingUi"
    }

    private var isTesting = false
    private var testStartTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }
        initView()
        setupClickListeners()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun initView() {
        if (!VpnController.hasTunnel()) {
            showStartVpnDialog()
            return
        }

        // Set initial state - ready to test
        showReadyState()
    }

    private fun showStartVpnDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.vpn_not_active_dialog_title))
        builder.setMessage(getString(R.string.vpn_not_active_dialog_desc))
        builder.setCancelable(false)
        builder.setPositiveButton(getString(R.string.dns_info_positive)) { dialogInterface, _ ->
            dialogInterface.dismiss()
            finish()
        }
        builder.create().show()
    }

    private fun setupClickListeners() {
        b.pingButton.setOnClickListener {
            if (!isTesting) {
                performPing()
            }
        }

        b.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun showReadyState() {
        b.statusIcon.setImageResource(R.drawable.ic_shield_check)
        b.statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
        b.statusTitle.text = getString(R.string.ping_ready_title)
        b.statusDescription.text = getString(R.string.ping_ready_desc)
        b.pingButton.text = getString(R.string.ping_test_button)
        b.pingButton.isEnabled = true

        b.progressIndicator.visibility = View.GONE
        b.latencyContainer.visibility = View.GONE
        b.serverInfoCard.visibility = View.GONE
    }

    private fun showTestingState() {
        isTesting = true
        testStartTime = System.currentTimeMillis()

        // Animate icon
        animateIconPulse()

        b.statusTitle.text = getString(R.string.ping_testing_title)
        b.statusDescription.text = getString(R.string.ping_testing_desc)
        b.pingButton.text = getString(R.string.ping_testing_title)
        b.pingButton.isEnabled = false

        b.progressIndicator.visibility = View.VISIBLE
        b.latencyContainer.visibility = View.GONE
        b.serverInfoCard.visibility = View.GONE
    }

    private fun showSuccessState(latencyMs: Long) {
        isTesting = false

        // Success animation
        animateSuccess()

        b.statusIcon.setImageResource(R.drawable.ic_tick)
        b.statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.accentGood))
        b.statusTitle.text = getString(R.string.ping_success_title)
        b.statusDescription.text = getString(R.string.ping_success_desc)
        b.pingButton.text = getString(R.string.ping_test_again)
        b.pingButton.isEnabled = true

        b.progressIndicator.visibility = View.GONE

        // Show latency
        b.latencyContainer.visibility = View.VISIBLE
        b.latencyText.text = getString(R.string.two_argument, "Response:", "${latencyMs}ms")

        // Show server info card with animation
        showServerInfoCard(true)
    }

    private fun showFailureState() {
        isTesting = false

        // Failure animation
        animateFailure()

        b.statusIcon.setImageResource(R.drawable.ic_cross_accent)
        b.statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.accentBad))
        b.statusTitle.text = getString(R.string.ping_failure_title)
        b.statusDescription.text = getString(R.string.ping_failure_desc)
        b.pingButton.text = getString(R.string.ping_test_again)
        b.pingButton.isEnabled = true

        b.progressIndicator.visibility = View.GONE
        b.latencyContainer.visibility = View.GONE

        // Show server info card with failure state
        showServerInfoCard(false)
    }

    private fun showServerInfoCard(success: Boolean) {
        b.serverInfoCard.visibility = View.VISIBLE
        b.serverInfoCard.alpha = 0f
        b.serverInfoCard.translationY = 30f

        b.serverInfoCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        b.serverNameText.text = getString(R.string.ping_server_name)
        b.testTimeText.text = getString(R.string.bubble_time_just_now)

        if (success) {
            b.connectionStatusText.text = getString(R.string.vpn_status_connected)
            b.connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.accentGood))
        } else {
            b.connectionStatusText.text = getString(R.string.ping_status_failed)
            b.connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.accentBad))
        }
    }

    private fun animateIconPulse() {
        val scaleX = ObjectAnimator.ofFloat(b.statusIcon, "scaleX", 1f, 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(b.statusIcon, "scaleY", 1f, 0.8f, 1f)
        val alpha = ObjectAnimator.ofFloat(b.statusIcon, "alpha", 1f, 0.6f, 1f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 1000
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    private fun animateSuccess() {
        // Pop animation for success
        val scaleX = ObjectAnimator.ofFloat(b.statusIcon, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(b.statusIcon, "scaleY", 0f, 1.2f, 1f)
        val rotation = ObjectAnimator.ofFloat(b.statusIcon, "rotation", -30f, 0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, rotation)
        animatorSet.duration = 500
        animatorSet.interpolator = OvershootInterpolator(2f)
        animatorSet.start()

        val bgAnimatorSet = AnimatorSet()
        bgAnimatorSet.duration = 400
        bgAnimatorSet.interpolator = OvershootInterpolator(1.5f)
        bgAnimatorSet.start()
    }

    private fun animateFailure() {
        // Shake animation for failure
        val shake = ObjectAnimator.ofFloat(b.statusIcon, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 500
        shake.interpolator = AccelerateDecelerateInterpolator()
        shake.start()

        // Scale animation
        val scaleX = ObjectAnimator.ofFloat(b.statusIcon, "scaleX", 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(b.statusIcon, "scaleY", 0f, 1f)

        val scaleSet = AnimatorSet()
        scaleSet.playTogether(scaleX, scaleY)
        scaleSet.duration = 300
        scaleSet.start()
    }

    private fun performPing() {
        try {
            Logger.v(Logger.LOG_IAB, "$TAG initiating WIN proxy test")
            showTestingState()

            io {
                val startTime = System.currentTimeMillis()
                val isWinReachable = VpnController.testRpnProxy(Backend.RpnWin)
                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime

                Logger.d(Logger.LOG_IAB, "$TAG WIN proxy reachable: $isWinReachable, latency: ${latency}ms")

                // Add slight delay for better UX (minimum 1 second of testing animation)
                val minTestDuration = 1500L
                val elapsed = System.currentTimeMillis() - testStartTime
                if (elapsed < minTestDuration) {
                    delay(minTestDuration - elapsed)
                }

                uiCtx {
                    if (isWinReachable) {
                        showSuccessState(latency)
                    } else {
                        showFailureState()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.LOG_IAB, "$TAG err during ping test: ${e.message}", e)
            showFailureState()
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }
}

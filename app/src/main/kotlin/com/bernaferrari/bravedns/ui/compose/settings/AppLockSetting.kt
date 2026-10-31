/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.common.findActivity
import com.bernaferrari.bravedns.util.BioMetricType

private const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Settings › App › App lock. Writes `biometricAuthType` (read by [AppLockScreen], the VPN
 * notification and the quick tile) with the [BioMetricType] the user picks.
 *
 * Turning the lock on from Off first asks for the same screen lock [AppLockScreen] will ask for;
 * only a successful check saves it, so a device without a usable lock (or a cancelled prompt) can't
 * lock the user out. Changing the timeout of a lock that's already on, or turning it off, needs no
 * check: whoever is here has already unlocked Keepout.
 */
@Composable
fun AppLockSettingScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(BioMetricType.fromValue(persistentState.biometricAuthType)) }
    val available = remember {
        BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val promptTitle = stringResource(R.string.app_lock_confirm_title)
    val promptSubtitle = stringResource(R.string.hs_biometeric_desc)

    fun save(type: BioMetricType) {
        persistentState.biometricAuthType = type.action
        // Just unlocked (or switched it off): don't ask again the moment Keepout reopens.
        persistentState.biometricAuthTime = SystemClock.elapsedRealtime()
        current = type
        eventLogger.log(EventType.UI_SETTING_CHANGED, Severity.LOW, "App lock", EventSource.UI, true, "App lock set to ${type.name.lowercase()}")
    }

    fun select(type: BioMetricType) {
        if (type == current) return
        if (!type.enabled() || current.enabled()) {
            save(type)
            return
        }
        val activity = context.findActivity<FragmentActivity>() ?: return
        if (!available) return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = save(type)
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    RethinkAppLockSettingsScreen(
        selected = current.toShared(),
        options = listOf(
            RethinkAppLockOption(RethinkAppLockTimeout.Off, stringResource(R.string.app_lock_off)),
            RethinkAppLockOption(RethinkAppLockTimeout.EveryTime, stringResource(R.string.app_lock_every_time), stringResource(R.string.app_lock_every_time_desc)),
            RethinkAppLockOption(RethinkAppLockTimeout.FiveMinutes, stringResource(R.string.app_lock_after_minutes, 5), stringResource(R.string.app_lock_after_desc)),
            RethinkAppLockOption(RethinkAppLockTimeout.FifteenMinutes, stringResource(R.string.app_lock_after_minutes, 15), stringResource(R.string.app_lock_after_desc)),
        ),
        strings = RethinkAppLockSettingsStrings(
            title = stringResource(R.string.app_lock_title),
            explanation = stringResource(R.string.app_lock_explanation),
            section = stringResource(R.string.app_lock_section),
            unavailable = stringResource(R.string.app_lock_unavailable),
        ),
        available = available,
        onSelect = { select(it.toBioMetricType()) },
        onBackClick = onBackClick,
    )
}

private fun BioMetricType.toShared() = when (this) {
    BioMetricType.OFF -> RethinkAppLockTimeout.Off
    BioMetricType.EVERY_TIME -> RethinkAppLockTimeout.EveryTime
    BioMetricType.FIVE_MIN -> RethinkAppLockTimeout.FiveMinutes
    BioMetricType.FIFTEEN_MIN -> RethinkAppLockTimeout.FifteenMinutes
}

private fun RethinkAppLockTimeout.toBioMetricType() = when (this) {
    RethinkAppLockTimeout.Off -> BioMetricType.OFF
    RethinkAppLockTimeout.EveryTime -> BioMetricType.EVERY_TIME
    RethinkAppLockTimeout.FiveMinutes -> BioMetricType.FIVE_MIN
    RethinkAppLockTimeout.FifteenMinutes -> BioMetricType.FIFTEEN_MIN
}

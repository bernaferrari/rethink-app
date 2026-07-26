/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.Severity
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isOsVersionAbove412
import com.celzero.firestack.settings.Settings

private const val DESYNC_SUPPORTED_VERSION = "4.12"

enum class DialStrategies(val mode: Int) {
    SPLIT_AUTO(Settings.SplitAuto), SPLIT_TCP(Settings.SplitTCP), SPLIT_TCP_TLS(Settings.SplitTCPOrTLS),
    DESYNC(Settings.SplitDesync), NEVER_SPLIT(Settings.SplitNever), TCP_PROXY(Settings.SplitAuto);
    companion object { fun fromInt(value: Int): DialStrategies? = entries.firstOrNull { it.mode == value } }
}

enum class RetryStrategies(val mode: Int) {
    RETRY_WITH_SPLIT(Settings.RetryWithSplit), RETRY_NEVER(Settings.RetryNever), RETRY_AFTER_SPLIT(Settings.RetryAfterSplit);
    companion object { fun fromInt(value: Int): RetryStrategies? = entries.firstOrNull { it.mode == value } }
}

/** Android persistence, compatibility and event logging bridge for the shared strategy picker. */
@Composable
fun AntiCensorshipScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null,
) {
    val desyncSupported = remember { isOsVersionAbove412(DESYNC_SUPPORTED_VERSION) }
    val initialDial = remember {
        val base = DialStrategies.fromInt(persistentState.dialStrategy) ?: DialStrategies.SPLIT_AUTO
        val resolved = if (base == DialStrategies.SPLIT_AUTO && persistentState.autoProxyEnabled) DialStrategies.TCP_PROXY else base
        if (!desyncSupported && resolved == DialStrategies.DESYNC) {
            persistentState.dialStrategy = DialStrategies.SPLIT_AUTO.mode
            DialStrategies.SPLIT_AUTO
        } else resolved
    }
    var dialSelection by remember { mutableStateOf(initialDial) }
    var retrySelection by remember { mutableStateOf(RetryStrategies.fromInt(persistentState.retryStrategy) ?: RetryStrategies.RETRY_WITH_SPLIT) }
    val context = LocalContext.current
    val dialOptions = dialOptions(desyncSupported)
    val retryOptions = retryOptions(dialSelection)
    RethinkAntiCensorshipScreen(
        dialOptions = dialOptions,
        retryOptions = retryOptions,
        selectedDialId = dialSelection.name,
        selectedRetryId = retrySelection.name,
        strings = RethinkAntiCensorshipStrings(
            title = stringResource(R.string.anti_censorship_title),
            split = stringResource(R.string.lbl_split),
            retryHeading = stringResource(R.string.ac_retry_options_title),
            retryDescription = stringResource(R.string.ac_retry_options_desc),
        ),
        onDialSelected = { id ->
            val strategy = DialStrategies.valueOf(id)
            if (dialSelection == strategy) return@RethinkAntiCensorshipScreen
            dialSelection = strategy
            persistentState.dialStrategy = strategy.mode
            persistentState.autoProxyEnabled = strategy == DialStrategies.TCP_PROXY
            val nextRetry = when (strategy) {
                DialStrategies.NEVER_SPLIT -> RetryStrategies.RETRY_NEVER
                DialStrategies.SPLIT_AUTO, DialStrategies.TCP_PROXY -> RetryStrategies.RETRY_WITH_SPLIT
                else -> RetryStrategies.fromInt(persistentState.retryStrategy) ?: RetryStrategies.RETRY_WITH_SPLIT
            }
            persistentState.retryStrategy = nextRetry.mode
            retrySelection = nextRetry
            logChange(eventLogger, "Anti-censorship dial strategy changed to ${strategy.mode}")
        },
        onRetrySelected = { id ->
            val strategy = RetryStrategies.valueOf(id)
            if (retrySelection == strategy) return@RethinkAntiCensorshipScreen
            val mode = if (dialSelection == DialStrategies.NEVER_SPLIT && strategy != RetryStrategies.RETRY_NEVER) RetryStrategies.RETRY_NEVER.mode else strategy.mode
            persistentState.retryStrategy = mode
            retrySelection = RetryStrategies.fromInt(mode) ?: strategy
            logChange(eventLogger, "Anti-censorship retry strategy changed to $mode")
        },
        onRetryDisabled = { Utilities.showToastUiCentered(context, context.getString(R.string.ac_toast_retry_disabled), Toast.LENGTH_LONG) },
        onBackClick = onBackClick,
    )
}

@Composable
private fun dialOptions(desyncSupported: Boolean): List<RethinkAntiCensorshipOption> =
    DialStrategies.entries.filter { desyncSupported || it != DialStrategies.DESYNC }.map { strategy ->
        RethinkAntiCensorshipOption(strategy.name, stringResource(strategy.titleRes()), stringResource(strategy.descriptionRes()))
    }

@Composable
private fun retryOptions(dialSelection: DialStrategies): List<RethinkAntiCensorshipOption> =
    RetryStrategies.entries.map { strategy ->
        RethinkAntiCensorshipOption(
            strategy.name,
            stringResource(strategy.titleRes()),
            stringResource(strategy.descriptionRes()),
            enabled = dialSelection != DialStrategies.NEVER_SPLIT || strategy == RetryStrategies.RETRY_NEVER,
        )
    }

private fun DialStrategies.titleRes() = when (this) {
    DialStrategies.NEVER_SPLIT -> R.string.settings_app_list_default_app
    DialStrategies.SPLIT_AUTO -> R.string.settings_ip_text_ipv46
    DialStrategies.SPLIT_TCP -> R.string.ac_split_tcp
    DialStrategies.SPLIT_TCP_TLS -> R.string.ac_split_tls
    DialStrategies.DESYNC -> R.string.ac_desync
    DialStrategies.TCP_PROXY -> R.string.ac_tcp_proxy
}

private fun DialStrategies.descriptionRes() = when (this) {
    DialStrategies.NEVER_SPLIT -> R.string.ac_never_split_desc
    DialStrategies.SPLIT_AUTO -> R.string.ac_split_auto_desc
    DialStrategies.SPLIT_TCP -> R.string.ac_split_tcp_desc
    DialStrategies.SPLIT_TCP_TLS -> R.string.ac_split_tls_desc
    DialStrategies.DESYNC -> R.string.ac_desync_desc
    DialStrategies.TCP_PROXY -> R.string.ac_tcp_proxy_desc
}

private fun RetryStrategies.titleRes() = when (this) {
    RetryStrategies.RETRY_NEVER -> R.string.settings_app_list_default_app
    RetryStrategies.RETRY_WITH_SPLIT -> R.string.settings_ip_text_ipv46
    RetryStrategies.RETRY_AFTER_SPLIT -> R.string.lbl_always
}

private fun RetryStrategies.descriptionRes() = when (this) {
    RetryStrategies.RETRY_NEVER -> R.string.ac_retry_options_never_desc
    RetryStrategies.RETRY_WITH_SPLIT -> R.string.ac_retry_options_with_split_desc
    RetryStrategies.RETRY_AFTER_SPLIT -> R.string.ac_retry_options_after_split_desc
}

private fun logChange(eventLogger: EventLogger, details: String) {
    eventLogger.log(EventType.UI_TOGGLE, Severity.LOW, "Anti-censorship UI", EventSource.UI, false, details)
}

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

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities.isOsVersionAbove412
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.firestack.settings.Settings
import io.github.aakira.napier.Napier
import org.koin.android.ext.android.inject

class AntiCensorshipActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()
    private val eventLogger by inject<EventLogger>()

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val DESYNC_SUPPORTED_VERSION = "4.12"
    }

    enum class DialStrategies(val mode: Int) {
        SPLIT_AUTO(Settings.SplitAuto),
        SPLIT_TCP(Settings.SplitTCP),
        SPLIT_TCP_TLS(Settings.SplitTCPOrTLS),
        DESYNC(Settings.SplitDesync),
        NEVER_SPLIT(Settings.SplitNever),
        TCP_PROXY(Settings.SplitAuto);

        companion object {
            fun fromInt(value: Int): DialStrategies? = DialStrategies.entries.firstOrNull { it.mode == value }
        }
    }

    enum class RetryStrategies(val mode: Int) {
        RETRY_WITH_SPLIT(Settings.RetryWithSplit),
        RETRY_NEVER(Settings.RetryNever),
        RETRY_AFTER_SPLIT(Settings.RetryAfterSplit);

        companion object {
            fun fromInt(value: Int): RetryStrategies? = RetryStrategies.entries.firstOrNull { it.mode == value }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }
        val initialDial = resolveDialSelection(persistentState.dialStrategy)
        val initialRetry = RetryStrategies.fromInt(persistentState.retryStrategy)
            ?: RetryStrategies.RETRY_WITH_SPLIT

        val desyncSupported = isOsVersionAbove412(DESYNC_SUPPORTED_VERSION)
        if (!desyncSupported && initialDial == DialStrategies.DESYNC) {
            persistentState.dialStrategy = DialStrategies.SPLIT_AUTO.mode
            Napier.i("Desync mode is not supported in Android 11 and below")
        }

        setContent {
            RethinkTheme {
                AntiCensorshipScreen(
                    initialDial = if (!desyncSupported && initialDial == DialStrategies.DESYNC) {
                        DialStrategies.SPLIT_AUTO
                    } else {
                        initialDial
                    },
                    initialRetry = initialRetry,
                    desyncSupported = desyncSupported
                )
            }
        }
    }

    private fun resolveDialSelection(mode: Int): DialStrategies {
        val base = DialStrategies.fromInt(mode) ?: DialStrategies.SPLIT_AUTO
        return if (base == DialStrategies.SPLIT_AUTO && persistentState.autoProxyEnabled) {
            DialStrategies.TCP_PROXY
        } else {
            base
        }
    }

    @Composable
    private fun AntiCensorshipScreen(
        initialDial: DialStrategies,
        initialRetry: RetryStrategies,
        desyncSupported: Boolean
    ) {
        var dialSelection by mutableStateOf(initialDial)
        var retrySelection by mutableStateOf(initialRetry)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader(
                title = stringResourceCompat(R.string.anti_censorship_title).lowercase(),
                description = stringResourceCompat(R.string.ac_desc)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    DialStrategies.entries.forEach { strategy ->
                        if (!desyncSupported && strategy == DialStrategies.DESYNC) return@forEach
                        val titleRes = when (strategy) {
                            DialStrategies.NEVER_SPLIT -> R.string.settings_app_list_default_app
                            DialStrategies.SPLIT_AUTO -> R.string.settings_ip_text_ipv46
                            DialStrategies.SPLIT_TCP -> R.string.ac_split_tcp
                            DialStrategies.SPLIT_TCP_TLS -> R.string.ac_split_tls
                            DialStrategies.DESYNC -> R.string.ac_desync
                            DialStrategies.TCP_PROXY -> R.string.ac_tcp_proxy
                        }
                        val descRes = when (strategy) {
                            DialStrategies.NEVER_SPLIT -> R.string.ac_never_split_desc
                            DialStrategies.SPLIT_AUTO -> R.string.ac_split_auto_desc
                            DialStrategies.SPLIT_TCP -> R.string.ac_split_tcp_desc
                            DialStrategies.SPLIT_TCP_TLS -> R.string.ac_split_tls_desc
                            DialStrategies.DESYNC -> R.string.ac_desync_desc
                            DialStrategies.TCP_PROXY -> R.string.ac_tcp_proxy_desc
                        }
                        OptionRow(
                            title = stringResourceCompat(titleRes),
                            description = stringResourceCompat(descRes),
                            selected = dialSelection == strategy,
                            onClick = {
                                if (dialSelection != strategy) {
                                    dialSelection = strategy
                                    handleDialSelection(strategy) { retry ->
                                        retrySelection = retry
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(
                title = stringResourceCompat(R.string.ac_retry_options_title),
                description = stringResourceCompat(R.string.ac_retry_options_desc)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    RetryStrategies.entries.forEach { strategy ->
                        val titleRes = when (strategy) {
                            RetryStrategies.RETRY_NEVER -> R.string.settings_app_list_default_app
                            RetryStrategies.RETRY_WITH_SPLIT -> R.string.settings_ip_text_ipv46
                            RetryStrategies.RETRY_AFTER_SPLIT -> R.string.lbl_always
                        }
                        val descRes = when (strategy) {
                            RetryStrategies.RETRY_NEVER -> R.string.ac_retry_options_never_desc
                            RetryStrategies.RETRY_WITH_SPLIT -> R.string.ac_retry_options_with_split_desc
                            RetryStrategies.RETRY_AFTER_SPLIT -> R.string.ac_retry_options_after_split_desc
                        }
                        val enabled = dialSelection != DialStrategies.NEVER_SPLIT ||
                            strategy == RetryStrategies.RETRY_NEVER
                        val context = LocalContext.current
                        OptionRow(
                            title = stringResourceCompat(titleRes),
                            description = stringResourceCompat(descRes),
                            selected = retrySelection == strategy,
                            enabled = enabled,
                            onClick = {
                                if (!enabled) {
                                    Utilities.showToastUiCentered(
                                        context,
                                        getString(R.string.ac_toast_retry_disabled),
                                        Toast.LENGTH_LONG
                                    )
                                    return@OptionRow
                                }
                                if (retrySelection != strategy) {
                                    retrySelection = strategy
                                    handleRetrySelection(strategy)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleDialSelection(
        strategy: DialStrategies,
        updateRetrySelection: (RetryStrategies) -> Unit
    ) {
        persistentState.dialStrategy = strategy.mode
        persistentState.autoProxyEnabled = strategy == DialStrategies.TCP_PROXY
        val nextRetry =
            when (strategy) {
                DialStrategies.NEVER_SPLIT -> RetryStrategies.RETRY_NEVER
                DialStrategies.SPLIT_AUTO, DialStrategies.TCP_PROXY -> RetryStrategies.RETRY_WITH_SPLIT
                else -> RetryStrategies.fromInt(persistentState.retryStrategy) ?: RetryStrategies.RETRY_WITH_SPLIT
            }
        persistentState.retryStrategy = nextRetry.mode
        updateRetrySelection(nextRetry)
        logEvent("Anti-censorship dial strategy changed to ${strategy.mode}")
    }

    private fun handleRetrySelection(strategy: RetryStrategies) {
        var mode = strategy.mode
        if (DialStrategies.NEVER_SPLIT.mode == persistentState.dialStrategy &&
            strategy != RetryStrategies.RETRY_NEVER
        ) {
            mode = RetryStrategies.RETRY_NEVER.mode
        }

        persistentState.retryStrategy = mode
        logEvent("Anti-censorship retry strategy changed to $mode")
    }

    @Composable
    private fun SectionHeader(title: String, description: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    @Composable
    private fun OptionRow(
        title: String,
        description: String,
        selected: Boolean,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val modifier = if (enabled) {
            Modifier.clickable { onClick() }
        } else {
            Modifier
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = if (enabled) onClick else null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun logEvent(details: String) {
        eventLogger.log(EventType.UI_TOGGLE, Severity.LOW, "Anti-censorship UI", EventSource.UI, false, details)
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) {
            context.getString(id, *args)
        } else {
            context.getString(id)
        }
    }
}

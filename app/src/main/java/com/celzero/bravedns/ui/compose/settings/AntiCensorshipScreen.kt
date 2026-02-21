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
package com.celzero.bravedns.ui.compose.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isOsVersionAbove412
import com.celzero.firestack.settings.Settings

private const val DESYNC_SUPPORTED_VERSION = "4.12"

enum class DialStrategies(val mode: Int) {
    SPLIT_AUTO(Settings.SplitAuto),
    SPLIT_TCP(Settings.SplitTCP),
    SPLIT_TCP_TLS(Settings.SplitTCPOrTLS),
    DESYNC(Settings.SplitDesync),
    NEVER_SPLIT(Settings.SplitNever),
    TCP_PROXY(Settings.SplitAuto);

    companion object {
        fun fromInt(value: Int): DialStrategies? = entries.firstOrNull { it.mode == value }
    }
}

enum class RetryStrategies(val mode: Int) {
    RETRY_WITH_SPLIT(Settings.RetryWithSplit),
    RETRY_NEVER(Settings.RetryNever),
    RETRY_AFTER_SPLIT(Settings.RetryAfterSplit);

    companion object {
        fun fromInt(value: Int): RetryStrategies? = entries.firstOrNull { it.mode == value }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiCensorshipScreen(
    persistentState: PersistentState,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val desyncSupported = remember { isOsVersionAbove412(DESYNC_SUPPORTED_VERSION) }
    
    val initialDial = remember {
        val base = DialStrategies.fromInt(persistentState.dialStrategy) ?: DialStrategies.SPLIT_AUTO
        val resolved = if (base == DialStrategies.SPLIT_AUTO && persistentState.autoProxyEnabled) {
            DialStrategies.TCP_PROXY
        } else {
            base
        }
        if (!desyncSupported && resolved == DialStrategies.DESYNC) {
            persistentState.dialStrategy = DialStrategies.SPLIT_AUTO.mode
            DialStrategies.SPLIT_AUTO
        } else {
            resolved
        }
    }
    
    val initialRetry = remember {
        RetryStrategies.fromInt(persistentState.retryStrategy) ?: RetryStrategies.RETRY_WITH_SPLIT
    }

    var dialSelection by remember { mutableStateOf(initialDial) }
    var retrySelection by remember { mutableStateOf(initialRetry) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(
                title = stringResource(R.string.anti_censorship_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding =
                PaddingValues(
                    start = Dimensions.screenPaddingHorizontal,
                    end = Dimensions.screenPaddingHorizontal,
                    bottom = Dimensions.spacing3xl
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                AntiCensorshipOverviewCard(
                    selectedDial = dialSelection,
                    selectedRetry = retrySelection
                )
            }

            item {
                SectionHeader(title = stringResource(R.string.anti_censorship_title))
                RethinkListGroup {
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

                        RethinkListItem(
                            headline = stringResource(titleRes),
                            supporting = stringResource(descRes),
                            leadingIcon =
                                if (dialSelection == strategy) {
                                    Icons.Rounded.RadioButtonChecked
                                } else {
                                    Icons.Rounded.RadioButtonUnchecked
                                },
                            leadingIconTint =
                                if (dialSelection == strategy) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            onClick = {
                                if (dialSelection != strategy) {
                                    dialSelection = strategy
                                    persistentState.dialStrategy = strategy.mode
                                    persistentState.autoProxyEnabled = strategy == DialStrategies.TCP_PROXY
                                    val nextRetry =
                                        when (strategy) {
                                            DialStrategies.NEVER_SPLIT -> RetryStrategies.RETRY_NEVER
                                            DialStrategies.SPLIT_AUTO, DialStrategies.TCP_PROXY -> RetryStrategies.RETRY_WITH_SPLIT
                                            else -> RetryStrategies.fromInt(persistentState.retryStrategy) ?: RetryStrategies.RETRY_WITH_SPLIT
                                        }
                                    persistentState.retryStrategy = nextRetry.mode
                                    retrySelection = nextRetry
                                    eventLogger.log(
                                        EventType.UI_TOGGLE,
                                        Severity.LOW,
                                        "Anti-censorship UI",
                                        EventSource.UI,
                                        false,
                                        "Anti-censorship dial strategy changed to ${strategy.mode}"
                                    )
                                }
                            },
                            trailing = {
                                RadioButton(
                                    selected = dialSelection == strategy,
                                    onClick = null
                                )
                            }
                        )
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.ac_retry_options_title))
                Text(
                    text = stringResource(R.string.ac_retry_options_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimensions.spacingMd, vertical = Dimensions.spacingXs)
                )

                RethinkListGroup {
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
                        val enabled =
                            dialSelection != DialStrategies.NEVER_SPLIT ||
                                strategy == RetryStrategies.RETRY_NEVER

                        RethinkListItem(
                            headline = stringResource(titleRes),
                            supporting = stringResource(descRes),
                            leadingIcon =
                                if (retrySelection == strategy) {
                                    Icons.Rounded.RadioButtonChecked
                                } else {
                                    Icons.Rounded.RadioButtonUnchecked
                                },
                            leadingIconTint =
                                if (enabled) {
                                    if (retrySelection == strategy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            enabled = enabled,
                            onClick = {
                                if (!enabled) {
                                    Utilities.showToastUiCentered(
                                        context,
                                        context.resources.getString(R.string.ac_toast_retry_disabled),
                                        Toast.LENGTH_LONG
                                    )
                                    return@RethinkListItem
                                }
                                if (retrySelection != strategy) {
                                    var mode = strategy.mode
                                    if (
                                        DialStrategies.NEVER_SPLIT.mode == persistentState.dialStrategy &&
                                            strategy != RetryStrategies.RETRY_NEVER
                                    ) {
                                        mode = RetryStrategies.RETRY_NEVER.mode
                                    }
                                    persistentState.retryStrategy = mode
                                    retrySelection = strategy
                                    eventLogger.log(
                                        EventType.UI_TOGGLE,
                                        Severity.LOW,
                                        "Anti-censorship UI",
                                        EventSource.UI,
                                        false,
                                        "Anti-censorship retry strategy changed to $mode"
                                    )
                                }
                            },
                            trailing = {
                                RadioButton(
                                    selected = retrySelection == strategy,
                                    onClick = null,
                                    enabled = enabled
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AntiCensorshipOverviewCard(
    selectedDial: DialStrategies,
    selectedRetry: RetryStrategies
) {
    val dialLabel =
        stringResource(
            when (selectedDial) {
                DialStrategies.NEVER_SPLIT -> R.string.settings_app_list_default_app
                DialStrategies.SPLIT_AUTO -> R.string.settings_ip_text_ipv46
                DialStrategies.SPLIT_TCP -> R.string.ac_split_tcp
                DialStrategies.SPLIT_TCP_TLS -> R.string.ac_split_tls
                DialStrategies.DESYNC -> R.string.ac_desync
                DialStrategies.TCP_PROXY -> R.string.ac_tcp_proxy
            }
        )
    val retryLabel =
        stringResource(
            when (selectedRetry) {
                RetryStrategies.RETRY_NEVER -> R.string.settings_app_list_default_app
                RetryStrategies.RETRY_WITH_SPLIT -> R.string.settings_ip_text_ipv46
                RetryStrategies.RETRY_AFTER_SPLIT -> R.string.lbl_always
            }
        )

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.anti_censorship_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.ac_split_auto_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text =
                    stringResource(
                        R.string.two_argument_colon,
                        stringResource(R.string.lbl_split),
                        dialLabel
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text =
                    stringResource(
                        R.string.two_argument_colon,
                        stringResource(R.string.ac_retry_options_title),
                        retryLabel
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

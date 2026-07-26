/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTwoOptionSegmentedRow
import kotlinx.coroutines.launch

enum class RethinkReachabilityProbeField {
    Ipv4Primary, Ipv4Secondary, Url4Primary, Url4Secondary,
    Ipv6Primary, Ipv6Secondary, Url6Primary, Url6Secondary,
}

enum class RethinkReachabilityProbeStatus { Success, Wifi, Cellular, Failure }

data class RethinkNetworkReachabilityConfiguration(
    val automatic: Boolean,
    val ipv4Ips: List<String>,
    val ipv4Urls: List<String>,
    val ipv6Ips: List<String>,
    val ipv6Urls: List<String>,
)

data class RethinkNetworkReachabilityEditorStrings(
    val automatic: String,
    val manual: String,
    val description: String,
    val ipv4: String,
    val ipv6: String,
    val restoreDefaults: String,
    val save: String,
    val test: String,
    val automaticIpv4Probe: String,
    val automaticIpv6Probe: String,
)

/** Portable connectivity-probe editor. Probing, validation, persistence, and notifications stay host-owned. */
@Composable
fun RethinkNetworkReachabilityEditor(
    initialConfiguration: RethinkNetworkReachabilityConfiguration,
    defaultConfiguration: RethinkNetworkReachabilityConfiguration,
    ipv4Supported: Boolean,
    ipv6Supported: Boolean,
    strings: RethinkNetworkReachabilityEditorStrings,
    onTest: suspend (RethinkNetworkReachabilityConfiguration) -> Map<RethinkReachabilityProbeField, RethinkReachabilityProbeStatus>,
    onSave: (RethinkNetworkReachabilityConfiguration) -> String?,
    modifier: Modifier = Modifier,
) {
    var configuration by remember(initialConfiguration) { mutableStateOf(initialConfiguration) }
    var probeResults by remember { mutableStateOf<Map<RethinkReachabilityProbeField, RethinkReachabilityProbeStatus>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val automatic = configuration.automatic
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        RethinkReachabilityCard {
            RethinkTwoOptionSegmentedRow(
                leftLabel = strings.automatic,
                rightLabel = strings.manual,
                leftSelected = automatic,
                onLeftClick = {
                    configuration = configuration.copy(automatic = true)
                    probeResults = emptyMap()
                    errorMessage = null
                },
                onRightClick = {
                    configuration = if (configuration.automatic) initialConfiguration.copy(automatic = false) else configuration
                    probeResults = emptyMap()
                    errorMessage = null
                },
            )
            Text(strings.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RethinkReachabilityCard {
            RethinkReachabilityHeader(strings.ipv4, ipv4Supported) {
                if (!automatic) TextButton(onClick = { configuration = defaultConfiguration.copy(automatic = false); probeResults = emptyMap() }) { Text(strings.restoreDefaults) }
            }
            RethinkReachabilityField(
                value = if (automatic) strings.automaticIpv4Probe else configuration.ipv4Ips.valueAt(0),
                enabled = !automatic,
                status = probeResults[RethinkReachabilityProbeField.Ipv4Primary],
                testing = testing,
                onValueChange = { configuration = configuration.copy(ipv4Ips = configuration.ipv4Ips.replaceAt(0, it)) },
            )
            RethinkReachabilityField(
                value = if (automatic) strings.automaticIpv4Probe else configuration.ipv4Ips.valueAt(1),
                enabled = !automatic,
                status = probeResults[RethinkReachabilityProbeField.Ipv4Secondary],
                testing = testing,
                onValueChange = { configuration = configuration.copy(ipv4Ips = configuration.ipv4Ips.replaceAt(1, it)) },
            )
            if (!automatic) {
                RethinkReachabilityField(configuration.ipv4Urls.valueAt(0), true, probeResults[RethinkReachabilityProbeField.Url4Primary], testing) {
                    configuration = configuration.copy(ipv4Urls = configuration.ipv4Urls.replaceAt(0, it))
                }
                RethinkReachabilityField(configuration.ipv4Urls.valueAt(1), true, probeResults[RethinkReachabilityProbeField.Url4Secondary], testing) {
                    configuration = configuration.copy(ipv4Urls = configuration.ipv4Urls.replaceAt(1, it))
                }
            }
        }
        RethinkReachabilityCard {
            RethinkReachabilityHeader(strings.ipv6, ipv6Supported)
            RethinkReachabilityField(
                value = if (automatic) strings.automaticIpv6Probe else configuration.ipv6Ips.valueAt(0),
                enabled = !automatic,
                status = probeResults[RethinkReachabilityProbeField.Ipv6Primary],
                testing = testing,
                onValueChange = { configuration = configuration.copy(ipv6Ips = configuration.ipv6Ips.replaceAt(0, it)) },
            )
            RethinkReachabilityField(
                value = if (automatic) strings.automaticIpv6Probe else configuration.ipv6Ips.valueAt(1),
                enabled = !automatic,
                status = probeResults[RethinkReachabilityProbeField.Ipv6Secondary],
                testing = testing,
                onValueChange = { configuration = configuration.copy(ipv6Ips = configuration.ipv6Ips.replaceAt(1, it)) },
            )
            if (!automatic) {
                RethinkReachabilityField(configuration.ipv6Urls.valueAt(0), true, probeResults[RethinkReachabilityProbeField.Url6Primary], testing) {
                    configuration = configuration.copy(ipv6Urls = configuration.ipv6Urls.replaceAt(0, it))
                }
                RethinkReachabilityField(configuration.ipv6Urls.valueAt(1), true, probeResults[RethinkReachabilityProbeField.Url6Secondary], testing) {
                    configuration = configuration.copy(ipv6Urls = configuration.ipv6Urls.replaceAt(1, it))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm, Alignment.End),
        ) {
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    probeResults = emptyMap()
                    errorMessage = null
                    scope.launch {
                        runCatching { onTest(configuration) }
                            .onSuccess { probeResults = it }
                            .onFailure { errorMessage = it.message }
                        testing = false
                    }
                },
            ) { Text(strings.test) }
            Button(
                enabled = !testing,
                onClick = { errorMessage = onSave(configuration) },
            ) { Text(strings.save) }
        }
        errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RethinkReachabilityCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(SharedDimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) { content() }
    }
}

@Composable
private fun RethinkReachabilityHeader(title: String, supported: Boolean, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (supported) MaterialSymbols.Filled.Check else MaterialSymbols.Filled.Close,
            contentDescription = null,
            tint = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(title, Modifier.padding(start = SharedDimensions.spacingSm), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun RethinkReachabilityField(
    value: String,
    enabled: Boolean,
    status: RethinkReachabilityProbeStatus?,
    testing: Boolean,
    onValueChange: (String) -> Unit,
) {
    RethinkFormTextField(
        value = value,
        onValueChange = onValueChange,
        label = null,
        enabled = enabled,
        singleLine = true,
        trailingIcon = {
            when {
                testing -> CircularProgressIndicator(Modifier.padding(10.dp))
                status != null -> Icon(reachabilityIcon(status), null, tint = reachabilityTint(status))
            }
        },
    )
}

@Composable
private fun reachabilityTint(status: RethinkReachabilityProbeStatus) = when (status) {
    RethinkReachabilityProbeStatus.Failure -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private fun reachabilityIcon(status: RethinkReachabilityProbeStatus): ImageVector = when (status) {
    RethinkReachabilityProbeStatus.Success -> MaterialSymbols.Filled.Check
    RethinkReachabilityProbeStatus.Wifi -> MaterialSymbols.Filled.Wifi
    RethinkReachabilityProbeStatus.Cellular -> MaterialSymbols.Filled.SignalCellularAlt
    RethinkReachabilityProbeStatus.Failure -> MaterialSymbols.Filled.Close
}

private fun List<String>.valueAt(index: Int): String = getOrNull(index).orEmpty()
private fun List<String>.replaceAt(index: Int, value: String): List<String> = List(2) { if (it == index) value else valueAt(it) }

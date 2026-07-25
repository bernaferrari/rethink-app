/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.RethinkTwoOptionSegmentedRow

data class RethinkLanIpAddress(
    val address: String,
    val prefix: String,
)

data class RethinkCustomLanIpConfiguration(
    val manual: Boolean,
    val gatewayV4: RethinkLanIpAddress,
    val gatewayV6: RethinkLanIpAddress,
    val routerV4: RethinkLanIpAddress,
    val routerV6: RethinkLanIpAddress,
    val dnsV4: RethinkLanIpAddress,
    val dnsV6: RethinkLanIpAddress,
)

data class RethinkCustomLanIpEditorStrings(
    val automatic: String,
    val manual: String,
    val automaticDescription: String,
    val manualDescription: String,
    val gateway: String,
    val router: String,
    val dns: String,
    val ipv4: String,
    val ipv6: String,
    val prefix: String,
    val save: String,
    val reset: String,
)

/** Portable local-address editor; hosts validate and persist the resulting configuration. */
@Composable
fun RethinkCustomLanIpEditor(
    initialConfiguration: RethinkCustomLanIpConfiguration,
    defaultConfiguration: RethinkCustomLanIpConfiguration,
    strings: RethinkCustomLanIpEditorStrings,
    onSave: (RethinkCustomLanIpConfiguration) -> String?,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configuration by remember(initialConfiguration) { mutableStateOf(initialConfiguration) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val manual = configuration.manual
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        RethinkLanEditorCard {
            RethinkTwoOptionSegmentedRow(
                leftLabel = strings.automatic,
                rightLabel = strings.manual,
                leftSelected = !manual,
                onLeftClick = {
                    configuration = defaultConfiguration.copy(manual = false)
                    errorMessage = null
                },
                onRightClick = {
                    configuration = if (configuration.manual) configuration else initialConfiguration.copy(manual = true)
                    errorMessage = null
                },
            )
            Text(
                if (manual) strings.manualDescription else strings.automaticDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RethinkLanEditorCard {
            RethinkLanAddressSection(
                title = strings.gateway,
                v4 = configuration.gatewayV4,
                v6 = configuration.gatewayV6,
                strings = strings,
                enabled = manual,
                onV4Change = { configuration = configuration.copy(gatewayV4 = it) },
                onV6Change = { configuration = configuration.copy(gatewayV6 = it) },
            )
            RethinkLanAddressSection(
                title = strings.router,
                v4 = configuration.routerV4,
                v6 = configuration.routerV6,
                strings = strings,
                enabled = manual,
                onV4Change = { configuration = configuration.copy(routerV4 = it) },
                onV6Change = { configuration = configuration.copy(routerV6 = it) },
            )
            RethinkLanAddressSection(
                title = strings.dns,
                v4 = configuration.dnsV4,
                v6 = configuration.dnsV6,
                strings = strings,
                enabled = manual,
                onV4Change = { configuration = configuration.copy(dnsV4 = it) },
                onV6Change = { configuration = configuration.copy(dnsV6 = it) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm, Alignment.End),
        ) {
            TextButton(
                enabled = manual,
                onClick = {
                    configuration = defaultConfiguration.copy(manual = true)
                    errorMessage = null
                    onReset()
                },
            ) { Text(strings.reset) }
            Button(
                onClick = { errorMessage = onSave(configuration) },
            ) { Text(strings.save) }
        }
        errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RethinkLanEditorCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(SharedDimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) { content() }
    }
}

@Composable
private fun RethinkLanAddressSection(
    title: String,
    v4: RethinkLanIpAddress,
    v6: RethinkLanIpAddress,
    strings: RethinkCustomLanIpEditorStrings,
    enabled: Boolean,
    onV4Change: (RethinkLanIpAddress) -> Unit,
    onV6Change: (RethinkLanIpAddress) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    RethinkLanIpField(v4, strings.ipv4, strings.prefix, enabled, onV4Change)
    RethinkLanIpField(v6, strings.ipv6, strings.prefix, enabled, onV6Change)
}

@Composable
private fun RethinkLanIpField(
    value: RethinkLanIpAddress,
    addressLabel: String,
    prefixLabel: String,
    enabled: Boolean,
    onValueChange: (RethinkLanIpAddress) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        OutlinedTextField(
            value = value.address,
            onValueChange = { onValueChange(value.copy(address = it)) },
            label = { Text(addressLabel) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value.prefix,
            onValueChange = { onValueChange(value.copy(prefix = it)) },
            label = { Text(prefixLabel) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(0.38f),
        )
    }
}

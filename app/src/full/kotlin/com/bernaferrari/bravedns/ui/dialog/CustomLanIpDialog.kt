/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.ui.dialog

import Logger
import Logger.LOG_TAG_UI
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.bernaferrari.bravedns.ui.compose.settings.RethinkCustomLanIpEditor
import com.bernaferrari.bravedns.ui.compose.settings.RethinkCustomLanIpEditorStrings
import com.bernaferrari.bravedns.ui.compose.settings.RethinkCustomLanIpConfiguration
import com.bernaferrari.bravedns.ui.compose.settings.RethinkLanIpAddress
import com.bernaferrari.bravedns.ui.compose.theme.Dimensions
import inet.ipaddr.IPAddressString

private const val GATEWAY_4_IP = "10.111.222.1"
private const val GATEWAY_6_IP = "fd66:f83a:c650::1"
private const val ROUTER_4_IP = "10.111.222.2"
private const val ROUTER_6_IP = "fd66:f83a:c650::2"
private const val DNS_4_IP = "10.111.222.3"
private const val DNS_6_IP = "fd66:f83a:c650::3"

/** Android persistence and IP-library adapter for the shared custom-LAN editor. */
@Composable
fun CustomLanIpSheet(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaults = remember { defaultLanConfiguration() }
    val initial = remember(persistentState.customLanIpMode) {
        persistentState.toLanConfiguration(defaults)
    }
    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal, vertical = Dimensions.spacingLg)
                .verticalScroll(rememberScrollState()),
        ) {
            RethinkCustomLanIpEditor(
                initialConfiguration = initial,
                defaultConfiguration = defaults,
                strings = RethinkCustomLanIpEditorStrings(
                    automatic = stringResource(R.string.settings_ip_text_ipv46),
                    manual = stringResource(R.string.lbl_manual),
                    automaticDescription = stringResource(R.string.custom_lan_ip_auto_desc),
                    manualDescription = stringResource(R.string.custom_lan_ip_manual_desc),
                    gateway = stringResource(R.string.custom_lan_ip_gateway),
                    router = stringResource(R.string.custom_lan_ip_router),
                    dns = stringResource(R.string.dns_mode_info_title),
                    ipv4 = stringResource(R.string.settings_ip_text_ipv4),
                    ipv6 = stringResource(R.string.settings_ip_text_ipv6),
                    prefix = stringResource(R.string.lbl_prefix),
                    save = stringResource(R.string.lbl_save),
                    reset = stringResource(R.string.lbl_reset),
                ),
                onSave = { configuration ->
                    saveLanConfiguration(
                        persistentState = persistentState,
                        initialManual = initial.manual,
                        configuration = configuration,
                        onSuccess = {
                            Toast.makeText(
                                context,
                                if (configuration.manual) R.string.custom_lan_ip_saved_manual else R.string.custom_lan_ip_saved_auto,
                                Toast.LENGTH_SHORT,
                            ).show()
                            onDismiss()
                        },
                        errorText = context.getString(R.string.custom_lan_ip_validation_error),
                        saveErrorText = context.getString(R.string.custom_lan_ip_save_error),
                    )
                },
                onReset = {
                    Toast.makeText(context, R.string.custom_lan_ip_saved_manual, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

private fun defaultLanConfiguration() = RethinkCustomLanIpConfiguration(
    manual = false,
    gatewayV4 = RethinkLanIpAddress(GATEWAY_4_IP, "24"),
    gatewayV6 = RethinkLanIpAddress(GATEWAY_6_IP, "120"),
    routerV4 = RethinkLanIpAddress(ROUTER_4_IP, "32"),
    routerV6 = RethinkLanIpAddress(ROUTER_6_IP, "128"),
    dnsV4 = RethinkLanIpAddress(DNS_4_IP, "32"),
    dnsV6 = RethinkLanIpAddress(DNS_6_IP, "128"),
)

private fun PersistentState.toLanConfiguration(defaults: RethinkCustomLanIpConfiguration): RethinkCustomLanIpConfiguration {
    if (!customLanIpMode) return defaults
    return defaults.copy(
        manual = true,
        gatewayV4 = customLanGatewayIpv4.toLanIpAddress(defaults.gatewayV4),
        gatewayV6 = customLanGatewayIpv6.toLanIpAddress(defaults.gatewayV6),
        routerV4 = customLanRouterIpv4.toLanIpAddress(defaults.routerV4),
        routerV6 = customLanRouterIpv6.toLanIpAddress(defaults.routerV6),
        dnsV4 = customLanDnsIpv4.toLanIpAddress(defaults.dnsV4),
        dnsV6 = customLanDnsIpv6.toLanIpAddress(defaults.dnsV6),
    )
}

private fun String.toLanIpAddress(fallback: RethinkLanIpAddress): RethinkLanIpAddress {
    if (isBlank()) return fallback
    val parts = split("/", limit = 2)
    return RethinkLanIpAddress(parts.firstOrNull().orEmpty(), parts.getOrNull(1).orEmpty())
}

private fun saveLanConfiguration(
    persistentState: PersistentState,
    initialManual: Boolean,
    configuration: RethinkCustomLanIpConfiguration,
    onSuccess: () -> Unit,
    errorText: String,
    saveErrorText: String,
): String? = try {
    if (!configuration.manual) {
        persistentState.customLanIpMode = false
        if (initialManual) persistentState.customModeOrIpChanged = true
        onSuccess()
        return null
    }
    if (!configuration.isValidLanConfiguration()) return errorText

    val changed = initialManual != configuration.manual || configuration.toStorageValues() != persistentState.lanStorageValues()
    persistentState.customLanIpMode = true
    persistentState.customLanGatewayIpv4 = configuration.gatewayV4.toStorageValue()
    persistentState.customLanGatewayIpv6 = configuration.gatewayV6.toStorageValue()
    persistentState.customLanRouterIpv4 = configuration.routerV4.toStorageValue()
    persistentState.customLanRouterIpv6 = configuration.routerV6.toStorageValue()
    persistentState.customLanDnsIpv4 = configuration.dnsV4.toStorageValue()
    persistentState.customLanDnsIpv6 = configuration.dnsV6.toStorageValue()
    if (changed) persistentState.customModeOrIpChanged = true
    onSuccess()
    null
} catch (error: Exception) {
    Logger.e(LOG_TAG_UI, "Unable to save custom LAN IP configuration", error)
    saveErrorText
}

private fun RethinkCustomLanIpConfiguration.toStorageValues() = listOf(
    gatewayV4.toStorageValue(), gatewayV6.toStorageValue(), routerV4.toStorageValue(),
    routerV6.toStorageValue(), dnsV4.toStorageValue(), dnsV6.toStorageValue(),
)

private fun PersistentState.lanStorageValues() = listOf(
    customLanGatewayIpv4, customLanGatewayIpv6, customLanRouterIpv4,
    customLanRouterIpv6, customLanDnsIpv4, customLanDnsIpv6,
)

private fun RethinkLanIpAddress.toStorageValue(): String = if (address.isBlank() && prefix.isBlank()) "" else "$address/$prefix"

private fun RethinkCustomLanIpConfiguration.isValidLanConfiguration(): Boolean = listOf(
    gatewayV4 to true, routerV4 to true, dnsV4 to true,
    gatewayV6 to false, routerV6 to false, dnsV6 to false,
).all { (value, ipv4) -> if (ipv4) value.isValidPrivateIpv4() else value.isValidUlaIpv6() }

private fun RethinkLanIpAddress.isValidPrivateIpv4(): Boolean {
    if (address.isBlank() && prefix.isBlank()) return true
    if (address.isBlank() || prefix.isBlank()) return false
    val parsed = IPAddressString(address).address ?: return false
    val prefixLength = prefix.toIntOrNull() ?: return false
    if (!parsed.isIPv4 || prefixLength !in 0..32) return false
    val normalized = parsed.toNormalizedString()
    return normalized.startsWith("10.") || normalized.startsWith("192.168.") ||
        (normalized.startsWith("172.") && normalized.split(".").getOrNull(1)?.toIntOrNull() in 16..31)
}

private fun RethinkLanIpAddress.isValidUlaIpv6(): Boolean {
    if (address.isBlank() && prefix.isBlank()) return true
    if (address.isBlank() || prefix.isBlank()) return false
    val parsed = IPAddressString(address).address ?: return false
    val prefixLength = prefix.toIntOrNull() ?: return false
    return parsed.isIPv6 && prefixLength in 0..128 && parsed.toNormalizedString().lowercase().let { it.startsWith("fc") || it.startsWith("fd") }
}

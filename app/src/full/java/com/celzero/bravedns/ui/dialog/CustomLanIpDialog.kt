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
package com.celzero.bravedns.ui.dialog


import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import inet.ipaddr.IPAddressString
import io.github.aakira.napier.Napier

private const val GATEWAY_4_PREFIX = 24
private const val GATEWAY_6_PREFIX = 120
private const val ROUTER_4_PREFIX = 32
private const val ROUTER_6_PREFIX = 128
private const val DNS_4_PREFIX = 32
private const val DNS_6_PREFIX = 128

private const val GATEWAY_4_IP = "10.111.222.1"
private const val GATEWAY_6_IP = "fd66:f83a:c650::1"
private const val ROUTER_4_IP = "10.111.222.2"
private const val ROUTER_6_IP = "fd66:f83a:c650::2"
private const val DNS_4_IP = "10.111.222.3"
private const val DNS_6_IP = "fd66:f83a:c650::3"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLanIpSheet(
    persistentState: PersistentState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var initialMode by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(false) }

    var gatewayIpv4 by remember { mutableStateOf("") }
    var gatewayIpv4Prefix by remember { mutableStateOf("") }
    var gatewayIpv6 by remember { mutableStateOf("") }
    var gatewayIpv6Prefix by remember { mutableStateOf("") }

    var routerIpv4 by remember { mutableStateOf("") }
    var routerIpv4Prefix by remember { mutableStateOf("") }
    var routerIpv6 by remember { mutableStateOf("") }
    var routerIpv6Prefix by remember { mutableStateOf("") }

    var dnsIpv4 by remember { mutableStateOf("") }
    var dnsIpv4Prefix by remember { mutableStateOf("") }
    var dnsIpv6 by remember { mutableStateOf("") }
    var dnsIpv6Prefix by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }

    fun loadDefaultAutoValues() {
        gatewayIpv4 = GATEWAY_4_IP
        gatewayIpv4Prefix = GATEWAY_4_PREFIX.toString()
        gatewayIpv6 = GATEWAY_6_IP
        gatewayIpv6Prefix = GATEWAY_6_PREFIX.toString()

        routerIpv4 = ROUTER_4_IP
        routerIpv4Prefix = ROUTER_4_PREFIX.toString()
        routerIpv6 = ROUTER_6_IP
        routerIpv6Prefix = ROUTER_6_PREFIX.toString()

        dnsIpv4 = DNS_4_IP
        dnsIpv4Prefix = DNS_4_PREFIX.toString()
        dnsIpv6 = DNS_6_IP
        dnsIpv6Prefix = DNS_6_PREFIX.toString()
    }

    fun loadManualValues() {
        if (persistentState.customLanGatewayIpv4.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanGatewayIpv4) { ip, prefix ->
                gatewayIpv4 = ip
                gatewayIpv4Prefix = prefix
            }
        } else {
            gatewayIpv4 = GATEWAY_4_IP
            gatewayIpv4Prefix = GATEWAY_4_PREFIX.toString()
        }

        if (persistentState.customLanGatewayIpv6.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanGatewayIpv6) { ip, prefix ->
                gatewayIpv6 = ip
                gatewayIpv6Prefix = prefix
            }
        } else {
            gatewayIpv6 = GATEWAY_6_IP
            gatewayIpv6Prefix = GATEWAY_6_PREFIX.toString()
        }

        if (persistentState.customLanRouterIpv4.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanRouterIpv4) { ip, prefix ->
                routerIpv4 = ip
                routerIpv4Prefix = prefix
            }
        } else {
            routerIpv4 = ROUTER_4_IP
            routerIpv4Prefix = ROUTER_4_PREFIX.toString()
        }

        if (persistentState.customLanRouterIpv6.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanRouterIpv6) { ip, prefix ->
                routerIpv6 = ip
                routerIpv6Prefix = prefix
            }
        } else {
            routerIpv6 = ROUTER_6_IP
            routerIpv6Prefix = ROUTER_6_PREFIX.toString()
        }

        if (persistentState.customLanDnsIpv4.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanDnsIpv4) { ip, prefix ->
                dnsIpv4 = ip
                dnsIpv4Prefix = prefix
            }
        } else {
            dnsIpv4 = DNS_4_IP
            dnsIpv4Prefix = DNS_4_PREFIX.toString()
        }

        if (persistentState.customLanDnsIpv6.isNotBlank()) {
            loadIpAndPrefixIntoFields(persistentState.customLanDnsIpv6) { ip, prefix ->
                dnsIpv6 = ip
                dnsIpv6Prefix = prefix
            }
        } else {
            dnsIpv6 = DNS_6_IP
            dnsIpv6Prefix = DNS_6_PREFIX.toString()
        }
    }

    fun hideError() {
        errorMessage = ""
    }

    fun resetManualFields() {
        loadDefaultAutoValues()
        hideError()
        Toast.makeText(context, R.string.custom_lan_ip_saved_manual, Toast.LENGTH_SHORT).show()
    }

    fun saveAutoMode() {
        try {
            val modeChanged = initialMode != currentMode
            persistentState.customLanIpMode = false
            if (modeChanged) {
                persistentState.customModeOrIpChanged = true
                Napier.i("Custom LAN IPs cleared (switched to AUTO)")
            }
            hideError()
            Toast.makeText(context, R.string.custom_lan_ip_saved_auto, Toast.LENGTH_SHORT).show()
            onDismiss()
        } catch (e: Exception) {
            Napier.e("err saving custom lan ip (auto): ${e.message}")
            errorMessage = context.getString(R.string.custom_lan_ip_save_error)
        }
    }

    fun saveManualMode() {
        try {
            val gatewayV4 = gatewayIpv4.trim()
            val gatewayV4Prefix = gatewayIpv4Prefix.trim()
            val gatewayV6 = gatewayIpv6.trim()
            val gatewayV6Prefix = gatewayIpv6Prefix.trim()

            val routerV4 = routerIpv4.trim()
            val routerV4Prefix = routerIpv4Prefix.trim()
            val routerV6 = routerIpv6.trim()
            val routerV6Prefix = routerIpv6Prefix.trim()

            val dnsV4 = dnsIpv4.trim()
            val dnsV4Prefix = dnsIpv4Prefix.trim()
            val dnsV6 = dnsIpv6.trim()
            val dnsV6Prefix = dnsIpv6Prefix.trim()

            if (!validateIpv4WithPrefix(gatewayV4, gatewayV4Prefix) ||
                !validateIpv6WithPrefix(gatewayV6, gatewayV6Prefix) ||
                !validateIpv4WithPrefix(routerV4, routerV4Prefix) ||
                !validateIpv6WithPrefix(routerV6, routerV6Prefix) ||
                !validateIpv4WithPrefix(dnsV4, dnsV4Prefix) ||
                !validateIpv6WithPrefix(dnsV6, dnsV6Prefix)
            ) {
                errorMessage = context.getString(R.string.custom_lan_ip_validation_error)
                return
            }

            val newGatewayV4 = combineIpAndPrefix(gatewayV4, gatewayV4Prefix)
            val newGatewayV6 = combineIpAndPrefix(gatewayV6, gatewayV6Prefix)
            val newRouterV4 = combineIpAndPrefix(routerV4, routerV4Prefix)
            val newRouterV6 = combineIpAndPrefix(routerV6, routerV6Prefix)
            val newDnsV4 = combineIpAndPrefix(dnsV4, dnsV4Prefix)
            val newDnsV6 = combineIpAndPrefix(dnsV6, dnsV6Prefix)

            val ipValuesChanged =
                newGatewayV4 != persistentState.customLanGatewayIpv4 ||
                    newGatewayV6 != persistentState.customLanGatewayIpv6 ||
                    newRouterV4 != persistentState.customLanRouterIpv4 ||
                    newRouterV6 != persistentState.customLanRouterIpv6 ||
                    newDnsV4 != persistentState.customLanDnsIpv4 ||
                    newDnsV6 != persistentState.customLanDnsIpv6

            val modeChanged = initialMode != currentMode

            persistentState.customLanIpMode = true
            persistentState.customLanGatewayIpv4 = newGatewayV4
            persistentState.customLanGatewayIpv6 = newGatewayV6
            persistentState.customLanRouterIpv4 = newRouterV4
            persistentState.customLanRouterIpv6 = newRouterV6
            persistentState.customLanDnsIpv4 = newDnsV4
            persistentState.customLanDnsIpv6 = newDnsV6

            if (modeChanged || ipValuesChanged) {
                persistentState.customModeOrIpChanged = true
                Napier.i(
                    "Custom LAN IPs changed - mode changed: $modeChanged, IP values changed: $ipValuesChanged"
                )
            }

            hideError()
            Toast.makeText(context, R.string.custom_lan_ip_saved_manual, Toast.LENGTH_SHORT).show()
            onDismiss()
        } catch (e: Exception) {
            Napier.e("err saving custom lan ip (manual): ${e.message}")
            errorMessage = context.getString(R.string.custom_lan_ip_save_error)
        }
    }

    LaunchedEffect(Unit) {
        persistentState.customModeOrIpChanged = false
        initialMode = persistentState.customLanIpMode
        currentMode = initialMode
        if (currentMode) {
            loadManualValues()
        } else {
            loadDefaultAutoValues()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val manualEnabled = currentMode
        val selectedBg = MaterialTheme.colorScheme.tertiary
        val unselectedBg = MaterialTheme.colorScheme.surface
        val selectedText = MaterialTheme.colorScheme.onSurface
        val unselectedText = MaterialTheme.colorScheme.onSurface

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton(
                    text = context.getString(R.string.settings_ip_text_ipv46),
                    selected = !currentMode,
                    selectedBg = selectedBg,
                    unselectedBg = unselectedBg,
                    selectedText = selectedText,
                    unselectedText = unselectedText,
                    modifier = Modifier.weight(1f)
                ) {
                    currentMode = false
                    loadDefaultAutoValues()
                    hideError()
                }
                ToggleButton(
                    text = context.getString(R.string.lbl_manual),
                    selected = currentMode,
                    selectedBg = selectedBg,
                    unselectedBg = unselectedBg,
                    selectedText = selectedText,
                    unselectedText = unselectedText,
                    modifier = Modifier.weight(1f)
                ) {
                    currentMode = true
                    loadManualValues()
                    hideError()
                }
            }

            Text(
                text =
                    if (currentMode) {
                        context.getString(R.string.custom_lan_ip_manual_desc)
                    } else {
                        context.getString(R.string.custom_lan_ip_auto_desc)
                    },
                style = MaterialTheme.typography.bodySmall
            )

            SectionTitle(text = context.getString(R.string.custom_lan_ip_gateway))
            IpRow(
                ipValue = gatewayIpv4,
                prefixValue = gatewayIpv4Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv4),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { gatewayIpv4 = it },
                onPrefixChange = { gatewayIpv4Prefix = it }
            )
            IpRow(
                ipValue = gatewayIpv6,
                prefixValue = gatewayIpv6Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv6),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { gatewayIpv6 = it },
                onPrefixChange = { gatewayIpv6Prefix = it }
            )

            SectionTitle(text = context.getString(R.string.custom_lan_ip_router))
            IpRow(
                ipValue = routerIpv4,
                prefixValue = routerIpv4Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv4),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { routerIpv4 = it },
                onPrefixChange = { routerIpv4Prefix = it }
            )
            IpRow(
                ipValue = routerIpv6,
                prefixValue = routerIpv6Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv6),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { routerIpv6 = it },
                onPrefixChange = { routerIpv6Prefix = it }
            )

            SectionTitle(text = context.getString(R.string.dns_mode_info_title))
            IpRow(
                ipValue = dnsIpv4,
                prefixValue = dnsIpv4Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv4),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { dnsIpv4 = it },
                onPrefixChange = { dnsIpv4Prefix = it }
            )
            IpRow(
                ipValue = dnsIpv6,
                prefixValue = dnsIpv6Prefix,
                ipHint = context.getString(R.string.settings_ip_text_ipv6),
                prefixHint = context.getString(R.string.lbl_prefix),
                enabled = manualEnabled,
                onIpChange = { dnsIpv6 = it },
                onPrefixChange = { dnsIpv6Prefix = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = {
                        if (!currentMode) {
                            Toast.makeText(context, R.string.custom_lan_ip_saved_auto, Toast.LENGTH_SHORT).show()
                        } else {
                            resetManualFields()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = currentMode
                ) {
                    Text(text = context.getString(R.string.lbl_reset))
                }
                Button(
                    onClick = {
                        if (!currentMode) {
                            saveAutoMode()
                        } else {
                            saveManualMode()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = context.getString(R.string.lbl_save))
                }
            }

            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    selected: Boolean,
    selectedBg: Color,
    unselectedBg: Color,
    selectedText: Color,
    unselectedText: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (selected) selectedBg else unselectedBg,
                contentColor = if (selected) selectedText else unselectedText
            )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun IpRow(
    ipValue: String,
    prefixValue: String,
    ipHint: String,
    prefixHint: String,
    enabled: Boolean,
    onIpChange: (String) -> Unit,
    onPrefixChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = ipValue,
            onValueChange = onIpChange,
            modifier = Modifier.weight(2f),
            singleLine = true,
            label = { Text(text = ipHint) },
            enabled = enabled
        )
        OutlinedTextField(
            value = prefixValue,
            onValueChange = onPrefixChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(text = prefixHint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled
        )
    }
}

private fun loadIpAndPrefixIntoFields(
    value: String,
    onLoaded: (String, String) -> Unit
) {
    if (value.isBlank()) {
        onLoaded("", "")
        return
    }
    val parts = value.split("/")
    val ip = parts.getOrNull(0).orEmpty()
    val prefix = parts.getOrNull(1).orEmpty()
    onLoaded(ip, prefix)
}

private fun combineIpAndPrefix(ip: String, prefix: String): String {
    if (ip.isBlank() && prefix.isBlank()) return ""
    return "$ip/$prefix"
}

private fun validateIpv4WithPrefix(ip: String, prefixText: String): Boolean {
    if (ip.isEmpty() && prefixText.isEmpty()) return true
    if (ip.isEmpty() || prefixText.isEmpty()) {
        Napier.w("IPv4 validation failed: both IP and prefix must be provided together")
        return false
    }

    return try {
        val addr = IPAddressString(ip).address
        if (addr == null) {
            Napier.w("IPv4 validation failed: invalid IP address format: $ip")
            return false
        }
        if (!addr.isIPv4) {
            Napier.w("IPv4 validation failed: not an IPv4 address: $ip")
            return false
        }

        val host = addr.toNormalizedString()
        if (!isRfc1918Ipv4(host)) {
            Napier.w(
                "IPv4 validation failed: not a private/unique local address (must be 10.x.x.x, 172.16-31.x.x, or 192.168.x.x): $host"
            )
            return false
        }

        val prefix = prefixText.toIntOrNull()
        if (prefix == null) {
            Napier.w("IPv4 validation failed: invalid prefix length: $prefixText")
            return false
        }
        if (prefix !in 0..32) {
            Napier.w("IPv4 validation failed: prefix out of range: $prefixText")
            return false
        }
        true
    } catch (e: Exception) {
        Napier.w("IPv4 validation failed: ${e.message}")
        false
    }
}

private fun validateIpv6WithPrefix(ip: String, prefixText: String): Boolean {
    if (ip.isEmpty() && prefixText.isEmpty()) return true
    if (ip.isEmpty() || prefixText.isEmpty()) {
        Napier.w("IPv6 validation failed: both IP and prefix must be provided together")
        return false
    }

    return try {
        val addr = IPAddressString(ip).address
        if (addr == null) {
            Napier.w("IPv6 validation failed: invalid IP address format: $ip")
            return false
        }
        if (!addr.isIPv6) {
            Napier.w("IPv6 validation failed: not an IPv6 address: $ip")
            return false
        }

        val host = addr.toNormalizedString()
        if (!isUlaIpv6(host)) {
            Napier.w(
                "IPv6 validation failed: not a unique local address (must start with fc or fd): $host"
            )
            return false
        }

        val prefix = prefixText.toIntOrNull()
        if (prefix == null) {
            Napier.w("IPv6 validation failed: invalid prefix length: $prefixText")
            return false
        }
        if (prefix !in 0..128) {
            Napier.w("IPv6 validation failed: prefix out of range: $prefixText")
            return false
        }
        true
    } catch (e: Exception) {
        Napier.w("IPv6 validation failed: ${e.message}")
        false
    }
}

private fun isRfc1918Ipv4(host: String): Boolean {
    if (host.startsWith("10.")) return true

    if (host.startsWith("172.")) {
        val parts = host.split(".")
        if (parts.size == 4) {
            val second = parts[1].toIntOrNull() ?: return false
            if (second in 16..31) return true
        }
    }

    if (host.startsWith("192.168.")) return true

    return false
}

private fun isUlaIpv6(host: String): Boolean {
    val lower = host.lowercase()
    return lower.startsWith("fc") || lower.startsWith("fd")
}

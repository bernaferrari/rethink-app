/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.celzero.bravedns.ui.dialog

import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.service.ConnectionMonitor
import com.celzero.bravedns.service.ConnectionMonitor.Companion.SCHEME_HTTP
import com.celzero.bravedns.service.ConnectionMonitor.Companion.SCHEME_HTTPS
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.firewall.RethinkRuleSheetModal
import com.celzero.bravedns.ui.compose.settings.RethinkNetworkReachabilityConfiguration
import com.celzero.bravedns.ui.compose.settings.RethinkNetworkReachabilityEditor
import com.celzero.bravedns.ui.compose.settings.RethinkNetworkReachabilityEditorStrings
import com.celzero.bravedns.ui.compose.settings.RethinkReachabilityProbeField
import com.celzero.bravedns.ui.compose.settings.RethinkReachabilityProbeStatus
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.util.Constants
import inet.ipaddr.IPAddress.IPVersion
import inet.ipaddr.IPAddressString
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val URL_SEGMENT4 = "#ipv4"
private const val URL_SEGMENT6 = "#ipv6"
private const val URL4 = "IPv4"
private const val URL6 = "IPv6"

/** Android service, validation, and preference adapter for the shared connectivity-probe editor. */
@Composable
fun NetworkReachabilitySheet(
    persistentState: PersistentState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaults = remember { defaultReachabilityConfiguration() }
    val initial = remember(
        persistentState.performAutoNetworkConnectivityChecks,
        persistentState.pingv4Ips,
        persistentState.pingv4Url,
        persistentState.pingv6Ips,
        persistentState.pingv6Url,
    ) { persistentState.toReachabilityConfiguration(defaults) }
    RethinkRuleSheetModal(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.screenPaddingHorizontal, vertical = Dimensions.spacingLg)
                .verticalScroll(rememberScrollState()),
        ) {
            val protocols = VpnController.protocols()
            RethinkNetworkReachabilityEditor(
                initialConfiguration = initial,
                defaultConfiguration = defaults,
                ipv4Supported = protocols.contains(URL4),
                ipv6Supported = protocols.contains(URL6),
                strings = RethinkNetworkReachabilityEditorStrings(
                    automatic = stringResource(R.string.settings_ip_text_ipv46),
                    manual = stringResource(R.string.lbl_manual),
                    description = stringResource(R.string.bypasses_network_restrictions),
                    ipv4 = stringResource(R.string.settings_ip_text_ipv4),
                    ipv6 = stringResource(R.string.settings_ip_text_ipv6),
                    restoreDefaults = stringResource(R.string.brbs_restore_title),
                    save = stringResource(R.string.lbl_save),
                    test = stringResource(R.string.lbl_test),
                    automaticIpv4Probe = "ip:ipv4 ${stringResource(R.string.lbl_auto)}",
                    automaticIpv6Probe = "ip:ipv6 ${stringResource(R.string.lbl_auto)}",
                ),
                onTest = { configuration -> probeReachability(configuration) },
                onSave = { configuration ->
                    saveReachabilityConfiguration(
                        persistentState = persistentState,
                        configuration = configuration,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.config_add_success_toast), Toast.LENGTH_LONG).show()
                            scope.launch(Dispatchers.IO) { VpnController.notifyConnectionMonitor() }
                            onDismiss()
                        },
                        validationError = context.getString(R.string.cd_dns_proxy_error_text_1),
                    )
                },
            )
        }
    }
}

private fun defaultReachabilityConfiguration() = RethinkNetworkReachabilityConfiguration(
    automatic = true,
    ipv4Ips = Constants.ip4probes.twoValues(),
    ipv4Urls = Constants.urlV4probes.map { it.removeSuffix(URL_SEGMENT4) }.twoValues(),
    ipv6Ips = Constants.ip6probes.twoValues(),
    ipv6Urls = Constants.urlV6probes.map { it.removeSuffix(URL_SEGMENT6) }.twoValues(),
)

private fun PersistentState.toReachabilityConfiguration(
    defaults: RethinkNetworkReachabilityConfiguration,
) = RethinkNetworkReachabilityConfiguration(
    automatic = performAutoNetworkConnectivityChecks,
    ipv4Ips = pingv4Ips.splitCsv(defaults.ipv4Ips),
    ipv4Urls = pingv4Url.splitCsv(defaults.ipv4Urls).map { it.removeSuffix(URL_SEGMENT4) },
    ipv6Ips = pingv6Ips.splitCsv(defaults.ipv6Ips),
    ipv6Urls = pingv6Url.splitCsv(defaults.ipv6Urls).map { it.removeSuffix(URL_SEGMENT6) },
)

private suspend fun probeReachability(
    configuration: RethinkNetworkReachabilityConfiguration,
): Map<RethinkReachabilityProbeField, RethinkReachabilityProbeStatus> = withContext(Dispatchers.IO) {
    val results = mutableMapOf<RethinkReachabilityProbeField, RethinkReachabilityProbeStatus>()
    val ipv4Primary = if (configuration.automatic) "${ConnectionMonitor.SCHEME_IP}:${ConnectionMonitor.PROTOCOL_V4}" else configuration.ipv4Ips.valueAt(0)
    val ipv4Secondary = if (configuration.automatic) "${ConnectionMonitor.SCHEME_HTTPS}:${ConnectionMonitor.PROTOCOL_V4}" else configuration.ipv4Ips.valueAt(1)
    val ipv6Primary = if (configuration.automatic) "${ConnectionMonitor.SCHEME_IP}:${ConnectionMonitor.PROTOCOL_V6}" else configuration.ipv6Ips.valueAt(0)
    val ipv6Secondary = if (configuration.automatic) "${ConnectionMonitor.SCHEME_HTTPS}:${ConnectionMonitor.PROTOCOL_V6}" else configuration.ipv6Ips.valueAt(1)
    results[RethinkReachabilityProbeField.Ipv4Primary] = probe(ipv4Primary, configuration.automatic)
    results[RethinkReachabilityProbeField.Ipv4Secondary] = probe(ipv4Secondary, configuration.automatic)
    if (!configuration.automatic) {
        results[RethinkReachabilityProbeField.Url4Primary] = probe(configuration.ipv4Urls.valueAt(0) + URL_SEGMENT4, false)
        results[RethinkReachabilityProbeField.Url4Secondary] = probe(configuration.ipv4Urls.valueAt(1) + URL_SEGMENT4, false)
    }
    results[RethinkReachabilityProbeField.Ipv6Primary] = probe(ipv6Primary, configuration.automatic)
    results[RethinkReachabilityProbeField.Ipv6Secondary] = probe(ipv6Secondary, configuration.automatic)
    if (!configuration.automatic) {
        results[RethinkReachabilityProbeField.Url6Primary] = probe(configuration.ipv6Urls.valueAt(0) + URL_SEGMENT6, false)
        results[RethinkReachabilityProbeField.Url6Secondary] = probe(configuration.ipv6Urls.valueAt(1) + URL_SEGMENT6, false)
    }
    results
}

private suspend fun probe(value: String, automatic: Boolean): RethinkReachabilityProbeStatus {
    val result = runCatching { VpnController.probeIpOrUrl(value, automatic) }.getOrNull()
    if (result?.ok != true) return RethinkReachabilityProbeStatus.Failure
    return when {
        result.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> RethinkReachabilityProbeStatus.Wifi
        result.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> RethinkReachabilityProbeStatus.Cellular
        else -> RethinkReachabilityProbeStatus.Success
    }
}

private fun saveReachabilityConfiguration(
    persistentState: PersistentState,
    configuration: RethinkNetworkReachabilityConfiguration,
    onSuccess: () -> Unit,
    validationError: String,
): String? {
    if (!configuration.automatic && !configuration.isValid()) return validationError
    val ipv4Ips = configuration.ipv4Ips.twoValues().joinToString(",")
    val ipv6Ips = configuration.ipv6Ips.twoValues().joinToString(",")
    val ipv4Urls = configuration.ipv4Urls.twoValues().joinToString(",") { it.withSegment(URL_SEGMENT4) }
    val ipv6Urls = configuration.ipv6Urls.twoValues().joinToString(",") { it.withSegment(URL_SEGMENT6) }
    val unchanged = persistentState.performAutoNetworkConnectivityChecks == configuration.automatic &&
        persistentState.pingv4Ips == ipv4Ips && persistentState.pingv6Ips == ipv6Ips &&
        persistentState.pingv4Url == ipv4Urls && persistentState.pingv6Url == ipv6Urls
    if (!unchanged) {
        persistentState.performAutoNetworkConnectivityChecks = configuration.automatic
        persistentState.pingv4Ips = ipv4Ips
        persistentState.pingv6Ips = ipv6Ips
        persistentState.pingv4Url = ipv4Urls
        persistentState.pingv6Url = ipv6Urls
    }
    onSuccess()
    return null
}

private fun RethinkNetworkReachabilityConfiguration.isValid() =
    ipv4Ips.twoValues().all { isValidIp(it, IPVersion.IPV4) } &&
        ipv6Ips.twoValues().all { isValidIp(it, IPVersion.IPV6) } &&
        ipv4Urls.twoValues().all(::isValidUrl) && ipv6Urls.twoValues().all(::isValidUrl)

private fun isValidIp(value: String, type: IPVersion): Boolean = runCatching {
    val address = IPAddressString(value).toAddress()
    if (type.isIPv4) address.isIPv4 else address.isIPv6
}.getOrDefault(false)

private fun isValidUrl(value: String): Boolean = try {
    val parsed = URL(value)
    (parsed.protocol == SCHEME_HTTPS || parsed.protocol == SCHEME_HTTP) && parsed.host.isNotEmpty() &&
        parsed.query == null && parsed.ref == null
} catch (_: MalformedURLException) {
    false
}

private fun List<String>.twoValues() = List(2) { getOrNull(it).orEmpty() }
private fun String.splitCsv(fallback: List<String>) = ifBlank { fallback.joinToString(",") }.split(",").twoValues()
private fun List<String>.valueAt(index: Int) = getOrNull(index).orEmpty()
private fun String.withSegment(segment: String) = if (contains(segment)) this else this + segment

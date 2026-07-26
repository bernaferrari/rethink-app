package com.bernaferrari.bravedns.ui.compose.rpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.DnsMode
import com.bernaferrari.bravedns.service.PersistentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android repository and preference adapter for the common RPN server-settings UI. */
@Composable
fun RpnServerSettingsScreen(persistentState: PersistentState, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dnsModes by remember {
        mutableStateOf<Set<RethinkRpnDnsMode>>(
            DnsMode.setFromCsv(persistentState.rpnDnsTunTypes).map { it.toRethinkMode() }.toSet(),
        )
    }
    var excluded by remember { mutableStateOf(persistentState.rpnAutoExcludedCcs.split(',').filter(String::isNotBlank).toSet()) }
    var countries by remember { mutableStateOf<List<CountryConfig>>(emptyList()) }
    var manualConfiguration by remember { mutableStateOf(persistentState.rpnConfigHandlingManual) }
    var alwaysChangeIdentity by remember { mutableStateOf(persistentState.rpnAlwaysChangeIdentity) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        countries = withContext(Dispatchers.IO) {
            RpnProxyManager.getWinServers()
                .filter { it.cc.isNotBlank() }
                .distinctBy { it.cc }
                .sortedBy { it.name }
        }
    }

    RethinkRpnServerSettingsScreen(
        selectedDnsModes = dnsModes,
        manualConfiguration = manualConfiguration,
        alwaysChangeIdentity = alwaysChangeIdentity,
        excludedCountries = excluded,
        countries = countries.map { RethinkRpnCountryOption(it.cc, it.name.ifBlank { it.cc }) },
        working = working,
        message = message,
        strings = RethinkRpnServerSettingsStrings(
            title = stringResource(R.string.rpn_server_settings_title),
            dnsFilteringTitle = stringResource(R.string.rpn_server_dns_filtering_title),
            dnsFilteringDescription = stringResource(R.string.rpn_server_dns_filtering_desc),
            defaultDnsMode = stringResource(R.string.rpn_server_dns_mode_default),
            privacyDnsMode = stringResource(R.string.rpn_server_dns_mode_privacy),
            parentalDnsMode = stringResource(R.string.rpn_server_dns_mode_parental),
            securityDnsMode = stringResource(R.string.rpn_server_dns_mode_security),
            configurationTitle = stringResource(R.string.rpn_server_configuration_title),
            configurationDescription = stringResource(R.string.rpn_server_configuration_desc),
            manualTitle = stringResource(R.string.rpn_server_manual_configuration),
            manualDescription = stringResource(R.string.rpn_server_manual_configuration_desc),
            changeIdentityTitle = stringResource(R.string.rpn_server_change_identity),
            changeIdentityDescription = stringResource(R.string.rpn_server_change_identity_desc),
            exclusionsTitle = stringResource(R.string.rpn_server_auto_exclusions),
            noExclusions = stringResource(R.string.rpn_server_no_exclusions),
            exclusionsCount = { count -> context.getString(R.string.rpn_server_exclusions_count, count) },
            maintenanceTitle = stringResource(R.string.rpn_server_maintenance_title),
            maintenanceDescription = stringResource(R.string.rpn_server_maintenance_desc),
            resetTitle = stringResource(R.string.rpn_server_reset_configuration),
            resetDescription = stringResource(R.string.rpn_server_reset_configuration_desc),
            resetConfirmationTitle = stringResource(R.string.rpn_server_reset_confirmation_title),
            resetConfirmationDescription = stringResource(R.string.rpn_server_reset_confirmation_desc),
            excludeLocationsTitle = stringResource(R.string.rpn_server_exclude_locations),
            save = stringResource(R.string.lbl_save),
            cancel = stringResource(R.string.lbl_cancel),
        ),
        onDnsModesChange = { selected ->
            dnsModes = selected
            persistentState.rpnDnsTunTypes = DnsMode.tunTypesFromSet(selected.mapTo(mutableSetOf()) { it.toDnsMode() })
        },
        onManualConfigurationChange = { enabled ->
            manualConfiguration = enabled
            persistentState.rpnConfigHandlingManual = enabled
        },
        onAlwaysChangeIdentityChange = { enabled ->
            alwaysChangeIdentity = enabled
            persistentState.rpnAlwaysChangeIdentity = enabled
        },
        onExcludedCountriesChange = { selected ->
            excluded = selected
            persistentState.rpnAutoExcludedCcs = selected.sorted().joinToString(",")
        },
        onReset = {
            working = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { RpnProxyManager.resetAndRefetchRpn() } }
                    .onFailure { message = it.message ?: context.getString(R.string.rpn_server_reset_failed) }
                working = false
            }
        },
        onBackClick = onBackClick,
    )
}

private fun DnsMode.toRethinkMode() = when (this) {
    DnsMode.DEFAULT -> RethinkRpnDnsMode.Default
    DnsMode.PRIVACY -> RethinkRpnDnsMode.Privacy
    DnsMode.PARENTAL -> RethinkRpnDnsMode.Parental
    DnsMode.SECURITY -> RethinkRpnDnsMode.Security
}

private fun RethinkRpnDnsMode.toDnsMode() = when (this) {
    RethinkRpnDnsMode.Default -> DnsMode.DEFAULT
    RethinkRpnDnsMode.Privacy -> DnsMode.PRIVACY
    RethinkRpnDnsMode.Parental -> DnsMode.PARENTAL
    RethinkRpnDnsMode.Security -> DnsMode.SECURITY
}

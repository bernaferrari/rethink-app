/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.dns.RethinkDnsCapability.Anonymous
import com.bernaferrari.bravedns.ui.compose.dns.RethinkDnsCapability.Fast
import com.bernaferrari.bravedns.ui.compose.dns.RethinkDnsCapability.Private
import com.bernaferrari.bravedns.ui.compose.dns.RethinkDnsCapability.Secure
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android status probe and destination mapper for the shared DNS protocol chooser. */
@Composable
fun DnsListScreen(
    appConfig: AppConfig,
    onConfigureOtherDns: (Int) -> Unit,
    onConfigureRethinkBasic: (Int) -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    var selectedType by remember { mutableStateOf<AppConfig.DnsType?>(null) }
    var selectedWorking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val (dnsType, isWorking) = withContext(Dispatchers.IO) {
            val backend = if (appConfig.isSmartDnsEnabled()) Backend.Plus else Backend.Preferred
            val working = when (Transaction.Status.fromId(VpnController.getDnsStatus(backend) ?: -1)) {
                Transaction.Status.COMPLETE, Transaction.Status.START -> true
                else -> false
            }
            appConfig.getDnsType() to working
        }
        selectedType = dnsType
        selectedWorking = isWorking
    }
    RethinkDnsListScreen(
        protocols = androidDnsProtocols(),
        selectedProtocolId = selectedType?.type,
        selectedProtocolWorking = selectedWorking,
        strings = RethinkDnsListStrings(
            title = stringResource(R.string.lbl_dns_servers),
            subtitle = stringResource(R.string.dns_desc),
            configure = stringResource(R.string.lbl_configure),
            fast = stringResource(R.string.lbl_fast),
            private = stringResource(R.string.lbl_private),
            secure = stringResource(R.string.lbl_secure),
            anonymous = stringResource(R.string.lbl_anonymous),
        ),
        onProtocolClick = { protocol ->
            when (protocol.id) {
                AppConfig.DnsType.DOH.type -> onConfigureOtherDns(DnsScreenType.DOH.index)
                AppConfig.DnsType.DOT.type -> onConfigureOtherDns(DnsScreenType.DOT.index)
                AppConfig.DnsType.DNSCRYPT.type -> onConfigureOtherDns(DnsScreenType.DNS_CRYPT.index)
                AppConfig.DnsType.DNS_PROXY.type -> onConfigureOtherDns(DnsScreenType.DNS_PROXY.index)
                AppConfig.DnsType.ODOH.type -> onConfigureOtherDns(DnsScreenType.ODOH.index)
                AppConfig.DnsType.RETHINK_REMOTE.type -> onConfigureRethinkBasic(0)
            }
        },
        onBackClick = onBackClick,
    )
}

@Composable
private fun androidDnsProtocols() = listOf(
    RethinkDnsProtocol(AppConfig.DnsType.DOH.type, stringResource(R.string.dc_doh), stringResource(R.string.cd_custom_doh_url_name_default), listOf(Private, Secure), MaterialSymbols.Filled.Language),
    RethinkDnsProtocol(AppConfig.DnsType.DOT.type, stringResource(R.string.lbl_dot_abbr), stringResource(R.string.lbl_dot), listOf(Private, Secure), MaterialSymbols.Filled.Shield),
    RethinkDnsProtocol(AppConfig.DnsType.DNSCRYPT.type, stringResource(R.string.dc_dns_crypt), stringResource(R.string.cd_dns_crypt_name_default), listOf(Private, Secure, Anonymous), MaterialSymbols.Filled.VpnKey),
    RethinkDnsProtocol(AppConfig.DnsType.DNS_PROXY.type, stringResource(R.string.lbl_dp_abbr), stringResource(R.string.lbl_dp), listOf(Fast), MaterialSymbols.Filled.Dns),
    RethinkDnsProtocol(AppConfig.DnsType.ODOH.type, stringResource(R.string.lbl_odoh_abbr), stringResource(R.string.lbl_odoh), listOf(Private, Secure, Anonymous), MaterialSymbols.Filled.ShieldMoon),
    RethinkDnsProtocol(AppConfig.DnsType.RETHINK_REMOTE.type, stringResource(R.string.dc_rethink_dns_radio), stringResource(R.string.lbl_rdns), listOf(Fast, Private, Secure), MaterialSymbols.Filled.Security),
)

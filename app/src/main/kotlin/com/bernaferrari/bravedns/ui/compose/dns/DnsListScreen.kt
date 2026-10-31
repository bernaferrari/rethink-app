/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsCryptEndpoint
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.bernaferrari.bravedns.database.DoTEndpoint
import com.bernaferrari.bravedns.database.ODoHEndpoint
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.viewmodel.DnsCryptEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsProxyEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DoHEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DoTEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.ODoHEndpointViewModel

/**
 * "Another provider": every DoH, DoT, DNSCrypt, DNS proxy and ODoH server in one list, grouped by
 * provider, each row the same endpoint row (and the same selection call) as its protocol's list.
 * Rethink DNS isn't here: it is its own choice on the DNS screen.
 */
@Composable
fun DnsListScreen(
    appConfig: AppConfig,
    persistentState: PersistentState,
    dohViewModel: DoHEndpointViewModel,
    dotViewModel: DoTEndpointViewModel,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    oDohViewModel: ODoHEndpointViewModel,
    onConfigureOtherDns: (Int) -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val doh = dohViewModel.dohEndpointList.collectAsLazyPagingItems()
    val dot = dotViewModel.dohEndpointList.collectAsLazyPagingItems()
    val proxy = dnsProxyViewModel.dnsProxyEndpointList.collectAsLazyPagingItems()
    val crypt = dnsCryptViewModel.dnsCryptEndpointList.collectAsLazyPagingItems()
    val odoh = oDohViewModel.dohEndpointList.collectAsLazyPagingItems()
    val feeds = listOf(doh, dot, proxy, crypt, odoh)
    // Wait for every table before fixing the order, or late tables would land at the end.
    val loaded = feeds.all { it.loadState.refresh is LoadState.NotLoading }

    val yours = stringResource(R.string.dns_providers_yours)
    val plain = stringResource(R.string.dns_protocol_plain)
    val servers = if (loaded) {
        doh.all().map { DnsServer.Doh(it) } +
            dot.all().map { DnsServer.Dot(it) } +
            crypt.all().map { DnsServer.DnsCrypt(it) } +
            odoh.all().map { DnsServer.ODoh(it) } +
            proxy.all().map { DnsServer.Proxy(it) }
    } else {
        emptyList()
    }
    val byKey = servers.associateBy { it.key }
    val order = rememberStableOrder(sortForEntry(servers).map { it.key })
    val groups = order.mapNotNull { byKey[it] }
        .groupBy { if (it.isCustom) YOURS_KEY else dnsProviderKey(it.name) }
        .map { (groupKey, items) ->
            RethinkDnsProviderGroup(
                title = if (groupKey == YOURS_KEY) yours else dnsProviderDisplayName(items.first().name),
                keys = items.map { it.key },
            )
        }

    var addType by remember { mutableStateOf<DnsScreenType?>(null) }
    val protocols = androidDnsProtocols()
    RethinkDnsListScreen(
        groups = groups,
        protocols = protocols,
        strings = RethinkDnsListStrings(
            title = stringResource(R.string.lbl_dns_servers),
            subtitle = stringResource(R.string.dns_providers_subtitle),
            add = stringResource(R.string.dns_providers_add),
            addTitle = stringResource(R.string.dns_providers_add_title),
            addDescription = stringResource(R.string.dns_providers_add_desc),
            byProtocol = stringResource(R.string.dns_providers_by_protocol),
            byProtocolDescription = stringResource(R.string.dns_providers_by_protocol_desc),
            loading = stringResource(R.string.dns_providers_loading),
        ),
        loading = !loaded,
        onProtocolClick = { onConfigureOtherDns(it.id) },
        onAddProtocol = { addType = DnsScreenType.fromIndex(it.id) },
        onBackClick = onBackClick,
    ) { key ->
        when (val server = byKey[key]) {
            is DnsServer.Doh -> DoHEndpointRow(server.endpoint, appConfig, badge = "DoH")
            is DnsServer.Dot -> DoTEndpointRow(server.endpoint, appConfig, badge = "DoT")
            is DnsServer.DnsCrypt -> DnsCryptRow(server.endpoint, appConfig, badge = "DNSCrypt")
            is DnsServer.ODoh -> ODoHEndpointRow(server.endpoint, appConfig, badge = "ODoH")
            is DnsServer.Proxy -> DnsProxyEndpointRow(server.endpoint, appConfig, badge = plain)
            null -> Unit
        }
    }

    addType?.let { type ->
        AddDnsEndpointDialog(type, appConfig, persistentState, scope, onDismiss = { addType = null })
    }
}

private const val YOURS_KEY = "\u0000yours"

/** Every loaded row of a feed (the endpoint tables are small; the first page holds them all). */
private fun <T : Any> LazyPagingItems<T>.all(): List<T> = (0 until itemCount).mapNotNull { get(it) }

/** One server of any protocol, keyed across tables. */
private sealed class DnsServer(val key: String, val name: String, val isCustom: Boolean, val selected: Boolean, val protocolRank: Int) {
    class Doh(val endpoint: DoHEndpoint) : DnsServer("doh:${endpoint.id}", endpoint.dohName, endpoint.isCustom, endpoint.isSelected, 0)
    class Dot(val endpoint: DoTEndpoint) : DnsServer("dot:${endpoint.id}", endpoint.name, endpoint.isCustom, endpoint.isSelected, 1)
    class DnsCrypt(val endpoint: DnsCryptEndpoint) : DnsServer("crypt:${endpoint.id}", endpoint.dnsCryptName, endpoint.isCustom, endpoint.isSelected, 2)
    class ODoh(val endpoint: ODoHEndpoint) : DnsServer("odoh:${endpoint.id}", endpoint.name, endpoint.isCustom, endpoint.isSelected, 3)
    class Proxy(val endpoint: DnsProxyEndpoint) : DnsServer("proxy:${endpoint.id}", endpoint.proxyName, endpoint.isCustom, endpoint.isSelected, 4)
}

/**
 * The order on entry: the provider in use first, then providers A–Z, your own servers last;
 * inside a provider the server in use first, then by name and protocol.
 */
private fun sortForEntry(servers: List<DnsServer>): List<DnsServer> {
    fun groupOf(server: DnsServer) = if (server.isCustom) YOURS_KEY else dnsProviderKey(server.name)
    val selectedGroup = servers.firstOrNull { it.selected }?.let(::groupOf)
    return servers.sortedWith(
        compareBy<DnsServer> { if (groupOf(it) == selectedGroup) 0 else 1 }
            .thenBy { if (it.isCustom) 1 else 0 }
            .thenBy { groupOf(it) }
            .thenBy { if (it.selected) 0 else 1 }
            .thenBy { it.name.lowercase() }
            .thenBy { it.protocolRank },
    )
}

@Composable
private fun androidDnsProtocols() = listOf(
    RethinkDnsProtocol(DnsScreenType.DOH.index, stringResource(R.string.dc_doh), stringResource(R.string.dns_protocol_doh_desc), MaterialSymbols.Filled.Public),
    RethinkDnsProtocol(DnsScreenType.DOT.index, stringResource(R.string.lbl_dot_abbr), stringResource(R.string.dns_protocol_dot_desc), MaterialSymbols.Filled.Shield),
    RethinkDnsProtocol(DnsScreenType.DNS_CRYPT.index, stringResource(R.string.dc_dns_crypt), stringResource(R.string.dns_protocol_dnscrypt_desc), MaterialSymbols.Filled.VpnKey),
    RethinkDnsProtocol(DnsScreenType.ODOH.index, stringResource(R.string.lbl_odoh_abbr), stringResource(R.string.dns_protocol_odoh_desc), MaterialSymbols.Filled.ShieldMoon),
    RethinkDnsProtocol(DnsScreenType.DNS_PROXY.index, stringResource(R.string.lbl_dp_abbr), stringResource(R.string.dns_protocol_proxy_desc), MaterialSymbols.Filled.Dns),
)

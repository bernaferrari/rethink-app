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
package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.components.DnsCryptRow
import com.bernaferrari.bravedns.ui.components.DnsProxyEndpointRow
import com.bernaferrari.bravedns.ui.components.DoHEndpointRow
import com.bernaferrari.bravedns.ui.components.DoTEndpointRow
import com.bernaferrari.bravedns.ui.components.ODoHEndpointRow
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.DnsCryptEndpoint
import com.bernaferrari.bravedns.database.DnsCryptRelayEndpoint
import com.bernaferrari.bravedns.database.DnsProxyEndpoint
import com.bernaferrari.bravedns.database.DoHEndpoint
import com.bernaferrari.bravedns.database.DoTEndpoint
import com.bernaferrari.bravedns.database.ODoHEndpoint
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.util.UIUtils
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.DnsCryptEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsCryptRelayEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DnsProxyEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DoHEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.DoTEndpointViewModel
import com.bernaferrari.bravedns.viewmodel.ODoHEndpointViewModel
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL

enum class DnsScreenType(val index: Int) {
    DOH(0),
    DNS_PROXY(1),
    DNS_CRYPT(2),
    DOT(3),
    ODOH(4);

    companion object {
        fun fromIndex(index: Int): DnsScreenType {
            return entries.find { it.index == index } ?: DOH
        }
    }
}

@Composable
fun ConfigureOtherDnsScreen(
    dnsType: DnsScreenType,
    appConfig: AppConfig,
    persistentState: PersistentState,
    dohViewModel: DoHEndpointViewModel,
    dotViewModel: DoTEndpointViewModel,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel,
    oDohViewModel: ODoHEndpointViewModel,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    RethinkEndpointConfigurationScaffold(
        title = getDnsTypeName(dnsType),
        subtitle = getDnsTypeSubtitle(dnsType),
        onBackClick = onBackClick,
    ) { paddingValues ->
        OtherDnsListContent(
            dnsType = dnsType,
            paddingValues = paddingValues,
            appConfig = appConfig,
            persistentState = persistentState,
            dohViewModel = dohViewModel,
            dotViewModel = dotViewModel,
            dnsProxyViewModel = dnsProxyViewModel,
            dnsCryptViewModel = dnsCryptViewModel,
            dnsCryptRelayViewModel = dnsCryptRelayViewModel,
            oDohViewModel = oDohViewModel,
            scope = scope,
        )
    }
}

@Composable
private fun getDnsTypeName(type: DnsScreenType): String {
    return when (type) {
        DnsScreenType.DOH -> stringResource(R.string.other_dns_list_tab1)
        DnsScreenType.DNS_CRYPT -> stringResource(R.string.dc_dns_crypt)
        DnsScreenType.DNS_PROXY -> stringResource(R.string.other_dns_list_tab3)
        DnsScreenType.DOT -> stringResource(R.string.lbl_dot)
        DnsScreenType.ODOH -> stringResource(R.string.lbl_odoh)
    }
}

@Composable
private fun getDnsTypeSubtitle(type: DnsScreenType): String {
    return when (type) {
        DnsScreenType.DOH ->
            stringResource(R.string.cd_doh_dialog_resolver_url)
        DnsScreenType.DNS_CRYPT ->
            stringResource(R.string.cd_dns_crypt_dialog_stamp)
        DnsScreenType.DNS_PROXY ->
            stringResource(R.string.dns_proxy_ip_address)
        DnsScreenType.DOT ->
            stringResource(R.string.lbl_dot_abbr)
        DnsScreenType.ODOH ->
            stringResource(R.string.lbl_odoh_abbr)
    }
}

@Composable
private fun OtherDnsListContent(
    dnsType: DnsScreenType,
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    persistentState: PersistentState,
    dohViewModel: DoHEndpointViewModel,
    dotViewModel: DoTEndpointViewModel,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel,
    oDohViewModel: ODoHEndpointViewModel,
    scope: CoroutineScope
) {
    when (dnsType) {
        DnsScreenType.DOH -> DohListContent(paddingValues, appConfig, dohViewModel, scope)
        DnsScreenType.DNS_PROXY -> DnsProxyListContent(
            paddingValues,
            appConfig,
            persistentState,
            dnsProxyViewModel,
            scope
        )

        DnsScreenType.DNS_CRYPT -> DnsCryptListContent(
            paddingValues,
            appConfig,
            dnsCryptViewModel,
            dnsCryptRelayViewModel,
            scope
        )

        DnsScreenType.DOT -> DotListContent(paddingValues, appConfig, dotViewModel, scope)
        DnsScreenType.ODOH -> OdohListContent(paddingValues, appConfig, oDohViewModel, scope)
    }
}

@Composable
private fun <T : Any> DnsEndpointListWithFab(
    paddingValues: PaddingValues,
    items: LazyPagingItems<T>,
    onFabClick: () -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    RethinkEndpointListWithAdd(
        feed = AndroidEndpointFeed(items),
        createLabel = stringResource(R.string.lbl_create),
        onCreate = onFabClick,
        contentPadding = paddingValues,
        itemContent = itemContent,
    )
}

private class AndroidEndpointFeed<T : Any>(private val items: LazyPagingItems<T>) : RethinkEndpointFeed<T> {
    override val itemCount: Int get() = items.itemCount
    override fun get(index: Int): T? = items[index]
}

@Composable
private fun DohListContent(
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    dohViewModel: DoHEndpointViewModel,
    scope: CoroutineScope
) {
    val items = dohViewModel.dohEndpointList.collectAsLazyPagingItems()
    var showDialog by remember { mutableStateOf(false) }
    val heading = stringResource(R.string.cd_doh_dialog_heading)
    val nameLabel = stringResource(R.string.cd_doh_dialog_resolver_name)
    val urlLabel = stringResource(R.string.cd_doh_dialog_resolver_url)
    val defaultName = stringResource(R.string.cd_custom_doh_url_name_default)
    val dohNameTemplate = stringResource(R.string.cd_custom_doh_url_name)
    val checkboxLabel = stringResource(R.string.cd_doh_dialog_checkbox_desc)
    val invalidUrlMessage = stringResource(R.string.custom_url_error_invalid_url)
    var dialogName by remember { mutableStateOf(defaultName) }

    DnsEndpointListWithFab(
        paddingValues = paddingValues,
        items = items,
        onFabClick = {
            scope.launch {
                dialogName = withContext(Dispatchers.IO) { String.format(dohNameTemplate, appConfig.getDohCount().plus(1).toString()) }
                showDialog = true
            }
        }
    ) { endpoint ->
        DoHEndpointRow(endpoint, appConfig)
    }

    if (showDialog) {
        RethinkEndpointEditorDialog(onDismiss = { showDialog = false }) {
            RethinkUrlEndpointEditor(
                title = heading,
                nameLabel = nameLabel,
                endpointLabel = urlLabel,
                defaultName = dialogName,
                initialEndpoint = "https://",
                insecureLabel = checkboxLabel,
                strings = RethinkEndpointEditorStrings(stringResource(R.string.lbl_cancel), stringResource(R.string.lbl_add)),
                onSubmit = { name, url, isSecure ->
                    if (checkUrl(url)) {
                        scope.launch(Dispatchers.IO) {
                            insertDoHEndpoint(appConfig, name, url, isSecure)
                        }
                        showDialog = false
                        null
                    } else {
                        invalidUrlMessage
                    }
                },
                onDismiss = { showDialog = false }
            )
        }
    }
}

private suspend fun insertDoHEndpoint(appConfig: AppConfig, name: String, url: String, isSecure: Boolean) {
    var dohName = name
    if (name.isBlank()) {
        dohName = url
    }
    val doHEndpoint = DoHEndpoint(
        id = 0,
        dohName,
        url,
        dohExplanation = "",
        isSelected = false,
        isCustom = true,
        isSecure = isSecure,
        modifiedDataTime = 0,
        latency = 0
    )
    appConfig.insertDohEndpoint(doHEndpoint)
}

private fun checkUrl(url: String): Boolean {
    return try {
        val parsed = URL(url)
        parsed.protocol == "https" &&
                parsed.host.isNotEmpty() &&
                parsed.path.isNotEmpty() &&
                parsed.query == null &&
                parsed.ref == null
    } catch (e: MalformedURLException) {
        false
    }
}

@Composable
private fun DotListContent(
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    dotViewModel: DoTEndpointViewModel,
    scope: CoroutineScope
) {
    val items = dotViewModel.dohEndpointList.collectAsLazyPagingItems()
    var showDialog by remember { mutableStateOf(false) }
    val heading = stringResource(
        R.string.two_argument_space,
        stringResource(R.string.lbl_add).replaceFirstChar(Char::titlecase),
        stringResource(R.string.lbl_dot)
    )
    val nameLabel = stringResource(R.string.cd_doh_dialog_resolver_name)
    val urlLabel = stringResource(R.string.cd_doh_dialog_resolver_url)
    val dotName = stringResource(R.string.lbl_dot)
    val checkboxLabel = stringResource(R.string.cd_doh_dialog_checkbox_desc)
    var dialogName by remember { mutableStateOf(dotName) }

    DnsEndpointListWithFab(
        paddingValues = paddingValues,
        items = items,
        onFabClick = {
            scope.launch {
                dialogName = withContext(Dispatchers.IO) { dotName + appConfig.getDoTCount().plus(1).toString() }
                showDialog = true
            }
        }
    ) { endpoint ->
        DoTEndpointRow(endpoint, appConfig)
    }

    if (showDialog) {
        val title = heading
        RethinkEndpointEditorDialog(onDismiss = { showDialog = false }) {
            RethinkUrlEndpointEditor(
                title = title,
                nameLabel = nameLabel,
                endpointLabel = urlLabel,
                defaultName = dialogName,
                initialEndpoint = "",
                insecureLabel = checkboxLabel,
                strings = RethinkEndpointEditorStrings(stringResource(R.string.lbl_cancel), stringResource(R.string.lbl_add)),
                onSubmit = { name, url, isSecure ->
                    scope.launch(Dispatchers.IO) {
                        insertDotEndpoint(appConfig, name, url, isSecure)
                    }
                    showDialog = false
                    null
                },
                onDismiss = { showDialog = false }
            )
        }
    }
}

private suspend fun insertDotEndpoint(appConfig: AppConfig, name: String, url: String, isSecure: Boolean) {
    var dotName = name
    if (name.isBlank()) {
        dotName = url
    }
    val endpoint = DoTEndpoint(
        id = 0,
        dotName,
        url,
        desc = "",
        isSelected = false,
        isCustom = true,
        isSecure = isSecure,
        modifiedDataTime = 0,
        latency = 0
    )
    appConfig.insertDoTEndpoint(endpoint)
}

@Composable
private fun DnsProxyListContent(
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    persistentState: PersistentState,
    dnsProxyViewModel: DnsProxyEndpointViewModel,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val items = dnsProxyViewModel.dnsProxyEndpointList.collectAsLazyPagingItems()
    var showDialog by remember { mutableStateOf(false) }
    var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextIndex by remember { mutableStateOf(0) }
    val defaultAppName = stringResource(R.string.settings_app_list_default_app)

    DnsEndpointListWithFab(
        paddingValues = paddingValues,
        items = items,
        onFabClick = {
            scope.launch {
                val names = withContext(Dispatchers.IO) {
                    val list: MutableList<String> = ArrayList()
                    list.add(defaultAppName)
                    list.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(context))
                    list
                }
                appNames = names
                nextIndex = appConfig.getDnsProxyCount().plus(1)
                showDialog = true
            }
        }
    ) { endpoint ->
        DnsProxyEndpointRow(endpoint, appConfig)
    }

    if (showDialog && appNames.isNotEmpty()) {
        val invalidIpMessage = stringResource(R.string.cd_dns_proxy_error_text_1)
        val invalidPortMessage = stringResource(R.string.cd_dns_proxy_error_text_3)
        val invalidLanPortMessage = stringResource(R.string.cd_dns_proxy_error_text_2)
        val mode = stringResource(R.string.cd_dns_proxy_mode_external)
        RethinkEndpointEditorDialog(onDismiss = { showDialog = false }) {
            RethinkDnsProxyEndpointEditor(
                appNames = appNames,
                defaultName = String.format(stringResource(R.string.cd_custom_dns_proxy_name), nextIndex.toString()),
                defaultIpAddress = stringResource(R.string.cd_custom_dns_proxy_default_ip),
                initialExcludeApps = !persistentState.excludeAppsInProxy,
                isLockdown = VpnController.isVpnLockdown(),
                strings = RethinkDnsProxyEditorStrings(
                    title = stringResource(R.string.dns_proxy_dialog_header_dns),
                    lockdownMessage = stringResource(R.string.settings_lock_down_mode_desc),
                    app = stringResource(R.string.settings_dns_proxy_dialog_app),
                    name = stringResource(R.string.dns_proxy_name),
                    ipAddress = stringResource(R.string.dns_proxy_ip_address),
                    port = stringResource(R.string.dns_proxy_port),
                    excludeApps = stringResource(R.string.settings_exclude_proxy_apps_heading),
                    cancel = stringResource(R.string.lbl_cancel),
                    add = stringResource(R.string.lbl_add),
                ),
                onOpenVpnProfile = { showDialog = false; UIUtils.openVpnProfile(context) },
                onSubmit = proxySubmit@{ input ->
                    val ipAddresses = input.ipAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (ipAddresses.isEmpty()) return@proxySubmit invalidIpMessage
                    val invalidIps = ipAddresses.filterNot { IPAddressString(it).isIPAddress }
                    if (invalidIps.isNotEmpty()) return@proxySubmit "$invalidIpMessage: ${invalidIps.joinToString(", ")}"
                    val port = input.port.toIntOrNull() ?: return@proxySubmit invalidPortMessage
                    if (ipAddresses.any { Utilities.isLanIpv4(it) && !Utilities.isValidLocalPort(port) }) {
                        return@proxySubmit invalidLanPortMessage
                    }
                    scope.launch(Dispatchers.IO) {
                        insertDNSProxyEndpointDB(
                            context = context,
                            appConfig = appConfig,
                            mode = mode,
                            name = input.name,
                            appName = input.appName,
                            ip = ipAddresses.joinToString(","),
                            port = port,
                            defaultApp = appNames.firstOrNull().orEmpty(),
                            isInternalMode = false,
                        )
                    }
                    persistentState.excludeAppsInProxy = !input.excludeApps
                    showDialog = false
                    null
                },
                onDismiss = { showDialog = false },
            )
        }
    }
}

private suspend fun insertDNSProxyEndpointDB(
    context: android.content.Context,
    appConfig: AppConfig,
    mode: String,
    name: String,
    appName: String?,
    ip: String,
    port: Int,
    defaultApp: String,
    isInternalMode: Boolean
) {
    if (appName == null) return

    val packageName = if (appName == defaultApp) {
        ""
    } else {
        FirewallManager.getPackageNameByAppName(appName) ?: ""
    }
    var proxyName = name
    if (proxyName.isBlank()) {
        proxyName = if (isInternalMode) {
            appName
        } else ip
    }
    val endpoint = DnsProxyEndpoint(
        id = 0,
        proxyName,
        mode,
        packageName,
        ip,
        port,
        isSelected = false,
        isCustom = true,
        modifiedDataTime = 0L,
        latency = 0
    )
    appConfig.insertDnsproxyEndpoint(endpoint)
}

@Composable
private fun DnsCryptListContent(
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    dnsCryptViewModel: DnsCryptEndpointViewModel,
    dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel,
    scope: CoroutineScope
) {
    val items = dnsCryptViewModel.dnsCryptEndpointList.collectAsLazyPagingItems()
    var showDialog by remember { mutableStateOf(false) }
    var showRelaysDialog by remember { mutableStateOf(false) }
    val resolverNameTemplate = stringResource(R.string.cd_dns_crypt_name)
    val relayNameTemplate = stringResource(R.string.cd_dns_crypt_relay_name)
    var resolverDialogName by remember { mutableStateOf("") }
    var relayDialogName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RethinkDnsCryptRelayShortcut(
            sectionTitle = stringResource(R.string.cd_dns_crypt_title),
            relayTitle = stringResource(R.string.cd_dnscrypt_relay_heading),
            configureLabel = stringResource(R.string.lbl_configure),
            onConfigure = { showRelaysDialog = true },
        )
        DnsEndpointListWithFab(
            paddingValues = PaddingValues(0.dp),
            items = items,
            onFabClick = {
                scope.launch {
                    val names = withContext(Dispatchers.IO) {
                        String.format(resolverNameTemplate, appConfig.getDnscryptCount().plus(1).toString()) to
                            String.format(relayNameTemplate, appConfig.getDnscryptRelayCount().plus(1).toString())
                    }
                    resolverDialogName = names.first
                    relayDialogName = names.second
                    showDialog = true
                }
            }
        ) { endpoint ->
            DnsCryptRow(endpoint, appConfig)
        }
    }

    if (showDialog) {
        RethinkEndpointEditorDialog(onDismiss = { showDialog = false }) {
            RethinkDnsCryptEndpointEditor(
                title = stringResource(R.string.cd_dns_crypt_dialog_heading),
                resolverLabel = stringResource(R.string.cd_dns_crypt_resolver_heading),
                relayLabel = stringResource(R.string.cd_dns_crypt_relay_heading),
                nameLabel = stringResource(R.string.cd_dns_crypt_dialog_name),
                stampLabel = stringResource(R.string.cd_dns_crypt_dialog_stamp),
                descriptionLabel = stringResource(R.string.cd_dns_crypt_dialog_desc),
                resolverDefaultName = resolverDialogName,
                relayDefaultName = relayDialogName,
                invalidInputMessage = stringResource(R.string.custom_url_error_invalid_url),
                strings = RethinkEndpointEditorStrings(stringResource(R.string.lbl_cancel), stringResource(R.string.lbl_add)),
                onSubmit = { kind, name, stamp, description ->
                    scope.launch(Dispatchers.IO) {
                        if (kind == RethinkDnsCryptEndpointKind.Resolver) insertDnsCrypt(appConfig, name, stamp, description) else insertDnsCryptRelay(appConfig, name, stamp, description)
                    }
                    showDialog = false
                    null
                },
                onDismiss = { showDialog = false },
            )
        }
    }

    if (showRelaysDialog) {
        com.bernaferrari.bravedns.ui.dialog.DnsCryptRelaysDialog(
            appConfig = appConfig,
            relays = dnsCryptRelayViewModel.dnsCryptRelayEndpointList,
            onDismiss = { showRelaysDialog = false }
        )
    }
}

private suspend fun insertDnsCrypt(appConfig: AppConfig, name: String, url: String, desc: String) {
    var dnscryptName = name
    if (name.isBlank()) {
        dnscryptName = url
    }
    val endpoint = DnsCryptEndpoint(
        id = 0,
        dnscryptName,
        url,
        desc,
        isSelected = false,
        isCustom = true,
        modifiedDataTime = 0,
        latency = 0
    )
    appConfig.insertDnscryptEndpoint(endpoint)
}

private suspend fun insertDnsCryptRelay(appConfig: AppConfig, name: String, url: String, desc: String) {
    var relayName = name
    if (name.isBlank()) {
        relayName = url
    }
    val endpoint = DnsCryptRelayEndpoint(
        id = 0,
        relayName,
        url,
        desc,
        isSelected = false,
        isCustom = true,
        modifiedDataTime = 0,
        latency = 0
    )
    appConfig.insertDnscryptRelayEndpoint(endpoint)
}

@Composable
private fun OdohListContent(
    paddingValues: PaddingValues,
    appConfig: AppConfig,
    oDohViewModel: ODoHEndpointViewModel,
    scope: CoroutineScope
) {
    val items = oDohViewModel.dohEndpointList.collectAsLazyPagingItems()
    var showDialog by remember { mutableStateOf(false) }
    val title = stringResource(
        R.string.two_argument_space,
        stringResource(R.string.lbl_add).replaceFirstChar(Char::uppercase),
        stringResource(R.string.lbl_odoh)
    )
    val nameLabel = stringResource(R.string.cd_doh_dialog_resolver_name)
    val proxyLabel = stringResource(R.string.settings_proxy_header) + stringResource(R.string.lbl_optional)
    val resolverLabel = stringResource(R.string.cd_doh_dialog_resolver_url)
    val defaultName = stringResource(R.string.lbl_odoh)
    val invalidUrlMessage = stringResource(R.string.custom_url_error_invalid_url)
    var dialogName by remember { mutableStateOf(defaultName) }

    DnsEndpointListWithFab(
        paddingValues = paddingValues,
        items = items,
        onFabClick = {
            scope.launch {
                dialogName = withContext(Dispatchers.IO) { defaultName + appConfig.getODoHCount().plus(1).toString() }
                showDialog = true
            }
        }
    ) { endpoint ->
        ODoHEndpointRow(endpoint, appConfig)
    }

    if (showDialog) {
        RethinkEndpointEditorDialog(onDismiss = { showDialog = false }) {
            RethinkODoHEndpointEditor(
                title = title,
                nameLabel = nameLabel,
                proxyLabel = proxyLabel,
                resolverLabel = resolverLabel,
                defaultName = dialogName,
                initialResolver = "https://",
                strings = RethinkEndpointEditorStrings(stringResource(R.string.lbl_cancel), stringResource(R.string.lbl_add)),
                onSubmit = { name, proxy, resolver ->
                    if (checkUrl(resolver)) {
                        scope.launch(Dispatchers.IO) {
                            insertOdoh(appConfig, name, proxy, resolver)
                        }
                        showDialog = false
                        null
                    } else {
                        invalidUrlMessage
                    }
                },
                onDismiss = { showDialog = false }
            )
        }
    }
}

private suspend fun insertOdoh(appConfig: AppConfig, name: String, proxy: String, resolver: String) {
    var odohName = name
    if (name.isBlank()) {
        odohName = resolver
    }
    val endpoint = ODoHEndpoint(
        id = 0,
        odohName,
        proxy,
        resolver,
        proxyIps = "",
        desc = "",
        isSelected = false,
        isCustom = true,
        modifiedDataTime = 0,
        latency = 0
    )
    appConfig.insertODoHEndpoint(endpoint)
}

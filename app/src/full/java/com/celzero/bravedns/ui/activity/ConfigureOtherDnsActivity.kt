/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.asFlow
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.DnsCryptRow
import com.celzero.bravedns.adapter.DnsProxyEndpointRow
import com.celzero.bravedns.adapter.DoHEndpointRow
import com.celzero.bravedns.adapter.DoTEndpointRow
import com.celzero.bravedns.adapter.ODoHEndpointRow
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DoHEndpoint
import com.celzero.bravedns.database.DoTEndpoint
import com.celzero.bravedns.database.DnsCryptEndpoint
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.database.DnsProxyEndpoint
import com.celzero.bravedns.database.ODoHEndpoint
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.DnsCryptRelaysDialog
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.DoHEndpointViewModel
import com.celzero.bravedns.viewmodel.DoTEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsCryptEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsCryptRelayEndpointViewModel
import com.celzero.bravedns.viewmodel.DnsProxyEndpointViewModel
import com.celzero.bravedns.viewmodel.ODoHEndpointViewModel
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.net.MalformedURLException
import java.net.URL

class ConfigureOtherDnsActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()

    private val dohViewModel: DoHEndpointViewModel by viewModel()
    private val dotViewModel: DoTEndpointViewModel by viewModel()
    private val dnsProxyViewModel: DnsProxyEndpointViewModel by viewModel()
    private val dnsCryptViewModel: DnsCryptEndpointViewModel by viewModel()
    private val dnsCryptRelayViewModel: DnsCryptRelayEndpointViewModel by viewModel()
    private val oDohViewModel: ODoHEndpointViewModel by viewModel()

    private var dnsType: Int = 0

    companion object {
        private const val DNS_TYPE = "dns_type"

        fun getIntent(context: Context, dnsType: Int): Intent {
            val intent = Intent(context, ConfigureOtherDnsActivity::class.java)
            intent.putExtra(DNS_TYPE, dnsType)
            return intent
        }
    }

    enum class DnsScreen(val index: Int) {
        DOH(0),
        DNS_PROXY(1),
        DNS_CRYPT(2),
        DOT(3),
        ODOH(4);

        companion object {
            fun getDnsType(index: Int): DnsScreen {
                return when (index) {
                    DOH.index -> DOH
                    DNS_PROXY.index -> DNS_PROXY
                    DNS_CRYPT.index -> DNS_CRYPT
                    DOT.index -> DOT
                    ODOH.index -> ODOH
                    else -> DOH
                }
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        dnsType = intent.getIntExtra(DNS_TYPE, dnsType)

        setContent {
            RethinkTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(text = getDnsTypeName(dnsType)) },
                            navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                        painter = painterResource(id = R.drawable.ic_arrow_back_24),
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    OtherDnsListContent(
                        dnsType = DnsScreen.getDnsType(dnsType),
                        paddingValues = padding
                    )
                }
            }
        }
    }

    @Composable
    private fun OtherDnsListContent(dnsType: DnsScreen, paddingValues: PaddingValues) {
        when (dnsType) {
            DnsScreen.DOH -> DohListContent(paddingValues)
            DnsScreen.DNS_PROXY -> DnsProxyListContent(paddingValues)
            DnsScreen.DNS_CRYPT -> DnsCryptListContent(paddingValues)
            DnsScreen.DOT -> DotListContent(paddingValues)
            DnsScreen.ODOH -> OdohListContent(paddingValues)
        }
    }

    @Composable
    private fun <T : Any> DnsEndpointListWithFab(
        paddingValues: PaddingValues,
        items: LazyPagingItems<T>,
        onFabClick: () -> Unit,
        itemContent: @Composable (T) -> Unit
    ) {
        val context = LocalContext.current
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                items(items.itemCount) { index ->
                    val item = items[index] ?: return@items
                    itemContent(item)
                }
            }
            FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fab_without_border),
                    contentDescription = context.getString(R.string.lbl_create)
                )
            }
        }
    }

    @Composable
    private fun DohListContent(paddingValues: PaddingValues) {
        val items = dohViewModel.dohEndpointList.asFlow().collectAsLazyPagingItems()
        var showDialog by remember { mutableStateOf(false) }
        DnsEndpointListWithFab(
            paddingValues = paddingValues,
            items = items,
            onFabClick = { showDialog = true }
        ) { endpoint ->
            DoHEndpointRow(endpoint, appConfig)
        }
        if (showDialog) {
            FullWidthDialog(onDismiss = { showDialog = false }) {
                CustomDohDialogContent(
                    title = getString(R.string.cd_doh_dialog_heading),
                    nameLabel = getString(R.string.cd_doh_dialog_resolver_name),
                    urlLabel = getString(R.string.cd_doh_dialog_resolver_url),
                    defaultName = getString(R.string.cd_custom_doh_url_name_default),
                    initialUrl = "https://",
                    checkboxLabel = getString(R.string.cd_doh_dialog_checkbox_desc),
                    loadNextIndex = { appConfig.getDohCount().plus(1) },
                    nameForIndex = { index ->
                        getString(R.string.cd_custom_doh_url_name, index.toString())
                    },
                    onSubmit = { name, url, isSecure ->
                        if (checkUrl(url)) {
                            insertDoHEndpoint(name, url, isSecure)
                            showDialog = false
                            null
                        } else {
                            getString(R.string.custom_url_error_invalid_url)
                        }
                    },
                    invalidUrlMessage = getString(R.string.custom_url_error_invalid_url),
                    onDismiss = { showDialog = false }
                )
            }
        }
    }

    private fun insertDoHEndpoint(name: String, url: String, isSecure: Boolean) {
        io {
            var dohName: String = name
            if (name.isBlank()) {
                dohName = url
            }
            val doHEndpoint =
                DoHEndpoint(
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
    private fun DotListContent(paddingValues: PaddingValues) {
        val items = dotViewModel.dohEndpointList.asFlow().collectAsLazyPagingItems()
        var showDialog by remember { mutableStateOf(false) }
        DnsEndpointListWithFab(
            paddingValues = paddingValues,
            items = items,
            onFabClick = { showDialog = true }
        ) { endpoint ->
            DoTEndpointRow(endpoint, appConfig)
        }
        if (showDialog) {
            val title =
                getString(
                    R.string.two_argument_space,
                    getString(R.string.lbl_add).replaceFirstChar(Char::titlecase),
                    getString(R.string.lbl_dot)
                )
            FullWidthDialog(onDismiss = { showDialog = false }) {
                CustomDohDialogContent(
                    title = title,
                    nameLabel = getString(R.string.cd_doh_dialog_resolver_name),
                    urlLabel = getString(R.string.cd_doh_dialog_resolver_url),
                    defaultName = getString(R.string.lbl_dot),
                    initialUrl = "",
                    checkboxLabel = getString(R.string.cd_doh_dialog_checkbox_desc),
                    loadNextIndex = { appConfig.getDoTCount().plus(1) },
                    nameForIndex = { index -> getString(R.string.lbl_dot) + index.toString() },
                    onSubmit = { name, url, isSecure ->
                        insertDotEndpoint(name, url, isSecure)
                        showDialog = false
                        null
                    },
                    invalidUrlMessage = "",
                    onDismiss = { showDialog = false }
                )
            }
        }
    }

    private fun insertDotEndpoint(name: String, url: String, isSecure: Boolean) {
        io {
            var dotName: String = name
            if (name.isBlank()) {
                dotName = url
            }
            val endpoint =
                DoTEndpoint(
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
    }

    @Composable
    private fun DnsProxyListContent(paddingValues: PaddingValues) {
        val items = dnsProxyViewModel.dnsProxyEndpointList.asFlow().collectAsLazyPagingItems()
        val scope = rememberCoroutineScope()
        var showDialog by remember { mutableStateOf(false) }
        var appNames by remember { mutableStateOf<List<String>>(emptyList()) }
        var nextIndex by remember { mutableStateOf(0) }
        DnsEndpointListWithFab(
            paddingValues = paddingValues,
            items = items,
            onFabClick = {
                scope.launch {
                    val names = withContext(Dispatchers.IO) {
                        val list: MutableList<String> = ArrayList()
                        list.add(getString(R.string.settings_app_list_default_app))
                        list.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this@ConfigureOtherDnsActivity))
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
            FullWidthDialog(onDismiss = { showDialog = false }) {
                DnsProxyDialogContent(
                    appNames = appNames,
                    nextIndex = nextIndex,
                    onDismiss = { showDialog = false }
                )
            }
        }
    }

    @Composable
    private fun DnsProxyDialogContent(
        appNames: List<String>,
        nextIndex: Int,
        onDismiss: () -> Unit
    ) {
        var selectedAppIndex by remember { mutableStateOf(0) }
        var appMenuExpanded by remember { mutableStateOf(false) }
        var proxyName by remember {
            mutableStateOf(getString(R.string.cd_custom_dns_proxy_name, nextIndex.toString()))
        }
        var ipAddress by remember { mutableStateOf(getString(R.string.cd_custom_dns_proxy_default_ip)) }
        var portText by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        var excludeAppsChecked by remember { mutableStateOf(!persistentState.excludeAppsInProxy) }

        val lockdown = VpnController.isVpnLockdown()

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = getString(R.string.dns_proxy_dialog_header_dns),
                style = MaterialTheme.typography.titleMedium
            )

            if (lockdown) {
                TextButton(onClick = { onDismiss(); UIUtils.openVpnProfile(this@ConfigureOtherDnsActivity) }) {
                    Text(text = getString(R.string.settings_lock_down_mode_desc))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getString(R.string.settings_dns_proxy_dialog_app),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.3f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(0.7f)) {
                    TextButton(onClick = { appMenuExpanded = true }) {
                        Text(text = appNames.getOrNull(selectedAppIndex) ?: "")
                    }
                    DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                        appNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(text = name) },
                                onClick = {
                                    selectedAppIndex = index
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = proxyName,
                onValueChange = { proxyName = it },
                label = { Text(text = getString(R.string.dns_proxy_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text(text = getString(R.string.dns_proxy_ip_address)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it },
                label = { Text(text = getString(R.string.dns_proxy_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = getString(R.string.settings_exclude_proxy_apps_heading))
                Checkbox(
                    checked = excludeAppsChecked,
                    onCheckedChange = { if (!lockdown) excludeAppsChecked = it },
                    enabled = !lockdown
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val mode = getString(R.string.cd_dns_proxy_mode_external)
                        val appName = appNames.getOrNull(selectedAppIndex).orEmpty()
                        val ipAddresses =
                            ipAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (ipAddresses.isEmpty()) {
                            errorText = getString(R.string.cd_dns_proxy_error_text_1)
                            return@Button
                        }

                        val invalidIps = mutableListOf<String>()
                        val validIps = mutableListOf<String>()
                        for (ip in ipAddresses) {
                            if (IPAddressString(ip).isIPAddress) {
                                validIps.add(ip)
                            } else {
                                invalidIps.add(ip)
                            }
                        }

                        if (invalidIps.isNotEmpty()) {
                            errorText =
                                getString(R.string.cd_dns_proxy_error_text_1) +
                                    ": ${invalidIps.joinToString(", ")}"
                            return@Button
                        }

                        val port = portText.toIntOrNull()
                        if (port == null) {
                            errorText = getString(R.string.cd_dns_proxy_error_text_3)
                            return@Button
                        }

                        var isPortValid = true
                        for (ip in validIps) {
                            if (Utilities.isLanIpv4(ip) && !Utilities.isValidLocalPort(port)) {
                                isPortValid = false
                                break
                            }
                        }

                        if (!isPortValid) {
                            errorText = getString(R.string.cd_dns_proxy_error_text_2)
                            return@Button
                        }

                        val ipString = validIps.joinToString(",")
                        io { insertDNSProxyEndpointDB(mode, proxyName, appName, ipString, port) }
                        persistentState.excludeAppsInProxy = !excludeAppsChecked
                        onDismiss()
                    }
                ) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    private suspend fun insertDNSProxyEndpointDB(
        mode: String,
        name: String,
        appName: String?,
        ip: String,
        port: Int
    ) {
        if (appName == null) return

        io {
            val packageName =
                if (appName == getString(R.string.settings_app_list_default_app)) {
                    ""
                } else {
                    FirewallManager.getPackageNameByAppName(appName) ?: ""
                }
            var proxyName = name
            if (proxyName.isBlank()) {
                proxyName =
                    if (mode == getString(R.string.cd_dns_proxy_mode_internal)) {
                        appName
                    } else ip
            }
            val endpoint =
                DnsProxyEndpoint(
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
    }

    @Composable
    private fun DnsCryptListContent(paddingValues: PaddingValues) {
        val items = dnsCryptViewModel.dnsCryptEndpointList.asFlow().collectAsLazyPagingItems()
        var showDialog by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cd_dns_crypt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { openDnsCryptRelaysDialog() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.cd_dnscrypt_relay_heading))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_right_arrow_secondary),
                            contentDescription = null
                        )
                    }
                }
            }
            DnsEndpointListWithFab(
                paddingValues = PaddingValues(0.dp),
                items = items,
                onFabClick = { showDialog = true }
            ) { endpoint ->
                DnsCryptRow(endpoint, appConfig)
            }
        }
        if (showDialog) {
            FullWidthDialog(onDismiss = { showDialog = false }) {
                DnsCryptDialogContent(onDismiss = { showDialog = false })
            }
        }
    }

    private fun openDnsCryptRelaysDialog() {
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val customDialog =
            DnsCryptRelaysDialog(
                activity = this,
                appConfig = appConfig,
                relays = dnsCryptRelayViewModel.dnsCryptRelayEndpointList,
                themeID = themeId
            )
        customDialog.show()
    }

    @Composable
    private fun DnsCryptDialogContent(onDismiss: () -> Unit) {
        var isServer by remember { mutableStateOf(true) }
        var dnscryptNextIndex by remember { mutableStateOf(0) }
        var relayNextIndex by remember { mutableStateOf(0) }
        var name by remember { mutableStateOf(getString(R.string.cd_dns_crypt_name_default)) }
        var url by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            dnscryptNextIndex = appConfig.getDnscryptCount().plus(1)
            relayNextIndex = appConfig.getDnscryptRelayCount().plus(1)
        }

        LaunchedEffect(isServer, dnscryptNextIndex, relayNextIndex) {
            name =
                if (isServer) {
                    getString(R.string.cd_dns_crypt_name, dnscryptNextIndex.toString())
                } else {
                    getString(R.string.cd_dns_crypt_relay_name, relayNextIndex.toString())
                }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = getString(R.string.cd_dns_crypt_dialog_heading),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isServer = true }) {
                    Text(text = getString(R.string.cd_dns_crypt_resolver_heading))
                }
                Spacer(modifier = Modifier.width(10.dp))
                TextButton(onClick = { isServer = false }) {
                    Text(text = getString(R.string.cd_dns_crypt_relay_heading))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = getString(R.string.cd_dns_crypt_dialog_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(text = getString(R.string.cd_dns_crypt_dialog_stamp)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(text = getString(R.string.cd_dns_crypt_dialog_desc)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || url.isBlank()) {
                            errorText = getString(R.string.custom_url_error_invalid_url)
                            return@Button
                        }

                        if (isServer) {
                            insertDnsCrypt(name, url, desc)
                        } else {
                            insertDnsCryptRelay(name, url, desc)
                        }
                        onDismiss()
                    }
                ) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    private fun insertDnsCrypt(name: String, url: String, desc: String) {
        io {
            var dnscryptName: String = name
            if (name.isBlank()) {
                dnscryptName = url
            }
            val endpoint =
                DnsCryptEndpoint(
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
    }

    private fun insertDnsCryptRelay(name: String, url: String, desc: String) {
        io {
            var relayName: String = name
            if (name.isBlank()) {
                relayName = url
            }
            val endpoint =
                DnsCryptRelayEndpoint(
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
    }

    @Composable
    private fun OdohListContent(paddingValues: PaddingValues) {
        val items = oDohViewModel.dohEndpointList.asFlow().collectAsLazyPagingItems()
        var showDialog by remember { mutableStateOf(false) }
        DnsEndpointListWithFab(
            paddingValues = paddingValues,
            items = items,
            onFabClick = { showDialog = true }
        ) { endpoint ->
            ODoHEndpointRow(endpoint, appConfig)
        }
        if (showDialog) {
            val title =
                getString(
                    R.string.two_argument_space,
                    getString(R.string.lbl_add).replaceFirstChar(Char::uppercase),
                    getString(R.string.lbl_odoh)
                )
            FullWidthDialog(onDismiss = { showDialog = false }) {
                CustomOdohDialogContent(
                    title = title,
                    nameLabel = getString(R.string.cd_doh_dialog_resolver_name),
                    proxyLabel =
                        getString(R.string.settings_proxy_header).replaceFirstChar(Char::uppercase) +
                            getString(R.string.lbl_optional),
                    resolverLabel = getString(R.string.cd_doh_dialog_resolver_url),
                    defaultName = getString(R.string.lbl_odoh),
                    initialResolver = "https://",
                    loadNextIndex = { appConfig.getODoHCount().plus(1) },
                    invalidUrlMessage = getString(R.string.custom_url_error_invalid_url),
                    onSubmit = { name, proxy, resolver ->
                        if (checkUrl(resolver)) {
                            insertOdoh(name, proxy, resolver)
                            showDialog = false
                            null
                        } else {
                            getString(R.string.custom_url_error_invalid_url)
                        }
                    },
                    onDismiss = { showDialog = false }
                )
            }
        }
    }

    @Composable
    private fun FullWidthDialog(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp
            ) {
                content()
            }
        }
    }

    @Composable
    private fun CustomDohDialogContent(
        title: String,
        nameLabel: String,
        urlLabel: String,
        defaultName: String,
        initialUrl: String,
        checkboxLabel: String,
        loadNextIndex: suspend () -> Int,
        nameForIndex: (Int) -> String,
        onSubmit: (String, String, Boolean) -> String?,
        invalidUrlMessage: String,
        onDismiss: () -> Unit
    ) {
        var name by remember { mutableStateOf(defaultName) }
        var url by remember { mutableStateOf(initialUrl) }
        var insecureChecked by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val nextIndex = withContext(Dispatchers.IO) { loadNextIndex() }
            name = nameForIndex(nextIndex)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = nameLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(text = urlLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = insecureChecked, onCheckedChange = { insecureChecked = it })
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = checkboxLabel)
            }
            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val isSecure = !insecureChecked
                        val error = onSubmit(name, url, isSecure)
                        if (error != null) {
                            errorText = error.ifBlank { invalidUrlMessage }
                        }
                    }
                ) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    @Composable
    private fun CustomOdohDialogContent(
        title: String,
        nameLabel: String,
        proxyLabel: String,
        resolverLabel: String,
        defaultName: String,
        initialResolver: String,
        loadNextIndex: suspend () -> Int,
        invalidUrlMessage: String,
        onSubmit: (String, String, String) -> String?,
        onDismiss: () -> Unit
    ) {
        var name by remember { mutableStateOf(defaultName) }
        var proxy by remember { mutableStateOf("") }
        var resolver by remember { mutableStateOf(initialResolver) }
        var errorText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val nextIndex = withContext(Dispatchers.IO) { loadNextIndex() }
            name = getString(R.string.lbl_odoh) + nextIndex.toString()
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = nameLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proxy,
                onValueChange = { proxy = it },
                label = { Text(text = proxyLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = resolver,
                onValueChange = { resolver = it },
                label = { Text(text = resolverLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorText.isNotBlank()) {
                Text(text = errorText, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = getString(R.string.lbl_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val error = onSubmit(name, proxy, resolver)
                        if (error != null) {
                            errorText = error.ifBlank { invalidUrlMessage }
                        }
                    }
                ) {
                    Text(text = getString(R.string.lbl_add))
                }
            }
        }
    }

    private fun insertOdoh(name: String, proxy: String, resolver: String) {
        io {
            var odohName: String = name
            if (name.isBlank()) {
                odohName = resolver
            }
            val endpoint =
                ODoHEndpoint(
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
    }

    private fun getDnsTypeName(type: Int): String {
        return when (DnsScreen.getDnsType(type)) {
            DnsScreen.DOH -> getString(R.string.other_dns_list_tab1)
            DnsScreen.DNS_CRYPT -> getString(R.string.dc_dns_crypt)
            DnsScreen.DNS_PROXY -> getString(R.string.other_dns_list_tab3)
            DnsScreen.DOT -> getString(R.string.lbl_dot)
            DnsScreen.ODOH -> getString(R.string.lbl_odoh)
        }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

}

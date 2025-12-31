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
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.DnsCryptEndpointAdapter
import com.celzero.bravedns.adapter.DnsCryptRelayEndpointAdapter
import com.celzero.bravedns.adapter.DnsProxyEndpointAdapter
import com.celzero.bravedns.adapter.DoTEndpointAdapter
import com.celzero.bravedns.adapter.DohEndpointAdapter
import com.celzero.bravedns.adapter.ODoHEndpointAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DoHEndpoint
import com.celzero.bravedns.database.DoTEndpoint
import com.celzero.bravedns.database.DnsCryptEndpoint
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.database.DnsProxyEndpoint
import com.celzero.bravedns.database.ODoHEndpoint
import com.celzero.bravedns.databinding.DialogSetCustomDohBinding
import com.celzero.bravedns.databinding.DialogSetCustomOdohBinding
import com.celzero.bravedns.databinding.DialogSetDnsCryptBinding
import com.celzero.bravedns.databinding.FragmentDnsCryptListBinding
import com.celzero.bravedns.databinding.FragmentDnsProxyListBinding
import com.celzero.bravedns.databinding.FragmentDohListBinding
import com.celzero.bravedns.databinding.FragmentDotListBinding
import com.celzero.bravedns.databinding.FragmentOdohListBinding
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val lifecycleOwner = LocalLifecycleOwner.current
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                when (dnsType) {
                    DnsScreen.DOH -> createDohListView(context, lifecycleOwner)
                    DnsScreen.DNS_PROXY -> createDnsProxyListView(context, lifecycleOwner)
                    DnsScreen.DNS_CRYPT -> createDnsCryptListView(context, lifecycleOwner)
                    DnsScreen.DOT -> createDotListView(context, lifecycleOwner)
                    DnsScreen.ODOH -> createOdohListView(context, lifecycleOwner)
                }
            }
        )
    }

    private fun createDohListView(context: Context, lifecycleOwner: LifecycleOwner): View {
        val binding = FragmentDohListBinding.inflate(LayoutInflater.from(context))
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerDohConnections.layoutManager = layoutManager

        val adapter = DohEndpointAdapter(context, appConfig)
        dohViewModel.dohEndpointList.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerDohConnections.adapter = adapter

        binding.dohFabAddServerIcon.bringToFront()
        binding.dohFabAddServerIcon.setOnClickListener { showAddCustomDohDialog() }

        return binding.root
    }

    private fun showAddCustomDohDialog() {
        val dialogBinding = DialogSetCustomDohBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val heading = dialogBinding.dialogCustomUrlTop
        val applyURLBtn = dialogBinding.dialogCustomUrlOkBtn
        val cancelURLBtn = dialogBinding.dialogCustomUrlCancelBtn
        val customName = dialogBinding.dialogCustomNameEditText
        val customURL = dialogBinding.dialogCustomUrlEditText
        val progressBar = dialogBinding.dialogCustomUrlLoading
        val errorTxt = dialogBinding.dialogCustomUrlFailureText
        val checkBox = dialogBinding.dialogSecureCheckbox

        heading.text = getString(R.string.cd_doh_dialog_heading)

        io {
            val nextIndex = appConfig.getDohCount().plus(1)
            uiCtx {
                customName.setText(
                    getString(R.string.cd_custom_doh_url_name, nextIndex.toString()),
                    TextView.BufferType.EDITABLE
                )
            }
        }

        customURL.setText("")
        customName.setText(
            getString(R.string.cd_custom_doh_url_name_default),
            TextView.BufferType.EDITABLE
        )
        applyURLBtn.setOnClickListener {
            val url = customURL.text.toString()
            val name = customName.text.toString()
            val isSecure = !checkBox.isChecked

            if (checkUrl(url)) {
                insertDoHEndpoint(name, url, isSecure)
                dialog.dismiss()
            } else {
                errorTxt.text = resources.getString(R.string.custom_url_error_invalid_url)
                errorTxt.visibility = View.VISIBLE
                cancelURLBtn.visibility = View.VISIBLE
                applyURLBtn.visibility = View.VISIBLE
                progressBar.visibility = View.INVISIBLE
            }
        }

        cancelURLBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    private fun createDotListView(context: Context, lifecycleOwner: LifecycleOwner): View {
        val binding = FragmentDotListBinding.inflate(LayoutInflater.from(context))
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerDot.layoutManager = layoutManager

        val adapter = DoTEndpointAdapter(context, appConfig)
        dotViewModel.dohEndpointList.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerDot.adapter = adapter

        binding.dotFabAdd.setOnClickListener { showAddDotDialog() }
        return binding.root
    }

    private fun showAddDotDialog() {
        val dialogBinding = DialogSetCustomDohBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val heading = dialogBinding.dialogCustomUrlTop
        val applyURLBtn = dialogBinding.dialogCustomUrlOkBtn
        val cancelURLBtn = dialogBinding.dialogCustomUrlCancelBtn
        val customName = dialogBinding.dialogCustomNameEditText
        val customURL = dialogBinding.dialogCustomUrlEditText
        val checkBox = dialogBinding.dialogSecureCheckbox

        heading.text =
            getString(
                R.string.two_argument_space,
                getString(R.string.lbl_add).replaceFirstChar(Char::titlecase),
                getString(R.string.lbl_dot)
            )

        io {
            val nextIndex = appConfig.getDoTCount().plus(1)
            uiCtx {
                customName.setText(
                    getString(R.string.lbl_dot) + nextIndex.toString(),
                    TextView.BufferType.EDITABLE
                )
            }
        }

        customURL.setText("")
        customName.setText(getString(R.string.lbl_dot), TextView.BufferType.EDITABLE)
        applyURLBtn.setOnClickListener {
            val url = customURL.text.toString()
            val name = customName.text.toString()
            val isSecure = !checkBox.isChecked

            insertDotEndpoint(name, url, isSecure)
            dialog.dismiss()
        }

        cancelURLBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    private fun createDnsProxyListView(context: Context, lifecycleOwner: LifecycleOwner): View {
        val binding = FragmentDnsProxyListBinding.inflate(LayoutInflater.from(context))
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerDnsProxyConnections.layoutManager = layoutManager

        val adapter = DnsProxyEndpointAdapter(context, lifecycleOwner, appConfig)
        dnsProxyViewModel.dnsProxyEndpointList.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerDnsProxyConnections.adapter = adapter

        binding.dohFabAddServerIcon.bringToFront()
        binding.dohFabAddServerIcon.setOnClickListener {
            io {
                val appNames: MutableList<String> = ArrayList()
                appNames.add(getString(R.string.settings_app_list_default_app))
                appNames.addAll(FirewallManager.getAllAppNamesSortedByVpnPermission(this))
                val nextIndex = appConfig.getDnsProxyCount().plus(1)
                uiCtx { showAddDnsProxyDialog(appNames, nextIndex) }
            }
        }

        return binding.root
    }

    private fun showAddDnsProxyDialog(appNames: List<String>, nextIndex: Int) {
        val dialog = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).create()
        val lp = WindowManager.LayoutParams()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        dialog.window?.attributes = lp
        dialog.setCancelable(true)

        val composeView = ComposeView(this)
        composeView.setContent {
            RethinkTheme {
                DnsProxyDialogContent(
                    appNames = appNames,
                    nextIndex = nextIndex,
                    onDismiss = { dialog.dismiss() }
                )
            }
        }
        dialog.setView(composeView)
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

    private fun createDnsCryptListView(context: Context, lifecycleOwner: LifecycleOwner): View {
        val binding = FragmentDnsCryptListBinding.inflate(LayoutInflater.from(context))
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerDnsCryptConnections.layoutManager = layoutManager

        val adapter = DnsCryptEndpointAdapter(context, appConfig)
        dnsCryptViewModel.dnsCryptEndpointList.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerDnsCryptConnections.adapter = adapter

        binding.addRelayBtn.setOnClickListener { openDnsCryptRelaysDialog(lifecycleOwner) }
        binding.dohFabAddServerIcon.bringToFront()
        binding.dohFabAddServerIcon.setOnClickListener { showAddDnsCryptDialog() }

        return binding.root
    }

    private fun openDnsCryptRelaysDialog(lifecycleOwner: LifecycleOwner) {
        val relayAdapter = DnsCryptRelayEndpointAdapter(this, lifecycleOwner, appConfig)
        dnsCryptRelayViewModel.dnsCryptRelayEndpointList.observe(lifecycleOwner) {
            relayAdapter.submitData(lifecycleOwner.lifecycle, it)
        }

        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val customDialog = DnsCryptRelaysDialog(this, relayAdapter, themeId)
        customDialog.show()
    }

    private fun showAddDnsCryptDialog() {
        val dialogBinding = DialogSetDnsCryptBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dialogBinding.root)

        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val radioServer = dialogBinding.dialogDnsCryptRadioServer
        val radioRelay = dialogBinding.dialogDnsCryptRadioRelay
        val applyURLBtn = dialogBinding.dialogDnsCryptOkBtn
        val cancelURLBtn = dialogBinding.dialogDnsCryptCancelBtn
        val cryptNameEditText = dialogBinding.dialogDnsCryptName
        val cryptURLEditText = dialogBinding.dialogDnsCryptUrl
        val cryptDescEditText = dialogBinding.dialogDnsCryptDesc
        val errorText = dialogBinding.dialogDnsCryptErrorTxt

        radioServer.isChecked = true
        var dnscryptNextIndex = 0
        var relayNextIndex = 0

        io {
            dnscryptNextIndex = appConfig.getDnscryptCount().plus(1)
            relayNextIndex = appConfig.getDnscryptRelayCount().plus(1)
            uiCtx {
                cryptNameEditText.setText(
                    getString(R.string.cd_dns_crypt_name, dnscryptNextIndex.toString()),
                    TextView.BufferType.EDITABLE
                )
            }
        }

        cryptNameEditText.setText(
            getString(R.string.cd_dns_crypt_name_default),
            TextView.BufferType.EDITABLE
        )

        radioServer.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) return@setOnCheckedChangeListener
            cryptNameEditText.setText(
                getString(R.string.cd_dns_crypt_name, dnscryptNextIndex.toString()),
                TextView.BufferType.EDITABLE
            )
        }

        radioRelay.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) return@setOnCheckedChangeListener
            cryptNameEditText.setText(
                getString(R.string.cd_dns_crypt_relay_name, relayNextIndex.toString()),
                TextView.BufferType.EDITABLE
            )
        }

        applyURLBtn.setOnClickListener {
            val name = cryptNameEditText.text.toString()
            val url = cryptURLEditText.text.toString()
            val desc = cryptDescEditText.text.toString()

            if (name.isBlank() || url.isBlank()) {
                errorText.visibility = View.VISIBLE
                errorText.text = getString(R.string.custom_url_error_invalid_url)
                return@setOnClickListener
            }

            if (radioServer.isChecked) {
                insertDnsCrypt(name, url, desc)
            } else {
                insertDnsCryptRelay(name, url, desc)
            }
            dialog.dismiss()
        }

        cancelURLBtn.setOnClickListener { dialog.dismiss() }
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

    private fun createOdohListView(context: Context, lifecycleOwner: LifecycleOwner): View {
        val binding = FragmentOdohListBinding.inflate(LayoutInflater.from(context))
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerOdoh.layoutManager = layoutManager

        val adapter = ODoHEndpointAdapter(context, appConfig)
        oDohViewModel.dohEndpointList.observe(lifecycleOwner) {
            adapter.submitData(lifecycleOwner.lifecycle, it)
        }
        binding.recyclerOdoh.adapter = adapter

        binding.odohFabAdd.bringToFront()
        binding.odohFabAdd.setOnClickListener { showAddOdohDialog() }

        return binding.root
    }

    private fun showAddOdohDialog() {
        val dialogBinding = DialogSetCustomOdohBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim).setView(dialogBinding.root)
        val lp = WindowManager.LayoutParams()
        val dialog = builder.create()
        dialog.show()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog.setCancelable(true)
        dialog.window?.attributes = lp

        val heading = dialogBinding.dialogCustomUrlTop
        val applyURLBtn = dialogBinding.dialogCustomUrlOkBtn
        val cancelURLBtn = dialogBinding.dialogCustomUrlCancelBtn
        val customName = dialogBinding.dialogCustomNameEditText
        val customProxy = dialogBinding.dialogCustomProxyEditText
        val customResolver = dialogBinding.dialogCustomResolverEditText
        val errorTxt = dialogBinding.dialogCustomUrlFailureText
        val hintInputLayout = dialogBinding.textInputLayout1

        val title =
            getString(
                R.string.two_argument_space,
                getString(R.string.lbl_add).replaceFirstChar(Char::uppercase),
                getString(R.string.lbl_odoh)
            )
        heading.text = title

        io {
            val nextIndex = appConfig.getODoHCount().plus(1)
            uiCtx {
                customName.setText(
                    getString(R.string.lbl_odoh) + nextIndex.toString(),
                    TextView.BufferType.EDITABLE
                )
            }
        }

        hintInputLayout.hint =
            getString(R.string.settings_proxy_header).replaceFirstChar(Char::uppercase) +
                getString(R.string.lbl_optional)

        customName.setText(getString(R.string.lbl_odoh), TextView.BufferType.EDITABLE)
        applyURLBtn.setOnClickListener {
            val proxy = customProxy.text.toString()
            val resolver = customResolver.text.toString()
            val name = customName.text.toString()

            if (checkUrl(resolver)) {
                insertOdoh(name, proxy, resolver)
                dialog.dismiss()
            } else {
                errorTxt.text = resources.getString(R.string.custom_url_error_invalid_url)
                errorTxt.visibility = View.VISIBLE
            }
        }

        cancelURLBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

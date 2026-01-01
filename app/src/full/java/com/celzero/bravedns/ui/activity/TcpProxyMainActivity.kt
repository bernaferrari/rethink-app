package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgIncludeAppsAdapter
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.dialog.WgIncludeAppsDialog
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.ProxyAppsMappingViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class TcpProxyMainActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()

    private val mappingViewModel: ProxyAppsMappingViewModel by viewModel()

    private var tcpProxySwitchChecked by mutableStateOf(false)
    private var tcpProxyStatus by mutableStateOf("")
    private var tcpProxyDesc by mutableStateOf("")
    private var tcpProxyAddAppsText by mutableStateOf("")
    private var tcpErrorVisible by mutableStateOf(false)
    private var tcpErrorText by mutableStateOf("")
    private var enableUdpRelayChecked by mutableStateOf(false)
    private var warpSwitchChecked by mutableStateOf(false)

    companion object {
        private const val TAG = "TcpProxyMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        tcpProxyDesc = getString(R.string.settings_https_desc)
        tcpProxyAddAppsText = "Add / Remove apps"

        setContent {
            RethinkTheme {
                TcpProxyContent()
            }
        }

        init()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun init() {
        displayTcpProxyStatus()
        observeTcpProxyApps()
    }

    private fun displayTcpProxyStatus() {
        val tcpProxies = TcpProxyHelper.getActiveTcpProxy()
        if (tcpProxies == null || !tcpProxies.isActive) {
            tcpProxyStatus = "Not active"
            showTcpErrorLayout()
            tcpProxySwitchChecked = false
            return
        }

        Napier.i("$TAG displayTcpProxyUi: ${tcpProxies.name}, ${tcpProxies.url}")
        tcpProxySwitchChecked = true
        tcpProxyStatus = "Active"
        tcpErrorVisible = false
        tcpErrorText = ""
    }

    private fun showTcpErrorLayout() {
        tcpErrorVisible = true
        tcpErrorText = "Something went wrong"
    }

    private fun observeTcpProxyApps() {
        mappingViewModel.getAppCountById(ProxyManager.ID_TCP_BASE).observe(this) { apps ->
            tcpProxyAddAppsText =
                if (apps == null || apps == 0) {
                    "Add / Remove apps"
                } else {
                    "Add / Remove apps ($apps added)"
                }
        }
    }

    private fun onTcpProxySwitchChanged(checked: Boolean) {
        tcpProxySwitchChecked = checked
        io {
            val isActive = true
            uiCtx {
                if (checked && isActive) {
                    tcpProxySwitchChecked = false
                    Utilities.showToastUiCentered(
                        this,
                        "Warp is active. Please disable it first.",
                        Toast.LENGTH_SHORT
                    )
                    return@uiCtx
                }

                val apps = ProxyManager.isAnyAppSelected(ProxyManager.ID_TCP_BASE)

                if (!apps) {
                    Utilities.showToastUiCentered(
                        this,
                        "Please add at least one app to enable Rethink Proxy.",
                        Toast.LENGTH_SHORT
                    )
                    warpSwitchChecked = false
                    tcpProxySwitchChecked = false
                    return@uiCtx
                }

                if (!checked) {
                    io { TcpProxyHelper.disable() }
                    tcpProxyDesc = getString(R.string.settings_https_desc)
                    return@uiCtx
                }

                if (appConfig.getBraveMode().isDnsMode()) {
                    tcpProxySwitchChecked = false
                    return@uiCtx
                }

                if (!appConfig.canEnableTcpProxy()) {
                    val s =
                        persistentState.proxyProvider
                            .lowercase()
                            .replaceFirstChar(Char::titlecase)
                    Utilities.showToastUiCentered(
                        this,
                        getString(R.string.settings_https_disabled_error, s),
                        Toast.LENGTH_SHORT
                    )
                    tcpProxySwitchChecked = false
                    return@uiCtx
                }
                enableTcpProxy()
            }
        }
    }

    private fun openAppsDialog() {
        val proxyId = ProxyManager.ID_TCP_BASE
        val proxyName = ProxyManager.TCP_PROXY_NAME
        val appsAdapter = WgIncludeAppsAdapter(this, proxyId, proxyName)
        var themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        if (Themes.isFrostTheme(themeId)) {
            themeId = R.style.App_Dialog_NoDim
        }
        val includeAppsDialog =
            WgIncludeAppsDialog(this, appsAdapter, mappingViewModel, themeId, proxyId, proxyName)
        includeAppsDialog.setCanceledOnTouchOutside(false)
        includeAppsDialog.show()
    }

    private suspend fun showConfigCreationError() {
        uiCtx {
            Utilities.showToastUiCentered(
                this,
                getString(R.string.new_warp_error_toast),
                Toast.LENGTH_LONG
            )
            enableUdpRelayChecked = false
        }
    }

    private suspend fun enableTcpProxy() {
        TcpProxyHelper.enable()
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    @Composable
    private fun TcpProxyContent() {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = getString(R.string.settings_proxy_header),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wireguard_icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Rethink's Proxy",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = tcpProxySwitchChecked,
                            onCheckedChange = { onTcpProxySwitchChanged(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = tcpProxyDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tcpProxyStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Enable UDP Relay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = enableUdpRelayChecked,
                            onCheckedChange = { enableUdpRelayChecked = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = { openAppsDialog() }) {
                        Text(text = tcpProxyAddAppsText)
                    }

                    if (tcpErrorVisible) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tcpErrorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wireguard_icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Cloudflare WARP",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = warpSwitchChecked,
                            onCheckedChange = { warpSwitchChecked = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text =
                            "Modern, fast, and secure way to connect to a VPN server. Secure all your internet traffic with an ultra-secure, lightning-quick VPN service that lets you connect with the WireGuard protocol.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

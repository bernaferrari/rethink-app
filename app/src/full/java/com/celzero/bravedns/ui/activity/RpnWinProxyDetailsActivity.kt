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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class RpnWinProxyDetailsActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()

    private lateinit var cc: String

    private var appsCount by mutableStateOf("-")
    private var domainsCount by mutableStateOf("-")
    private var ipsCount by mutableStateOf("-")
    private var proxyError by mutableStateOf("")

    companion object {
        const val TAG = "WinDetAct"
        const val COUNTRY_CODE = "country_code"
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        initViews()

        setContent {
            RethinkTheme {
                ProxyDetailsScreen()
            }
        }
    }

    private fun initViews() {
        cc = intent.getStringExtra(COUNTRY_CODE) ?: ""
        Napier.v(tag = TAG, message = "initViews: country code from intent: $cc")
        if (!::cc.isInitialized || cc.isEmpty()) {
            Napier.w(tag = TAG, message = "empty country code, finishing activity")
            showNoProxyFoundDialog()
            finish()
            return
        }
        io {
            val apps = ProxyManager.getAppsCountForProxy(cc)
            val ipCount = IpRulesManager.getRulesCountByCC(cc)
            val domainCount = DomainRulesManager.getRulesCountByCC(cc)
            Napier.i(tag = TAG, message = "apps: $apps, ips: $ipCount, domains: $domainCount for country code: $cc")
            uiCtx {
                appsCount = apps.toString()
                domainsCount = domainCount.toString()
                ipsCount = ipCount.toString()
            }
        }
    }

    private fun showNoProxyFoundDialog() {
        val builder = MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
        builder.setTitle("No proxy found")
        builder.setMessage("Proxy information is missing for this proxy id. Please ensure that the proxy is configured correctly and try again.")
        builder.setCancelable(false)
        builder.setPositiveButton(getString(R.string.ada_noapp_dialog_positive)) { dialogInterface, _ ->
            dialogInterface.dismiss()
            finish()
        }
        builder.create().show()
    }

    private fun io(f: suspend () -> Unit) {
        this.lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    @Composable
    private fun ProxyDetailsScreen() {
        Column(modifier = Modifier.fillMaxWidth()) {
            StatsRow()
            Spacer(modifier = Modifier.height(12.dp))
            DetailsSection()
            Spacer(modifier = Modifier.height(16.dp))
            ActionButton()
        }
    }

    @Composable
    private fun StatsRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCard(label = "Apps", value = appsCount, modifier = Modifier.weight(1f))
            StatCard(label = "Domains", value = domainsCount, modifier = Modifier.weight(1f))
            StatCard(label = "IPs", value = ipsCount, modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun DetailsSection() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Proxy Name", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(label = "Who", value = "who_or_service_email@domain.com")
            if (proxyError.isNotEmpty()) {
                DetailRow(label = "Error", value = proxyError, valueColor = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(label = "Country", value = cc.uppercase())
            DetailRow(label = "Latency", value = "37 ms")
            DetailRow(label = "Last connected", value = "2 min ago")
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(label = "Status", value = "CONNECTED", valueColor = Color(0xFF2E7D32))
        }
    }

    @Composable
    private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
    }

    @Composable
    private fun ActionButton() {
        val context = LocalContext.current
        Button(
            onClick = {
                Utilities.showToastUiCentered(
                    context,
                    "Apps part of other proxy/excluded from proxy will be listed here",
                    Toast.LENGTH_LONG
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_loop_back_app),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = "Select Apps For Proxy")
        }
    }
}

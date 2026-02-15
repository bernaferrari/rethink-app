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
package com.celzero.bravedns.ui.compose.rpn

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.service.DomainRulesManager
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.util.Utilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnWinProxyDetailsScreen(
    countryCode: String,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var appsCount by remember { mutableStateOf("-") }
    var domainsCount by remember { mutableStateOf("-") }
    var ipsCount by remember { mutableStateOf("-") }
    var proxyError by remember { mutableStateOf("") }
    var showNoProxyFoundDialog by remember { mutableStateOf(false) }

    LaunchedEffect(countryCode) {
        if (countryCode.isEmpty()) {
            Napier.w(tag = TAG, message = "empty country code, showing dialog")
            showNoProxyFoundDialog = true
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            val apps = ProxyManager.getAppsCountForProxy(countryCode)
            val ipCount = IpRulesManager.getRulesCountByCC(countryCode)
            val domainCount = DomainRulesManager.getRulesCountByCC(countryCode)
            Napier.i(tag = TAG, message = "apps: $apps, ips: $ipCount, domains: $domainCount for country code: $countryCode")
            appsCount = apps.toString()
            domainsCount = domainCount.toString()
            ipsCount = ipCount.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Proxy details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
        ) {
            if (showNoProxyFoundDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = "No proxy found") },
                    text = {
                        Text(
                            text = "Proxy information is missing for this proxy id. Please ensure that the proxy is configured correctly and try again."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onBackClick) {
                            Text(text = context.getString(R.string.ada_noapp_dialog_positive))
                        }
                    }
                )
            }
            StatsRow(appsCount, domainsCount, ipsCount)
            Spacer(modifier = Modifier.height(12.dp))
            DetailsSection(countryCode, proxyError)
            Spacer(modifier = Modifier.height(16.dp))
            ActionButton(onClick = {
                Utilities.showToastUiCentered(
                    context,
                    "Apps part of other proxy/excluded from proxy will be listed here",
                    Toast.LENGTH_LONG
                )
            })
        }
    }
}

@Composable
private fun StatsRow(appsCount: String, domainsCount: String, ipsCount: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
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
private fun DetailsSection(countryCode: String, proxyError: String) {
    Column(
        modifier =
            Modifier
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
        DetailRow(label = "Country", value = countryCode.uppercase())
        DetailRow(label = "Latency", value = "37 ms")
        DetailRow(label = "Last connected", value = "2 min ago")
        Spacer(modifier = Modifier.height(12.dp))
        DetailRow(label = "Status", value = "CONNECTED", valueColor = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun ActionButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_loop_back_app),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = "Select Apps For Proxy")
    }
}

private const val TAG = "RpnWinProxyDetails"

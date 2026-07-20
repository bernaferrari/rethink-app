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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.components.CountryRow
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnCountriesScreen(
    onBackClick: () -> Unit,
    onServerDetails: (String) -> Unit = {}
) {
    var countries by remember { mutableStateOf<List<CountryConfig>>(emptyList()) }
    var showNoCountriesDialog by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadToken) {
        countries = withContext(Dispatchers.IO) {
            runCatching { RpnProxyManager.getWinServers() }
                .getOrElse { errorMessage = it.message ?: "Unable to load servers"; emptyList() }
                .filter { it.cc.isNotBlank() && it.key.isNotBlank() }
                .sortedWith(compareByDescending<CountryConfig> { it.isEnabled }.thenBy { it.name }.thenBy { it.city })
        }
        if (countries.isEmpty()) {
            showNoCountriesDialog = true
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.lbl_countries),
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            if (busyKey == null) {
                                busyKey = "refresh"
                                scope.launch {
                                    val refreshed = withContext(Dispatchers.IO) {
                                        runCatching { RpnProxyManager.updateWinProxy() }
                                    }
                                    busyKey = null
                                    refreshed.onFailure { errorMessage = it.message ?: "Unable to refresh servers" }
                                    reloadToken++
                                }
                            }
                        }
                    ) {
                        if (busyKey == "refresh") {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        } else {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_refresh_white),
                                contentDescription = stringResource(R.string.rules_load_failure_reload)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            if (showNoCountriesDialog) {
                RethinkConfirmDialog(
                    onDismissRequest = {},
                    title = stringResource(id = R.string.rpn_no_countries_title),
                    message = stringResource(id = R.string.rpn_no_countries_desc),
                    confirmText = stringResource(id = R.string.dns_info_positive),
                    onConfirm = onBackClick
                )
            }
            errorMessage?.let { message ->
                RethinkConfirmDialog(
                    onDismissRequest = { errorMessage = null },
                    title = stringResource(R.string.rpn_no_countries_title),
                    message = message,
                    confirmText = stringResource(R.string.ada_noapp_dialog_positive),
                    onConfirm = { errorMessage = null }
                )
            }
            androidx.compose.material3.Surface(
                modifier = Modifier.padding(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(Dimensions.spacingLg)) {
                    Text(
                        text = stringResource(id = R.string.lbl_countries),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.rpn_availability_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        enabled = busyKey == null,
                        onClick = {
                            busyKey = "reset"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { RpnProxyManager.resetAndRefetchRpn() }
                                }
                                busyKey = null
                                result.onFailure { errorMessage = it.message ?: "Unable to reset RPN" }
                                reloadToken++
                            }
                        }
                    ) {
                        Text(if (busyKey == "reset") "Resetting…" else "Reset server list")
                    }
                }
            }

            CountriesList(
                countries = countries,
                busyKey = busyKey,
                onToggle = { server, enabled ->
                    busyKey = server.key
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (enabled) RpnProxyManager.enableWinServer(server.key)
                            else RpnProxyManager.disableWinServer(server.key)
                        }
                        busyKey = null
                        if (!result.first) errorMessage = result.second
                        reloadToken++
                    }
                },
                onDetails = { onServerDetails(it.cc) },
                modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)
            )
        }
    }
}

@Composable
private fun CountriesList(
    countries: List<CountryConfig>,
    busyKey: String?,
    onToggle: (CountryConfig, Boolean) -> Unit,
    onDetails: (CountryConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(countries, key = { it.key }) { server ->
            CountryRow(
                conf = server.cc.uppercase(),
                isSelected = server.isEnabled,
                name = server.name.ifBlank { server.cc.uppercase() },
                city = server.city,
                enabled = busyKey == null || busyKey == server.key,
                onToggle = { onToggle(server, it) },
                onClick = { onDetails(server) }
            )
        }
    }
}

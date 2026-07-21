package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compose replacement for the upstream RPN server-selection fragment. */
@Composable
fun RpnCountriesScreen(
    onBackClick: () -> Unit,
    onServerDetails: (String) -> Unit = {},
    onServerSettings: () -> Unit = {},
) {
    var servers by remember { mutableStateOf<List<CountryConfig>>(emptyList()) }
    var frequent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadToken) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                RpnProxyManager.getWinServers() to RpnProxyManager.getFrequentCountryCodes().toSet()
            }
        }
        result.onSuccess { (loaded, frequentCodes) ->
            servers = loaded.sortedWith(compareByDescending<CountryConfig> { it.isEnabled }
                .thenByDescending { it.isFavourite }.thenBy { it.name }.thenBy { it.city })
            frequent = frequentCodes
        }.onFailure { errorMessage = it.message ?: "Unable to load RPN servers" }
    }

    val visible = servers.filter { server ->
        val textMatches = query.isBlank() || listOf(server.name, server.cc, server.city, server.serverLocation)
            .any { it.contains(query, ignoreCase = true) }
        textMatches && (!favouritesOnly || server.isFavourite)
    }

    Scaffold(
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(R.string.lbl_countries),
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onServerSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "RPN server settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search countries and locations") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = favouritesOnly, onClick = { favouritesOnly = !favouritesOnly }, label = { Text("Favourites") })
                Row {
                    TextButton(enabled = busyKey == null, onClick = {
                        busyKey = "refresh"
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { RpnProxyManager.updateWinProxy() } }
                                .onFailure { errorMessage = it.message ?: "Unable to refresh servers" }
                            busyKey = null; reloadToken++
                        }
                    }) { Text(if (busyKey == "refresh") "Refreshing…" else "Refresh") }
                    TextButton(enabled = busyKey == null, onClick = {
                        busyKey = "reset"
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { RpnProxyManager.resetAndRefetchRpn() } }
                                .onFailure { errorMessage = it.message ?: "Unable to reset RPN" }
                            busyKey = null; reloadToken++
                        }
                    }) { Text(if (busyKey == "reset") "Resetting…" else "Reset") }
                }
            }
            if (errorMessage != null) {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        isFrequent = server.cc in frequent,
                        enabled = busyKey == null || busyKey == server.key,
                        onToggle = { enabled ->
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
                        onFavourite = {
                            scope.launch { withContext(Dispatchers.IO) { RpnProxyManager.setCountryFavourite(server.cc, !server.isFavourite) }; reloadToken++ }
                        },
                        onDetails = { onServerDetails(server.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: CountryConfig,
    isFrequent: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onFavourite: () -> Unit,
    onDetails: () -> Unit,
) {
    Surface(onClick = onDetails, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(server.name.ifBlank { if (server.id.equals("AUTO", true)) "Automatic location" else server.cc }, style = MaterialTheme.typography.titleSmall)
                Text(listOf(server.serverLocation, if (isFrequent) "Frequently used" else null).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled = enabled && server.cc.isNotBlank(), onClick = onFavourite) {
                Icon(if (server.isFavourite) Icons.Default.Star else Icons.Outlined.StarBorder, contentDescription = "Favourite server")
            }
            Switch(checked = server.isEnabled, enabled = enabled, onCheckedChange = onToggle)
        }
    }
}

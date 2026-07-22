package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.database.CountryConfig
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.rpnproxy.RpnProxyManager.DnsMode
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkActionListItem
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkToggleListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compose replacement for the upstream RPN server-settings and country-exclusion sheets. */
@Composable
fun RpnServerSettingsScreen(persistentState: PersistentState, onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dnsModes by remember { mutableStateOf(DnsMode.setFromCsv(persistentState.rpnDnsTunTypes)) }
    var excluded by remember { mutableStateOf(persistentState.rpnAutoExcludedCcs.split(',').filter(String::isNotBlank).toSet()) }
    var countries by remember { mutableStateOf<List<CountryConfig>>(emptyList()) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        countries = withContext(Dispatchers.IO) { RpnProxyManager.getWinServers().filter { it.cc.isNotBlank() }.distinctBy { it.cc }.sortedBy { it.name } }
    }

    Scaffold(topBar = { RethinkTopBar(title = "RPN server settings", onBackClick = onBackClick) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeaderWithSubtitle(
                    title = "DNS filtering",
                    subtitle = "Choose which DNS traffic RPN filters while connected.",
                )
            }
            item {
                RethinkListGroup {
                    DnsMode.entries.forEachIndexed { index, mode ->
                        RethinkToggleListItem(
                            title = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                            description = mode.tunType,
                            icon = Icons.Default.Security,
                            checked = mode in dnsModes,
                            onCheckedChange = { checked ->
                                dnsModes = dnsModes.toMutableSet().apply { if (checked) add(mode) else remove(mode) }
                                persistentState.rpnDnsTunTypes = DnsMode.tunTypesFromSet(dnsModes)
                            },
                            position = cardPositionFor(index, DnsMode.entries.size - 1),
                        )
                    }
                }
            }
            item { SectionHeaderWithSubtitle(title = "Configuration", subtitle = "Control how RPN chooses and refreshes locations.") }
            item {
                RethinkListGroup {
                    RethinkToggleListItem(
                        title = "Manual server configuration",
                        description = "Keep location choices under your control.",
                        icon = Icons.Default.Tune,
                        checked = persistentState.rpnConfigHandlingManual,
                        onCheckedChange = { persistentState.rpnConfigHandlingManual = it },
                        position = CardPosition.First,
                    )
                    RethinkToggleListItem(
                        title = "Always change identity",
                        description = "Use a fresh identity whenever RPN reconnects.",
                        icon = Icons.Default.Security,
                        checked = persistentState.rpnAlwaysChangeIdentity,
                        onCheckedChange = { persistentState.rpnAlwaysChangeIdentity = it },
                        position = CardPosition.Middle,
                    )
                    RethinkActionListItem(
                        title = "Automatic location exclusions",
                        description = if (excluded.isEmpty()) "No countries excluded" else "${excluded.size} countries excluded",
                        icon = Icons.Default.EditLocationAlt,
                        position = CardPosition.Last,
                        onClick = { showExcludeDialog = true },
                    )
                }
            }
            item { SectionHeaderWithSubtitle(title = "Maintenance", subtitle = "Refresh RPN registration and location data without affecting your subscription.") }
            item {
                RethinkActionListItem(
                    title = "Reset RPN configuration",
                    description = "Fetch a fresh registration and server list.",
                    icon = Icons.Default.RestartAlt,
                    accentColor = MaterialTheme.colorScheme.error,
                    enabled = !working,
                    position = CardPosition.Single,
                    onClick = { showResetDialog = true },
                )
            }
            message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) } }
        }
    }
    if (showExcludeDialog) CountryExclusionDialog(countries, excluded, onDismiss = { showExcludeDialog = false }) { selected ->
        excluded = selected
        persistentState.rpnAutoExcludedCcs = selected.sorted().joinToString(",")
        showExcludeDialog = false
    }
    if (showResetDialog) AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = { Text("Reset RPN?") },
        text = { Text("This refreshes the registration and server configuration. Your subscription remains unchanged.") },
        confirmButton = { TextButton(onClick = {
            showResetDialog = false; working = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { RpnProxyManager.resetAndRefetchRpn() } }
                    .onFailure { message = it.message ?: "RPN reset failed" }
                working = false
            }
        }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
    )
}

@Composable private fun CountryExclusionDialog(countries: List<CountryConfig>, current: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exclude automatic locations") },
        text = { LazyColumn(Modifier.heightIn(max = 440.dp)) { items(countries, key = { it.cc }) { country ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(country.cc in selected, onCheckedChange = { selected = selected.toMutableSet().apply { if (it) add(country.cc) else remove(country.cc) } })
                Text(country.name.ifBlank { country.cc })
            }
        } } },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

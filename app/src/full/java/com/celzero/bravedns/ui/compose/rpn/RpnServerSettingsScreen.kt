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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
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
    val context = LocalContext.current
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

    Scaffold(topBar = { RethinkTopBar(title = stringResource(R.string.rpn_server_settings_title), onBackClick = onBackClick) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeaderWithSubtitle(
                    title = stringResource(R.string.rpn_server_dns_filtering_title),
                    subtitle = stringResource(R.string.rpn_server_dns_filtering_desc),
                )
            }
            item {
                RethinkListGroup {
                    DnsMode.entries.forEachIndexed { index, mode ->
                        RethinkToggleListItem(
                            title = dnsModeTitle(context, mode),
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
            item { SectionHeaderWithSubtitle(title = stringResource(R.string.rpn_server_configuration_title), subtitle = stringResource(R.string.rpn_server_configuration_desc)) }
            item {
                RethinkListGroup {
                    RethinkToggleListItem(
                        title = stringResource(R.string.rpn_server_manual_configuration),
                        description = stringResource(R.string.rpn_server_manual_configuration_desc),
                        icon = Icons.Default.Tune,
                        checked = persistentState.rpnConfigHandlingManual,
                        onCheckedChange = { persistentState.rpnConfigHandlingManual = it },
                        position = CardPosition.First,
                    )
                    RethinkToggleListItem(
                        title = stringResource(R.string.rpn_server_change_identity),
                        description = stringResource(R.string.rpn_server_change_identity_desc),
                        icon = Icons.Default.Security,
                        checked = persistentState.rpnAlwaysChangeIdentity,
                        onCheckedChange = { persistentState.rpnAlwaysChangeIdentity = it },
                        position = CardPosition.Middle,
                    )
                    RethinkActionListItem(
                        title = stringResource(R.string.rpn_server_auto_exclusions),
                        description = if (excluded.isEmpty()) stringResource(R.string.rpn_server_no_exclusions) else stringResource(R.string.rpn_server_exclusions_count, excluded.size),
                        icon = Icons.Default.EditLocationAlt,
                        position = CardPosition.Last,
                        onClick = { showExcludeDialog = true },
                    )
                }
            }
            item { SectionHeaderWithSubtitle(title = stringResource(R.string.rpn_server_maintenance_title), subtitle = stringResource(R.string.rpn_server_maintenance_desc)) }
            item {
                RethinkActionListItem(
                    title = stringResource(R.string.rpn_server_reset_configuration),
                    description = stringResource(R.string.rpn_server_reset_configuration_desc),
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
        title = { Text(stringResource(R.string.rpn_server_reset_confirmation_title)) },
        text = { Text(stringResource(R.string.rpn_server_reset_confirmation_desc)) },
        confirmButton = { TextButton(onClick = {
            showResetDialog = false; working = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { RpnProxyManager.resetAndRefetchRpn() } }
                    .onFailure { message = it.message ?: context.getString(R.string.rpn_server_reset_failed) }
                working = false
            }
        }) { Text(stringResource(R.string.rpn_server_reset_configuration)) } },
        dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}

@Composable private fun CountryExclusionDialog(countries: List<CountryConfig>, current: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rpn_server_exclude_locations)) },
        text = { LazyColumn(Modifier.heightIn(max = 440.dp)) { items(countries, key = { it.cc }) { country ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(country.cc in selected, onCheckedChange = { selected = selected.toMutableSet().apply { if (it) add(country.cc) else remove(country.cc) } })
                Text(country.name.ifBlank { country.cc })
            }
        } } },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.lbl_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lbl_cancel)) } },
    )
}

private fun dnsModeTitle(context: android.content.Context, mode: DnsMode): String =
    context.getString(
        when (mode) {
            DnsMode.DEFAULT -> R.string.rpn_server_dns_mode_default
            DnsMode.PRIVACY -> R.string.rpn_server_dns_mode_privacy
            DnsMode.PARENTAL -> R.string.rpn_server_dns_mode_parental
            DnsMode.SECURITY -> R.string.rpn_server_dns_mode_security
        }
    )

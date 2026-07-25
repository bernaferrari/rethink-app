/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.rpn

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkRpnDnsMode { Default, Privacy, Parental, Security }

data class RethinkRpnCountryOption(val code: String, val name: String)

data class RethinkRpnServerSettingsStrings(
    val title: String,
    val dnsFilteringTitle: String,
    val dnsFilteringDescription: String,
    val defaultDnsMode: String,
    val privacyDnsMode: String,
    val parentalDnsMode: String,
    val securityDnsMode: String,
    val configurationTitle: String,
    val configurationDescription: String,
    val manualTitle: String,
    val manualDescription: String,
    val changeIdentityTitle: String,
    val changeIdentityDescription: String,
    val exclusionsTitle: String,
    val noExclusions: String,
    val exclusionsCount: (Int) -> String,
    val maintenanceTitle: String,
    val maintenanceDescription: String,
    val resetTitle: String,
    val resetDescription: String,
    val resetConfirmationTitle: String,
    val resetConfirmationDescription: String,
    val excludeLocationsTitle: String,
    val save: String,
    val cancel: String,
)

/** Shared RPN server preferences, including exclusion and destructive-action dialogs. */
@Composable
fun RethinkRpnServerSettingsScreen(
    selectedDnsModes: Set<RethinkRpnDnsMode>,
    manualConfiguration: Boolean,
    alwaysChangeIdentity: Boolean,
    excludedCountries: Set<String>,
    countries: List<RethinkRpnCountryOption>,
    working: Boolean,
    message: String?,
    strings: RethinkRpnServerSettingsStrings,
    onDnsModesChange: (Set<RethinkRpnDnsMode>) -> Unit,
    onManualConfigurationChange: (Boolean) -> Unit,
    onAlwaysChangeIdentityChange: (Boolean) -> Unit,
    onExcludedCountriesChange: (Set<String>) -> Unit,
    onReset: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExclusions by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    RethinkLazyColumnScreenScaffold(
        modifier = modifier,
        topBar = { RethinkTopBar(strings.title, onBackClick = onBackClick) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            bottom = SharedDimensions.spacing3xl,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
            item { SettingsSectionHeader(strings.dnsFilteringTitle, strings.dnsFilteringDescription) }
            item {
                RethinkRpnDnsMode.entries.forEachIndexed { index, mode ->
                    RpnToggleRow(
                        title = strings.dnsModeTitle(mode),
                        description = mode.name,
                        icon = MaterialSymbols.Filled.Security,
                        checked = mode in selectedDnsModes,
                        position = cardPositionFor(index, RethinkRpnDnsMode.entries.lastIndex),
                        onCheckedChange = { checked ->
                            onDnsModesChange(selectedDnsModes.toMutableSet().apply { if (checked) add(mode) else remove(mode) })
                        },
                    )
                }
            }
            item { SettingsSectionHeader(strings.configurationTitle, strings.configurationDescription) }
            item {
                RpnToggleRow(strings.manualTitle, strings.manualDescription, MaterialSymbols.Filled.Tune, manualConfiguration, CardPosition.First, onManualConfigurationChange)
                RpnToggleRow(strings.changeIdentityTitle, strings.changeIdentityDescription, MaterialSymbols.Filled.Security, alwaysChangeIdentity, CardPosition.Middle, onAlwaysChangeIdentityChange)
                RpnActionRow(
                    title = strings.exclusionsTitle,
                    description = if (excludedCountries.isEmpty()) strings.noExclusions else strings.exclusionsCount(excludedCountries.size),
                    icon = MaterialSymbols.Filled.EditLocationAlt,
                    position = CardPosition.Last,
                    onClick = { showExclusions = true },
                )
            }
            item { SettingsSectionHeader(strings.maintenanceTitle, strings.maintenanceDescription) }
            item {
                RpnActionRow(
                    title = strings.resetTitle,
                    description = strings.resetDescription,
                    icon = MaterialSymbols.Filled.RestartAlt,
                    position = CardPosition.Single,
                    enabled = !working,
                    destructive = true,
                    onClick = { showReset = true },
                )
            }
            message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = SharedDimensions.spacingSm)) } }
    }
    if (showExclusions) {
        RethinkCountryExclusionDialog(countries, excludedCountries, strings, onDismiss = { showExclusions = false }) { selected ->
            onExcludedCountriesChange(selected)
            showExclusions = false
        }
    }
    if (showReset) {
        RethinkConfirmDialog(
            onDismissRequest = { showReset = false },
            title = strings.resetConfirmationTitle,
            message = strings.resetConfirmationDescription,
            confirmText = strings.resetTitle,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = { showReset = false; onReset() },
            onDismiss = { showReset = false },
        )
    }
}

private fun RethinkRpnServerSettingsStrings.dnsModeTitle(mode: RethinkRpnDnsMode) = when (mode) {
    RethinkRpnDnsMode.Default -> defaultDnsMode
    RethinkRpnDnsMode.Privacy -> privacyDnsMode
    RethinkRpnDnsMode.Parental -> parentalDnsMode
    RethinkRpnDnsMode.Security -> securityDnsMode
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = SharedDimensions.spacingMd, start = SharedDimensions.spacingLg, bottom = SharedDimensions.spacingXs)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RpnToggleRow(
    title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean, position: CardPosition, onCheckedChange: (Boolean) -> Unit,
) {
    RethinkListItem(
        headline = title, supporting = description, position = position, onClick = { onCheckedChange(!checked) },
        leadingContent = { RethinkSharedIconContainer(MaterialTheme.colorScheme.primary) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun RpnActionRow(
    title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    position: CardPosition, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    RethinkListItem(
        headline = title, supporting = description, position = position, enabled = enabled, onClick = onClick,
        leadingContent = { RethinkSharedIconContainer(accent) { Icon(icon, null, tint = accent) } },
        trailing = { Icon(MaterialSymbols.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun RethinkCountryExclusionDialog(
    countries: List<RethinkRpnCountryOption>, current: Set<String>, strings: RethinkRpnServerSettingsStrings,
    onDismiss: () -> Unit, onSave: (Set<String>) -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        verticalSpacing = SharedDimensions.spacingMd,
        includeBottomSpacer = false,
    ) {
        Text(strings.excludeLocationsTitle, style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            // Keep the actions visible at phone height; this list, rather than the sheet, scrolls.
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            items(countries, key = RethinkRpnCountryOption::code) { country ->
                val checked = country.code in selected
                RethinkListItem(
                    headline = country.name,
                    position = CardPosition.Single,
                    highlighted = checked,
                    onClick = {
                        selected = selected.toMutableSet().apply {
                            if (checked) remove(country.code) else add(country.code)
                        }
                    },
                    trailing = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selected = selected.toMutableSet().apply {
                                    if (isChecked) add(country.code) else remove(country.code)
                                }
                            },
                        )
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
            Button(onClick = { onSave(selected) }) { Text(strings.save) }
        }
    }
}

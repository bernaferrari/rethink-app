/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.compose.theme.RethinkSwitchListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkCheckboxListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen

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
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        rethinkSection(strings.dnsFilteringTitle, strings.dnsFilteringDescription) {
            RethinkListGroup {
                RethinkRpnDnsMode.entries.forEachIndexed { index, mode ->
                    RpnToggleRow(
                        title = strings.dnsModeTitle(mode),
                        description = "",
                        icon = MaterialSymbols.Filled.Dns,
                        checked = mode in selectedDnsModes,
                        position = cardPositionFor(index, RethinkRpnDnsMode.entries.lastIndex),
                        onCheckedChange = { checked ->
                            onDnsModesChange(selectedDnsModes.toMutableSet().apply { if (checked) add(mode) else remove(mode) })
                        },
                    )
                }
            }
        }
        rethinkSection(strings.configurationTitle, strings.configurationDescription) {
            RethinkListGroup {
                RpnToggleRow(strings.manualTitle, strings.manualDescription, MaterialSymbols.Filled.Tune, manualConfiguration, CardPosition.First, onManualConfigurationChange)
                RpnToggleRow(strings.changeIdentityTitle, strings.changeIdentityDescription, MaterialSymbols.Filled.Refresh, alwaysChangeIdentity, CardPosition.Middle, onAlwaysChangeIdentityChange)
                RpnActionRow(
                    title = strings.exclusionsTitle,
                    description = if (excludedCountries.isEmpty()) strings.noExclusions else strings.exclusionsCount(excludedCountries.size),
                    icon = MaterialSymbols.Filled.EditLocationAlt,
                    position = CardPosition.Last,
                    onClick = { showExclusions = true },
                )
            }
        }
        rethinkSection(strings.maintenanceTitle, strings.maintenanceDescription) {
            RethinkListGroup {
                RpnActionRow(
                    title = strings.resetTitle,
                    description = strings.resetDescription,
                    icon = MaterialSymbols.Filled.Refresh,
                    position = CardPosition.Single,
                    enabled = !working,
                    destructive = true,
                    onClick = { showReset = true },
                )
            }
            message?.let { text -> Text(text, color = MaterialTheme.colorScheme.error) }
        }
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
private fun RpnToggleRow(
    title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean, position: CardPosition, onCheckedChange: (Boolean) -> Unit,
) {
    RethinkSwitchListItem(
        headline = title, checked = checked, onCheckedChange = onCheckedChange,
        supporting = description.takeIf(String::isNotBlank), position = position, leadingIcon = icon,
    )
}

@Composable
private fun RpnActionRow(
    title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    position: CardPosition, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit,
) {
    RethinkListItem(
        headline = title, supporting = description, position = position, enabled = enabled, onClick = onClick,
        leadingIcon = icon,
        destructive = destructive,
        // Navigation rows get the standard chevron; destructive actions open a confirmation instead.
        showTrailingChevron = !destructive,
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
        expandOnShow = true,
    ) { dismissSheet ->
        RethinkSheetHeader(strings.excludeLocationsTitle)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight),
        ) {
            itemsIndexed(countries, key = { _, country -> country.code }) { index, country ->
                val checked = country.code in selected
                RethinkCheckboxListItem(
                    headline = country.name,
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        selected = selected.toMutableSet().apply {
                            if (isChecked) add(country.code) else remove(country.code)
                        }
                    },
                    // One grouped list (not a stack of single cards), one tone above the sheet.
                    position = cardPositionFor(index, countries.lastIndex),
                    highlighted = checked,
                )
            }
        }
        RethinkFormActionRow(
            confirmLabel = strings.save,
            onConfirm = { onSave(selected) },
            dismissLabel = strings.cancel,
            onDismiss = dismissSheet,
        )
    }
}

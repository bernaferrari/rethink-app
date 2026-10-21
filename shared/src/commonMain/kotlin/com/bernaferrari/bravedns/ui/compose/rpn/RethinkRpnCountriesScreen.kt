/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

data class RethinkRpnCountry(
    val id: String,
    val key: String,
    val name: String,
    val countryCode: String,
    val location: String,
    val isEnabled: Boolean,
    val isFavourite: Boolean,
    val isFrequent: Boolean,
    val isAutomatic: Boolean,
)

data class RethinkRpnCountriesStrings(
    val title: String,
    val settingsDescription: String,
    val searchHint: String,
    val clearSearchDescription: String,
    val favourites: String,
    val refresh: String,
    val refreshing: String,
    val reset: String,
    val resetting: String,
    val automaticLocation: String,
    val frequentlyUsed: String,
    val addFavourite: String,
    val removeFavourite: String,
    val resetConfirmationTitle: String,
    val resetConfirmationDescription: String,
    val cancel: String,
    val moreOptions: String,
    val noMatches: String,
    val clearFilters: String,
)

/** Shared RPN country selection, search, favourite, refresh, and list rendering. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkRpnCountriesScreen(
    countries: List<RethinkRpnCountry>,
    busyKey: String?,
    errorMessage: String?,
    strings: RethinkRpnCountriesStrings,
    onBackClick: () -> Unit,
    onServerDetails: (String) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onEnabledChange: (RethinkRpnCountry, Boolean) -> Unit,
    onFavouriteClick: (RethinkRpnCountry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val visible = countries.filter { country ->
        val queryMatches = query.isBlank() || listOf(country.name, country.countryCode, country.location)
            .any { it.contains(query, ignoreCase = true) }
        queryMatches && (!favouritesOnly || country.isFavourite)
    }
    val actionsEnabled = busyKey == null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = when (busyKey) {
                    "refresh" -> strings.refreshing
                    "reset" -> strings.resetting
                    else -> null
                },
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(MaterialSymbols.Filled.Settings, strings.settingsDescription)
                    }
                    // Refresh and Reset act on the whole configuration, not on the list filter,
                    // so they live in the overflow rather than next to the filter chips.
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(MaterialSymbols.Filled.MoreVert, strings.moreOptions)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (busyKey == "refresh") strings.refreshing else strings.refresh) },
                                leadingIcon = { Icon(MaterialSymbols.Filled.Refresh, null) },
                                enabled = actionsEnabled,
                                onClick = { menuExpanded = false; onRefresh() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (busyKey == "reset") strings.resetting else strings.reset) },
                                leadingIcon = { Icon(MaterialSymbols.Filled.RestartAlt, null) },
                                enabled = actionsEnabled,
                                onClick = { menuExpanded = false; showResetConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            item(key = "search") {
                RethinkSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = strings.searchHint,
                    modifier = Modifier.fillMaxWidth(),
                    clearQueryContentDescription = strings.clearSearchDescription,
                )
            }
            item(key = "filters") {
                RethinkFilterChip(
                    label = strings.favourites,
                    selected = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly },
                    leadingIcon = { Icon(MaterialSymbols.Filled.Star, null, Modifier.size(SharedDimensions.iconSizeXs)) },
                    modifier = Modifier.padding(vertical = SharedDimensions.spacingSm),
                )
            }
            errorMessage?.let { error ->
                item(key = "error") {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = SharedDimensions.spacingSm),
                    )
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    RethinkEmptyState(
                        title = strings.noMatches,
                        message = "",
                        icon = MaterialSymbols.Filled.Public,
                        ctaLabel = strings.clearFilters.takeIf { query.isNotBlank() || favouritesOnly },
                        onCtaClick = { query = ""; favouritesOnly = false },
                    )
                }
            }
            itemsIndexed(visible, key = { _, country -> country.id }) { index, country ->
                RethinkRpnCountryRow(
                    country = country,
                    position = cardPositionFor(index, visible.lastIndex),
                    enabled = actionsEnabled || busyKey == country.key,
                    strings = strings,
                    onToggle = { onEnabledChange(country, it) },
                    onFavourite = { onFavouriteClick(country) },
                    onDetails = { onServerDetails(country.key) },
                )
            }
        }
    }
    if (showResetConfirm) {
        RethinkConfirmDialog(
            onDismissRequest = { showResetConfirm = false },
            title = strings.resetConfirmationTitle,
            message = strings.resetConfirmationDescription,
            confirmText = strings.reset,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = { showResetConfirm = false; onReset() },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@Composable
private fun RethinkRpnCountryRow(
    country: RethinkRpnCountry,
    position: CardPosition,
    enabled: Boolean,
    strings: RethinkRpnCountriesStrings,
    onToggle: (Boolean) -> Unit,
    onFavourite: () -> Unit,
    onDetails: () -> Unit,
) {
    val label = when {
        country.name.isNotBlank() -> country.name
        country.isAutomatic -> strings.automaticLocation
        else -> country.countryCode
    }
    val supporting = listOfNotNull(country.location.takeIf { it.isNotBlank() }, strings.frequentlyUsed.takeIf { country.isFrequent })
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
    RethinkListItem(
        headline = label,
        supporting = supporting,
        position = position,
        enabled = enabled,
        // The star is a real toggle (announced as checked/unchecked), leading so the trailing
        // edge holds a single control: the enable switch.
        leadingContent = {
            IconToggleButton(
                checked = country.isFavourite,
                onCheckedChange = { onFavourite() },
                enabled = enabled && country.countryCode.isNotBlank(),
            ) {
                Icon(
                    if (country.isFavourite) MaterialSymbols.Filled.Star else MaterialSymbols.Outlined.StarBorder,
                    contentDescription = if (country.isFavourite) strings.removeFavourite else strings.addFavourite,
                    tint = if (country.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailing = {
            Switch(
                checked = country.isEnabled,
                enabled = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        onClick = onDetails,
    )
}

/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

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
)

/** Shared RPN country selection, search, favourite, refresh, and list rendering. */
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
    val visible = countries.filter { country ->
        val queryMatches = query.isBlank() || listOf(country.name, country.countryCode, country.location)
            .any { it.contains(query, ignoreCase = true) }
        queryMatches && (!favouritesOnly || country.isFavourite)
    }
    val actionsEnabled = busyKey == null

    Scaffold(
        modifier = modifier,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(MaterialSymbols.Filled.Settings, strings.settingsDescription)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        ) {
            RethinkSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.searchHint,
                modifier = Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingSm),
                clearQueryContentDescription = strings.clearSearchDescription,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                RethinkFilterChip(strings.favourites, favouritesOnly, onClick = { favouritesOnly = !favouritesOnly })
                TextButton(enabled = actionsEnabled, onClick = onRefresh) {
                    Text(if (busyKey == "refresh") strings.refreshing else strings.refresh)
                }
                TextButton(enabled = actionsEnabled, onClick = onReset) {
                    Text(if (busyKey == "reset") strings.resetting else strings.reset)
                }
            }
            errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = SharedDimensions.spacingSm))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
                modifier = Modifier.fillMaxSize(),
            ) {
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
    val countryShape = rethinkGroupedListShape(position)
    val label = when {
        country.name.isNotBlank() -> country.name
        country.isAutomatic -> strings.automaticLocation
        else -> country.countryCode
    }
    Surface(
        onClick = onDetails,
        shape = countryShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(countryShape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(SharedDimensions.cardPaddingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(country.location, strings.frequentlyUsed.takeIf { country.isFrequent })
                        .filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = enabled && country.countryCode.isNotBlank(), onClick = onFavourite) {
                Icon(
                    if (country.isFavourite) MaterialSymbols.Filled.Star else MaterialSymbols.Outlined.StarBorder,
                    if (country.isFavourite) strings.removeFavourite else strings.addFavourite,
                )
            }
            Switch(
                checked = country.isEnabled,
                enabled = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

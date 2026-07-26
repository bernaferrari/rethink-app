package com.bernaferrari.bravedns.ui.compose.rpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android RPN repository adapter for the target-neutral country-selection screen. */
@Composable
fun RpnCountriesScreen(
    onBackClick: () -> Unit,
    onServerDetails: (String) -> Unit = {},
    onServerSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf<List<CountryConfig>>(emptyList()) }
    var frequent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadToken) {
        val result = withContext(Dispatchers.IO) {
            runCatching { RpnProxyManager.getWinServers() to RpnProxyManager.getFrequentCountryCodes().toSet() }
        }
        result.onSuccess { (loaded, frequentCodes) ->
            servers = loaded.sortedWith(
                compareByDescending<CountryConfig> { it.isEnabled }
                    .thenByDescending { it.isFavourite }
                    .thenBy { it.name }
                    .thenBy { it.city },
            )
            frequent = frequentCodes
        }.onFailure { errorMessage = it.message ?: context.getString(R.string.rpn_countries_load_failed) }
    }

    RethinkRpnCountriesScreen(
        countries = servers.map { it.toRethinkRpnCountry(frequent) },
        busyKey = busyKey,
        errorMessage = errorMessage,
        strings = RethinkRpnCountriesStrings(
            title = stringResource(R.string.lbl_countries),
            settingsDescription = stringResource(R.string.rpn_countries_settings),
            searchHint = stringResource(R.string.rpn_countries_search),
            clearSearchDescription = stringResource(R.string.rpn_countries_clear_search),
            favourites = stringResource(R.string.rpn_countries_favourites),
            refresh = stringResource(R.string.rpn_countries_refresh),
            refreshing = stringResource(R.string.rpn_countries_refreshing),
            reset = stringResource(R.string.rpn_server_reset_configuration),
            resetting = stringResource(R.string.rpn_countries_resetting),
            automaticLocation = stringResource(R.string.rpn_countries_automatic_location),
            frequentlyUsed = stringResource(R.string.rpn_countries_frequently_used),
            addFavourite = stringResource(R.string.rpn_countries_add_favourite),
            removeFavourite = stringResource(R.string.rpn_countries_remove_favourite),
        ),
        onBackClick = onBackClick,
        onServerDetails = onServerDetails,
        onSettings = onServerSettings,
        onRefresh = {
            busyKey = "refresh"
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { RpnProxyManager.updateWinProxy() } }
                    .onFailure { errorMessage = it.message ?: context.getString(R.string.rpn_countries_refresh_failed) }
                busyKey = null
                reloadToken++
            }
        },
        onReset = {
            busyKey = "reset"
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { RpnProxyManager.resetAndRefetchRpn() } }
                    .onFailure { errorMessage = it.message ?: context.getString(R.string.rpn_countries_reset_failed) }
                busyKey = null
                reloadToken++
            }
        },
        onEnabledChange = { country, enabled ->
            busyKey = country.key
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    if (enabled) RpnProxyManager.enableWinServer(country.key) else RpnProxyManager.disableWinServer(country.key)
                }
                busyKey = null
                if (!result.first) errorMessage = result.second
                reloadToken++
            }
        },
        onFavouriteClick = { country ->
            scope.launch {
                withContext(Dispatchers.IO) { RpnProxyManager.setCountryFavourite(country.countryCode, !country.isFavourite) }
                reloadToken++
            }
        },
    )
}

private fun CountryConfig.toRethinkRpnCountry(frequent: Set<String>) = RethinkRpnCountry(
    id = id,
    key = key,
    name = name,
    countryCode = cc,
    location = serverLocation,
    isEnabled = isEnabled,
    isFavourite = isFavourite,
    isFrequent = cc in frequent,
    isAutomatic = id.equals("AUTO", ignoreCase = true),
)

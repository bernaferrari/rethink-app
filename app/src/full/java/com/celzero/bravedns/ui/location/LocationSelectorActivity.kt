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
package com.celzero.bravedns.ui.location

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import org.koin.android.ext.android.inject

class LocationSelectorActivity : AppCompatActivity() {

    private val persistentState by inject<PersistentState>()
    private var countries by mutableStateOf<List<Country>>(emptyList())

    companion object {
        private const val MAX_SELECTIONS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        loadSampleData()

        setContent {
            RethinkTheme {
                LocationSelectorScreen()
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    private fun LocationSelectorScreen() {
        val selectedCount = remember(countries) { getTotalSelectedServers(countries) }
        val scale = remember { Animatable(1f) }
        val serverLabel =
            if (selectedCount == 1) {
                stringResourceCompat(R.string.location_selector_server)
            } else {
                stringResourceCompat(R.string.location_selector_servers)
            }

        LaunchedEffect(selectedCount) {
            scale.snapTo(1f)
            scale.animateTo(1.15f, animationSpec = tween(durationMillis = 180))
            scale.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = stringResourceCompat(R.string.location_selector_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_on_24),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$selectedCount $serverLabel selected",
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale.value),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResourceCompat(R.string.location_selector_max, MAX_SELECTIONS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(countries, key = { it.id }) { country ->
                    CountryCard(country, onToggle = { toggleCountryExpansion(country) }) { server ->
                        handleServerSelection(server)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    @Composable
    private fun CountryCard(
        country: Country,
        onToggle: () -> Unit,
        onServerToggle: (ServerLocation) -> Unit
    ) {
        val rotation = if (country.isExpanded) 180f else 0f
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_flag_placeholder),
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 32.dp, height = 24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = country.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResourceCompat(
                                R.string.location_selector_servers_available,
                                country.serverCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24),
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                AnimatedVisibility(visible = country.isExpanded) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        country.servers.forEach { server ->
                            ServerRow(server, onServerToggle)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ServerRow(server: ServerLocation, onToggle: (ServerLocation) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable { onToggle(server) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = server.latency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Checkbox(
                checked = server.isSelected,
                onCheckedChange = { onToggle(server) }
            )
        }
    }

    private fun toggleCountryExpansion(country: Country) {
        countries = countries.map {
            if (it.id == country.id) it.copy(isExpanded = !it.isExpanded) else it
        }
    }

    private fun handleServerSelection(server: ServerLocation) {
        val selectedCount = getTotalSelectedServers(countries)
        val isSelecting = !server.isSelected
        if (isSelecting && selectedCount >= MAX_SELECTIONS) {
            Toast.makeText(this, getString(R.string.location_selector_max_toast), Toast.LENGTH_SHORT).show()
            return
        }

        if (!isSelecting && selectedCount <= 1) {
            Toast.makeText(this, getString(R.string.location_selector_min_toast), Toast.LENGTH_SHORT).show()
            return
        }

        countries = countries.map { country ->
            if (country.servers.any { it.id == server.id }) {
                country.copy(
                    servers = country.servers.map {
                        if (it.id == server.id) it.copy(isSelected = !it.isSelected) else it
                    }
                )
            } else {
                country
            }
        }
    }

    private fun loadSampleData() {
        val sampleCountries = mutableListOf<Country>()
        val usServers = listOf(
            ServerLocation("us-ny", "New York", "25ms"),
            ServerLocation("us-ca", "California", "35ms"),
            ServerLocation("us-tx", "Texas", "40ms"),
            ServerLocation("us-fl", "Florida", "45ms")
        )
        sampleCountries.add(Country("us", "United States", "flag_us", usServers))

        val ukServers = listOf(
            ServerLocation("uk-london", "London", "15ms"),
            ServerLocation("uk-manchester", "Manchester", "20ms"),
            ServerLocation("uk-edinburgh", "Edinburgh", "22ms")
        )
        sampleCountries.add(Country("uk", "United Kingdom", "flag_uk", ukServers))

        val deServers = listOf(
            ServerLocation("de-berlin", "Berlin", "18ms"),
            ServerLocation("de-munich", "Munich", "20ms"),
            ServerLocation("de-frankfurt", "Frankfurt", "16ms"),
            ServerLocation("de-hamburg", "Hamburg", "19ms")
        )
        sampleCountries.add(Country("de", "Germany", "flag_de", deServers))

        val jpServers = listOf(
            ServerLocation("jp-tokyo", "Tokyo", "12ms"),
            ServerLocation("jp-osaka", "Osaka", "15ms")
        )
        sampleCountries.add(Country("jp", "Japan", "flag_jp", jpServers))

        val auServers = listOf(
            ServerLocation("au-sydney", "Sydney", "30ms"),
            ServerLocation("au-melbourne", "Melbourne", "32ms"),
            ServerLocation("au-perth", "Perth", "45ms")
        )
        sampleCountries.add(Country("au", "Australia", "flag_au", auServers))

        val caServers = listOf(
            ServerLocation("ca-toronto", "Toronto", "28ms"),
            ServerLocation("ca-vancouver", "Vancouver", "35ms")
        )
        sampleCountries.add(Country("ca", "Canada", "flag_ca", caServers))

        countries = sampleCountries
    }

    private fun getTotalSelectedServers(list: List<Country>): Int {
        return list.sumOf { country -> country.servers.count { it.isSelected } }
    }

    @Composable
    private fun stringResourceCompat(id: Int, vararg args: Any): String {
        val context = LocalContext.current
        return if (args.isNotEmpty()) {
            context.getString(id, *args)
        } else {
            context.getString(id)
        }
    }
}

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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.CountryRow
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class RpnCountriesActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

    private var countries by mutableStateOf<List<String>>(emptyList())
    private var selectedCountries by mutableStateOf<Set<String>>(emptySet())

    companion object {
        private const val TAG = "RpncUi"
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
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

        lifecycleScope.launch {
            fetchProxyCountries()
            if (countries.isEmpty()) {
                showNoProxyCountriesDialog()
            }
        }

        setContent {
            RethinkTheme {
                RpnCountriesScreen()
            }
        }
    }

    private suspend fun fetchProxyCountries() {
        val ccs = emptyList<String>()
        val selectedCCs = emptyList<String>()
        withContext(Dispatchers.Main) {
            countries = ccs
            selectedCountries = selectedCCs.toSet()
        }
    }

    private fun showNoProxyCountriesDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("No countries available")
            .setMessage("No countries available for RPN. Please try again later.")
            .setPositiveButton(R.string.dns_info_positive) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    @Composable
    private fun RpnCountriesScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResourceCompat(R.string.lbl_countries),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            CountriesList()
        }
    }

    @Composable
    private fun CountriesList() {
        val list = countries
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list.size) { index ->
                val country = list[index]
                CountryRow(country, selectedCountries.contains(country))
            }
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }
}

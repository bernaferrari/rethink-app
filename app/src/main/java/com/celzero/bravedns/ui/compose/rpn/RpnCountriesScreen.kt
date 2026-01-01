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
package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.CountryRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpnCountriesScreen(onBackClick: () -> Unit) {
    var countries by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCountries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showNoCountriesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val ccs = emptyList<String>()
        val selectedCCs = emptyList<String>()
        countries = ccs
        selectedCountries = selectedCCs.toSet()
        if (countries.isEmpty()) {
            showNoCountriesDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.lbl_countries)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            if (showNoCountriesDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(text = "No countries available") },
                    text = { Text(text = "No countries available for RPN. Please try again later.") },
                    confirmButton = {
                        TextButton(onClick = onBackClick) {
                            Text(text = stringResource(id = R.string.dns_info_positive))
                        }
                    }
                )
            }
            Text(
                text = stringResource(id = R.string.lbl_countries),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
            )
            CountriesList(countries, selectedCountries)
        }
    }
}

@Composable
private fun CountriesList(countries: List<String>, selectedCountries: Set<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(countries.size) { index ->
            val country = countries[index]
            CountryRow(country, selectedCountries.contains(country))
        }
    }
}

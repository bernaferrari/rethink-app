/*
 * Copyright 2024 RethinkDNS and its authors
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import org.koin.android.ext.android.inject

class AdvancedSettingActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()

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

        setContent {
            RethinkTheme {
                AdvancedSettingsScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AdvancedSettingsScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.lbl_network),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (DEBUG) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            SettingToggleRow(
                                icon = Icons.Filled.Build,
                                title = stringResource(R.string.adv_set_experimental_title),
                                description = stringResource(R.string.adv_set_experimental_desc),
                                checked = persistentState.nwEngExperimentalFeatures,
                                onCheckedChange = { persistentState.nwEngExperimentalFeatures = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            SettingToggleRow(
                                icon = Icons.Filled.Tune,
                                title = stringResource(R.string.set_auto_dial_title),
                                description = stringResource(R.string.set_auto_dial_desc),
                                checked = persistentState.autoDialsParallel,
                                onCheckedChange = { persistentState.autoDialsParallel = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            SettingToggleRow(
                                icon = Icons.Filled.Warning,
                                title = "Randomly Panic",
                                description = "Enable random panic in tunnel.",
                                checked = persistentState.panicRandom,
                                onCheckedChange = { persistentState.panicRandom = it }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingToggleRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        var localChecked by remember { mutableStateOf(checked) }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable {
                        localChecked = !localChecked
                        onCheckedChange(localChecked)
                    }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = localChecked,
                onCheckedChange = {
                    localChecked = it
                    onCheckedChange(it)
                }
            )
        }
    }
}

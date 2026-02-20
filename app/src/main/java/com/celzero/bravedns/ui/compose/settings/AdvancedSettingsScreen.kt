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
package com.celzero.bravedns.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.celzero.bravedns.ui.compose.theme.Dimensions
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar

@Composable
fun AdvancedSettingsScreen(
    persistentState: PersistentState,
    onBackClick: (() -> Unit)? = null
) {
    if (!DEBUG) {
        return
    }

    var experimentalEnabled by remember { mutableStateOf(persistentState.nwEngExperimentalFeatures) }
    var autoDialEnabled by remember { mutableStateOf(persistentState.autoDialsParallel) }
    var panicEnabled by remember { mutableStateOf(persistentState.panicRandom) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(
                title = stringResource(id = R.string.lbl_advanced).replaceFirstChar { it.uppercase() },
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenPaddingHorizontal)
                    .padding(top = Dimensions.spacingMd)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadiusLarge)
                    )
                    .padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacing2xl)
            ) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Text(
                        text = stringResource(id = R.string.lbl_advanced).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.Text(
                        text = stringResource(id = R.string.adv_set_experimental_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Dimensions.spacingLg))

            RethinkListGroup(modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)) {
                RethinkListItem(
                    headline = stringResource(id = R.string.adv_set_experimental_title),
                    supporting = stringResource(id = R.string.adv_set_experimental_desc),
                    leadingIcon = Icons.Filled.Build,
                    onClick = {
                        experimentalEnabled = !experimentalEnabled
                        persistentState.nwEngExperimentalFeatures = experimentalEnabled
                    },
                    trailing = {
                        Switch(
                            checked = experimentalEnabled,
                            onCheckedChange = {
                                experimentalEnabled = it
                                persistentState.nwEngExperimentalFeatures = it
                            }
                        )
                    }
                )

                RethinkListItem(
                    headline = stringResource(id = R.string.set_auto_dial_title),
                    supporting = stringResource(id = R.string.set_auto_dial_desc),
                    leadingIcon = Icons.Filled.Tune,
                    onClick = {
                        autoDialEnabled = !autoDialEnabled
                        persistentState.autoDialsParallel = autoDialEnabled
                    },
                    trailing = {
                        Switch(
                            checked = autoDialEnabled,
                            onCheckedChange = {
                                autoDialEnabled = it
                                persistentState.autoDialsParallel = it
                            }
                        )
                    }
                )

                RethinkListItem(
                    headline = "Random panic",
                    supporting = "Debug-only chaos mode for tunnel reliability testing.",
                    leadingIcon = Icons.Filled.Warning,
                    showDivider = false,
                    onClick = {
                        panicEnabled = !panicEnabled
                        persistentState.panicRandom = panicEnabled
                    },
                    trailing = {
                        Switch(
                            checked = panicEnabled,
                            onCheckedChange = {
                                panicEnabled = it
                                persistentState.panicRandom = it
                            }
                        )
                    }
                )
            }
        }
    }
}

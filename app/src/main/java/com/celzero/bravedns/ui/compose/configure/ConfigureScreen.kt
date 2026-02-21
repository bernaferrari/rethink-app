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
package com.celzero.bravedns.ui.compose.configure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureScreen(
    isDebug: Boolean,
    onAppsClick: () -> Unit,
    onDnsClick: () -> Unit,
    onFirewallClick: () -> Unit,
    onProxyClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onOthersClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAntiCensorshipClick: () -> Unit,
    onAdvancedClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkTopBar(title = stringResource(id = R.string.lbl_configure))
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Dimensions.spacingLg,
                            vertical = Dimensions.spacingMd
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
                    ) {
                        Text(
                            text = stringResource(id = R.string.lbl_configure),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.settings_title_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_protection))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_apps),
                        supporting = stringResource(id = R.string.apps_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.ic_app_info_accent),
                        onClick = onAppsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_dns),
                        supporting = stringResource(id = R.string.dns_mode_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.dns_home_screen),
                        onClick = onDnsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_firewall),
                        supporting = stringResource(id = R.string.firewall_mode_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.firewall_home_screen),
                        onClick = onFirewallClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_proxy),
                        supporting = stringResource(id = R.string.cd_custom_dns_proxy_name_default),
                        leadingIconPainter = painterResource(id = R.drawable.ic_proxy),
                        showDivider = false,
                        onClick = onProxyClick
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_system))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_network),
                        supporting = stringResource(id = R.string.firewall_act_network_monitor_tab),
                        leadingIconPainter = painterResource(id = R.drawable.ic_network_tunnel),
                        onClick = onNetworkClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.title_settings),
                        supporting = stringResource(id = R.string.settings_general_header),
                        leadingIconPainter = painterResource(id = R.drawable.ic_other_settings),
                        onClick = onOthersClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_logs),
                        supporting = stringResource(id = R.string.settings_enable_logs_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_logs_accent),
                        showDivider = false,
                        onClick = onLogsClick
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.lbl_advanced))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.anti_censorship_title),
                        supporting = stringResource(id = R.string.anti_censorship_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_anti_dpi),
                        showDivider = isDebug,
                        onClick = onAntiCensorshipClick
                    )
                    if (isDebug) {
                        RethinkListItem(
                            headline = stringResource(id = R.string.lbl_advanced),
                            supporting = stringResource(id = R.string.adv_set_experimental_desc),
                            leadingIconPainter = painterResource(id = R.drawable.ic_advanced_settings),
                            showDivider = false,
                            onClick = onAdvancedClick
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimensions.spacingSm))
            }
        }
    }
}

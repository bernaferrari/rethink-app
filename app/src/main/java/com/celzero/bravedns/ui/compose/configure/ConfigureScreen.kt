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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            RethinkAnimatedSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.spacingLg, horizontal = Dimensions.spacingSm)
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name_small_case),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.alpha(Dimensions.Opacity.LOW)
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                    Text(
                        text = stringResource(id = R.string.settings_title_desc),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.alpha(Dimensions.Opacity.MEDIUM)
                    )
                }
            }

            RethinkAnimatedSection(index = 1) {
                SectionHeader(title = stringResource(R.string.lbl_protection))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.apps_info_title),
                        supporting = stringResource(id = R.string.lbl_apps),
                        leadingIconPainter = painterResource(id = R.drawable.ic_app_info_accent),
                        onClick = onAppsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.dns_mode_info_title),
                        supporting = stringResource(id = R.string.lbl_dns),
                        leadingIconPainter = painterResource(id = R.drawable.dns_home_screen),
                        onClick = onDnsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.firewall_mode_info_title),
                        supporting = stringResource(id = R.string.lbl_firewall),
                        leadingIconPainter = painterResource(id = R.drawable.firewall_home_screen),
                        onClick = onFirewallClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.cd_custom_dns_proxy_name_default),
                        supporting = stringResource(id = R.string.lbl_proxy),
                        leadingIconPainter = painterResource(id = R.drawable.ic_proxy),
                        showDivider = false,
                        onClick = onProxyClick
                    )
                }
            }

            RethinkAnimatedSection(index = 2) {
                SectionHeader(title = stringResource(R.string.lbl_system))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_network).replaceFirstChar { it.uppercase() },
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
                        headline = stringResource(id = R.string.lbl_logs).replaceFirstChar { it.uppercase() },
                        supporting = stringResource(id = R.string.settings_enable_logs_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_logs_accent),
                        showDivider = false,
                        onClick = onLogsClick
                    )
                }
            }

            RethinkAnimatedSection(index = 3) {
                SectionHeader(title = stringResource(R.string.lbl_advanced))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.anti_censorship_title).replaceFirstChar { it.uppercase() },
                        supporting = stringResource(id = R.string.anti_censorship_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_anti_dpi),
                        showDivider = isDebug,
                        onClick = onAntiCensorshipClick
                    )

                    if (isDebug) {
                        RethinkListItem(
                            headline = stringResource(id = R.string.lbl_advanced).replaceFirstChar { it.uppercase() },
                            supporting = stringResource(id = R.string.adv_set_experimental_desc),
                            leadingIconPainter = painterResource(id = R.drawable.ic_advanced_settings),
                            showDivider = false,
                            onClick = onAdvancedClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
        }
    }
}

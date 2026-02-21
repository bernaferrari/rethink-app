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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.lbl_configure),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
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
                SectionHeader(title = stringResource(R.string.lbl_protection))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_apps),
                        supporting = stringResource(id = R.string.apps_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.ic_app_info_accent),
                        position = CardPosition.First,
                        onClick = onAppsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_dns),
                        supporting = stringResource(id = R.string.dns_mode_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.dns_home_screen),
                        position = CardPosition.Middle,
                        onClick = onDnsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_firewall),
                        supporting = stringResource(id = R.string.firewall_mode_info_title),
                        leadingIconPainter = painterResource(id = R.drawable.firewall_home_screen),
                        position = CardPosition.Middle,
                        onClick = onFirewallClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_proxy),
                        supporting = stringResource(id = R.string.cd_custom_dns_proxy_name_default),
                        leadingIconPainter = painterResource(id = R.drawable.ic_proxy),
                        position = CardPosition.Last,
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
                        position = CardPosition.First,
                        onClick = onNetworkClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.title_settings),
                        supporting = stringResource(id = R.string.settings_general_header),
                        leadingIconPainter = painterResource(id = R.drawable.ic_other_settings),
                        position = CardPosition.Middle,
                        onClick = onOthersClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_logs),
                        supporting = stringResource(id = R.string.settings_enable_logs_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_logs_accent),
                        position = CardPosition.Last,
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
                        position = if (isDebug) CardPosition.First else CardPosition.Single,
                        onClick = onAntiCensorshipClick
                    )
                    if (isDebug) {
                        RethinkListItem(
                            headline = stringResource(id = R.string.lbl_advanced),
                            supporting = stringResource(id = R.string.adv_set_experimental_desc),
                            leadingIconPainter = painterResource(id = R.drawable.ic_advanced_settings),
                            position = CardPosition.Last,
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

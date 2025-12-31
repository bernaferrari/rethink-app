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
package com.celzero.bravedns.ui.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.celzero.bravedns.R
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.about.AboutScreen
import com.celzero.bravedns.ui.compose.about.AboutUiState
import com.celzero.bravedns.ui.compose.configure.ConfigureScreen
import com.celzero.bravedns.ui.compose.home.HomeScreen
import com.celzero.bravedns.ui.compose.home.HomeScreenUiState
import com.celzero.bravedns.ui.compose.statistics.SummaryStatisticsScreen
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel

enum class HomeDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int
) {
    HOME("home", R.string.txt_home, R.drawable.ic_home_black_24dp),
    STATS("stats", R.string.title_statistics, R.drawable.ic_statistics),
    CONFIGURE("configure", R.string.lbl_configure, R.drawable.ic_settings),
    ABOUT("about", R.string.title_about, R.drawable.ic_about)
}

@Composable
fun HomeScreenRoot(
    homeUiState: HomeScreenUiState,
    onHomeStartStopClick: () -> Unit,
    onHomeDnsClick: () -> Unit,
    onHomeFirewallClick: () -> Unit,
    onHomeProxyClick: () -> Unit,
    onHomeLogsClick: () -> Unit,
    onHomeAppsClick: () -> Unit,
    onHomeSponsorClick: () -> Unit,
    summaryViewModel: SummaryStatisticsViewModel,
    onOpenDetailedStats: (SummaryStatisticsType) -> Unit,
    isDebug: Boolean,
    onConfigureAppsClick: () -> Unit,
    onConfigureDnsClick: () -> Unit,
    onConfigureFirewallClick: () -> Unit,
    onConfigureProxyClick: () -> Unit,
    onConfigureNetworkClick: () -> Unit,
    onConfigureOthersClick: () -> Unit,
    onConfigureLogsClick: () -> Unit,
    onConfigureAntiCensorshipClick: () -> Unit,
    onConfigureAdvancedClick: () -> Unit,
    aboutUiState: AboutUiState,
    onSponsorClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onAppUpdateClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onGithubClick: () -> Unit,
    onFaqClick: () -> Unit,
    onDocsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onEmailClick: () -> Unit,
    onRedditClick: () -> Unit,
    onElementClick: () -> Unit,
    onMastodonClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onVpnProfileClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDbStatsClick: () -> Unit,
    onFlightRecordClick: () -> Unit,
    onEventLogsClick: () -> Unit,
    onTokenClick: () -> Unit,
    onTokenDoubleTap: () -> Unit,
    onFossClick: () -> Unit,
    onFlossFundsClick: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HomeDestination.HOME.route

    BackHandler(enabled = currentRoute != HomeDestination.HOME.route) {
        navController.navigate(HomeDestination.HOME.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                HomeDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = destination.iconRes),
                                contentDescription = stringResource(id = destination.labelRes)
                            )
                        },
                        label = {
                            androidx.compose.material3.Text(
                                text = stringResource(id = destination.labelRes)
                            )
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = HomeDestination.HOME.route
        ) {
            composable(HomeDestination.HOME.route) {
                HomeScreen(
                    uiState = homeUiState,
                    onStartStopClick = onHomeStartStopClick,
                    onDnsClick = onHomeDnsClick,
                    onFirewallClick = onHomeFirewallClick,
                    onProxyClick = onHomeProxyClick,
                    onLogsClick = onHomeLogsClick,
                    onAppsClick = onHomeAppsClick,
                    onSponsorClick = onHomeSponsorClick
                )
            }
            composable(HomeDestination.STATS.route) {
                SummaryStatisticsScreen(
                    viewModel = summaryViewModel,
                    onSeeMoreClick = onOpenDetailedStats
                )
            }
            composable(HomeDestination.CONFIGURE.route) {
                ConfigureScreen(
                    isDebug = isDebug,
                    onAppsClick = onConfigureAppsClick,
                    onDnsClick = onConfigureDnsClick,
                    onFirewallClick = onConfigureFirewallClick,
                    onProxyClick = onConfigureProxyClick,
                    onNetworkClick = onConfigureNetworkClick,
                    onOthersClick = onConfigureOthersClick,
                    onLogsClick = onConfigureLogsClick,
                    onAntiCensorshipClick = onConfigureAntiCensorshipClick,
                    onAdvancedClick = onConfigureAdvancedClick
                )
            }
            composable(HomeDestination.ABOUT.route) {
                AboutScreen(
                    uiState = aboutUiState,
                    onSponsorClick = onSponsorClick,
                    onTelegramClick = onTelegramClick,
                    onBugReportClick = onBugReportClick,
                    onWhatsNewClick = onWhatsNewClick,
                    onAppUpdateClick = onAppUpdateClick,
                    onContributorsClick = onContributorsClick,
                    onTranslateClick = onTranslateClick,
                    onWebsiteClick = onWebsiteClick,
                    onGithubClick = onGithubClick,
                    onFaqClick = onFaqClick,
                    onDocsClick = onDocsClick,
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onTermsOfServiceClick = onTermsOfServiceClick,
                    onLicenseClick = onLicenseClick,
                    onTwitterClick = onTwitterClick,
                    onEmailClick = onEmailClick,
                    onRedditClick = onRedditClick,
                    onElementClick = onElementClick,
                    onMastodonClick = onMastodonClick,
                    onAppInfoClick = onAppInfoClick,
                    onVpnProfileClick = onVpnProfileClick,
                    onNotificationClick = onNotificationClick,
                    onStatsClick = onStatsClick,
                    onDbStatsClick = onDbStatsClick,
                    onFlightRecordClick = onFlightRecordClick,
                    onEventLogsClick = onEventLogsClick,
                    onTokenClick = onTokenClick,
                    onTokenDoubleTap = onTokenDoubleTap,
                    onFossClick = onFossClick,
                    onFlossFundsClick = onFlossFundsClick
                )
            }
        }
    }
}

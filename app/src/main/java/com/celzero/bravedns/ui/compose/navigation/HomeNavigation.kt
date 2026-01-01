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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.celzero.bravedns.R
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.alerts.AlertsScreen
import com.celzero.bravedns.ui.compose.about.AboutScreen
import com.celzero.bravedns.ui.compose.about.AboutUiState
import com.celzero.bravedns.ui.compose.configure.ConfigureScreen
import com.celzero.bravedns.ui.compose.events.EventsScreen
import com.celzero.bravedns.ui.compose.home.HomeScreen
import com.celzero.bravedns.ui.compose.home.HomeScreenUiState
import com.celzero.bravedns.ui.compose.rpn.RpnAvailabilityScreen
import com.celzero.bravedns.ui.compose.rpn.RpnCountriesScreen
import com.celzero.bravedns.ui.compose.rpn.RpnWinProxyDetailsScreen
import com.celzero.bravedns.ui.compose.logs.DomainConnectionsInputType
import com.celzero.bravedns.ui.compose.logs.DomainConnectionsScreen
import com.celzero.bravedns.ui.compose.statistics.DetailedStatisticsScreen
import com.celzero.bravedns.ui.compose.statistics.SummaryStatisticsScreen
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel
import com.celzero.bravedns.viewmodel.EventsViewModel
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

sealed interface HomeNavRequest {
    data class DetailedStats(
        val type: SummaryStatisticsType,
        val timeCategory: SummaryStatisticsViewModel.TimeCategory
    ) : HomeNavRequest
    data object Alerts : HomeNavRequest
    data object RpnCountries : HomeNavRequest
    data object RpnAvailability : HomeNavRequest
    data object Events : HomeNavRequest
    data class RpnWinProxyDetails(val countryCode: String) : HomeNavRequest
    data class DomainConnections(
        val type: DomainConnectionsInputType,
        val flag: String,
        val domain: String,
        val asn: String,
        val ip: String,
        val isBlocked: Boolean,
        val timeCategory: DomainConnectionsViewModel.TimeCategory
    ) : HomeNavRequest
}

private const val ROUTE_DETAILED_STATS = "detailedStats"
private const val ROUTE_ALERTS = "alerts"
private const val ROUTE_RPN_COUNTRIES = "rpnCountries"
private const val ROUTE_RPN_AVAILABILITY = "rpnAvailability"
private const val ROUTE_DOMAIN_CONNECTIONS = "domainConnections"
private const val ROUTE_EVENTS = "events"
private const val ROUTE_RPN_WIN_PROXY_DETAILS = "rpnWinProxyDetails"

private fun domainConnectionsRoute(
    type: DomainConnectionsInputType,
    flag: String,
    domain: String,
    asn: String,
    ip: String,
    isBlocked: Boolean,
    timeCategory: DomainConnectionsViewModel.TimeCategory
): String {
    val encodedFlag = Uri.encode(flag)
    val encodedDomain = Uri.encode(domain)
    val encodedAsn = Uri.encode(asn)
    val encodedIp = Uri.encode(ip)
    return "$ROUTE_DOMAIN_CONNECTIONS/${type.type}/${timeCategory.value}" +
        "?flag=$encodedFlag&domain=$encodedDomain&asn=$encodedAsn&ip=$encodedIp&blocked=$isBlocked"
}

private fun detailedStatsRoute(typeId: Int, timeCategory: Int): String {
    return "$ROUTE_DETAILED_STATS/$typeId/$timeCategory"
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
    onFlossFundsClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    detailedStatsViewModel: DetailedStatisticsViewModel,
    domainConnectionsViewModel: DomainConnectionsViewModel,
    eventsViewModel: EventsViewModel,
    eventDao: EventDao,
    homeNavRequest: HomeNavRequest?,
    onHomeNavConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HomeDestination.HOME.route

    LaunchedEffect(homeNavRequest) {
        val request = homeNavRequest ?: return@LaunchedEffect
        when (request) {
            is HomeNavRequest.DetailedStats -> {
                navController.navigate(
                    detailedStatsRoute(request.type.tid, request.timeCategory.value)
                )
            }
            HomeNavRequest.Alerts -> {
                navController.navigate(ROUTE_ALERTS)
            }
            HomeNavRequest.RpnCountries -> {
                navController.navigate(ROUTE_RPN_COUNTRIES)
            }
            HomeNavRequest.RpnAvailability -> {
                navController.navigate(ROUTE_RPN_AVAILABILITY)
            }
            HomeNavRequest.Events -> {
                navController.navigate(ROUTE_EVENTS)
            }
            is HomeNavRequest.RpnWinProxyDetails -> {
                navController.navigate("$ROUTE_RPN_WIN_PROXY_DETAILS/${Uri.encode(request.countryCode)}")
            }
            is HomeNavRequest.DomainConnections -> {
                navController.navigate(
                    domainConnectionsRoute(
                        request.type,
                        request.flag,
                        request.domain,
                        request.asn,
                        request.ip,
                        request.isBlocked,
                        request.timeCategory
                    )
                )
            }
        }
        onHomeNavConsumed()
    }

    BackHandler(enabled = currentRoute != HomeDestination.HOME.route) {
        navController.navigate(HomeDestination.HOME.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            composable(ROUTE_ALERTS) {
                AlertsScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_RPN_COUNTRIES) {
                RpnCountriesScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_RPN_AVAILABILITY) {
                RpnAvailabilityScreen(onBackClick = { navController.popBackStack() })
            }
            composable(ROUTE_EVENTS) {
                EventsScreen(
                    viewModel = eventsViewModel,
                    eventDao = eventDao,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_RPN_WIN_PROXY_DETAILS/{countryCode}",
                arguments = listOf(navArgument("countryCode") { type = NavType.StringType })
            ) { entry ->
                val cc = entry.arguments?.getString("countryCode").orEmpty()
                RpnWinProxyDetailsScreen(
                    countryCode = cc,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_DOMAIN_CONNECTIONS/{type}/{timeCategory}?flag={flag}&domain={domain}&asn={asn}&ip={ip}&blocked={blocked}",
                arguments =
                    listOf(
                        navArgument("type") { type = NavType.IntType },
                        navArgument("timeCategory") { type = NavType.IntType },
                        navArgument("flag") { type = NavType.StringType; defaultValue = "" },
                        navArgument("domain") { type = NavType.StringType; defaultValue = "" },
                        navArgument("asn") { type = NavType.StringType; defaultValue = "" },
                        navArgument("ip") { type = NavType.StringType; defaultValue = "" },
                        navArgument("blocked") { type = NavType.BoolType; defaultValue = false }
                    )
            ) { entry ->
                val typeId = entry.arguments?.getInt("type") ?: DomainConnectionsInputType.DOMAIN.type
                val tcValue = entry.arguments?.getInt("timeCategory")
                    ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR.value
                val flag = entry.arguments?.getString("flag").orEmpty()
                val domain = entry.arguments?.getString("domain").orEmpty()
                val asn = entry.arguments?.getString("asn").orEmpty()
                val ip = entry.arguments?.getString("ip").orEmpty()
                val isBlocked = entry.arguments?.getBoolean("blocked") ?: false
                val type = DomainConnectionsInputType.fromValue(typeId)
                val timeCategory =
                    DomainConnectionsViewModel.TimeCategory.fromValue(tcValue)
                        ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
                DomainConnectionsScreen(
                    viewModel = domainConnectionsViewModel,
                    type = type,
                    flag = flag,
                    domain = domain,
                    asn = asn,
                    ip = ip,
                    isBlocked = isBlocked,
                    timeCategory = timeCategory,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_DETAILED_STATS/{typeId}/{timeCategory}",
                arguments =
                    listOf(
                        navArgument("typeId") { type = NavType.IntType },
                        navArgument("timeCategory") { type = NavType.IntType }
                    )
            ) { entry ->
                val typeId = entry.arguments?.getInt("typeId")
                    ?: SummaryStatisticsType.MOST_CONNECTED_APPS.tid
                val timeCategoryValue = entry.arguments?.getInt("timeCategory")
                    ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR.value
                val type = SummaryStatisticsType.getType(typeId)
                val timeCategory =
                    SummaryStatisticsViewModel.TimeCategory.fromValue(timeCategoryValue)
                        ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR
                DetailedStatisticsScreen(
                    viewModel = detailedStatsViewModel,
                    type = type,
                    timeCategory = timeCategory,
                    onBackClick = { navController.popBackStack() }
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

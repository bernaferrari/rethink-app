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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkGridTile
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeaderWithSubtitle
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import kotlinx.coroutines.delay

private data class ConfigureEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val iconRes: Int,
    val onClick: () -> Unit,
    val keywords: List<String> = emptyList(),
)

private data class ConfigureSectionModel(
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val layout: ConfigureSectionLayout,
    val entries: List<ConfigureEntry>,
)

private enum class ConfigureSectionLayout {
    GridFour,
    GridPairThenList,
    List
}

private data class ConfigureSearchTarget(
    val id: String,
    val title: String,
    val path: String,
    val iconRes: Int,
    val onClick: () -> Unit,
    val keywords: List<String>,
)

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
    onAdvancedClick: () -> Unit,
    onSearchDestinationClick: ((SettingsSearchDestination) -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val protectionTitle = stringResource(R.string.lbl_protection)
    val systemTitle = stringResource(R.string.lbl_system)
    val advancedTitle = stringResource(R.string.lbl_advanced)

    val appsTitle = stringResource(R.string.lbl_apps)
    val dnsTitle = stringResource(R.string.lbl_dns)
    val firewallTitle = stringResource(R.string.lbl_firewall)
    val proxyTitle = stringResource(R.string.lbl_proxy)
    val networkTitle = stringResource(R.string.lbl_network)
    val settingsTitle = stringResource(R.string.title_settings)
    val logsTitle = stringResource(R.string.lbl_logs)
    val antiCensorshipTitle = stringResource(R.string.anti_censorship_title)

    val sections = buildList {
        add(
            ConfigureSectionModel(
                title = protectionTitle,
                subtitle = stringResource(R.string.settings_title_desc),
                accentColor = MaterialTheme.colorScheme.primary,
                layout = ConfigureSectionLayout.GridFour,
                entries = listOf(
                    ConfigureEntry(
                        id = "apps",
                        title = appsTitle,
                        iconRes = R.drawable.ic_app_info_accent,
                        onClick = onAppsClick,
                        keywords = listOf("apps", "application", "app list")
                    ),
                    ConfigureEntry(
                        id = "dns",
                        title = dnsTitle,
                        iconRes = R.drawable.dns_home_screen,
                        onClick = onDnsClick,
                        keywords = listOf("dns", "doh", "dot", "dnscrypt", "resolver", "blocklist")
                    ),
                    ConfigureEntry(
                        id = "firewall",
                        title = firewallTitle,
                        iconRes = R.drawable.firewall_home_screen,
                        onClick = onFirewallClick,
                        keywords = listOf("firewall", "allow", "block", "rules", "wifi", "mobile")
                    ),
                    ConfigureEntry(
                        id = "proxy",
                        title = proxyTitle,
                        iconRes = R.drawable.ic_proxy,
                        onClick = onProxyClick,
                        keywords = listOf("proxy", "socks5", "http proxy", "wireguard", "orbot", "tor")
                    )
                )
            )
        )

        add(
            ConfigureSectionModel(
                title = systemTitle,
                subtitle = stringResource(R.string.firewall_act_network_monitor_tab),
                accentColor = MaterialTheme.colorScheme.secondary,
                layout = ConfigureSectionLayout.GridPairThenList,
                entries = listOf(
                    ConfigureEntry(
                        id = "network",
                        title = networkTitle,
                        iconRes = R.drawable.ic_network_tunnel,
                        onClick = onNetworkClick,
                        keywords = listOf("network", "vpn", "tunnel", "metered")
                    ),
                    ConfigureEntry(
                        id = "settings",
                        title = settingsTitle,
                        iconRes = R.drawable.ic_other_settings,
                        onClick = onOthersClick,
                        keywords = listOf("settings", "general", "theme", "appearance", "backup", "restore")
                    ),
                    ConfigureEntry(
                        id = "logs",
                        title = logsTitle,
                        subtitle = stringResource(R.string.settings_enable_logs_desc),
                        iconRes = R.drawable.ic_logs_accent,
                        onClick = onLogsClick,
                        keywords = listOf("logs", "events", "network logs", "console logs")
                    )
                )
            )
        )

        add(
            ConfigureSectionModel(
                title = advancedTitle,
                subtitle = stringResource(R.string.adv_set_experimental_desc),
                accentColor = MaterialTheme.colorScheme.tertiary,
                layout = ConfigureSectionLayout.List,
                entries = buildList {
                    add(
                        ConfigureEntry(
                            id = "anti-censorship",
                            title = antiCensorshipTitle,
                            subtitle = stringResource(R.string.anti_censorship_desc),
                            iconRes = R.drawable.ic_anti_dpi,
                            onClick = onAntiCensorshipClick,
                            keywords = listOf("anti censorship", "dpi", "evasion")
                        )
                    )
                    if (isDebug) {
                        add(
                            ConfigureEntry(
                                id = "advanced",
                                title = advancedTitle,
                                subtitle = stringResource(R.string.adv_set_experimental_desc),
                                iconRes = R.drawable.ic_advanced_settings,
                                onClick = onAdvancedClick,
                                keywords = listOf("advanced", "experimental", "debug")
                            )
                        )
                    }
                }
            )
        )
    }

    fun openDestination(destination: SettingsSearchDestination) {
        if (onSearchDestinationClick != null) {
            onSearchDestinationClick(destination)
            return
        }

        when (destination) {
            SettingsSearchDestination.Apps -> onAppsClick()
            is SettingsSearchDestination.Dns -> onDnsClick()
            is SettingsSearchDestination.Firewall -> onFirewallClick()
            is SettingsSearchDestination.Proxy -> onProxyClick()
            is SettingsSearchDestination.Network -> onNetworkClick()
            is SettingsSearchDestination.General -> onOthersClick()
            SettingsSearchDestination.Logs -> onLogsClick()
            SettingsSearchDestination.AntiCensorship -> onAntiCensorshipClick()
            SettingsSearchDestination.Advanced -> onAdvancedClick()
        }
    }

    val deepSearchTargets =
        buildSettingsSearchIndex(isDebug = isDebug).map { entry ->
            ConfigureSearchTarget(
                id = entry.id,
                title = entry.title,
                path = entry.path,
                iconRes = entry.iconRes,
                onClick = { openDestination(entry.destination) },
                keywords = buildList {
                    addAll(entry.keywords)
                    add(entry.title)
                    add(entry.subtitle)
                    add(entry.path)
                }
            )
        }

    val topLevelSearchTargets = sections.flatMap { section ->
        section.entries.map { entry ->
            ConfigureSearchTarget(
                id = "top-level.${entry.id}",
                title = entry.title,
                path = "${section.title} > ${entry.title}",
                iconRes = entry.iconRes,
                onClick = entry.onClick,
                keywords = buildList {
                    addAll(entry.keywords)
                    add(section.title)
                    add(entry.title)
                    entry.subtitle?.let { add(it) }
                }
            )
        }
    }

    val normalizedQuery = if (isSearchOpen) query.normalizeSearchQuery() else ""
    val searchTargets = (deepSearchTargets + topLevelSearchTargets).distinctBy { it.id }

    val searchResults = remember(normalizedQuery, searchTargets) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            searchTargets
                .mapNotNull { target ->
                    val score = target.searchScore(normalizedQuery)
                    if (score > 0) target to score else null
                }
                .sortedWith(
                    compareByDescending<Pair<ConfigureSearchTarget, Int>> { it.second }
                        .thenBy { it.first.title }
                )
                .map { it.first }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (isSearchOpen) {
                        IconButton(
                            onClick = {
                                isSearchOpen = false
                                query = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(id = R.string.configure_search_close),
                            )
                        }
                    }
                },
                title = {
                    if (isSearchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.configure_search_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.lbl_configure),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isSearchOpen) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(id = R.string.cd_clear_search)
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(id = R.string.configure_search_open)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                bottom = Dimensions.spacingLg
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            if (normalizedQuery.isBlank()) {
                sections.forEachIndexed { index, section ->
                    item {
                        RethinkAnimatedSection(index = index) {
                            ConfigureSection(section)
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(Dimensions.spacingSm))
                }
            } else {
                item {
                    if (searchResults.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.cornerRadiusXl),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.configure_search_empty_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(id = R.string.configure_search_empty_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        SearchResultsGroup(
                            results = searchResults,
                            query = normalizedQuery,
                            onResultClick = { target ->
                                target.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureSection(section: ConfigureSectionModel) {
    val containerColor = section.accentColor.copy(alpha = 0.15f)
    Column {
        SectionHeaderWithSubtitle(
            title = section.title,
            subtitle = section.subtitle,
            color = section.accentColor
        )

        Spacer(modifier = Modifier.height(Dimensions.spacingSm))

        when (section.layout) {
            ConfigureSectionLayout.GridFour -> {
                ConfigureGrid(
                    entries = section.entries,
                    accentColor = section.accentColor
                )
            }

            ConfigureSectionLayout.GridPairThenList -> {
                when (section.entries.size) {
                    3 -> {
                        ConfigureTriadGrid(
                            entries = section.entries,
                            accentColor = section.accentColor
                        )
                    }

                    2 -> {
                        ConfigurePairGrid(
                            first = section.entries[0],
                            second = section.entries[1],
                            accentColor = section.accentColor
                        )
                    }

                    in 4..Int.MAX_VALUE -> {
                        ConfigurePairGrid(
                            first = section.entries[0],
                            second = section.entries[1],
                            accentColor = section.accentColor
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                        ConfigureSectionList(
                            entries = section.entries.drop(2),
                            accentColor = section.accentColor,
                            iconContainerColor = containerColor
                        )
                    }

                    else -> {
                        ConfigureSectionList(
                            entries = section.entries,
                            accentColor = section.accentColor,
                            iconContainerColor = containerColor
                        )
                    }
                }
            }

            ConfigureSectionLayout.List -> {
                ConfigureSectionList(
                    entries = section.entries,
                    accentColor = section.accentColor,
                    iconContainerColor = containerColor
                )
            }
        }
    }
}

@Composable
private fun ConfigureGrid(
    entries: List<ConfigureEntry>,
    accentColor: Color
) {
    if (entries.size != 4) return

    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        ConfigureTopRowTiles(
            first = entries[0],
            second = entries[1],
            accentColor = accentColor
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
        ) {
            ConfigureGridTile(
                entry = entries[2],
                accentColor = accentColor,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
                modifier = Modifier.weight(1f)
            )
            ConfigureGridTile(
                entry = entries[3],
                accentColor = accentColor,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConfigurePairGrid(
    first: ConfigureEntry,
    second: ConfigureEntry,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
    ) {
        ConfigureGridTile(
            entry = first,
            accentColor = accentColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
            modifier = Modifier.weight(1f)
        )
        ConfigureGridTile(
            entry = second,
            accentColor = accentColor,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 28.dp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConfigureTriadGrid(
    entries: List<ConfigureEntry>,
    accentColor: Color
) {
    if (entries.size != 3) return

    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        ConfigureTopRowTiles(
            first = entries[0],
            second = entries[1],
            accentColor = accentColor
        )

        ConfigureGridTile(
            entry = entries[2],
            accentColor = accentColor,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConfigureTopRowTiles(
    first: ConfigureEntry,
    second: ConfigureEntry,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)
    ) {
        ConfigureGridTile(
            entry = first,
            accentColor = accentColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
            modifier = Modifier.weight(1f)
        )
        ConfigureGridTile(
            entry = second,
            accentColor = accentColor,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConfigureSectionList(
    entries: List<ConfigureEntry>,
    accentColor: Color,
    iconContainerColor: Color
) {
    Column {
        entries.forEachIndexed { index, entry ->
            RethinkListItem(
                headline = entry.title,
                supporting = entry.subtitle?.takeIf { it.isNotBlank() },
                leadingIconPainter = painterResource(id = entry.iconRes),
                leadingIconTint = accentColor,
                leadingIconContainerColor = iconContainerColor,
                position = cardPositionFor(index = index, lastIndex = entries.lastIndex),
                highlightContainerColor = accentColor.copy(alpha = 0.24f),
                showTrailingChevron = false,
                onClick = entry.onClick,
            )
        }
    }
}

@Composable
private fun ConfigureGridTile(
    entry: ConfigureEntry,
    accentColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    RethinkGridTile(
        title = entry.title,
        iconRes = entry.iconRes,
        accentColor = accentColor,
        shape = shape,
        modifier = modifier,
        onClick = entry.onClick
    )
}

@Composable
private fun SearchResultsGroup(
    results: List<ConfigureSearchTarget>,
    query: String,
    onResultClick: (ConfigureSearchTarget) -> Unit
) {
    val limitedResults = results.take(12)
    val highlightColor = MaterialTheme.colorScheme.primary

    Column {
        limitedResults.forEachIndexed { index, target ->
            val highlightedTitle = remember(target.title, query, highlightColor) {
                target.title.highlightMatches(query = query, highlightColor = highlightColor)
            }
            val highlightedPath = remember(target.path, query, highlightColor) {
                target.path.highlightMatches(query = query, highlightColor = highlightColor)
            }
            RethinkListItem(
                headline = target.title,
                headlineAnnotated = highlightedTitle,
                supporting = target.path,
                supportingAnnotated = highlightedPath,
                leadingIconPainter = painterResource(id = target.iconRes),
                leadingIconTint = MaterialTheme.colorScheme.primary,
                leadingIconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                position = cardPositionFor(index = index, lastIndex = limitedResults.lastIndex),
                highlightContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
                showTrailingChevron = false,
                onClick = { onResultClick(target) }
            )
        }
    }
}

private fun String.normalizeSearchQuery(): String {
    return lowercase().trim().replace(Regex("\\s+"), " ")
}

private fun ConfigureSearchTarget.searchScore(query: String): Int {
    if (query.isBlank()) return 0

    val titleNorm = title.normalizeSearchQuery()
    val pathNorm = path.normalizeSearchQuery()
    val keywordNorm = keywords.joinToString(" ").normalizeSearchQuery()

    var score = 0
    if (titleNorm.startsWith(query)) score += 10
    if (titleNorm.contains(query)) score += 7
    if (keywordNorm.contains(query)) score += 6
    if (pathNorm.contains(query)) score += 4

    return score
}

private fun String.highlightMatches(query: String, highlightColor: Color): AnnotatedString {
    if (isBlank() || query.isBlank()) return AnnotatedString(this)

    val ranges = mutableListOf<Pair<Int, Int>>()
    val tokens = query.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    tokens.forEach { token ->
        var startIndex = 0
        while (startIndex < length) {
            val index = indexOf(token, startIndex = startIndex, ignoreCase = true)
            if (index < 0) break
            ranges += index to (index + token.length)
            startIndex = index + token.length
        }
    }

    if (ranges.isEmpty()) return AnnotatedString(this)

    val merged = ranges
        .sortedBy { it.first }
        .fold(mutableListOf<Pair<Int, Int>>()) { acc, range ->
            if (acc.isEmpty()) {
                acc += range
                return@fold acc
            }
            val last = acc.last()
            if (range.first <= last.second) {
                acc[acc.lastIndex] = last.first to maxOf(last.second, range.second)
            } else {
                acc += range
            }
            acc
        }

    return buildAnnotatedString {
        append(this@highlightMatches)
        merged.forEach { (start, end) ->
            addStyle(
                style = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold),
                start = start,
                end = end
            )
        }
    }
}

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
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.compose.apps.IpRow
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.ui.compose.firewall.AppIpRulesSheet
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Utilities.removeBeginningTrailingCommas
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel

@Composable
fun AppWiseIpLogsScreen(
    uid: Int,
    isAsn: Boolean,
    viewModel: AppConnectionsViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    val showDeleteIcon = remember { !isAsn }
    var selectedCategory by remember { mutableStateOf(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
    val header = rememberAppWiseLogsHeader(uid, isAsn, onMissing = { onBackClick?.invoke() })

    LaunchedEffect(uid, isAsn) {
        if (uid == INVALID_UID) return@LaunchedEffect
        viewModel.timeCategoryChanged(selectedCategory, isDomain = false)
    }

    RethinkAppWiseLogsScaffold(
        title = header?.appName.orEmpty().ifBlank { stringResource(R.string.lbl_logs) },
        onBackClick = onBackClick,
        strings = appWiseLogsStrings(),
        onDelete = if (showDeleteIcon) ({ viewModel.deleteLogs(uid) }) else null,
    ) { paddingValues ->
        RethinkAppWiseLogsContent(
            searchHint = header?.searchHint.orEmpty(),
            fallbackSearchHint = stringResource(R.string.search_universal_ips),
            strings = appWiseLogsStrings(),
            selectedRange = selectedCategory.toRethinkTimeRange(),
            onRangeSelected = { range ->
                selectedCategory = range.toAppWiseTimeCategory()
                viewModel.timeCategoryChanged(selectedCategory, isDomain = false)
            },
            onQueryChange = { query ->
                val type =
                    if (isAsn) {
                        AppConnectionsViewModel.FilterType.ASN
                    } else {
                        AppConnectionsViewModel.FilterType.IP
                    }
                viewModel.setFilter(query, type)
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            queryEnabled = !isAsn,
            appIcon = header.searchIcon(),
        ) {
            AppWiseIpList(
                viewModel = viewModel,
                uid = uid,
                isAsn = isAsn,
                isRethinkApp = header?.isRethinkApp == true,
                eventLogger = eventLogger
            )
        }
    }
}

@Composable
private fun AppWiseIpList(
    viewModel: AppConnectionsViewModel,
    uid: Int,
    isAsn: Boolean,
    isRethinkApp: Boolean,
    eventLogger: EventLogger
) {
    var showIpRulesSheet by remember { mutableStateOf(false) }
    var selectedIp by remember { mutableStateOf("") }
    var selectedDomains by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }

    if (showIpRulesSheet && selectedIp.isNotEmpty()) {
        AppIpRulesSheet(
            uid = uid,
            ipAddress = selectedIp,
            domains = selectedDomains,
            eventLogger = eventLogger,
            onDismiss = { showIpRulesSheet = false },
            onUpdated = { refreshToken++ }
        )
    }

    LaunchedEffect(uid, isRethinkApp) {
        if (!isRethinkApp) viewModel.setUid(uid)
    }

    val flow = remember(isRethinkApp, isAsn) {
        if (isRethinkApp) viewModel.rinrIpLogs else if (isAsn) viewModel.asnLogs else viewModel.appIpLogs
    }
    val items = flow.collectAsLazyPagingItems()

    AppWiseLogsPagedList(items = items) { item, position ->
            IpRow(
                conn = item,
                position = position,
                isAsn = isAsn,
                refreshToken = refreshToken,
                onIpClick = { conn ->
                    if (!isAsn) {
                        selectedIp = conn.ipAddress
                        selectedDomains =
                            removeBeginningTrailingCommas(conn.appOrDnsName ?: "").replace(",,", ",").replace(",", ", ")
                        showIpRulesSheet = true
                    }
                }
            )
    }
}

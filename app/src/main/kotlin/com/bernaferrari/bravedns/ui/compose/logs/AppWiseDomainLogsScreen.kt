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
import com.bernaferrari.bravedns.ui.compose.apps.DomainRow
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.ui.compose.firewall.AppDomainRulesSheet
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel

@Composable
fun AppWiseDomainLogsScreen(
    uid: Int,
    viewModel: AppConnectionsViewModel,
    eventLogger: EventLogger,
    onBackClick: (() -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS) }
    val header = rememberAppWiseLogsHeader(uid, isAsn = false, onMissing = { onBackClick?.invoke() })

    LaunchedEffect(uid) {
        if (uid == INVALID_UID) return@LaunchedEffect
        viewModel.setUid(uid)
        viewModel.setFilter("", AppConnectionsViewModel.FilterType.DOMAIN)
        viewModel.timeCategoryChanged(selectedCategory, true)
    }

    val items = remember(uid) { viewModel.appDomainLogs }.collectAsLazyPagingItems()

    RethinkAppWiseLogsScaffold(
        title = header?.appName.orEmpty().ifBlank { stringResource(R.string.lbl_logs) },
        onBackClick = onBackClick,
        strings = appWiseLogsStrings(),
        onDelete = { viewModel.deleteLogs(uid) },
    ) { paddingValues ->
        RethinkAppWiseLogsContent(
            searchHint = header?.searchHint.orEmpty(),
            fallbackSearchHint = stringResource(R.string.search_custom_domains),
            strings = appWiseLogsStrings(),
            selectedRange = selectedCategory.toRethinkTimeRange(),
            onRangeSelected = { range ->
                selectedCategory = range.toAppWiseTimeCategory()
                viewModel.timeCategoryChanged(selectedCategory, true)
            },
            onQueryChange = { query ->
                viewModel.setFilter(query, AppConnectionsViewModel.FilterType.DOMAIN)
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            appIcon = header.searchIcon(),
        ) {
            AppWiseDomainList(
                items = items,
                uid = uid,
                eventLogger = eventLogger
            )
        }
    }
}

@Composable
private fun AppWiseDomainList(
    items: androidx.paging.compose.LazyPagingItems<AppConnection>,
    uid: Int,
    eventLogger: EventLogger
) {
    var showDomainRulesSheet by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }
    if (showDomainRulesSheet && selectedDomain.isNotEmpty()) {
        AppDomainRulesSheet(
            uid = uid,
            domain = selectedDomain,
            eventLogger = eventLogger,
            onDismiss = { showDomainRulesSheet = false },
            onUpdated = { refreshToken++ }
        )
    }

    AppWiseLogsPagedList(items = items) { item, position ->
        DomainRow(
            conn = item,
            position = position,
            uid = uid,
            refreshToken = refreshToken,
            onIpClick = { conn ->
                selectedDomain = conn.appOrDnsName.orEmpty()
                if (selectedDomain.isNotEmpty()) {
                    showDomainRulesSheet = true
                }
            }
        )
    }
}

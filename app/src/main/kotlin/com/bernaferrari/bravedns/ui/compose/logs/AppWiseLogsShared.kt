/*
 * Copyright 2026 RethinkDNS and its authors
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

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.common.timeWindowLabel
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppWiseLogsHeader(
    val appName: String,
    val searchHint: String,
    val packageName: String,
    val isRethinkApp: Boolean
)

/**
 * The app-wise log screens' header (title, search hint, icon), loaded off the main thread for
 * [uid]. Null until loaded; [onMissing] runs when the uid is invalid or has no app.
 */
@Composable
internal fun rememberAppWiseLogsHeader(uid: Int, isAsn: Boolean, onMissing: () -> Unit): AppWiseLogsHeader? {
    val context = LocalContext.current
    val currentOnMissing by rememberUpdatedState(onMissing)
    var header by remember(uid, isAsn) { mutableStateOf<AppWiseLogsHeader?>(null) }
    LaunchedEffect(uid, isAsn) {
        header = withContext(Dispatchers.IO) { resolveAppWiseLogsHeader(context, uid, isAsn) }
        if (header == null) currentOnMissing()
    }
    return header
}

private suspend fun resolveAppWiseLogsHeader(context: Context, uid: Int, isAsn: Boolean): AppWiseLogsHeader? {
    if (uid == INVALID_UID) return null

    val info = FirewallManager.getAppInfoByUid(uid) ?: return null
    val packageNames = FirewallManager.getPackageNamesByUid(uid)
    val visibleName =
        if (packageNames.size >= 2) {
            context.getString(R.string.ctbs_app_other_apps, info.appName, (packageNames.size - 1).toString())
        } else {
            info.appName
        }
    val truncated = visibleName.take(10)
    val scope =
        if (isAsn) {
            context.getString(R.string.two_argument_space, context.getString(R.string.lbl_search), context.getString(R.string.lbl_service_providers))
        } else {
            context.getString(R.string.search_universal_ips)
        }

    return AppWiseLogsHeader(
        appName = visibleName,
        searchHint = context.getString(R.string.two_argument_colon, truncated, scope),
        packageName = info.packageName,
        isRethinkApp = packageNames.any { it == context.packageName }
    )
}

@Composable
internal fun <T : Any> AppWiseLogsPagedList(
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T, CardPosition) -> Unit
) {
    val refreshState = items.loadState.refresh
    val itemCount = items.itemCount
    val listContentState = when {
        refreshState is LoadState.Loading && itemCount == 0 -> RethinkPagedLogListState.Loading
        refreshState is LoadState.NotLoading && itemCount == 0 -> RethinkPagedLogListState.Empty
        refreshState is LoadState.Error && itemCount == 0 -> RethinkPagedLogListState.Error { items.retry() }
        else -> RethinkPagedLogListState.Content
    }
    RethinkPagedLogList(
        itemCount = itemCount,
        state = listContentState,
        strings = logListStrings(),
        listState = rememberLazyListState(),
        modifier = modifier,
    ) { index, count ->
        val item = items[index] ?: return@RethinkPagedLogList
        row(item, cardPositionFor(index, count - 1))
    }
}

@Composable
internal fun appWiseLogsStrings(): RethinkAppWiseLogsStrings = RethinkAppWiseLogsStrings(
    clearSearch = stringResource(R.string.cd_clear_search),
    delete = stringResource(R.string.lbl_delete),
    deleteTitle = stringResource(R.string.ada_delete_logs_dialog_title),
    deleteDescription = stringResource(R.string.ada_delete_logs_dialog_desc),
    proceed = stringResource(R.string.lbl_proceed),
    cancel = stringResource(R.string.lbl_cancel),
    oneHour = timeWindowLabel(AppConnectionsViewModel.TimeCategory.ONE_HOUR.value),
    twentyFourHours = timeWindowLabel(AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR.value),
    sevenDays = timeWindowLabel(AppConnectionsViewModel.TimeCategory.SEVEN_DAYS.value),
    moreActions = stringResource(R.string.cd_more),
)

internal fun AppConnectionsViewModel.TimeCategory.toRethinkTimeRange() = when (this) {
    AppConnectionsViewModel.TimeCategory.ONE_HOUR -> RethinkAppWiseLogTimeRange.OneHour
    AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> RethinkAppWiseLogTimeRange.TwentyFourHours
    AppConnectionsViewModel.TimeCategory.SEVEN_DAYS -> RethinkAppWiseLogTimeRange.SevenDays
}

internal fun RethinkAppWiseLogTimeRange.toAppWiseTimeCategory() = when (this) {
    RethinkAppWiseLogTimeRange.OneHour -> AppConnectionsViewModel.TimeCategory.ONE_HOUR
    RethinkAppWiseLogTimeRange.TwentyFourHours -> AppConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR
    RethinkAppWiseLogTimeRange.SevenDays -> AppConnectionsViewModel.TimeCategory.SEVEN_DAYS
}

/** The header's app icon, sized for the shared search row's small well; null until the header loads. */
internal fun AppWiseLogsHeader?.searchIcon(): (@Composable () -> Unit)? =
    this?.let { header -> { AppIcon(header.packageName, appName = header.appName, size = SharedDimensions.iconSizeSm) } }

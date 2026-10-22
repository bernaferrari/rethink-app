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
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.viewmodel.AppConnectionsViewModel

internal data class AppWiseLogsHeader(
    val appName: String,
    val searchHint: String,
    val appIcon: Drawable?,
    val isRethinkApp: Boolean
)

internal suspend fun resolveAppWiseLogsHeader(
    context: Context,
    uid: Int,
    isAsn: Boolean,
    appOtherAppsTemplate: String,
    twoArgumentColonTemplate: String,
    twoArgumentSpaceTemplate: String,
    searchLabel: String,
    serviceProvidersLabel: String,
    universalIpsLabel: String
): AppWiseLogsHeader? {
    if (uid == INVALID_UID) return null

    val info = FirewallManager.getAppInfoByUid(uid) ?: return null
    val packageNames = FirewallManager.getPackageNamesByUid(uid)
    val isRethinkApp = packageNames.any { it == context.packageName }

    val visibleName =
        if (packageNames.size >= 2) {
            String.format(
                appOtherAppsTemplate,
                info.appName,
                (packageNames.size - 1).toString()
            )
        } else {
            info.appName
        }
    val truncated = visibleName.substring(0, visibleName.length.coerceAtMost(10))
    val hint =
        if (isAsn) {
            val txt =
                String.format(twoArgumentSpaceTemplate, searchLabel, serviceProvidersLabel)
            String.format(twoArgumentColonTemplate, truncated, txt)
        } else {
            String.format(twoArgumentColonTemplate, truncated, universalIpsLabel)
        }

    return AppWiseLogsHeader(
        appName = visibleName,
        searchHint = hint,
        appIcon = Utilities.getIcon(context, info.packageName, info.appName),
        isRethinkApp = isRethinkApp
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
    oneHour = stringResource(R.string.ci_desc, "1", stringResource(R.string.lbl_hour)),
    twentyFourHours = stringResource(R.string.ci_desc, "24", stringResource(R.string.lbl_hour)),
    sevenDays = stringResource(R.string.ci_desc, "7", stringResource(R.string.lbl_day)),
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

@Composable
internal fun AppWiseLogsAndroidIcon(appIcon: Drawable?) {
    val bitmap = remember(appIcon) { appIcon?.toBitmap(width = 48, height = 48) }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier = Modifier.size(20.dp))
    } else {
        Icon(MaterialSymbols.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

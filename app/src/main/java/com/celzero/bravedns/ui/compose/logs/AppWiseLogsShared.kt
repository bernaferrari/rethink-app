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
package com.celzero.bravedns.ui.compose.logs

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.paging.compose.LazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.util.Constants.Companion.INVALID_UID
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.viewmodel.AppConnectionsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

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
    row: @Composable (T) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = Dimensions.screenPaddingHorizontal,
                vertical = Dimensions.spacingSm
            ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
    ) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            row(item)
        }
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
        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

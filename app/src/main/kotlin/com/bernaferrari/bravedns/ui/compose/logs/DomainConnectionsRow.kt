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

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.HomeScreenActivity
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.statistics.RethinkStatisticsSummaryItem
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.formatByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionRow(dc: AppConnection, position: CardPosition = CardPosition.Single) {
    val context = LocalContext.current
    val fallbackName = if (dc.appOrDnsName.isNullOrEmpty()) {
        stringResource(R.string.network_log_app_name_unnamed, "(${dc.uid})")
    } else {
        dc.appOrDnsName
    }
    val downloadBytes = dc.downloadBytes
    val uploadBytes = dc.uploadBytes
    val totalUsageText = if (downloadBytes != null && uploadBytes != null) {
        val download =
            stringResource(
                R.string.symbol_download,
                formatByteCount(downloadBytes)
            )
        val upload =
            stringResource(
                R.string.symbol_upload,
                formatByteCount(uploadBytes)
            )
        stringResource(R.string.two_argument, upload, download)
    } else {
        null
    }

    val scope = rememberCoroutineScope()
    var title by remember(dc.uid, dc.appOrDnsName) { mutableStateOf(fallbackName.orEmpty()) }
    var packageName by remember(dc.uid) { mutableStateOf("") }
    var isUnknown by remember(dc.uid) { mutableStateOf(true) }

    LaunchedEffect(dc.uid, dc.appOrDnsName) {
        val resolved =
            withContext(Dispatchers.IO) {
                val appInfo = FirewallManager.getAppInfoByUid(dc.uid)
                val displayName = if (dc.appOrDnsName.isNullOrEmpty()) {
                    appInfo?.appName ?: fallbackName.orEmpty()
                } else {
                    dc.appOrDnsName
                }
                Triple(appInfo == null, displayName, appInfo?.packageName.orEmpty())
            }
        isUnknown = resolved.first
        title = resolved.second ?: fallbackName.orEmpty()
        packageName = resolved.third
    }

    val onClick = {
        scope.launch(Dispatchers.IO) {
            if (isUnknown) {
                // Navigate to network logs via HomeScreenActivity
                val intent = Intent(context, HomeScreenActivity::class.java)
                intent.putExtra(HomeScreenActivity.EXTRA_NAV_TARGET, HomeScreenActivity.NAV_TARGET_NETWORK_LOGS)
                intent.putExtra(Constants.SEARCH_QUERY, dc.appOrDnsName)
                withContext(Dispatchers.Main) { context.startActivity(intent) }
            } else {
                val intent = Intent(context, HomeScreenActivity::class.java)
                intent.putExtra(HomeScreenActivity.EXTRA_NAV_TARGET, HomeScreenActivity.NAV_TARGET_APP_INFO)
                intent.putExtra(HomeScreenActivity.EXTRA_APP_INFO_UID, dc.uid)
                withContext(Dispatchers.Main) { context.startActivity(intent) }
            }
        }
        Unit
    }

    RethinkStatisticsSummaryItem(
        title = title,
        subtitle = totalUsageText,
        countText = dc.count.toString(),
        flagText = null,
        showProgress = false,
        progress = 0f,
        progressColor = Color.Transparent,
        showIndicator = true,
        onClick = onClick,
        position = position,
        leadingContent = { AppIcon(packageName) },
    )
}

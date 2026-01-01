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
package com.celzero.bravedns.adapter

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.activity.AppInfoActivity
import com.celzero.bravedns.ui.activity.DomainConnectionsActivity
import com.celzero.bravedns.ui.activity.NetworkLogsActivity
import com.celzero.bravedns.ui.compose.statistics.StatisticsSummaryItem
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DomainConnectionsAdapter(
    private val context: Context,
    private val type: DomainConnectionsActivity.InputType
) : PagingDataAdapter<AppConnection, DomainConnectionsAdapter.DomainConnectionsViewHolder>(
        DIFF_CALLBACK
    ) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AppConnection>() {
                override fun areItemsTheSame(
                    oldConnection: AppConnection,
                    newConnection: AppConnection
                ): Boolean {
                    return (oldConnection == newConnection)
                }

                override fun areContentsTheSame(
                    oldConnection: AppConnection,
                    newConnection: AppConnection
                ): Boolean {
                    return (oldConnection == newConnection)
                }
            }
    }

    data class SummaryItemUi(
        val title: String,
        val subtitle: String?,
        val countText: String,
        val iconDrawable: Drawable?,
        val flagText: String?,
        val showProgress: Boolean,
        val progress: Float,
        val progressColor: Color,
        val showIndicator: Boolean,
        val onClick: (() -> Unit)?
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainConnectionsViewHolder {
        val composeView = ComposeView(parent.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        return DomainConnectionsViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: DomainConnectionsViewHolder, position: Int) {
        val dc = getItem(position) ?: return
        val fallbackName = if (dc.appOrDnsName.isNullOrEmpty()) {
            context.getString(R.string.network_log_app_name_unnamed, "(${dc.uid})")
        } else {
            dc.appOrDnsName
        }
        val totalUsageText = if (dc.downloadBytes != null && dc.uploadBytes != null) {
            val download =
                context.getString(
                    R.string.symbol_download,
                    Utilities.humanReadableByteCount(dc.downloadBytes, true)
                )
            val upload =
                context.getString(
                    R.string.symbol_upload,
                    Utilities.humanReadableByteCount(dc.uploadBytes, true)
                )
            context.getString(R.string.two_argument, upload, download)
        } else {
            null
        }

        val clickHandler = {
            io {
                if (isUnknownApp(dc)) {
                    uiCtx {
                        val intent = Intent(context, NetworkLogsActivity::class.java)
                        intent.putExtra(
                            Constants.VIEW_PAGER_SCREEN_TO_LOAD,
                            NetworkLogsActivity.Tabs.NETWORK_LOGS.screen
                        )
                        intent.putExtra(Constants.SEARCH_QUERY, dc.appOrDnsName)
                        context.startActivity(intent)
                    }
                } else {
                    uiCtx {
                        val intent = Intent(context, AppInfoActivity::class.java)
                        intent.putExtra(AppInfoActivity.INTENT_UID, dc.uid)
                        context.startActivity(intent)
                    }
                }
            }
        }

        val baseUi = SummaryItemUi(
            title = fallbackName ?: "",
            subtitle = totalUsageText,
            countText = dc.count.toString(),
            iconDrawable = Utilities.getDefaultIcon(context),
            flagText = null,
            showProgress = false,
            progress = 0f,
            progressColor = Color.Transparent,
            showIndicator = true,
            onClick = clickHandler
        )
        holder.bind(baseUi)

        io {
            val appInfo = FirewallManager.getAppInfoByUid(dc.uid)
            val displayName = if (dc.appOrDnsName.isNullOrEmpty()) {
                appInfo?.appName ?: fallbackName.orEmpty()
            } else {
                dc.appOrDnsName
            }
            val icon =
                Utilities.getIcon(
                    context,
                    appInfo?.packageName ?: "",
                    appInfo?.appName ?: ""
                ) ?: Utilities.getDefaultIcon(context)
            uiCtx {
                holder.bind(baseUi.copy(title = displayName, iconDrawable = icon))
            }
        }
    }

    inner class DomainConnectionsViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun bind(ui: SummaryItemUi) {
            composeView.setContent {
                RethinkTheme {
                    StatisticsSummaryItem(
                        title = ui.title,
                        subtitle = ui.subtitle,
                        countText = ui.countText,
                        iconDrawable = ui.iconDrawable,
                        flagText = ui.flagText,
                        showProgress = ui.showProgress,
                        progress = ui.progress,
                        progressColor = ui.progressColor,
                        showIndicator = ui.showIndicator,
                        onClick = ui.onClick
                    )
                }
            }
        }
    }

    @Composable
    fun ConnectionRow(dc: AppConnection) {
        val fallbackName = if (dc.appOrDnsName.isNullOrEmpty()) {
            context.getString(R.string.network_log_app_name_unnamed, "(${dc.uid})")
        } else {
            dc.appOrDnsName
        }
        val totalUsageText = if (dc.downloadBytes != null && dc.uploadBytes != null) {
            val download =
                context.getString(
                    R.string.symbol_download,
                    Utilities.humanReadableByteCount(dc.downloadBytes, true)
                )
            val upload =
                context.getString(
                    R.string.symbol_upload,
                    Utilities.humanReadableByteCount(dc.uploadBytes, true)
                )
            context.getString(R.string.two_argument, upload, download)
        } else {
            null
        }

        val scope = rememberCoroutineScope()
        var title by remember { mutableStateOf(fallbackName.orEmpty()) }
        var icon by remember { mutableStateOf(Utilities.getDefaultIcon(context)) }
        var isUnknown by remember { mutableStateOf(true) }

        LaunchedEffect(dc.uid, dc.appOrDnsName) {
            withContext(Dispatchers.IO) {
                val appInfo = FirewallManager.getAppInfoByUid(dc.uid)
                val displayName = if (dc.appOrDnsName.isNullOrEmpty()) {
                    appInfo?.appName ?: fallbackName.orEmpty()
                } else {
                    dc.appOrDnsName
                }
                val resolvedIcon =
                    Utilities.getIcon(
                        context,
                        appInfo?.packageName ?: "",
                        appInfo?.appName ?: ""
                    ) ?: Utilities.getDefaultIcon(context)
                withContext(Dispatchers.Main) {
                    isUnknown = appInfo == null
                    title = displayName ?: ""
                    icon = resolvedIcon
                }
            }
        }

        val onClick = {
            scope.launch(Dispatchers.IO) {
                if (isUnknown) {
                    val intent = Intent(context, NetworkLogsActivity::class.java)
                    intent.putExtra(
                        Constants.VIEW_PAGER_SCREEN_TO_LOAD,
                        NetworkLogsActivity.Tabs.NETWORK_LOGS.screen
                    )
                    intent.putExtra(Constants.SEARCH_QUERY, dc.appOrDnsName)
                    withContext(Dispatchers.Main) { context.startActivity(intent) }
                } else {
                    val intent = Intent(context, AppInfoActivity::class.java)
                    intent.putExtra(AppInfoActivity.INTENT_UID, dc.uid)
                    withContext(Dispatchers.Main) { context.startActivity(intent) }
                }
            }
            Unit
        }

        StatisticsSummaryItem(
            title = title,
            subtitle = totalUsageText,
            countText = dc.count.toString(),
            iconDrawable = icon,
            flagText = null,
            showProgress = false,
            progress = 0f,
            progressColor = Color.Transparent,
            showIndicator = true,
            onClick = onClick
        )
    }

    private suspend fun isUnknownApp(appConnection: AppConnection): Boolean {
        val appInfo = FirewallManager.getAppInfoByUid(appConnection.uid)
        return appInfo == null
    }

    private fun io(f: suspend () -> Unit) {
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

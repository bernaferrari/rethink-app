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

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.database.AppInfo
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.compose.statistics.StatisticsSummaryItem
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log2

class WgNwStatsAdapter(private val context: Context) :
    PagingDataAdapter<AppConnection, WgNwStatsAdapter.WgNwStatsAdapterViewHolder>(DIFF_CALLBACK) {

    private var maxValue: Int = 0

    companion object {
        private val TAG = WgNwStatsAdapter::class.simpleName

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AppConnection>() {
                override fun areItemsTheSame(old: AppConnection, new: AppConnection): Boolean {
                    return (old == new)
                }

                override fun areContentsTheSame(old: AppConnection, new: AppConnection): Boolean {
                    return (old == new)
                }
            }
    }

    private data class SummaryItemUi(
        val title: String,
        val subtitle: String?,
        val countText: String,
        val iconDrawable: Drawable?,
        val showProgress: Boolean,
        val progress: Float,
        val progressColor: Color
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WgNwStatsAdapterViewHolder {
        val composeView = ComposeView(parent.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        return WgNwStatsAdapterViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: WgNwStatsAdapterViewHolder, position: Int) {
        val conn = getItem(position) ?: return
        holder.bind(conn)
    }

    private fun calculatePercentage(c: Double): Int {
        val value = (log2(c) * 100).toInt()
        if (value > maxValue) {
            maxValue = value
        }
        return if (maxValue == 0) {
            0
        } else {
            (value * 100 / maxValue)
        }
    }

    inner class WgNwStatsAdapterViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun bind(conn: AppConnection) {
            Logger.d(LOG_TAG_UI, "$TAG: Binding data for ${conn.uid}, ${conn.appOrDnsName}")

            val usageText = if (conn.downloadBytes != null && conn.uploadBytes != null) {
                val download =
                    context.getString(
                        R.string.symbol_download,
                        Utilities.humanReadableByteCount(conn.downloadBytes, true)
                    )
                val upload =
                    context.getString(
                        R.string.symbol_upload,
                        Utilities.humanReadableByteCount(conn.uploadBytes, true)
                    )
                context.getString(R.string.two_argument, upload, download)
            } else {
                null
            }

            val progressPercent = calculatePercentage(((conn.downloadBytes ?: 0L) + (conn.uploadBytes ?: 0L)).toDouble())
            val baseUi = SummaryItemUi(
                title = conn.appOrDnsName ?: "",
                subtitle = usageText,
                countText = conn.count.toString(),
                iconDrawable = Utilities.getDefaultIcon(context),
                showProgress = true,
                progress = progressPercent / 100f,
                progressColor = Color.Transparent
            )
            bindUi(baseUi)

            io {
                val appInfo = FirewallManager.getAppInfoByUid(conn.uid)
                val appName = getAppName(conn, appInfo) ?: ""
                val icon =
                    Utilities.getIcon(
                        context,
                        appInfo?.packageName ?: "",
                        appInfo?.appName ?: ""
                    ) ?: Utilities.getDefaultIcon(context)
                uiCtx {
                    bindUi(baseUi.copy(title = appName, iconDrawable = icon))
                }
            }
        }

        private fun bindUi(ui: SummaryItemUi) {
            composeView.setContent {
                RethinkTheme {
                    StatisticsSummaryItem(
                        title = ui.title,
                        subtitle = ui.subtitle,
                        countText = ui.countText,
                        iconDrawable = ui.iconDrawable,
                        flagText = null,
                        showProgress = ui.showProgress,
                        progress = ui.progress,
                        progressColor = androidx.compose.ui.res.colorResource(id = R.color.accentGood),
                        showIndicator = false,
                        onClick = null
                    )
                }
            }
        }

        private fun getAppName(conn: AppConnection, appInfo: AppInfo?): String? {
            return if (conn.appOrDnsName.isNullOrEmpty()) {
                if (appInfo?.appName.isNullOrEmpty()) {
                    context.getString(R.string.network_log_app_name_unnamed, "($conn.uid)")
                } else {
                    appInfo?.appName
                }
            } else {
                conn.appOrDnsName
            }
        }
    }

    private fun io(f: suspend () -> Unit) {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) { f() }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }
}

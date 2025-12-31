/*
 * Copyright 2021 RethinkDNS and its authors
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
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConnection
import com.celzero.bravedns.service.IpRulesManager
import com.celzero.bravedns.ui.bottomsheet.AppIpRulesDialog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.removeBeginningTrailingCommas
import io.github.aakira.napier.Napier
import kotlin.math.log2

class AppWiseIpsAdapter(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    val uid: Int,
    val isAsn: Boolean = false
) : PagingDataAdapter<AppConnection, AppWiseIpsAdapter.ConnectionDetailsViewHolder>(DIFF_CALLBACK) {

    private var maxValue: Int = 0
    private var minPercentage: Int = 100

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AppConnection>() {
                override fun areItemsTheSame(old: AppConnection, new: AppConnection) = old == new

                override fun areContentsTheSame(old: AppConnection, new: AppConnection) = old == new
            }
        private const val TAG = "AppWiseIpsAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionDetailsViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return ConnectionDetailsViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ConnectionDetailsViewHolder, position: Int) {
        val appConnection: AppConnection = getItem(position) ?: return
        holder.update(appConnection)
    }

    private fun calculatePercentage(c: Double): Int {
        val value = (log2(c) * 100).toInt()
        if (value > maxValue) {
            maxValue = value
        }
        return if (maxValue == 0) {
            0
        } else {
            val percentage = (value * 100 / maxValue)
            if (percentage < minPercentage && percentage != 0) {
                minPercentage = percentage
            }
            percentage
        }
    }

    inner class ConnectionDetailsViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {
        fun update(conn: AppConnection) {
            composeView.setContent {
                RethinkTheme {
                    IpRow(conn)
                }
            }
        }
    }

    @Composable
    private fun IpRow(conn: AppConnection) {
        val countText = conn.count.toString()
        val flagText =
            if (isAsn) {
                val cc = Utilities.getFlag(conn.flag)
                if (cc.isEmpty()) "--" else cc
            } else {
                conn.flag
            }
        val titleText = if (isAsn) conn.appOrDnsName else conn.ipAddress
        val secondaryText =
            if (isAsn) conn.ipAddress else conn.appOrDnsName?.let { beautifyDomainString(it) }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { handleClick(conn) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = flagText, style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titleText.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    if (!secondaryText.isNullOrEmpty()) {
                        Text(text = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isAsn) {
                        IpProgress(conn)
                    }
                }
                Text(text = countText, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.fillMaxWidth())
        }
    }

    @Composable
    private fun IpProgress(conn: AppConnection) {
        val status = IpRulesManager.getMostSpecificRuleMatch(conn.uid, conn.ipAddress)
        val color =
            when (status) {
                IpRulesManager.IpRuleStatus.NONE ->
                    UIUtils.fetchToggleBtnColors(context, R.color.chipTextNeutral)
                IpRulesManager.IpRuleStatus.BLOCK ->
                    UIUtils.fetchToggleBtnColors(context, R.color.accentBad)
                IpRulesManager.IpRuleStatus.BYPASS_UNIVERSAL ->
                    UIUtils.fetchToggleBtnColors(context, R.color.accentGood)
                IpRulesManager.IpRuleStatus.TRUST ->
                    UIUtils.fetchToggleBtnColors(context, R.color.accentGood)
            }

        var p = calculatePercentage(conn.count.toDouble())
        if (p == 0) {
            p = minPercentage / 2
        }

        LinearProgressIndicator(
            progress = p / 100f,
            color = Color(color),
            trackColor = Color(UIUtils.fetchColor(context, R.attr.background)),
            modifier = Modifier.fillMaxWidth()
        )
    }

    private fun handleClick(conn: AppConnection) {
        if (isAsn) return
        openBottomSheet(conn)
    }

    private fun openBottomSheet(conn: AppConnection) {
        if (isAsn) return
        val activity = context as? FragmentActivity
        if (activity == null) {
            Napier.w("$TAG invalid context for app ip dialog")
            return
        }
        AppIpRulesDialog(
            activity = activity,
            uid = uid,
            ipAddress = conn.ipAddress,
            domains = beautifyDomainString(conn.appOrDnsName ?: ""),
            position = RecyclerView.NO_POSITION
        ) { position ->
            notifyDataset(position)
        }.show()
    }

    private fun beautifyDomainString(d: String): String {
        return removeBeginningTrailingCommas(d).replace(",,", ",").replace(",", ", ")
    }

    private fun notifyDataset(position: Int) {
        try {
            if (position >= 0 && position < itemCount) {
                notifyItemChanged(position)
            } else {
                Napier.w("$TAG invalid position: $position, itemCount: $itemCount, refreshing adapter")
                refresh()
            }
        } catch (e: Exception) {
            Napier.e("$TAG error notifying position $position: ${e.message}", e)
            refresh()
        }
    }
}

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
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.database.ConsoleLog
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import io.github.aakira.napier.Napier

class ConsoleLogAdapter(private val context: Context) :
    PagingDataAdapter<ConsoleLog, ConsoleLogAdapter.ConsoleLogViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ConsoleLog>() {
                override fun areItemsTheSame(old: ConsoleLog, new: ConsoleLog): Boolean {
                    return old.id == new.id
                }

                override fun areContentsTheSame(old: ConsoleLog, new: ConsoleLog): Boolean {
                    return old == new
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConsoleLogViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return ConsoleLogViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ConsoleLogViewHolder, position: Int) {
        if (position < 0 || position >= itemCount) return

        try {
            val logInfo = getItem(position) ?: return
            holder.update(logInfo)
        } catch (e: IndexOutOfBoundsException) {
            Napier.w("ConsoleLogAdapter err invalid pos: $position, itemCount: $itemCount")
        }
    }

    inner class ConsoleLogViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(log: ConsoleLog) {
            try {
                if (log.message.isEmpty()) return
                composeView.setContent {
                    RethinkTheme {
                        ConsoleLogRow(log)
                    }
                }
            } catch (e: Exception) {
                Napier.w("ConsoleLogAdapter error updating view holder: ${e.message}")
            }
        }
    }

    @Composable
    private fun ConsoleLogRow(log: ConsoleLog) {
        val logLevel = log.message.firstOrNull() ?: 'V'
        val colorRes =
            when (logLevel) {
                'I' -> R.attr.defaultToggleBtnTxt
                'W' -> R.attr.firewallWhiteListToggleBtnTxt
                'E' -> R.attr.firewallBlockToggleBtnTxt
                else -> R.attr.primaryLightColorText
            }
        val logColor = Color(UIUtils.fetchColor(context, colorRes))
        val timestamp =
            if (DEBUG) {
                "${log.id}\n${Utilities.convertLongToTime(log.timestamp, TIME_FORMAT_1)}"
            } else {
                Utilities.convertLongToTime(log.timestamp, TIME_FORMAT_1)
            }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = Color(UIUtils.fetchColor(context, R.attr.primaryLightColorText))
            )
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = logColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

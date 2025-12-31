/*
 * Copyright 2023 RethinkDNS and its authors
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.database.RethinkRemoteFileTag
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteAdvancedViewAdapter(val context: Context) :
    PagingDataAdapter<
        RethinkRemoteFileTag,
        RemoteAdvancedViewAdapter.RethinkRemoteFileTagViewHolder
    >(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<RethinkRemoteFileTag>() {

                override fun areItemsTheSame(
                    oldConnection: RethinkRemoteFileTag,
                    newConnection: RethinkRemoteFileTag
                ): Boolean {
                    return oldConnection == newConnection
                }

                override fun areContentsTheSame(
                    oldConnection: RethinkRemoteFileTag,
                    newConnection: RethinkRemoteFileTag
                ): Boolean {
                    return oldConnection == newConnection
                }
            }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RethinkRemoteFileTagViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return RethinkRemoteFileTagViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: RethinkRemoteFileTagViewHolder, position: Int) {
        val filetag: RethinkRemoteFileTag = getItem(position) ?: return
        holder.update(filetag, position)
    }

    inner class RethinkRemoteFileTagViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(filetag: RethinkRemoteFileTag, position: Int) {
            val showHeader = position == 0 || getItem(position - 1)?.group != filetag.group
            composeView.setContent {
                RethinkTheme {
                    BlocklistRow(filetag = filetag, showHeader = showHeader) { isSelected ->
                        toggleCheckbox(isSelected, filetag)
                    }
                }
            }
        }

        private fun toggleCheckbox(isSelected: Boolean, filetag: RethinkRemoteFileTag) {
            setFileTag(filetag, isSelected)
        }

        private fun setFileTag(filetag: RethinkRemoteFileTag, selected: Boolean) {
            io {
                filetag.isSelected = selected
                RethinkBlocklistManager.updateFiletagRemote(filetag)
                val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
                RethinkBlocklistState.updateFileTagList(list)
            }
        }
    }

    @Composable
    private fun BlocklistRow(
        filetag: RethinkRemoteFileTag,
        showHeader: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val backgroundColor =
            if (filetag.isSelected) {
                Color(fetchColor(context, R.attr.selectedCardBg))
            } else {
                Color(fetchColor(context, R.attr.background))
            }
        val groupText = if (filetag.subg.isEmpty()) filetag.group else filetag.subg
        val entryText = context.getString(R.string.dc_entries, filetag.entries.toString())
        val level = filetag.level?.firstOrNull()
        val (chipText, chipBg) = chipColorsForLevel(level)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showHeader) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = getGroupName(filetag.group),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(fetchColor(context, R.attr.accentBad))
                    )
                    Text(
                        text = getTitleDesc(filetag.group),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(fetchColor(context, R.attr.primaryLightColorText))
                    )
                }
            }

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(!filetag.isSelected) },
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = filetag.vname,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = groupText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            AssistChip(
                                onClick = { openUrl(context, filetag.url[0]) },
                                label = { Text(text = entryText) },
                                colors =
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = chipBg,
                                        labelColor = chipText
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = filetag.isSelected,
                        onCheckedChange = { onToggle(it) }
                    )
                }
            }
        }
    }

    private fun chipColorsForLevel(level: Int?): Pair<Color, Color> {
        if (level == null) {
            val text = Color(fetchColor(context, R.attr.primaryTextColor))
            val bg = Color(fetchColor(context, R.attr.background))
            return text to bg
        }

        return when (level) {
            0 -> {
                val text = Color(fetchColor(context, R.attr.chipTextPositive))
                val bg = Color(fetchColor(context, R.attr.chipBgColorPositive))
                text to bg
            }
            1 -> {
                val text = Color(fetchColor(context, R.attr.chipTextNeutral))
                val bg = Color(fetchColor(context, R.attr.chipBgColorNeutral))
                text to bg
            }
            2 -> {
                val text = Color(fetchColor(context, R.attr.chipTextNegative))
                val bg = Color(fetchColor(context, R.attr.chipBgColorNegative))
                text to bg
            }
            else -> {
                val text = Color(fetchColor(context, R.attr.primaryTextColor))
                val bg = Color(fetchColor(context, R.attr.background))
                text to bg
            }
        }
    }

    private fun getTitleDesc(title: String): String {
        return if (title.equals(RethinkBlocklistManager.PARENTAL_CONTROL.name, true)) {
            context.getString(RethinkBlocklistManager.PARENTAL_CONTROL.desc)
        } else if (title.equals(RethinkBlocklistManager.SECURITY.name, true)) {
            context.getString(RethinkBlocklistManager.SECURITY.desc)
        } else if (title.equals(RethinkBlocklistManager.PRIVACY.name, true)) {
            context.getString(RethinkBlocklistManager.PRIVACY.desc)
        } else {
            ""
        }
    }

    private fun getGroupName(group: String): String {
        return if (group.equals(RethinkBlocklistManager.PARENTAL_CONTROL.name, true)) {
            context.getString(RethinkBlocklistManager.PARENTAL_CONTROL.label)
        } else if (group.equals(RethinkBlocklistManager.SECURITY.name, true)) {
            context.getString(RethinkBlocklistManager.SECURITY.label)
        } else if (group.equals(RethinkBlocklistManager.PRIVACY.name, true)) {
            context.getString(RethinkBlocklistManager.PRIVACY.label)
        } else {
            ""
        }
    }

    private fun io(f: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { f() }
    }
}

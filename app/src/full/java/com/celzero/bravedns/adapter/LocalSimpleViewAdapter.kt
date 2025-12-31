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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.celzero.bravedns.database.LocalBlocklistPacksMap
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.UIUtils.fetchToggleBtnColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocalSimpleViewAdapter(val context: Context) :
    PagingDataAdapter<LocalBlocklistPacksMap, LocalSimpleViewAdapter.RethinkSimpleViewHolder>(
        DIFF_CALLBACK
    ) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<LocalBlocklistPacksMap>() {

                override fun areItemsTheSame(
                    oldConnection: LocalBlocklistPacksMap,
                    newConnection: LocalBlocklistPacksMap
                ): Boolean {
                    return oldConnection == newConnection
                }

                override fun areContentsTheSame(
                    oldConnection: LocalBlocklistPacksMap,
                    newConnection: LocalBlocklistPacksMap
                ): Boolean {
                    return oldConnection == newConnection
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RethinkSimpleViewHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        return RethinkSimpleViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: RethinkSimpleViewHolder, position: Int) {
        val map: LocalBlocklistPacksMap = getItem(position) ?: return
        holder.update(map, position)
    }

    inner class RethinkSimpleViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        fun update(map: LocalBlocklistPacksMap, position: Int) {
            val showHeader = position == 0 || getItem(position - 1)?.group != map.group
            composeView.setContent {
                RethinkTheme {
                    BlocklistRow(map = map, showHeader = showHeader) { isSelected ->
                        toggleCheckbox(isSelected, map)
                    }
                }
            }
        }

        private fun toggleCheckbox(isSelected: Boolean, map: LocalBlocklistPacksMap) {
            setFileTag(map.blocklistIds.toMutableList(), if (isSelected) 1 else 0)
        }

        private fun setFileTag(tagIds: MutableList<Int>, selected: Int) {
            io {
                RethinkBlocklistManager.updateFiletagsLocal(tagIds.toSet(), selected)
                val selectedTags = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
                RethinkBlocklistState.updateFileTagList(selectedTags)
                ui { notifyDataSetChanged() }
            }
        }
    }

    @Composable
    private fun BlocklistRow(
        map: LocalBlocklistPacksMap,
        showHeader: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val selectedTags = RethinkBlocklistState.getSelectedFileTags()
        val isSelected = selectedTags.containsAll(map.blocklistIds)
        val backgroundColor =
            if (isSelected) {
                Color(fetchColor(context, R.attr.selectedCardBg))
            } else {
                Color(fetchColor(context, R.attr.background))
            }
        val indicatorColor = getLevelIndicatorColor(map.level)

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
                        text = getGroupName(map.group),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(fetchColor(context, R.attr.accentBad))
                    )
                    Text(
                        text = getTitleDesc(map.group),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(fetchColor(context, R.attr.primaryLightColorText))
                    )
                }
            }

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(!isSelected) },
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(2.5.dp)
                                .fillMaxHeight()
                                .background(indicatorColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = map.pack.replaceFirstChar(Char::titlecase),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text =
                                context.getString(
                                    R.string.rsv_blocklist_count_text,
                                    map.blocklistIds.size.toString()
                                ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(checked = isSelected, onCheckedChange = { onToggle(it) })
                }
            }
        }
    }

    private fun getLevelIndicatorColor(level: Int): Color {
        val resId =
            when (level) {
                0 -> R.color.firewallNoRuleToggleBtnBg
                1 -> R.color.firewallWhiteListToggleBtnTxt
                2 -> R.color.firewallBlockToggleBtnTxt
                else -> R.color.firewallNoRuleToggleBtnBg
            }
        return Color(fetchToggleBtnColors(context, resId))
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

    private fun ui(f: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch { f() }
    }
}

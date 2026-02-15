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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.LocalBlocklistPacksMap
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState

@Composable
fun LocalSimpleBlocklistRow(
    map: LocalBlocklistPacksMap,
    showHeader: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val selectedTags = RethinkBlocklistState.getSelectedFileTags()
    val isSelected = selectedTags.containsAll(map.blocklistIds)
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
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
                    text = RethinkBlocklistManager.getGroupName(context, map.group),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = RethinkBlocklistManager.getTitleDesc(context, map.group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun getLevelIndicatorColor(level: Int): Color =
    when (level) {
        1 -> MaterialTheme.colorScheme.tertiary
        2 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.RethinkRemoteFileTag
import com.celzero.bravedns.service.RethinkBlocklistManager
import com.celzero.bravedns.ui.rethink.RethinkBlocklistState
import com.celzero.bravedns.util.UIUtils.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RemoteAdvancedBlocklistRow(
    filetag: RethinkRemoteFileTag,
    showHeader: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val backgroundColor =
        if (filetag.isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }
    val groupText = if (filetag.subg.isEmpty()) filetag.group else filetag.subg
    val entryText = context.resources.getString(R.string.dc_entries, filetag.entries.toString())
    val level = filetag.level?.firstOrNull()
    val (chipText, chipBg) = chipColorsForLevel(context, level)

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
                    text = RethinkBlocklistManager.getGroupName(context, filetag.group),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = RethinkBlocklistManager.getTitleDesc(context, filetag.group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun chipColorsForLevel(context: Context, level: Int?): Pair<Color, Color> {
    if (level == null) {
        val text = MaterialTheme.colorScheme.onSurface
        val bg = MaterialTheme.colorScheme.surface
        return text to bg
    }

    return when (level) {
        0 -> {
            val text = MaterialTheme.colorScheme.tertiary
            val bg = MaterialTheme.colorScheme.tertiaryContainer
            text to bg
        }
        1 -> {
            val text = MaterialTheme.colorScheme.onSurfaceVariant
            val bg = MaterialTheme.colorScheme.surfaceVariant
            text to bg
        }
        2 -> {
            val text = MaterialTheme.colorScheme.error
            val bg = MaterialTheme.colorScheme.errorContainer
            text to bg
        }
        else -> {
            val text = MaterialTheme.colorScheme.onSurface
            val bg = MaterialTheme.colorScheme.surface
            text to bg
        }
    }
}

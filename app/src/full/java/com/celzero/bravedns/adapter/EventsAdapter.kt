/*
 * Copyright 2025 RethinkDNS and its authors
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.Event
import com.celzero.bravedns.database.Severity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ROTATION_EXPANDED = 180f
private const val ROTATION_COLLAPSED = 0f

fun copyEventToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Event Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
fun EventCard(event: Event, onCopy: (String) -> Unit) {
    val hasDetails = !event.details.isNullOrBlank()
    var expanded by remember(event.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) ROTATION_EXPANDED else ROTATION_COLLAPSED,
        label = "event-expand"
    )

    val severityColor = Color(
        when (event.severity) {
            Severity.LOW -> 0xFF4CAF50.toInt()
            Severity.MEDIUM -> 0xFFFFC107.toInt()
            Severity.HIGH -> 0xFFFF9800.toInt()
            Severity.CRITICAL -> 0xFFF44336.toInt()
        }
    )
    val iconRes = when (event.severity) {
        Severity.LOW -> R.drawable.ic_tick_normal
        Severity.MEDIUM -> R.drawable.ic_app_info_accent
        Severity.HIGH -> R.drawable.ic_block_accent
        Severity.CRITICAL -> R.drawable.ic_block
    }

    Card(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (hasDetails) expanded = !expanded },
                    onLongClick = { onCopy(event.message) }
                )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(severityColor)
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = severityColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = event.eventType.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = severityColor,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = event.severity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (hasDetails) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_down),
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }

                val date =
                    remember(event.timestamp) {
                        val df = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                        df.format(Date(event.timestamp))
                    }
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium
                )

                AnimatedVisibility(visible = expanded && hasDetails) {
                    Text(
                        text = event.details ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

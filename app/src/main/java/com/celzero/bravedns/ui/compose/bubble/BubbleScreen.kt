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
package com.celzero.bravedns.ui.compose.bubble

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AllowedAppInfo
import com.celzero.bravedns.data.BlockedAppInfo
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import io.github.aakira.napier.Napier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun BubbleScreen(
    vpnOn: Boolean,
    allowedItems: LazyPagingItems<AllowedAppInfo>,
    blockedItems: LazyPagingItems<BlockedAppInfo>,
    onAllowApp: (BlockedAppInfo, () -> Unit) -> Unit,
    onRemoveAllowed: (AllowedAppInfo, () -> Unit) -> Unit
) {
    val allowedLoaded = allowedItems.loadState.refresh is LoadState.NotLoading
    val allowedCount = allowedItems.itemCount
    val showAllowedSection = vpnOn && allowedLoaded && allowedCount > 0

    val blockedLoading = blockedItems.loadState.refresh is LoadState.Loading
    val blockedError = blockedItems.loadState.refresh is LoadState.Error
    val blockedLoaded = blockedItems.loadState.refresh is LoadState.NotLoading
    val blockedEmpty = blockedLoaded && blockedItems.itemCount == 0

    val showEmptyState = !vpnOn || blockedError || blockedEmpty

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeaderSection()
        }

        if (showAllowedSection) {
            item {
                AllowedHeader(count = allowedCount)
            }
            itemsIndexed(
                items = List(allowedItems.itemCount) { it },
                key = { index, _ -> allowedItems[index]?.uid ?: index }
            ) { index, _ ->
                val app = allowedItems[index] ?: return@itemsIndexed
                AllowedAppRow(
                    app = app,
                    onRemove = {
                        onRemoveAllowed(app) {
                            allowedItems.refresh()
                            blockedItems.refresh()
                        }
                    }
                )
            }
        }

        item {
            BlockedHeader()
        }

        when {
            blockedLoading -> {
                item { LoadingCard() }
            }
            showEmptyState -> {
                item { EmptyState() }
            }
            else -> {
                itemsIndexed(
                    items = List(blockedItems.itemCount) { it },
                    key = { index, _ -> blockedItems[index]?.uid ?: index }
                ) { index, _ ->
                    val app = blockedItems[index] ?: return@itemsIndexed
                    BlockedAppRow(
                        app = app,
                        onAllow = {
                            onAllowApp(app) {
                                blockedItems.refresh()
                                allowedItems.refresh()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            text = stringResource(R.string.firewall_bubble_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.firewall_bubble_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllowedHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bubble_allowed_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun BlockedHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bubble_activity_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.bubble_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_firewall_shield),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.bubble_empty_state_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.bubble_empty_state_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
fun AllowedAppRow(app: AllowedAppInfo, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = allowedTimeRemaining(app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.lbl_remove),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun BlockedAppRow(app: BlockedAppInfo, onAllow: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.bubble_blocked_count, app.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeAgo(context, app.lastBlocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(onClick = onAllow) {
                Text(text = stringResource(R.string.bubble_allow_btn))
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) { loadAppIcon(context, packageName) }
    Box(
        modifier =
        Modifier.size(44.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val painter = rememberDrawablePainter(icon)
        painter?.let {
            Image(
                painter = it,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun loadAppIcon(context: android.content.Context, packageName: String): Drawable {
    return try {
        if (packageName != "Unknown") {
            context.packageManager.getApplicationIcon(packageName)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
        }
    } catch (_: Exception) {
        Napier.e("App icon not found for $packageName")
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
    }
}

private fun allowedTimeRemaining(app: AllowedAppInfo): String {
    val now = System.currentTimeMillis()
    val expiresAt = app.allowedAt + (15 * 60 * 1000)
    val remaining = (expiresAt - now) / 1000 / 60
    return if (remaining > 0) {
        "$remaining min${if (remaining != 1L) "s" else ""} remaining"
    } else {
        "Expired"
    }
}

private fun timeAgo(context: android.content.Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> {
            context.getString(R.string.bubble_time_just_now)
        }
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            context.getString(R.string.bubble_time_minutes_ago, minutes)
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            context.getString(R.string.bubble_time_hours_ago, hours)
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.bubble

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkBubbleAllowedItem(val id: String, val packageName: String, val appName: String, val remainingLabel: String)
data class RethinkBubbleBlockedItem(val id: String, val packageName: String, val appName: String, val blockedLabel: String, val timeLabel: String)

data class RethinkBubbleStrings(
    val title: String,
    val subtitle: String,
    val allowedTitle: String,
    val activityTitle: String,
    val loading: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val remove: String,
    val allow: String,
)

/** Target-neutral firewall bubble activity surface. The host provides paging and app icons. */
@Composable
fun RethinkBubbleScreen(
    vpnOn: Boolean,
    allowedItems: List<RethinkBubbleAllowedItem>,
    blockedItems: List<RethinkBubbleBlockedItem>,
    blockedLoading: Boolean,
    blockedError: Boolean,
    strings: RethinkBubbleStrings,
    appIcon: @Composable (packageName: String) -> Unit,
    onAllow: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAllowed = vpnOn && allowedItems.isNotEmpty()
    val showEmpty = !vpnOn || blockedError || (!blockedLoading && blockedItems.isEmpty())
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingXl),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        item { BubbleHeader(strings) }
        if (showAllowed) {
            item { AllowedHeader(strings.allowedTitle, allowedItems.size) }
            items(allowedItems, key = { it.id }) { item ->
                BubbleAllowedRow(item, strings.remove, appIcon, onRemove)
            }
        }
        item { SectionHeader(strings.activityTitle, modifier = Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal)) }
        when {
            blockedLoading -> item { BubbleLoading(strings.loading) }
            showEmpty -> item { BubbleEmpty(strings) }
            else -> items(blockedItems, key = { it.id }) { item -> BubbleBlockedRow(item, strings.allow, appIcon, onAllow) }
        }
    }
}

@Composable
private fun BubbleHeader(strings: RethinkBubbleStrings) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(SharedDimensions.spacingLg), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Filled.Security, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(strings.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AllowedHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg)) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun BubbleAllowedRow(item: RethinkBubbleAllowedItem, remove: String, appIcon: @Composable (String) -> Unit, onRemove: (String) -> Unit) {
    BubbleAppRow(item.packageName, item.appName, appIcon, details = {
        Spacer(Modifier.height(2.dp))
        Text(item.remainingLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }) {
        TextButton(onClick = { onRemove(item.id) }) { Text(remove, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun BubbleBlockedRow(item: RethinkBubbleBlockedItem, allow: String, appIcon: @Composable (String) -> Unit, onAllow: (String) -> Unit) {
    BubbleAppRow(item.packageName, item.appName, appIcon, details = {
        Spacer(Modifier.height(2.dp))
        Text(item.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.blockedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(SharedDimensions.spacingSm))
            Text(item.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }) {
        Button(
            onClick = { onAllow(item.id) },
            modifier = Modifier.height(SharedDimensions.buttonHeightSm),
            shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
            contentPadding = PaddingValues(horizontal = SharedDimensions.spacingMd),
        ) { Text(allow) }
    }
}

@Composable
private fun BubbleAppRow(packageName: String, appName: String, appIcon: @Composable (String) -> Unit, details: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    BubbleListCard {
        appIcon(packageName)
        Spacer(Modifier.width(SharedDimensions.spacingMd))
        Column(Modifier.weight(1f)) {
            Text(appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            details()
        }
        trailing()
    }
}

@Composable
private fun BubbleLoading(label: String) = BubbleCard {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(36.dp)); Spacer(Modifier.height(10.dp)); Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BubbleEmpty(strings: RethinkBubbleStrings) = BubbleCard {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(MaterialSymbols.Filled.Security, null, modifier = Modifier.padding(10.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(SharedDimensions.spacingMd))
        Text(strings.emptyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SharedDimensions.spacingSm))
        Text(strings.emptyDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BubbleCard(content: @Composable () -> Unit) = Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    tonalElevation = 1.dp,
    content = content,
)

@Composable
private fun BubbleListCard(content: @Composable RowScope.() -> Unit) = Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
    shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    tonalElevation = 1.dp,
) { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content) }

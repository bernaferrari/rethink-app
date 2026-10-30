/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.bubble

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkScreenDefaults
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

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
    val vpnOffTitle: String,
    val vpnOffDescription: String,
    val errorTitle: String,
    val errorDescription: String,
    val retry: String,
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAllowed = vpnOn && allowedItems.isNotEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = RethinkScreenDefaults.contentPadding,
    ) {
        item(key = "bubble_header") {
            RethinkListItem(
                headline = strings.title,
                supporting = strings.subtitle,
                leadingIcon = MaterialSymbols.Filled.ChatBubble,
                position = CardPosition.Single,
            )
        }
        if (showAllowed) {
            item(key = "bubble_allowed_header") { BubbleSectionHeader(strings.allowedTitle, allowedItems.size) }
            itemsIndexed(allowedItems, key = { _, it -> "allowed_${it.id}" }) { index, item ->
                BubbleAllowedRow(item, strings.remove, cardPositionFor(index, allowedItems.lastIndex), appIcon, onRemove)
            }
        }
        item(key = "bubble_activity_header") { SectionHeader(strings.activityTitle) }
        when {
            !vpnOn -> item {
                RethinkEmptyState(icon = MaterialSymbols.Filled.RemoveModerator, title = strings.vpnOffTitle, message = strings.vpnOffDescription)
            }
            blockedLoading -> item { RethinkLoadingState(label = strings.loading) }
            blockedError -> item {
                RethinkErrorState(title = strings.errorTitle, message = strings.errorDescription, retryLabel = strings.retry, onRetry = onRetry)
            }
            blockedItems.isEmpty() -> item {
                RethinkEmptyState(icon = MaterialSymbols.Filled.GppGood, title = strings.emptyTitle, message = strings.emptyDescription)
            }
            else -> itemsIndexed(blockedItems, key = { _, it -> "blocked_${it.id}" }) { index, item ->
                BubbleBlockedRow(item, strings.allow, cardPositionFor(index, blockedItems.lastIndex), appIcon, onAllow)
            }
        }
    }
}

@Composable
private fun BubbleSectionHeader(title: String, count: Int) {
    SectionHeader(title, trailing = { RethinkPill(count.toString(), colors = RethinkPillDefaults.primary()) })
}

@Composable
private fun BubbleAllowedRow(
    item: RethinkBubbleAllowedItem,
    remove: String,
    position: CardPosition,
    appIcon: @Composable (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    RethinkListItem(
        headline = item.appName,
        supporting = item.remainingLabel,
        leadingContent = { appIcon(item.packageName) },
        position = position,
        trailing = {
            TextButton(onClick = { onRemove(item.id) }) {
                Text(remove, color = RethinkStatusColors.blocked, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun BubbleBlockedRow(
    item: RethinkBubbleBlockedItem,
    allow: String,
    position: CardPosition,
    appIcon: @Composable (String) -> Unit,
    onAllow: (String) -> Unit,
) {
    val blockedColor = RethinkStatusColors.blocked
    val supporting = remember(item.blockedLabel, item.timeLabel, blockedColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = blockedColor)) { append(item.blockedLabel) }
            if (item.timeLabel.isNotBlank()) {
                append(" · ")
                append(item.timeLabel)
            }
        }
    }
    RethinkListItem(
        headline = item.appName,
        supportingAnnotated = supporting,
        leadingContent = { appIcon(item.packageName) },
        position = position,
        trailing = {
            FilledTonalButton(
                onClick = { onAllow(item.id) },
                modifier = Modifier.height(SharedDimensions.buttonHeightSm),
                contentPadding = PaddingValues(horizontal = SharedDimensions.spacingMd),
            ) { Text(allow) }
        },
    )
}

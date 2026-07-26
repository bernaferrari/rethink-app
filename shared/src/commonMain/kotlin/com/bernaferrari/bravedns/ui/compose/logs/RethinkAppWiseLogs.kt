/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

enum class RethinkAppWiseLogTimeRange { OneHour, TwentyFourHours, SevenDays }

data class RethinkAppWiseLogsStrings(
    val clearSearch: String,
    val delete: String,
    val deleteTitle: String,
    val deleteDescription: String,
    val proceed: String,
    val cancel: String,
    val oneHour: String,
    val twentyFourHours: String,
    val sevenDays: String,
)

@Composable
fun RethinkAppWiseLogsScaffold(
    title: String,
    onBackClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { RethinkLargeTopBar(title, onBackClick = onBackClick, scrollBehavior = scroll) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun RethinkAppWiseLogsDeleteDialog(strings: RethinkAppWiseLogsStrings, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = strings.deleteTitle,
        message = strings.deleteDescription,
        confirmText = strings.proceed,
        dismissText = strings.cancel,
        isConfirmDestructive = true,
        onConfirm = { onDismiss(); onConfirm() },
        onDismiss = onDismiss,
    )
}

@Composable
fun RethinkAppWiseLogsContent(
    title: String,
    searchHint: String,
    fallbackSearchHint: String,
    strings: RethinkAppWiseLogsStrings,
    selectedRange: RethinkAppWiseLogTimeRange,
    onRangeSelected: (RethinkAppWiseLogTimeRange) -> Unit,
    onQueryChange: (String) -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    queryEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    appIcon: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingSm),
            shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(SharedDimensions.spacingLg), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(searchHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingMd)) {
            RethinkAppWiseTimeRangeRow(selectedRange, strings, onRangeSelected)
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            RethinkAppWiseSearchRow(searchHint, fallbackSearchHint, strings, onQueryChange, onDeleteClick, queryEnabled, appIcon)
        }
        content()
    }
}

@Composable
private fun RethinkAppWiseTimeRangeRow(selected: RethinkAppWiseLogTimeRange, strings: RethinkAppWiseLogsStrings, onSelected: (RethinkAppWiseLogTimeRange) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingSm), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
        listOf(RethinkAppWiseLogTimeRange.OneHour to strings.oneHour, RethinkAppWiseLogTimeRange.TwentyFourHours to strings.twentyFourHours, RethinkAppWiseLogTimeRange.SevenDays to strings.sevenDays).forEach { (range, label) ->
            RethinkFilterChip(label, selected = selected == range, onClick = { onSelected(range) }, modifier = Modifier.weight(1f), minHeight = 44.dp)
        }
    }
}

@Composable
private fun RethinkAppWiseSearchRow(
    searchHint: String, fallbackHint: String, strings: RethinkAppWiseLogsStrings, onQueryChange: (String) -> Unit,
    onDeleteClick: (() -> Unit)?, queryEnabled: Boolean, appIcon: (@Composable () -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { snapshotFlow { query }.debounce(500).distinctUntilChanged().collect(onQueryChange) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(SharedDimensions.spacingSm).size(SharedDimensions.iconSizeMd).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                if (appIcon == null) Icon(MaterialSymbols.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) else appIcon()
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, enabled = queryEnabled,
                placeholder = { Text(searchHint.ifEmpty { fallbackHint }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            AnimatedVisibility(query.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                IconButton(onClick = { query = "" }) { Icon(MaterialSymbols.Filled.Close, strings.clearSearch, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeSm)) }
            }
            onDeleteClick?.let { delete -> IconButton(onClick = delete) { Icon(MaterialSymbols.Filled.Delete, strings.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeMd)) } }
        }
    }
}

/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
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
    val moreActions: String = "More options",
)

@Composable
fun RethinkAppWiseLogsScaffold(
    title: String,
    onBackClick: (() -> Unit)? = null,
    strings: RethinkAppWiseLogsStrings? = null,
    /** Clearing this app's logs lives in the overflow, like every other log screen. */
    onDeleteClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title,
                onBackClick = onBackClick,
                scrollBehavior = scroll,
                actions = {
                    if (strings != null && onDeleteClick != null) {
                        RethinkOverflowMenu(
                            moreDescription = strings.moreActions,
                            items = listOf(RethinkOverflowItem(strings.delete, MaterialSymbols.Filled.DeleteOutline, onDeleteClick)),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
    searchHint: String,
    fallbackSearchHint: String,
    strings: RethinkAppWiseLogsStrings,
    selectedRange: RethinkAppWiseLogTimeRange,
    onRangeSelected: (RethinkAppWiseLogTimeRange) -> Unit,
    onQueryChange: (String) -> Unit,
    queryEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    appIcon: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // The large top bar already names the app and the search field carries the hint, so there is
    // no extra header card repeating either.
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingSm)) {
            RethinkAppWiseTimeRangeRow(selectedRange, strings, onRangeSelected)
            Spacer(Modifier.height(SharedDimensions.spacingSm))
            RethinkAppWiseSearchRow(searchHint, fallbackSearchHint, strings, onQueryChange, queryEnabled, appIcon)
        }
        content()
    }
}

@Composable
private fun RethinkAppWiseTimeRangeRow(selected: RethinkAppWiseLogTimeRange, strings: RethinkAppWiseLogsStrings, onSelected: (RethinkAppWiseLogTimeRange) -> Unit) {
    val options = listOf(
        RethinkAppWiseLogTimeRange.OneHour to strings.oneHour,
        RethinkAppWiseLogTimeRange.TwentyFourHours to strings.twentyFourHours,
        RethinkAppWiseLogTimeRange.SevenDays to strings.sevenDays,
    )
    RethinkConnectedChoiceGroup(
        options = options.map { (range, label) -> RethinkChoiceOption(range, label) },
        selected = selected,
        onSelect = onSelected,
    )
}

@Composable
private fun RethinkAppWiseSearchRow(
    searchHint: String, fallbackHint: String, strings: RethinkAppWiseLogsStrings, onQueryChange: (String) -> Unit,
    queryEnabled: Boolean, appIcon: (@Composable () -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { snapshotFlow { query }.debounce(500).distinctUntilChanged().collect(onQueryChange) }
    RethinkSearchField(
        query = query,
        onQueryChange = { query = it },
        placeholder = searchHint.ifEmpty { fallbackHint },
        modifier = Modifier.fillMaxWidth(),
        enabled = queryEnabled,
        clearQueryContentDescription = strings.clearSearch,
        leadingContent = {
            // The scoped app's icon stands in for the search glyph.
            RethinkIconWell(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                size = SharedDimensions.iconSizeMd,
                shape = CircleShape,
            ) {
                if (appIcon == null) Icon(MaterialSymbols.Filled.Search, null, modifier = Modifier.size(SharedDimensions.iconSizeSm)) else appIcon()
            }
        },
    )
}

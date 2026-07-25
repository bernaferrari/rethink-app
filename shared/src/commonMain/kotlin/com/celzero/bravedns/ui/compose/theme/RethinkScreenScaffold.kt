package com.celzero.bravedns.ui.compose.theme

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkLargeTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    titleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    titleStartPadding: Dp = 0.dp,
    titleLeading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backDescription: String = "Navigate back",
) = LargeTopAppBar(
    title = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = titleStartPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titleLeading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = titleTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    },
    navigationIcon = {
        if (onBackClick != null) {
            IconButton(onBackClick) {
                Icon(MaterialSymbols.AutoMirrored.Filled.ArrowBack, backDescription)
            }
        }
    },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    actions: @Composable RowScope.() -> Unit = {},
    backDescription: String = "Navigate back",
) = TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
    navigationIcon = { if (onBackClick != null) IconButton(onBackClick) { Icon(MaterialSymbols.AutoMirrored.Filled.ArrowBack, backDescription) } },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = scrolledContainerColor, navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant, titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkLazyColumnScreenScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    topBar: @Composable () -> Unit,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = SharedDimensions.screenPaddingHorizontal,
        end = SharedDimensions.screenPaddingHorizontal,
        top = SharedDimensions.spacingSm,
        bottom = SharedDimensions.spacing3xl,
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SharedDimensions.spacingLg),
    content: LazyListScope.() -> Unit,
) {
    val state = listState ?: rememberLazyListState()
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = topBar,
    ) { padding ->
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkTopBarLazyColumnScreen(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    topBarContainerColor: Color = MaterialTheme.colorScheme.surface,
    topBarScrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    topBarTitleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = SharedDimensions.screenPaddingHorizontal,
        end = SharedDimensions.screenPaddingHorizontal,
        top = SharedDimensions.spacingSm,
        bottom = SharedDimensions.spacing3xl,
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SharedDimensions.spacingLg),
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    RethinkLazyColumnScreenScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                containerColor = topBarContainerColor,
                scrolledContainerColor = topBarScrolledContainerColor,
                titleTextStyle = topBarTitleTextStyle,
                actions = topBarActions,
            )
        },
        listState = listState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

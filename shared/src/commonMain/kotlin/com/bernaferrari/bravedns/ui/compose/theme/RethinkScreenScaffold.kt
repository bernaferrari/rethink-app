package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp

/**
 * Localized label for every top-bar back arrow. Android provides it from resources at the theme
 * root, so shared screens never ship the English fallback to TalkBack.
 */
val LocalRethinkBackDescription = staticCompositionLocalOf { "Navigate back" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkLargeTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** Null keeps the bar's own expanded/collapsed type scale; only override for data titles. */
    titleTextStyle: TextStyle? = null,
    titleLeading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backDescription: String = LocalRethinkBackDescription.current,
) = LargeFlexibleTopAppBar(
    title = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titleLeading?.invoke()
            // Emphasized: the large title is the screen's headline, so it carries bold weight
            // and slightly tighter tracking at its expanded size.
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = titleTextStyle ?: LocalTextStyle.current.merge(EmphasizedTitle),
            )
        }
    },
    subtitle = if (!subtitle.isNullOrBlank()) {
        {
            Text(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Body, not label: a sentence under a display-size title reads as prose, and the
                // bar's subtitle slot already supplies the spacing below the title.
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        null
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
    backDescription: String = LocalRethinkBackDescription.current,
    /** Close (X) for contextual modes such as multi-select; back arrow otherwise. */
    navigationIcon: ImageVector = MaterialSymbols.AutoMirrored.Filled.ArrowBack,
) = TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
    navigationIcon = { if (onBackClick != null) IconButton(onBackClick) { Icon(navigationIcon, backDescription) } },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = scrolledContainerColor, navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant, titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
)

private val EmphasizedTitle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

object RethinkScreenDefaults {
    /** The one list-screen inset: screen gutters, [SharedDimensions.screenContentTop], 3xl bottom. */
    val contentPadding: PaddingValues = PaddingValues(
        start = SharedDimensions.screenPaddingHorizontal,
        end = SharedDimensions.screenPaddingHorizontal,
        top = SharedDimensions.screenContentTop,
        bottom = SharedDimensions.spacing3xl,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkLazyColumnScreenScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    topBar: @Composable () -> Unit,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = RethinkScreenDefaults.contentPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SharedDimensions.spacingLg),
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val state = listState ?: rememberLazyListState()
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
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
    containerColor: Color = MaterialTheme.colorScheme.surface,
    topBarContainerColor: Color = MaterialTheme.colorScheme.surface,
    topBarScrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    topBarTitleTextStyle: TextStyle? = null,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = RethinkScreenDefaults.contentPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SharedDimensions.spacingLg),
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarTitleLeading: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
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
                titleLeading = topBarTitleLeading,
                actions = topBarActions,
            )
        },
        listState = listState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content,
    )
}

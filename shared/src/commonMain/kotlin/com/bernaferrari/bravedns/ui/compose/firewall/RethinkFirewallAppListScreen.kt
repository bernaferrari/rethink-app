@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.apps.DiagonalWipeIcon
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.components.RethinkFastScrollerMark
import com.bernaferrari.bravedns.ui.compose.components.RethinkIndexedFastScroller
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkToolbarIconToggle
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedRow

/** Data the portable firewall app-list renderer needs from a host. */
data class RethinkFirewallApp(
    val id: String,
    val uid: Int,
    val appName: String,
    val packageName: String,
    val status: RethinkFirewallAppStatus,
    val wifiBlocked: Boolean,
    val mobileBlocked: Boolean,
    val dataUsage: String? = null,
    val proxyEnabled: Boolean = false,
    val hasInternetPermission: Boolean = true,
    val tombstoned: Boolean = false,
    val canToggleConnections: Boolean = true,
    val technicalIdentity: String? = null,
)

enum class RethinkFirewallAppStatus { Allowed, Blocked, Bypass, Excluded, Lockdown, Unknown }

/** The app shown beside the list in a list–detail layout; its row is drawn selected. */
val LocalSelectedFirewallAppUid = androidx.compose.runtime.compositionLocalOf<Int?> { null }

enum class RethinkFirewallFilter { All, Allowed, Blocked, Bypass, Excluded, Lockdown, BlockedWifi, BlockedMobile }

enum class RethinkAppSort { Name, Package, Uid }

data class RethinkAppListFilterState(val query: String, val selectedQuickFilter: RethinkFirewallFilter, val filters: RethinkFirewallFilters)

data class RethinkAppSorting(val order: RethinkAppSort = RethinkAppSort.Name, val onChange: (RethinkAppSort) -> Unit = {})

enum class RethinkFirewallTopLevelFilter { Installed, System, All }

enum class RethinkFirewallBulkAction { Wifi, Mobile, Bypass, BypassDns, Exclude, Lockdown }

data class RethinkFirewallFilters(
    val categories: Set<String> = emptySet(),
    val topLevel: RethinkFirewallTopLevelFilter = RethinkFirewallTopLevelFilter.Installed,
    val status: RethinkFirewallFilter = RethinkFirewallFilter.All,
    val query: String = "",
)

data class RethinkFirewallBulkDialogCopy(val title: String, val message: String)

data class RethinkFirewallAppListStrings(
    val title: String,
    val searchHint: @Composable (Int) -> String,
    val refresh: String,
    val filter: String,
    val rules: String,
    val clearSearch: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val view: String,
    val installed: String,
    val system: String,
    val all: String,
    val status: String,
    val categories: String,
    val clear: String,
    val noCategories: String,
    val apply: String,
    val cancel: String,
    val close: String,
    val enabled: String,
    val disabled: String,
    val proxy: String,
    val wifiActionLabel: @Composable (blocked: Boolean) -> String,
    val mobileActionLabel: @Composable (blocked: Boolean) -> String,
    val bulkDescription: String,
    val selectedApps: @Composable (Int) -> String,
    val actionLabel: @Composable (RethinkFirewallBulkAction) -> String,
    val actionDescription: @Composable (RethinkFirewallBulkAction) -> String,
    val bulkDialogCopy: (RethinkFirewallBulkAction, Boolean) -> RethinkFirewallBulkDialogCopy,
    val filterLabel: @Composable (RethinkFirewallFilter) -> String,
    val statusLabel: @Composable (RethinkFirewallAppStatus) -> String,
    /** Content description of the top-bar overflow button that holds the bulk-rules entry. */
    val moreOptions: String = "More options",
    val sortLabels: List<String> = emptyList(),
    val sortTitle: String = "Sort apps",
    val uidLabel: @Composable (Int) -> String = { "UID $it" },
)

/**
 * Canonical data-usage line: download first, single line ("↓ 8 MB · ↑ 1 MB"). Hosts format the
 * byte counts; this keeps the order and separators identical on every surface.
 */
fun rethinkUsageText(download: String, upload: String): String = "↓ $download · ↑ $upload"

/** Width reserved at the end of indexed lists for [RethinkIndexedFastScroller]. */
private val FastScrollerGutter = 36.dp
private const val FAST_SCROLLER_MIN_MARKS = 4

/**
 * Full target-neutral app firewall UI. Hosts own installed-app discovery, package icons, shared
 * UID confirmation, and persistence; this renderer owns every list, filter and bulk-action
 * surface so Android and the web demo do not drift visually.
 */
@Composable
fun RethinkFirewallAppListScreen(
    apps: List<RethinkFirewallApp>,
    filterState: RethinkAppListFilterState,
    activeBulkActions: Set<RethinkFirewallBulkAction>,
    strings: RethinkFirewallAppListStrings,
    isRefreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onFiltersChange: (RethinkFirewallFilters) -> Unit,
    onQuickFilterChange: (RethinkFirewallFilter) -> Unit,
    onBulkAction: (RethinkFirewallBulkAction) -> Unit,
    loadCategories: suspend (RethinkFirewallTopLevelFilter) -> List<String>,
    onAppClick: (RethinkFirewallApp) -> Unit,
    onWifiToggle: (RethinkFirewallApp) -> Unit,
    onMobileToggle: (RethinkFirewallApp) -> Unit,
    appIcon: @Composable (RethinkFirewallApp) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sorting: RethinkAppSorting = RethinkAppSorting(),
) {
    val query = filterState.query
    val selectedQuickFilter = filterState.selectedQuickFilter
    val filters = filterState.filters
    val sortOrder = sorting.order
    var showFilters by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var pendingBulkAction by remember { mutableStateOf<RethinkFirewallBulkAction?>(null) }
    val hasActiveFilters = selectedQuickFilter != RethinkFirewallFilter.All ||
        filters.topLevel != RethinkFirewallTopLevelFilter.Installed ||
        filters.categories.isNotEmpty()

    // Hosts may return packages in install/UID order. Keep the renderer deterministic so the
    // letter headers, fast scroller, and rows always describe the same alphabetical sequence.
    val orderedApps = remember(apps, sortOrder) { orderFirewallApps(apps, sortOrder) }
    val alphabetical = sortOrder == RethinkAppSort.Name
    val scrollerMarks = remember(orderedApps, alphabetical) {
        if (alphabetical) rethinkFirewallScrollerMarks(orderedApps) else emptyList()
    }
    // The search row and the list share one end inset so their right edges line up, whether or
    // not the fast scroller is shown.
    val endGutter = if (scrollerMarks.size >= FAST_SCROLLER_MIN_MARKS) FastScrollerGutter else 0.dp

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                actions = {
                    RethinkAppSortMenu(strings.sortTitle, strings.sortLabels, sorting)
                    RethinkFirewallTopBarActions(
                        isRefreshing = isRefreshing,
                        strings = strings,
                        onRefresh = onRefresh,
                        onOpenRules = { showRules = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RethinkFirewallAppControls(
                query = query,
                count = apps.size,
                hasActiveFilters = hasActiveFilters,
                endGutter = endGutter,
                strings = strings,
                onQueryChange = onQueryChange,
                onOpenFilters = { showFilters = true },
            )
            RethinkFirewallAppList(
                modifier = Modifier.weight(1f),
                apps = orderedApps,
                scrollerMarks = scrollerMarks,
                sortOrder = sortOrder,
                endGutter = endGutter,
                query = query,
                strings = strings,
                appIcon = appIcon,
                onAppClick = onAppClick,
                onWifiToggle = onWifiToggle,
                onMobileToggle = onMobileToggle,
            )
        }
    }

    if (showFilters) {
        RethinkFirewallFilterDialog(
            filters = filters,
            selectedQuickFilter = selectedQuickFilter,
            strings = strings,
            loadCategories = loadCategories,
            onDismiss = { showFilters = false },
            onFiltersChange = onFiltersChange,
            onQuickFilterChange = onQuickFilterChange,
        )
    }
    if (showRules) {
        RethinkFirewallRulesDialog(
            appsCount = apps.size,
            activeActions = activeBulkActions,
            strings = strings,
            onDismiss = { showRules = false },
            onAction = { action ->
                showRules = false
                pendingBulkAction = action
            },
        )
    }
    pendingBulkAction?.let { action ->
        val copy = strings.bulkDialogCopy(action, activeBulkActions.contains(action))
        RethinkConfirmDialog(
            onDismissRequest = { pendingBulkAction = null },
            title = copy.title,
            message = copy.message,
            confirmText = strings.apply,
            dismissText = strings.cancel,
            onConfirm = { pendingBulkAction = null; onBulkAction(action) },
            onDismiss = { pendingBulkAction = null },
        )
    }
}

/** Refresh and the bulk-rules sheet as two direct actions: an overflow holding one item hid it. */
@Composable
private fun RethinkFirewallTopBarActions(
    isRefreshing: Boolean,
    strings: RethinkFirewallAppListStrings,
    onRefresh: () -> Unit,
    onOpenRules: () -> Unit,
) {
    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
        if (isRefreshing) {
            LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
        } else {
            Icon(MaterialSymbols.Filled.Refresh, strings.refresh)
        }
    }
    IconButton(onClick = onOpenRules) {
        Icon(MaterialSymbols.Filled.Gavel, strings.rules)
    }
}

@Composable
private fun RethinkFirewallAppControls(
    query: String,
    count: Int,
    hasActiveFilters: Boolean,
    endGutter: Dp,
    strings: RethinkFirewallAppListStrings,
    onQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal + endGutter,
            top = SharedDimensions.spacingSm,
            bottom = SharedDimensions.spacingXs,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        RethinkSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = strings.searchHint(count),
            clearQueryContentDescription = strings.clearSearch,
            modifier = Modifier.weight(1f),
        )
        // The single filter entry point: status, view and categories all live in the sheet. Same
        // tonal toggle as the Logs toolbar, sized to the search field so the strip reads as one;
        // the badge tells the user a non-default filter is narrowing the list.
        RethinkToolbarIconToggle(
            icon = MaterialSymbols.Filled.FilterList,
            contentDescription = strings.filter,
            selected = hasActiveFilters,
            size = TextFieldDefaults.MinHeight,
            showSelectedDot = true,
            onClick = onOpenFilters,
        )
    }
}

@Composable
private fun RethinkFirewallAppList(
    apps: List<RethinkFirewallApp>,
    scrollerMarks: List<RethinkFastScrollerMark>,
    sortOrder: RethinkAppSort,
    endGutter: Dp,
    query: String,
    strings: RethinkFirewallAppListStrings,
    appIcon: @Composable (RethinkFirewallApp) -> Unit,
    onAppClick: (RethinkFirewallApp) -> Unit,
    onWifiToggle: (RethinkFirewallApp) -> Unit,
    onMobileToggle: (RethinkFirewallApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        RethinkEmptyState(
            icon = MaterialSymbols.Filled.Apps,
            title = strings.emptyTitle,
            message = strings.emptyDescription,
            modifier = modifier.fillMaxSize().padding(SharedDimensions.screenPaddingHorizontal),
        )
        return
    }

    val state = rememberLazyListState()
    LaunchedEffect(sortOrder) { state.scrollToItem(0) }
    Box(modifier.fillMaxSize()) {
        RethinkFirewallAppRows(
            apps = apps,
            state = state,
            sortOrder = sortOrder,
            endGutter = endGutter,
            query = query,
            strings = strings,
            appIcon = appIcon,
            onAppClick = onAppClick,
            onWifiToggle = onWifiToggle,
            onMobileToggle = onMobileToggle,
        )
        RethinkIndexedFastScroller(
            marks = scrollerMarks,
            listState = state,
            minMarkCount = FAST_SCROLLER_MIN_MARKS,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = SharedDimensions.spacingGridTile, bottom = SharedDimensions.spacingLg),
        )
    }
}

@Composable
private fun RethinkFirewallAppRows(
    apps: List<RethinkFirewallApp>,
    state: LazyListState,
    sortOrder: RethinkAppSort,
    endGutter: Dp,
    query: String,
    strings: RethinkFirewallAppListStrings,
    appIcon: @Composable (RethinkFirewallApp) -> Unit,
    onAppClick: (RethinkFirewallApp) -> Unit,
    onWifiToggle: (RethinkFirewallApp) -> Unit,
    onMobileToggle: (RethinkFirewallApp) -> Unit,
) {
    val alphabetical = sortOrder == RethinkAppSort.Name
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal + endGutter,
            top = SharedDimensions.spacingXs,
            bottom = SharedDimensions.spacing3xl,
        ),
    ) {
        apps.forEachIndexed { index, app ->
            val initial = if (alphabetical) appInitial(app) else ""
            val previousInitial = if (alphabetical) apps.getOrNull(index - 1)?.let(::appInitial) else if (index > 0) "" else null
            val nextInitial = if (alphabetical) apps.getOrNull(index + 1)?.let(::appInitial) else if (index < apps.lastIndex) "" else null
            if (alphabetical && (index == 0 || initial != previousInitial)) {
                // The same initial can legitimately appear in separate groups after filtering;
                // include the source position so LazyColumn never receives duplicate keys.
                stickyHeader(key = "firewall_header_${initial}_$index", contentType = "header") {
                    SectionHeader(
                        title = initial,
                        horizontalPadding = SharedDimensions.spacingLg,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
            // Rows group per letter: the first/last row of each letter gets the outer corners.
            val position = when {
                previousInitial != initial && nextInitial != initial -> CardPosition.Single
                previousInitial != initial -> CardPosition.First
                nextInitial != initial -> CardPosition.Last
                else -> CardPosition.Middle
            }
            item(key = app.id, contentType = "app") {
                RethinkFirewallAppRow(
                    app = app.copy(technicalIdentity = when (sortOrder) {
                        RethinkAppSort.Name -> null
                        RethinkAppSort.Package -> app.packageName
                        RethinkAppSort.Uid -> strings.uidLabel(app.uid)
                    }),
                    query = query,
                    statusLabel = strings.statusLabel(app.status),
                    position = position,
                    appIcon = { appIcon(app) },
                    onClick = { onAppClick(app) },
                    onWifiToggle = { onWifiToggle(app) },
                    onMobileToggle = { onMobileToggle(app) },
                    proxyLabel = strings.proxy,
                    wifiActionLabel = strings.wifiActionLabel(app.wifiBlocked),
                    mobileActionLabel = strings.mobileActionLabel(app.mobileBlocked),
                )
            }
        }
    }
}

@Composable
private fun RethinkFirewallAppRow(
    app: RethinkFirewallApp,
    query: String,
    statusLabel: String,
    position: CardPosition,
    appIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    onWifiToggle: () -> Unit,
    onMobileToggle: () -> Unit,
    proxyLabel: String,
    wifiActionLabel: String,
    mobileActionLabel: String,
) {
    val motion = LocalRethinkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reducedMotion) .98f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "firewall_app_row_scale",
    )
    val shape = rethinkGroupedListShape(position)
    val accent = statusColor(app.status, app.wifiBlocked || app.mobileBlocked)
    val usage = app.dataUsage?.takeIf { it.isNotBlank() }
    val hasSupportingLine = statusLabel.isNotBlank() || app.proxyEnabled || usage != null
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.scale(scale).rethinkGroupedRow(position),
        shape = shape,
        color = if (LocalSelectedFirewallAppUid.current == app.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Box(Modifier.size(SharedDimensions.iconContainerMd).clip(RoundedCornerShape(SharedDimensions.iconContainerRadius)), contentAlignment = Alignment.Center) { appIcon() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
                Text(
                    text = highlighted(
                        app.appName.ifBlank { app.packageName },
                        query,
                        MaterialTheme.colorScheme.primary,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (app.hasInternetPermission) 1f else .42f),
                    textDecoration = if (app.tombstoned) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.technicalIdentity?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // One non-wrapping supporting line: status/proxy badges first, then usage, which
                // is the only part allowed to ellipsize.
                if (hasSupportingLine) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                    ) {
                        if (statusLabel.isNotBlank()) {
                            RethinkPill(statusLabel, colors = RethinkPillDefaults.status(accent), size = RethinkPillSize.Small)
                        }
                        if (app.proxyEnabled) {
                            RethinkPill(proxyLabel, colors = RethinkPillDefaults.tertiary(), size = RethinkPillSize.Small, fontWeight = FontWeight.SemiBold)
                        }
                        usage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
            }
            if (app.canToggleConnections) {
                RethinkNetworkSegmentPair(
                    wifiBlocked = app.wifiBlocked,
                    mobileBlocked = app.mobileBlocked,
                    wifiDescription = wifiActionLabel,
                    mobileDescription = mobileActionLabel,
                    onWifiToggle = onWifiToggle,
                    onMobileToggle = onMobileToggle,
                )
            }
        }
    }
}

/**
 * Wi-Fi and mobile data as one connected button group: two segments sharing one pill
 * silhouette (fully round outer corners, tight inner ones, a 2dp gap). Allowed segments are
 * tonal; a blocked one fills with the error container and its inner corners round out, so the
 * state change is a colour and shape morph rather than a bubble popping in.
 */
@Composable
fun RethinkNetworkSegmentPair(
    wifiBlocked: Boolean,
    mobileBlocked: Boolean,
    wifiDescription: String,
    mobileDescription: String,
    onWifiToggle: () -> Unit,
    onMobileToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(NetworkSegmentGap)) {
        RethinkNetworkSegment(wifiBlocked, MaterialSymbols.Filled.Wifi, MaterialSymbols.Filled.WifiOff, wifiDescription, leading = true, onWifiToggle)
        RethinkNetworkSegment(mobileBlocked, MaterialSymbols.Outlined.MobiledataArrows, MaterialSymbols.Outlined.MobiledataOff, mobileDescription, leading = false, onMobileToggle)
    }
}

@Composable
private fun RethinkNetworkSegment(
    blocked: Boolean,
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    contentDescription: String,
    leading: Boolean,
    onClick: () -> Unit,
) {
    val motion = LocalRethinkMotion.current
    val scheme = MaterialTheme.colorScheme
    val inner by animateDpAsState(
        targetValue = if (blocked) NetworkSegmentInnerBlocked else NetworkSegmentInner,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "networkSegmentCorner",
    )
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (blocked) scheme.errorContainer else scheme.surfaceContainerHigh,
        animationSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "networkSegmentColor",
    )
    val outer = NetworkSegmentHeight / 2
    val shape = if (leading) {
        RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
    } else {
        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        modifier = Modifier.size(width = NetworkSegmentWidth, height = NetworkSegmentHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DiagonalWipeIcon(
                blocked = blocked,
                allowedIcon = allowedIcon,
                blockedIcon = blockedIcon,
                allowedTint = scheme.onSurfaceVariant,
                blockedTint = scheme.onErrorContainer,
                contentDescription = contentDescription,
                modifier = Modifier.size(SharedDimensions.iconSizeSm),
            )
        }
    }
}

private val NetworkSegmentWidth = 48.dp
private val NetworkSegmentHeight = 40.dp
private val NetworkSegmentGap = 2.dp
private val NetworkSegmentInner = 5.dp
private val NetworkSegmentInnerBlocked = 14.dp


@Composable
fun RethinkFirewallNetworkToggle(
    blocked: Boolean,
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val motion = LocalRethinkMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !motion.reducedMotion) 0.96f else 1f,
        animationSpec = if (motion.reducedMotion) {
            snap()
        } else {
            spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
        },
        label = "firewall_network_toggle_press",
    )
    // Hit box is 48.dp; visual bubble/icon stay 34 / 18.dp. Springs run only via
    // graphicsLayer so on/off never remeasure the row.
    val stateTransition = androidx.compose.animation.core.updateTransition(
        targetState = blocked,
        label = "firewall_network_toggle_state",
    )
    val unblockSpec: FiniteAnimationSpec<Float> = if (motion.reducedMotion) {
        snap()
    } else {
        keyframes {
            durationMillis = motion.durationFast + 100
            1f at 0
            1f at 64
            0.64f at 160
            0f at durationMillis
        }
    }
    val unblockScaleSpec: FiniteAnimationSpec<Float> = if (motion.reducedMotion) {
        snap()
    } else {
        keyframes {
            durationMillis = motion.durationFast + 100
            1f at 0
            1.1f at 64
            0.68f at 160
            0f at durationMillis
        }
    }
    val bubbleAlpha by stateTransition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                unblockSpec
            } else {
                if (motion.reducedMotion) {
                    snap()
                } else {
                    spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = 0.82f,
                    )
                }
            }
        },
        label = "firewall_network_toggle_bubble_alpha",
    ) { isBlocked -> if (isBlocked) 1f else 0f }
    val bubbleScale by stateTransition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                unblockScaleSpec
            } else {
                if (motion.reducedMotion) {
                    snap()
                } else {
                    spring(stiffness = 640f, dampingRatio = 0.76f)
                }
            }
        },
        label = "firewall_network_toggle_bubble_scale",
    ) { isBlocked -> if (isBlocked) 1f else 0f }
    // Spring-scaled for weight; layout box stays 18.dp so the row never remeasures.
    val iconScale by stateTransition.animateFloat(
        transitionSpec = {
            if (motion.reducedMotion) {
                snap()
            } else {
                spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                )
            }
        },
        label = "firewall_network_toggle_icon_scale",
    ) { isBlocked -> if (isBlocked) 1f else 0.9f }
    val allowedTint = if (blocked) {
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.44f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val blockedTint = if (blocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
    }
    val blockedBackground = MaterialTheme.colorScheme.errorContainer
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(SharedDimensions.touchTargetMin)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = CircleShape,
        color = Color.Transparent,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .clip(CircleShape)
                    .background(blockedBackground),
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            ) {
                DiagonalWipeIcon(
                    blocked = blocked,
                    allowedIcon = allowedIcon,
                    blockedIcon = blockedIcon,
                    allowedTint = allowedTint,
                    blockedTint = blockedTint,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RethinkFirewallFilterDialog(
    filters: RethinkFirewallFilters,
    selectedQuickFilter: RethinkFirewallFilter,
    strings: RethinkFirewallAppListStrings,
    loadCategories: suspend (RethinkFirewallTopLevelFilter) -> List<String>,
    onDismiss: () -> Unit,
    onFiltersChange: (RethinkFirewallFilters) -> Unit,
    onQuickFilterChange: (RethinkFirewallFilter) -> Unit,
) {
    var topLevel by remember(filters) { mutableStateOf(filters.topLevel) }
    var status by remember(selectedQuickFilter) { mutableStateOf(selectedQuickFilter) }
    val categories = remember { mutableStateListOf<String>() }
    val selectedCategories = remember(filters) { mutableStateListOf<String>().apply { addAll(filters.categories) } }
    androidx.compose.runtime.LaunchedEffect(topLevel) {
        categories.clear()
        categories.addAll(loadCategories(topLevel))
        selectedCategories.retainAll(categories.toSet())
    }
    fun publish(top: RethinkFirewallTopLevelFilter = topLevel, selectedStatus: RethinkFirewallFilter = status) {
        onQuickFilterChange(selectedStatus)
        onFiltersChange(filters.copy(topLevel = top, status = selectedStatus, categories = selectedCategories.toSet()))
    }
    val defaults = topLevel == RethinkFirewallTopLevelFilter.Installed && status == RethinkFirewallFilter.All && selectedCategories.isEmpty()
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(SharedDimensions.spacingLg),
        verticalSpacing = SharedDimensions.spacingLg,
        includeBottomSpacer = false,
        expandOnShow = true,
    ) { dismissSheet ->
        Column(
            Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
                RethinkSheetHeader(
                    title = strings.filter,
                    trailing = {
                        TextButton(
                            onClick = {
                                topLevel = RethinkFirewallTopLevelFilter.Installed
                                status = RethinkFirewallFilter.All
                                selectedCategories.clear()
                                publish()
                            },
                            enabled = !defaults,
                        ) { Text(strings.clear) }
                    },
                )
                RethinkFirewallFilterSection(strings.view) {
                    RethinkFirewallFilterOptions(
                        options = listOf(RethinkFirewallTopLevelFilter.Installed, RethinkFirewallTopLevelFilter.System, RethinkFirewallTopLevelFilter.All),
                        selected = topLevel,
                        label = { option -> when (option) {
                            RethinkFirewallTopLevelFilter.Installed -> strings.installed
                            RethinkFirewallTopLevelFilter.System -> strings.system
                            RethinkFirewallTopLevelFilter.All -> strings.all
                        } },
                        onSelect = { option -> topLevel = option; selectedCategories.clear(); publish(top = option) },
                    )
                }
                RethinkFirewallFilterSection(strings.status) {
                    RethinkFirewallFilterOptions(
                        options = RethinkFirewallFilter.entries,
                        selected = status,
                        label = strings.filterLabel,
                        icon = { option -> option.icon() },
                        onSelect = { option -> status = option; publish(selectedStatus = option) },
                    )
                }
                RethinkFirewallFilterSection("${strings.categories} · ${selectedCategories.size}") {
                    if (categories.isEmpty()) {
                        Text(strings.noCategories, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                        ) {
                            categories.forEach { category ->
                                RethinkFilterChip(
                                    label = category,
                                    selected = category in selectedCategories,
                                    onClick = {
                                        if (category in selectedCategories) selectedCategories.remove(category) else selectedCategories.add(category)
                                        publish()
                                    },
                                    minHeight = SharedDimensions.touchTargetMin,
                                )
                            }
                        }
                    }
                }
        }
        RethinkFormActionRow(
            confirmLabel = strings.close,
            onConfirm = dismissSheet,
        )
    }
}

@Composable
private fun RethinkFirewallFilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun <T> RethinkFirewallFilterOptions(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    icon: ((T) -> ImageVector?)? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        options.forEach { option ->
            RethinkFilterChip(
                label = label(option),
                selected = option == selected,
                onClick = { if (option != selected) onSelect(option) },
                leadingIcon = icon?.invoke(option)?.let { vector -> @Composable { Icon(vector, null, Modifier.size(FilterChipDefaults.IconSize)) } },
                minHeight = SharedDimensions.touchTargetMin,
            )
        }
    }
}

@Composable
private fun RethinkFirewallRulesDialog(
    appsCount: Int,
    activeActions: Set<RethinkFirewallBulkAction>,
    strings: RethinkFirewallAppListStrings,
    onDismiss: () -> Unit,
    onAction: (RethinkFirewallBulkAction) -> Unit,
) {
    RethinkModalBottomSheet(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(SharedDimensions.spacingLg),
        verticalSpacing = SharedDimensions.spacingMd,
        includeBottomSpacer = false,
        expandOnShow = true,
    ) { dismissSheet ->
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = SharedDimensions.sheetContentMaxHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            RethinkSheetHeader(strings.rules)
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                Text(strings.selectedApps(appsCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(strings.bulkDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                RethinkListGroup {
                    RethinkFirewallBulkAction.entries.forEachIndexed { index, action ->
                        val active = action in activeActions
                        RethinkListItem(
                            headline = strings.actionLabel(action),
                            supporting = strings.actionDescription(action),
                            leadingContent = { RethinkFirewallBulkIcon(action) },
                            position = cardPositionFor(index, RethinkFirewallBulkAction.entries.lastIndex),
                            highlighted = active,
                            onClick = { onAction(action) },
                            trailing = {
                                Text(
                                    if (active) strings.enabled else strings.disabled,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
        RethinkFormActionRow(
            confirmLabel = strings.close,
            onConfirm = dismissSheet,
        )
    }
}

@Composable
private fun RethinkFirewallBulkIcon(action: RethinkFirewallBulkAction) {
    val icon = when (action) {
        RethinkFirewallBulkAction.Wifi -> MaterialSymbols.Filled.WifiOff
        RethinkFirewallBulkAction.Mobile -> MaterialSymbols.Filled.MobileOff
        RethinkFirewallBulkAction.Bypass, RethinkFirewallBulkAction.BypassDns -> MaterialSymbols.Filled.VpnKey
        RethinkFirewallBulkAction.Exclude -> MaterialSymbols.Filled.Security
        RethinkFirewallBulkAction.Lockdown -> MaterialSymbols.Filled.Block
    }
    RethinkIconWell(
        icon = icon,
        tint = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        size = SharedDimensions.iconContainerSm,
        iconSize = SharedDimensions.iconSizeXs,
        shape = CircleShape,
    )
}

/** Leading chip icon per filter; "All" is the neutral reset and carries no icon. */
private fun RethinkFirewallFilter.icon(): ImageVector? = when (this) {
    RethinkFirewallFilter.All -> null
    RethinkFirewallFilter.Allowed -> MaterialSymbols.Filled.CheckCircle
    RethinkFirewallFilter.Blocked -> MaterialSymbols.Filled.Block
    RethinkFirewallFilter.Bypass -> MaterialSymbols.Filled.VpnKey
    RethinkFirewallFilter.Excluded -> MaterialSymbols.Filled.Security
    RethinkFirewallFilter.Lockdown -> MaterialSymbols.Filled.Lock
    RethinkFirewallFilter.BlockedWifi -> MaterialSymbols.Filled.WifiOff
    RethinkFirewallFilter.BlockedMobile -> MaterialSymbols.Filled.MobileOff
}

private fun appInitial(app: RethinkFirewallApp): String {
    val source = app.appName.ifBlank { app.packageName }.trim()
    return source.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
}

fun rethinkFirewallScrollerMarks(apps: List<RethinkFirewallApp>): List<RethinkFastScrollerMark> {
    val marks = mutableListOf<RethinkFastScrollerMark>()
    var listIndex = 0
    var previous: String? = null
    apps.forEach { app ->
        val initial = appInitial(app)
        if (previous != initial) {
            marks += RethinkFastScrollerMark(initial, listIndex)
            listIndex++
            previous = initial
        }
        listIndex++
    }
    return marks
}

private fun highlighted(value: String, query: String, color: Color): AnnotatedString {
    val indices = findSubsequenceMatchIndices(value, query) ?: return AnnotatedString(value)
    if (indices.isEmpty()) return AnnotatedString(value)
    return buildAnnotatedString {
        value.forEachIndexed { index, char ->
            if (index in indices) {
                pushStyle(SpanStyle(color = color))
                append(char)
                pop()
            } else {
                append(char)
            }
        }
    }
}

private fun findSubsequenceMatchIndices(text: String, query: String): Set<Int>? {
    val needle = query.filterNot(Char::isWhitespace)
    if (needle.isEmpty()) return emptySet()
    val matched = mutableSetOf<Int>()
    var queryIndex = 0
    for (index in text.indices) {
        if (queryIndex >= needle.length) break
        if (text[index].equals(needle[queryIndex], ignoreCase = true)) {
            matched += index
            queryIndex++
        }
    }
    return if (queryIndex == needle.length) matched else null
}

@Composable
private fun statusColor(status: RethinkFirewallAppStatus, hasBlockedNetwork: Boolean): Color = when (status) {
    RethinkFirewallAppStatus.Allowed -> if (hasBlockedNetwork) RethinkStatusColors.mixed else RethinkStatusColors.allowed
    RethinkFirewallAppStatus.Blocked, RethinkFirewallAppStatus.Lockdown -> RethinkStatusColors.blocked
    RethinkFirewallAppStatus.Bypass, RethinkFirewallAppStatus.Excluded -> RethinkStatusColors.mixed
    RethinkFirewallAppStatus.Unknown -> RethinkStatusColors.idle
}

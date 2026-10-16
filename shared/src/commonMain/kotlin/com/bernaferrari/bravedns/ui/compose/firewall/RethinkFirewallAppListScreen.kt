@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.components.RethinkFastScrollerMark
import com.bernaferrari.bravedns.ui.compose.components.RethinkIndexedFastScroller
import com.bernaferrari.bravedns.ui.compose.apps.DiagonalWipeIcon
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSearchField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

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
)

enum class RethinkFirewallAppStatus { Allowed, Blocked, Bypass, Excluded, Lockdown, Unknown }

enum class RethinkFirewallFilter { All, Allowed, Blocked, Bypass, Excluded, Lockdown, BlockedWifi, BlockedMobile }

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
)

/**
 * Full target-neutral app firewall UI. Hosts own installed-app discovery, package icons, shared
 * UID confirmation, and persistence; this renderer owns every list, filter and bulk-action
 * surface so Android and the web demo do not drift visually.
 */
@Composable
fun RethinkFirewallAppListScreen(
    apps: List<RethinkFirewallApp>,
    query: String,
    selectedQuickFilter: RethinkFirewallFilter,
    filters: RethinkFirewallFilters,
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
) {
    var showFilters by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var pendingBulkAction by remember { mutableStateOf<RethinkFirewallBulkAction?>(null) }
    val hasAdvancedFilters = filters.topLevel != RethinkFirewallTopLevelFilter.Installed ||
        filters.categories.isNotEmpty()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkTopBar(
                title = strings.title,
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            LoadingIndicator(Modifier.size(SharedDimensions.iconSizeMd))
                        } else {
                            Icon(MaterialSymbols.Filled.Refresh, strings.refresh)
                        }
                    }
                    IconButton(onClick = { showRules = true }) { Icon(MaterialSymbols.Filled.Gavel, strings.rules) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RethinkFirewallAppControls(
                query = query,
                count = apps.size,
                selected = selectedQuickFilter,
                hasAdvancedFilters = hasAdvancedFilters,
                strings = strings,
                onQueryChange = onQueryChange,
                onQuickFilterChange = onQuickFilterChange,
                onOpenAdvancedFilters = { showFilters = true },
            )
            RethinkFirewallAppList(
                modifier = Modifier.weight(1f),
                apps = apps,
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

@Composable
private fun RethinkFirewallAppControls(
    query: String,
    count: Int,
    selected: RethinkFirewallFilter,
    hasAdvancedFilters: Boolean,
    strings: RethinkFirewallAppListStrings,
    onQueryChange: (String) -> Unit,
    onQuickFilterChange: (RethinkFirewallFilter) -> Unit,
    onOpenAdvancedFilters: () -> Unit,
) {
    val quickFilters = listOf(
        RethinkFirewallFilter.All,
        RethinkFirewallFilter.Allowed,
        RethinkFirewallFilter.Blocked,
        RethinkFirewallFilter.Bypass,
        RethinkFirewallFilter.Excluded,
        RethinkFirewallFilter.Lockdown,
    )
    val hasQuickFilter = selected != RethinkFirewallFilter.All
    var filtersExpanded by remember { mutableStateOf(hasQuickFilter || hasAdvancedFilters) }
    val motion = LocalRethinkMotion.current
    val expandSpec = tween<IntSize>(
        durationMillis = motion.durationMedium,
        easing = motion.easingStandard,
    )
    val fadeSpec = tween<Float>(
        durationMillis = motion.durationMedium,
        easing = motion.easingStandard,
    )
    val collapseSpec = tween<IntSize>(
        durationMillis = motion.durationExit,
        easing = motion.easingStandard,
    )
    val fadeOutSpec = tween<Float>(
        durationMillis = motion.durationExit,
        easing = motion.easingStandard,
    )
    Column(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingSm,
            bottom = SharedDimensions.spacingXs,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            RethinkSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = strings.searchHint(count),
                clearQueryContentDescription = strings.clearSearch,
                onClearQuery = { onQueryChange("") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
            Surface(
                onClick = { filtersExpanded = !filtersExpanded },
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
                color = if (filtersExpanded || hasQuickFilter || hasAdvancedFilters) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        MaterialSymbols.Filled.FilterList,
                        strings.filter,
                        tint = if (filtersExpanded || hasQuickFilter || hasAdvancedFilters) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (hasQuickFilter || hasAdvancedFilters) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = filtersExpanded,
            enter = fadeIn(fadeSpec) + expandVertically(
                animationSpec = expandSpec,
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(fadeOutSpec) + shrinkVertically(
                animationSpec = collapseSpec,
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SharedDimensions.spacingSm)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quickFilters.forEach { filter ->
                    RethinkFilterChip(
                        label = strings.filterLabel(filter),
                        selected = selected == filter,
                        onClick = {
                            onQuickFilterChange(if (selected == filter) RethinkFirewallFilter.All else filter)
                        },
                        leadingIcon = filter.icon()?.let { icon -> @Composable { Icon(icon, null, Modifier.size(14.dp)) } },
                        minHeight = SharedDimensions.touchTargetMin,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                RethinkFilterChip(
                    label = strings.filter,
                    selected = hasAdvancedFilters,
                    onClick = onOpenAdvancedFilters,
                    leadingIcon = { Icon(MaterialSymbols.Filled.Tune, null, Modifier.size(14.dp)) },
                    minHeight = SharedDimensions.touchTargetMin,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RethinkFirewallAppList(
    apps: List<RethinkFirewallApp>,
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

    // Hosts may return packages in install/UID order. Keep the renderer deterministic so the
    // letter headers, fast scroller, and rows always describe the same alphabetical sequence.
    val orderedApps = remember(apps) {
        apps.sortedWith(
            compareBy(
                { it.appName.ifBlank { it.packageName }.trim().lowercase() },
                { it.packageName.lowercase() },
            ),
        )
    }
    val state = rememberLazyListState()
    val scrollerMarks = remember(orderedApps) { rethinkFirewallScrollerMarks(orderedApps) }
    Box(modifier.fillMaxSize()) {
        RethinkFirewallAppRows(
            apps = orderedApps,
            state = state,
            query = query,
            strings = strings,
            appIcon = appIcon,
            onAppClick = onAppClick,
            onWifiToggle = onWifiToggle,
            onMobileToggle = onMobileToggle,
            proxyLabel = strings.proxy,
            wifiActionLabel = strings.wifiActionLabel,
            mobileActionLabel = strings.mobileActionLabel,
        )
        RethinkIndexedFastScroller(
            marks = scrollerMarks,
            listState = state,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp, bottom = SharedDimensions.spacingLg),
        )
    }
}

@Composable
private fun RethinkFirewallAppRows(
    apps: List<RethinkFirewallApp>,
    state: LazyListState,
    query: String,
    strings: RethinkFirewallAppListStrings,
    appIcon: @Composable (RethinkFirewallApp) -> Unit,
    onAppClick: (RethinkFirewallApp) -> Unit,
    onWifiToggle: (RethinkFirewallApp) -> Unit,
    onMobileToggle: (RethinkFirewallApp) -> Unit,
    proxyLabel: String,
    wifiActionLabel: @Composable (Boolean) -> String,
    mobileActionLabel: @Composable (Boolean) -> String,
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal + 36.dp,
            top = SharedDimensions.spacingXs,
            bottom = SharedDimensions.spacingXl,
        ),
    ) {
        apps.forEachIndexed { index, app ->
            val initial = appInitial(app)
            val previousInitial = apps.getOrNull(index - 1)?.let(::appInitial)
            val nextInitial = apps.getOrNull(index + 1)?.let(::appInitial)
            if (index == 0 || initial != previousInitial) {
                // The same initial can legitimately appear in separate groups after filtering;
                // include the source position so LazyColumn never receives duplicate keys.
                stickyHeader(key = "firewall_header_${initial}_$index") { RethinkFirewallLetterHeader(initial) }
            }
            val position = when {
                previousInitial != initial && nextInitial != initial -> CardPosition.Single
                previousInitial != initial -> CardPosition.First
                nextInitial != initial -> CardPosition.Last
                else -> CardPosition.Middle
            }
            item(key = app.id) {
                RethinkFirewallAppRow(
                    app = app,
                    query = query,
                    statusLabel = strings.statusLabel(app.status),
                    position = position,
                    appIcon = { appIcon(app) },
                    onClick = { onAppClick(app) },
                    onWifiToggle = { onWifiToggle(app) },
                    onMobileToggle = { onMobileToggle(app) },
                    proxyLabel = proxyLabel,
                    wifiActionLabel = wifiActionLabel(app.wifiBlocked),
                    mobileActionLabel = mobileActionLabel(app.mobileBlocked),
                )
            }
        }
    }
}

@Composable
private fun RethinkFirewallLetterHeader(letter: String) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(start = SharedDimensions.spacingLg, top = SharedDimensions.spacingLg, bottom = SharedDimensions.spacingXs),
    ) {
        Text(letter, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
        animationSpec = if (motion.reducedMotion) snap() else spring(dampingRatio = .72f),
        label = "firewall_app_row_scale",
    )
    val shape = rethinkGroupedListShape(position)
    val accent = statusColor(app.status, app.wifiBlocked || app.mobileBlocked)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().padding(top = if (position == CardPosition.First || position == CardPosition.Single) 0.dp else 2.dp).scale(scale).clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Box(Modifier.size(SharedDimensions.iconContainerMd).clip(RoundedCornerShape(SharedDimensions.iconContainerRadius)), contentAlignment = Alignment.Center) { appIcon() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
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
                        modifier = Modifier.weight(1f),
                    )
                    if (statusLabel.isNotBlank()) RethinkFirewallStatusBadge(statusLabel, accent)
                }
                if (app.proxyEnabled) {
                    Surface(shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .76f)) {
                        Text(proxyLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                app.dataUsage?.takeIf { it.isNotBlank() }?.let { usage ->
                    Text(usage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f))
                }
            }
            if (app.canToggleConnections) {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    RethinkFirewallNetworkToggle(
                        blocked = app.wifiBlocked,
                        allowedIcon = MaterialSymbols.Filled.Wifi,
                        blockedIcon = MaterialSymbols.Filled.WifiOff,
                        contentDescription = wifiActionLabel,
                        onClick = onWifiToggle,
                    )
                    RethinkFirewallNetworkToggle(
                        blocked = app.mobileBlocked,
                        allowedIcon = MaterialSymbols.Outlined.MobiledataArrows,
                        blockedIcon = MaterialSymbols.Outlined.MobiledataOff,
                        contentDescription = mobileActionLabel,
                        onClick = onMobileToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun RethinkFirewallStatusBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius), color = color.copy(alpha = .12f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

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
    // Hit box is 48.dp; visual bubble/icon stay 30 / 18.dp. Springs run only via
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
    val blockedBackground = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
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
                    .size(30.dp)
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
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.filter, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = {
                            topLevel = RethinkFirewallTopLevelFilter.Installed
                            status = RethinkFirewallFilter.All
                            selectedCategories.clear()
                            publish()
                        },
                        enabled = !defaults,
                    ) { Text(strings.clear) }
                }
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
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                leadingIcon = icon?.invoke(option)?.let { vector -> @Composable { Icon(vector, null, Modifier.size(14.dp)) } },
                minHeight = SharedDimensions.touchTargetMin,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Text(strings.rules, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(17.dp)) }
    }
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

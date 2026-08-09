/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class RethinkFirewallSettingsStrings(
    val title: String,
    val subtitle: String,
    val universalSection: String,
    val universalTitle: String,
    val universalDescription: String,
    val blockedTitle: String,
    val blockedDescription: String,
    val appWiseSection: String,
    val appWiseTitle: String,
    val appWiseDescription: String,
)

/** Shared Firewall Settings navigation and focus/highlight behavior. */
@Composable
fun RethinkFirewallSettingsScreen(
    strings: RethinkFirewallSettingsStrings,
    onUniversalFirewallClick: () -> Unit,
    onCustomIpDomainClick: () -> Unit,
    onAppWiseIpDomainClick: () -> Unit,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val initialFocus = initialFocusKey?.trim().orEmpty()
    var pendingFocus by remember(initialFocus) { mutableStateOf(initialFocus) }
    var activeFocus by remember(initialFocus) { mutableStateOf(initialFocus.ifBlank { null }) }

    LaunchedEffect(pendingFocus) {
        val key = pendingFocus.trim()
        if (key.isBlank()) return@LaunchedEffect
        activeFocus = key
        val target = when (key) {
            "firewall_universal", "firewall_universal_main" -> 0 to 0
            "firewall_universal_blocked" -> 0 to 108
            "firewall_apps", "firewall_apps_rules" -> 1 to 0
            else -> null
        }
        target?.let { (index, offsetDp) ->
            val offset = with(density) { offsetDp.dp.toPx().roundToInt() }
            if (motion.reducedMotion) {
                listState.scrollToItem(index, offset)
            } else {
                listState.animateScrollToItem(index, offset)
            }
            delay(900)
            if (activeFocus == key) activeFocus = null
        }
        pendingFocus = ""
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = strings.subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            item {
                FirewallSectionTitle(strings.universalSection)
                FirewallNavigationRow(
                    title = strings.universalTitle,
                    description = strings.universalDescription,
                    icon = MaterialSymbols.Filled.Public,
                    position = CardPosition.First,
                    highlighted = activeFocus == "firewall_universal_main",
                    onClick = onUniversalFirewallClick,
                )
                FirewallNavigationRow(
                    title = strings.blockedTitle,
                    description = strings.blockedDescription,
                    icon = MaterialSymbols.Filled.GppBad,
                    position = CardPosition.Last,
                    highlighted = activeFocus == "firewall_universal_blocked",
                    onClick = onCustomIpDomainClick,
                )
            }
            item {
                FirewallSectionTitle(strings.appWiseSection)
                FirewallNavigationRow(
                    title = strings.appWiseTitle,
                    description = strings.appWiseDescription,
                    icon = MaterialSymbols.Filled.Apps,
                    position = CardPosition.Single,
                    highlighted = activeFocus == "firewall_apps_rules",
                    onClick = onAppWiseIpDomainClick,
                )
            }
        }
    }
}

@Composable
private fun FirewallSectionTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = SharedDimensions.spacingLg, bottom = SharedDimensions.spacingSm),
    )
}

@Composable
private fun FirewallNavigationRow(
    title: String,
    description: String,
    icon: ImageVector,
    position: CardPosition,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        position = position,
        highlighted = highlighted,
        onClick = onClick,
        leadingContent = {
            RethinkSharedIconContainer(MaterialTheme.colorScheme.primary) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        trailing = { Icon(MaterialSymbols.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

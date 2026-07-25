/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class RethinkNavigationItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    val contentDescription: String = label,
)

/**
 * Shared navigation chrome that follows Material 3's adaptive guidance: a bottom bar on compact
 * windows and a navigation rail when there is enough horizontal room. The navigation component
 * owns its space, so [content] receives no artificial inset on either form factor.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun RethinkAdaptiveNavigationScaffold(
    destinations: List<RethinkNavigationItem>,
    selectedId: String?,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    // Compose for web can briefly measure the root with an unbounded, zero-sized constraint while
    // establishing the viewport. NavigationSuiteScaffold requires a real bounded viewport, so
    // wait for the following measure pass instead of attempting an invalid intermediate layout.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (
            constraints.hasBoundedWidth &&
                constraints.hasBoundedHeight &&
                constraints.maxWidth > 0 &&
                constraints.maxHeight > 0
        ) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    destinations.forEach { destination ->
                        val selected = destination.id == selectedId
                        item(
                            selected = selected,
                            onClick = { onDestinationSelected(destination.id) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.contentDescription,
                                )
                            },
                            label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                    ) {
                        content(PaddingValues())
                    }
                }
            }
        }
    }
}

@Composable
fun RethinkBottomNavigation(
    destinations: List<RethinkNavigationItem>,
    selectedId: String?,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    NavigationBar(modifier = modifier, windowInsets = windowInsets) {
        destinations.forEach { destination ->
            val selected = destination.id == selectedId
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination.id) },
                icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, destination.contentDescription) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = rethinkNavigationItemColors(),
            )
        }
    }
}

@Composable
fun RethinkSideNavigation(
    destinations: List<RethinkNavigationItem>,
    selectedId: String?,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
) {
    NavigationRail(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer, windowInsets = windowInsets) {
        destinations.forEach { destination ->
            val selected = destination.id == selectedId
            NavigationRailItem(
                selected = selected,
                onClick = { onDestinationSelected(destination.id) },
                icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, destination.contentDescription) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun rethinkNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

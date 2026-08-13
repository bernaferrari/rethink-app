/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.NavDisplay
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkMotion

/**
 * QuietGuard-shaped navigation chrome shared by every host: adaptive suite (bar/rail) with a
 * bounded-viewport gate for Compose for Web (CMP-8543). Hosts own the Nav3 back stack and
 * [androidx.navigation3.ui.NavDisplay] content.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun RethinkNavShell(
    destinations: List<RethinkNavigationItem>,
    selectedId: String?,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    background: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (!showNavigation) {
        Box(modifier = modifier.fillMaxSize()) {
            background()
            content()
        }
        return
    }
    BoundedNavigationSuiteScaffold(
        modifier = modifier,
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
                    label = {
                        Text(destination.label, style = MaterialTheme.typography.labelMedium)
                    },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            background()
            content()
        }
    }
}

@Composable
fun RethinkCenteredScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
fun rememberRethinkDetailTransitionMetadata(motion: RethinkMotion = LocalRethinkMotion.current) =
    remember(motion) {
        NavDisplay.transitionSpec {
            ContentTransform(
                targetContentEnter = slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = if (motion.reducedMotion) 0 else motion.durationMedium,
                        easing = motion.easingDecelerate,
                    ),
                    initialOffsetX = { fullWidth -> fullWidth },
                ),
                initialContentExit = ExitTransition.None,
            )
        } +
            NavDisplay.popTransitionSpec {
                ContentTransform(
                    targetContentEnter = EnterTransition.None,
                    initialContentExit = slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = if (motion.reducedMotion) 0 else motion.durationExit,
                            easing = motion.easingAccelerate,
                        ),
                        targetOffsetX = { fullWidth -> fullWidth },
                    ),
                )
            }
    }

/**
 * NavigationSuiteScaffold assumes both axes are bounded. Web layout can briefly perform an
 * unbounded zero-width probe (CMP-8543); ignore that transient measure.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun BoundedNavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (
            constraints.hasBoundedWidth &&
                constraints.hasBoundedHeight &&
                constraints.maxWidth > 0 &&
                constraints.maxHeight > 0
        ) {
            NavigationSuiteScaffold(
                navigationSuiteItems = navigationSuiteItems,
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                content = content,
            )
        }
    }
}

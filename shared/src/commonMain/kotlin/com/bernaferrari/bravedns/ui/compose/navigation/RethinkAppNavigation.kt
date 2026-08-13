/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion

/**
 * Shared navigation host for Android and wasm.
 *
 * Hosts differ only in [entryBuilder] (real screens vs preview fixtures) and optional chrome
 * labels — not in stack policy, suite layout, or route types.
 *
 * Forward/back animations are set globally on [NavDisplay] (Nav3 defaults)
 * without per-entry metadata.
 */
@Composable
fun RethinkAppNavigation(
    destinations: List<RethinkNavigationItem>,
    startRoute: RethinkRoute = RethinkRoute.Home,
    pendingRoute: RethinkRoute? = null,
    onRouteNavigated: () -> Unit = {},
    modifier: Modifier = Modifier,
    background: @Composable (current: RethinkRoute?) -> Unit = {},
    entryBuilder: EntryProviderScope<NavKey>.(RethinkNavOps) -> Unit,
) {
    val startStack = remember(startRoute) { canonicalStackFor(startRoute).toTypedArray() }
    val backStack = rememberNavBackStack(rethinkNavSavedStateConfiguration, *startStack)
    val motion = LocalRethinkMotion.current

    fun popBackStack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun setStack(vararg keys: RethinkRoute) {
        backStack.clear()
        backStack.addAll(keys.toList())
    }

    fun push(destination: RethinkRoute) {
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun open(destination: RethinkRoute) {
        setStack(*canonicalStackFor(destination).toTypedArray())
    }

    fun selectRoot(destination: RethinkRootDestination) {
        setStack(destination.route)
    }

    val currentKey = backStack.lastOrNull() as? RethinkRoute
    val selectedRoot = currentKey?.rootDestination()
    val showNavigation = currentKey?.showsNavigationChrome() != false

    // Fresh closures each composition so entry bodies always see the latest stack.
    val liveOps = RethinkNavOps(
        pop = { popBackStack() },
        pushRoute = { push(it) },
        openRoute = { open(it) },
        selectRootTab = { selectRoot(it) },
        current = { backStack.lastOrNull() as? RethinkRoute },
    )

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        open(route)
        onRouteNavigated()
    }

    // Push/pop motion applied as NavDisplay defaults
    // (https://developer.android.com/guide/navigation/navigation-3/animate-destinations).
    val forwardTransition: ContentTransform = remember(motion) {
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
    }
    val popTransition: ContentTransform = remember(motion) {
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

    RethinkNavShell(
        destinations = destinations,
        selectedId = selectedRoot?.name,
        onDestinationSelected = { id ->
            RethinkRootDestination.entries.firstOrNull { it.name == id }?.let(::selectRoot)
        },
        showNavigation = showNavigation,
        modifier = modifier,
        background = { background(currentKey) },
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { forwardTransition },
            popTransitionSpec = { popTransition },
            predictivePopTransitionSpec = { popTransition },
            entryProvider = entryProvider {
                entryBuilder(liveOps)
            },
        )
    }
}

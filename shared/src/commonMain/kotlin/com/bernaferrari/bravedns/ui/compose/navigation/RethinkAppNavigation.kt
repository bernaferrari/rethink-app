/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // Tab bar switches replace the stack; they must not inherit the detail slide.
    var tabTransition by remember { mutableStateOf(false) }

    fun currentRoute(): RethinkRoute? = backStack.lastOrNull() as? RethinkRoute

    fun popBackStack() {
        tabTransition = false
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun setStack(vararg keys: RethinkRoute) {
        backStack.clear()
        backStack.addAll(keys.toList())
    }

    fun push(destination: RethinkRoute) {
        tabTransition = false
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun open(destination: RethinkRoute) {
        val next = canonicalStackFor(destination)
        tabTransition = next.size == 1 &&
            currentRoute()?.rootDestination() != destination.rootDestination()
        setStack(*next.toTypedArray())
    }

    fun selectRoot(destination: RethinkRootDestination) {
        val current = currentRoute()
        if (current == destination.route) return
        tabTransition = current?.rootDestination() != destination
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
    // Root tabs cross-fade; details keep the horizontal slide.
    val fadeMillis = if (motion.reducedMotion) 0 else motion.durationFast
    val fadeTransition: ContentTransform = remember(motion) {
        ContentTransform(
            targetContentEnter = fadeIn(
                animationSpec = tween(durationMillis = fadeMillis, easing = motion.easingDecelerate),
            ),
            initialContentExit = fadeOut(
                animationSpec = tween(durationMillis = fadeMillis, easing = motion.easingStandard),
            ),
        )
    }
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
            transitionSpec = { if (tabTransition) fadeTransition else forwardTransition },
            popTransitionSpec = { if (tabTransition) fadeTransition else popTransition },
            predictivePopTransitionSpec = { if (tabTransition) fadeTransition else popTransition },
            entryProvider = entryProvider {
                entryBuilder(liveOps)
            },
        )
    }
}

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
    snackbar: @Composable () -> Unit = {},
    entryBuilder: EntryProviderScope<NavKey>.(RethinkNavOps) -> Unit,
) {
    val startStack = remember(startRoute) { canonicalStackFor(startRoute).toTypedArray() }
    val backStack = rememberNavBackStack(rethinkNavSavedStateConfiguration, *startStack)
    val savedStacks = remember { mutableMapOf<RethinkRootDestination, List<RethinkRoute>>() }
    val motion = LocalRethinkMotion.current
    // Tab bar switches replace the stack; they must not inherit the detail slide.
    var tabTransition by remember { mutableStateOf(false) }

    fun currentRoute(): RethinkRoute? = backStack.lastOrNull() as? RethinkRoute

    fun currentStack(): List<RethinkRoute> = backStack.mapNotNull { it as? RethinkRoute }

    fun setStack(vararg keys: RethinkRoute) {
        backStack.clear()
        backStack.addAll(keys.toList())
    }

    /** Apply [next] with a pop/push mutation when possible so Nav3 can animate. */
    fun applyStack(current: List<RethinkRoute>, next: List<RethinkRoute>) {
        if (next == current) return
        if (next.size < current.size && current.take(next.size) == next) {
            repeat(current.size - next.size) { backStack.removeAt(backStack.lastIndex) }
            return
        }
        if (next.size == current.size + 1 && next.dropLast(1) == current) {
            backStack.add(next.last())
            return
        }
        setStack(*next.toTypedArray())
    }

    fun popBackStack() {
        val current = currentStack()
        val next = stackAfterPop(current) ?: return
        tabTransition = current.size == 1 && next.singleOrNull() == RethinkRoute.Home
        applyStack(current, next)
    }

    fun push(destination: RethinkRoute) {
        tabTransition = false
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun rememberLeavingRoot(current: List<RethinkRoute>) {
        val root = current.lastOrNull()?.rootDestination() ?: return
        val saved = stackToSaveForRoot(current)
        if (saved != null) {
            savedStacks[saved.first] = saved.second
        } else {
            savedStacks.remove(root)
        }
    }

    fun open(destination: RethinkRoute) {
        val current = currentStack()
        val next = stackAfterOpen(current, destination)
        tabTransition = next.size == 1 &&
            current.lastOrNull()?.rootDestination() != destination.rootDestination()
        applyStack(current, next)
    }

    fun openFromApp(destination: RethinkRoute) {
        val current = currentStack()
        val currentRoot = current.lastOrNull()?.rootDestination()
        val destRoot = destination.rootDestination()
        tabTransition = currentRoot != destRoot
        if (currentRoot != null && currentRoot != destRoot) {
            rememberLeavingRoot(current)
        }
        applyStack(current, stackAfterOpenFromApp(current, destination))
    }

    fun selectRoot(destination: RethinkRootDestination) {
        val current = currentStack()
        val next = stackAfterSelectRoot(current, destination, savedStacks[destination]) ?: return
        tabTransition = current.lastOrNull()?.rootDestination() != destination
        rememberLeavingRoot(current)
        applyStack(current, next)
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
        openFromApp(route)
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
        snackbar = snackbar,
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { popBackStack() },
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

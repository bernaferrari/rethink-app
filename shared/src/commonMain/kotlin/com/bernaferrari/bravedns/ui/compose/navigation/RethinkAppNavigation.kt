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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion

/**
 * Shared navigation host for Android and wasm.
 *
 * One saveable [rememberNavBackStack] per root tab (the official Nav3 multiple-backstacks
 * recipe): every tab keeps its own depth and entry state across process death, and switching
 * tabs swaps the decorated entry list instead of mutating a single stack.
 *
 * Hosts differ only in [entryBuilder] (real screens vs preview fixtures) and optional chrome
 * labels — not in stack policy, suite layout, or route types.
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
    val backStacks = RethinkRootDestination.entries.associateWith { root ->
        rememberNavBackStack(rethinkNavSavedStateConfiguration, root.route)
    }
    var activeRootName by rememberSaveable(startRoute) {
        mutableStateOf((startRoute.rootDestination() ?: RethinkRootDestination.Home).name)
    }
    val activeRoot = RethinkRootDestination.entries.first { it.name == activeRootName }
    val backStack = backStacks.getValue(activeRoot)
    val motion = LocalRethinkMotion.current
    // Tab bar switches replace the entry list; they must not inherit the detail slide.
    var tabTransition by remember { mutableStateOf(false) }

    fun stackOf(stack: NavBackStack<NavKey>): List<RethinkRoute> =
        stack.mapNotNull { it as? RethinkRoute }

    fun currentStack(): List<RethinkRoute> = stackOf(backStack)

    fun applyStackOn(
        stack: NavBackStack<NavKey>,
        current: List<RethinkRoute>,
        next: List<RethinkRoute>,
    ) {
        if (next == current) return
        if (next.size < current.size && current.take(next.size) == next) {
            repeat(current.size - next.size) { stack.removeAt(stack.lastIndex) }
            return
        }
        if (next.size == current.size + 1 && next.dropLast(1) == current) {
            stack.add(next.last())
            return
        }
        stack.clear()
        stack.addAll(next)
    }

    fun popBackStack() {
        val current = currentStack()
        val next = stackAfterPop(current) ?: return
        // Configure/Statistics roots exit through Home instead of finishing.
        if (activeRoot != RethinkRootDestination.Home && next == listOf(RethinkRoute.Home)) {
            tabTransition = true
            activeRootName = RethinkRootDestination.Home.name
            return
        }
        tabTransition = false
        applyStackOn(backStack, current, next)
    }

    fun push(destination: RethinkRoute) {
        tabTransition = false
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun open(destination: RethinkRoute) {
        val targetRoot = destination.rootDestination() ?: RethinkRootDestination.Home
        val target = backStacks.getValue(targetRoot)
        val current = stackOf(target)
        tabTransition = targetRoot != activeRoot
        activeRootName = targetRoot.name
        applyStackOn(target, current, stackAfterOpen(current, destination))
    }

    fun openFromApp(destination: RethinkRoute) {
        val targetRoot = destination.rootDestination() ?: RethinkRootDestination.Home
        val target = backStacks.getValue(targetRoot)
        val current = stackOf(target)
        tabTransition = targetRoot != activeRoot
        activeRootName = targetRoot.name
        applyStackOn(target, current, stackAfterOpenFromApp(current, destination))
    }

    fun selectRoot(destination: RethinkRootDestination) {
        if (destination == activeRoot) return
        tabTransition = true
        activeRootName = destination.name
    }

    val currentKey = backStack.lastOrNull() as? RethinkRoute
    val selectedRoot = currentKey?.rootDestination() ?: activeRoot
    val showNavigation = currentKey?.showsNavigationChrome() != false

    // Fresh closures each composition so entry bodies always see the latest stack.
    val liveOps = RethinkNavOps(
        // A double back-tap during a transition would pop twice; push/open dedup naturally.
        pop = dropUnlessResumed { popBackStack() },
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
        selectedId = selectedRoot.name,
        onDestinationSelected = { id ->
            RethinkRootDestination.entries.firstOrNull { it.name == id }?.let(::selectRoot)
        },
        showNavigation = showNavigation,
        modifier = modifier,
        background = { background(currentKey) },
        snackbar = snackbar,
    ) {
        // Per-stack decorated entries: decorator state (saveable, ViewModel) only
        // survives tab switches if each stack owns its decoration list.
        val provider = entryProvider {
            entryBuilder(liveOps)
        }
        val decorated = RethinkRootDestination.entries.associateWith { root ->
            rememberDecoratedNavEntries(
                backStack = backStacks.getValue(root),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = { key ->
                    provider(key) ?: NavEntry(key) {}
                },
            )
        }

        NavDisplay<NavKey>(
            entries = decorated.getValue(activeRoot),
            modifier = Modifier.fillMaxSize(),
            onBack = { popBackStack() },
            transitionSpec = { if (tabTransition) fadeTransition else forwardTransition },
            popTransitionSpec = { if (tabTransition) fadeTransition else popTransition },
            predictivePopTransitionSpec = { if (tabTransition) fadeTransition else popTransition },
        )
    }
}

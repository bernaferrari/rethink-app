@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

    // Entries for every root are built in the first composition (while Home is active) and keep
    // the ops they captured, so stack lookups must resolve the active root when called rather
    // than closing over this composition's `backStack`; otherwise a push from Settings lands on
    // the hidden Home stack.
    fun liveActiveRoot(): RethinkRootDestination =
        RethinkRootDestination.entries.first { it.name == activeRootName }

    fun liveBackStack(): NavBackStack<NavKey> = backStacks.getValue(liveActiveRoot())

    fun currentStack(): List<RethinkRoute> = stackOf(liveBackStack())

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
        if (liveActiveRoot() != RethinkRootDestination.Home && next == listOf(RethinkRoute.Home)) {
            tabTransition = true
            activeRootName = RethinkRootDestination.Home.name
            return
        }
        tabTransition = false
        applyStackOn(liveBackStack(), current, next)
    }

    fun push(destination: RethinkRoute) {
        tabTransition = false
        val stack = liveBackStack()
        if (stack.lastOrNull() != destination) {
            stack.add(destination)
        }
    }

    fun open(destination: RethinkRoute) {
        val targetRoot = destination.rootDestination() ?: RethinkRootDestination.Home
        val target = backStacks.getValue(targetRoot)
        val current = stackOf(target)
        tabTransition = targetRoot != liveActiveRoot()
        activeRootName = targetRoot.name
        applyStackOn(target, current, stackAfterOpen(current, destination))
    }

    fun openFromApp(destination: RethinkRoute) {
        val targetRoot = destination.rootDestination() ?: RethinkRootDestination.Home
        val target = backStacks.getValue(targetRoot)
        val current = stackOf(target)
        tabTransition = targetRoot != liveActiveRoot()
        activeRootName = targetRoot.name
        applyStackOn(target, current, stackAfterOpenFromApp(current, destination))
    }

    fun selectRoot(destination: RethinkRootDestination) {
        if (destination == liveActiveRoot()) return
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
        current = { liveBackStack().lastOrNull() as? RethinkRoute },
    )

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        openFromApp(route)
        onRouteNavigated()
    }

    // Push/pop motion applied as NavDisplay defaults
    // (https://developer.android.com/guide/navigation/navigation-3/animate-destinations).
    // Root tabs cross-fade; details slide on the Material motion scheme's spatial springs.
    val motionScheme = MaterialTheme.motionScheme
    val fadeTransition: ContentTransform = remember(motion, motionScheme) {
        val fade: FiniteAnimationSpec<Float> =
            if (motion.reducedMotion) snap() else motionScheme.defaultEffectsSpec()
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = fade),
            initialContentExit = fadeOut(animationSpec = fade),
        )
    }
    val forwardTransition: ContentTransform = remember(motion, motionScheme) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = if (motion.reducedMotion) snap() else motionScheme.defaultSpatialSpec(),
                initialOffsetX = { fullWidth -> fullWidth },
            ),
            initialContentExit = ExitTransition.None,
        )
    }
    val popTransition: ContentTransform = remember(motion, motionScheme) {
        ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = slideOutHorizontally(
                animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastSpatialSpec(),
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
                    val entry = provider(key) ?: NavEntry(key) {}
                    // Readable width everywhere (tablets, foldables, wide browser panes) except the
                    // screens that own an expanded layout of their own.
                    if ((key as? RethinkRoute)?.hasAdaptiveLayout() == true) {
                        entry
                    } else {
                        NavEntry(entry) { _: NavKey -> RethinkCenteredScreen { entry.Content() } }
                    }
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

/** Routes whose screens switch to multi-column layouts at Expanded width. */
private fun RethinkRoute.hasAdaptiveLayout(): Boolean = when (this) {
    RethinkRoute.Home, RethinkRoute.Statistics, RethinkRoute.Configure, RethinkRoute.DnsList -> true
    is RethinkRoute.NetworkLogs, is RethinkRoute.AppInfo, RethinkRoute.AppList -> true
    else -> false
}

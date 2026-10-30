/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import com.bernaferrari.bravedns.ui.compose.common.RethinkWindowBreakpoints
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.NavDisplay

/**
 * Navigation chrome shared by every host: adaptive suite (bar/rail) with a
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
    snackbar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val navigationFocus = remember { FocusRequester() }
    var navigationReady by remember(selectedId, destinations.map { it.id }) { mutableStateOf(false) }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(showNavigation, selectedId, inputMode, navigationReady) {
        if (showNavigation && navigationReady && inputMode == InputMode.Keyboard) {
            navigationFocus.requestFocus()
        }
    }
    // Hide the bar through the scaffold state instead of swapping parents: moving `content` to a
    // different parent would rebuild NavDisplay (and its saveable decorators) and cut the
    // Welcome → Home transition.
    val suiteState = rememberNavigationSuiteScaffoldState(
        if (showNavigation) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden,
    )
    LaunchedEffect(showNavigation) {
        if (showNavigation) suiteState.show() else suiteState.hide()
    }
    BoundedNavigationSuiteScaffold(
        modifier = modifier,
        state = suiteState,
        navigationSuiteItems = {
            destinations.forEach { destination ->
                val selected = destination.id == selectedId
                item(
                    modifier = if (selected) Modifier.focusRequester(navigationFocus).onGloballyPositioned { navigationReady = true } else Modifier,
                    selected = selected,
                    onClick = { onDestinationSelected(destination.id) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            // The visible label below already names the item; repeating it
                            // here makes TalkBack read "Home, Home".
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
        RethinkNavSurface(Modifier.fillMaxSize(), background, snackbar, content)
    }
}

@Composable
private fun RethinkNavSurface(
    modifier: Modifier,
    background: @Composable () -> Unit,
    snackbar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        background()
        content()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            snackbar()
        }
    }
}

@Composable
fun RethinkCenteredScreen(content: @Composable () -> Unit) {
    // Opaque across the full width: the side gutters must hide the entry underneath (a pushed
    // screen slides over its parent), not reveal it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = RethinkWindowBreakpoints.ContentMax)
                .fillMaxWidth(),
        ) {
            content()
        }
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
    state: NavigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState(),
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
                state = state,
                content = content,
            )
        }
    }
}

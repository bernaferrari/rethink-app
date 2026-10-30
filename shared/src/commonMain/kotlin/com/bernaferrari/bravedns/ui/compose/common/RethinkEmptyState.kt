@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.common

import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkListContainerColor
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

private val EmptyStateIconWell = 72.dp

/*
 * Loading vs empty: the contract every screen keeps.
 *
 * - Until the first load *completes*, show [RethinkLoadingState] (or nothing), never
 *   [RethinkEmptyState] or [RethinkErrorState]. Copy that states a fact ("No apps found",
 *   "This app is no longer installed", "Search 0 apps") is only true once the data says so.
 * - Model the data so "not loaded yet" can't look like "loaded, nothing there": a nullable
 *   list/value where null means loading, an explicit Loading/Loaded state, or a paging
 *   LoadState. An initial `emptyList()` or a default object must never drive an empty or
 *   missing state.
 * - Counts in titles or hints ("Search %d apps") wait for the load too; show the count-less
 *   copy, or nothing, until then.
 * - A refresh after the first load keeps the current content (or empty state) on screen; only
 *   the first load shows the loading state.
 */

/**
 * In-section state container: the grouped-list radius and row tone, so empty/loading/error states
 * that sit inside a section read as one more row group rather than a free-floating block.
 */
@Composable
private fun StateContainer(contained: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    if (!contained) {
        Box(modifier) { content() }
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = LocalRethinkListContainerColor.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

/**
 * A section or screen with nothing to show. Only after a completed load returned nothing; see
 * the loading-vs-empty contract above.
 */
@Composable
fun RethinkEmptyState(
    title: String,
    /** A second line under [title]; blank when the title says it all. */
    message: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    contained: Boolean = false,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) = StateContainer(contained, modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SharedDimensions.spacingLg,
                vertical = SharedDimensions.spacingXl,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            // Expressive shape echoes the Home beacon; sized as an illustration, not a list icon.
            Surface(
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = iconContainerColor,
                modifier = Modifier.size(EmptyStateIconWell),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(SharedDimensions.iconSizeLg),
                    )
                }
            }
            Spacer(Modifier.height(SharedDimensions.spacingLg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SharedDimensions.spacingSm),
            )
        }
        if (ctaLabel != null && onCtaClick != null) {
            Spacer(Modifier.height(SharedDimensions.spacingLg))
            FilledTonalButton(onClick = onCtaClick, shapes = ButtonDefaults.shapes()) {
                Text(ctaLabel)
            }
        }
    }
}

/** The first load of a section or screen, until it completes (see the contract above). */
@Composable
fun RethinkLoadingState(
    label: String,
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) = StateContainer(contained, modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SharedDimensions.spacingLg,
                vertical = SharedDimensions.spacingXl,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator()
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SharedDimensions.spacingMd),
            )
        }
    }
}

/** A failed load: [RethinkEmptyState] styling with an error-toned icon and an optional retry. */
@Composable
fun RethinkErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    contained: Boolean = false,
) = RethinkEmptyState(
    title = title ?: message,
    message = if (title != null) message else "",
    modifier = modifier,
    icon = MaterialSymbols.Filled.Warning,
    ctaLabel = retryLabel,
    onCtaClick = onRetry,
    contained = contained,
    iconContainerColor = MaterialTheme.colorScheme.errorContainer,
    iconTint = MaterialTheme.colorScheme.onErrorContainer,
)

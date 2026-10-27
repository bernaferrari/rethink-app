/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic status roles used by firewall, logs, and DNS.
 *
 * Allowed is the semantic green of [RethinkSemanticColors] and blocked is [error], whatever the
 * seed: a coral (or red, or green) brand must never make allow and block look alike.
 * Disabled / idle states use [idle], never [blocked].
 */
object RethinkStatusColors {
    val allowed: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.allowed

    val allowedContainer: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.allowedContainer

    val onAllowedContainer: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.onAllowedContainer

    val blockedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.errorContainer

    val onBlockedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onErrorContainer

    val blocked: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    val mixed: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

    val idle: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

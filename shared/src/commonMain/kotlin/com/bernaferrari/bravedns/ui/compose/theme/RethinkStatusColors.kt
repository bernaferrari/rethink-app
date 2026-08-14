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
 * Allowed is always [primary] — never tertiary — so allow/block reads the same on every surface.
 * Disabled / idle states use [idle], never [blocked].
 */
object RethinkStatusColors {
    val allowed: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    val blocked: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    val mixed: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

    val idle: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

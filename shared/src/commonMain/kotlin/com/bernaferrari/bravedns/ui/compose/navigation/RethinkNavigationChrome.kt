/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.ui.graphics.vector.ImageVector

data class RethinkNavigationItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    /** Only for icon-only presentations; the visible label already names the destination. */
    val contentDescription: String? = null,
)

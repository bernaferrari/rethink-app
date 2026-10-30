/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.compose.wireguard

import com.bernaferrari.bravedns.ui.components.RethinkIncludeAppRow
import com.bernaferrari.bravedns.ui.components.RethinkIncludeAppState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bernaferrari.bravedns.database.ProxyApplicationMapping
import com.bernaferrari.bravedns.database.hasInternetPermission
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.common.AppIcon
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android app/package state and artwork adapter for the shared proxy-selection row. */
@Composable
fun IncludeAppRow(
    mapping: ProxyApplicationMapping,
    proxyId: String,
    position: CardPosition = CardPosition.Single,
    onInterfaceUpdate: (ProxyApplicationMapping, Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageManager = context.packageManager
    var isProxyExcluded by remember(mapping.uid, mapping.packageName) { mutableStateOf(false) }
    var hasInternetPermission by remember(mapping.uid, mapping.packageName) { mutableStateOf(true) }
    var isIncluded by remember(mapping.uid, mapping.packageName, mapping.proxyId) { mutableStateOf(false) }

    LaunchedEffect(mapping.uid, mapping.proxyId, mapping.packageName) {
        isProxyExcluded = withContext(Dispatchers.IO) { FirewallManager.isAppExcludedFromProxy(mapping.uid) }
        hasInternetPermission = mapping.hasInternetPermission(packageManager)
        isIncluded = mapping.proxyId == proxyId && mapping.proxyId.isNotEmpty() && !isProxyExcluded
    }

    RethinkIncludeAppRow(
        state = RethinkIncludeAppState(
            id = "${mapping.uid}:${mapping.packageName}",
            title = mapping.appName,
            isIncluded = isIncluded,
            isProxyExcluded = isProxyExcluded,
            hasInternetPermission = hasInternetPermission,
        ),
        position = position,
        onIncludedChange = { checked ->
            isIncluded = checked
            onInterfaceUpdate(mapping, checked)
        },
        appIcon = { AppIcon(mapping.packageName, appName = mapping.appName) },
    )
}

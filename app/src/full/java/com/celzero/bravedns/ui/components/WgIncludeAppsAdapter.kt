/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.celzero.bravedns.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ProxyApplicationMapping
import com.celzero.bravedns.database.hasInternetPermission
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.util.Utilities.getDefaultIcon
import com.celzero.bravedns.util.Utilities.getIcon
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
    var iconDrawable by remember(mapping.uid, mapping.packageName) { mutableStateOf<Drawable?>(null) }
    var isIncluded by remember(mapping.uid, mapping.packageName, mapping.proxyId) { mutableStateOf(false) }

    LaunchedEffect(mapping.uid, mapping.proxyId, mapping.packageName) {
        isProxyExcluded = withContext(Dispatchers.IO) { FirewallManager.isAppExcludedFromProxy(mapping.uid) }
        hasInternetPermission = mapping.hasInternetPermission(packageManager)
        iconDrawable = withContext(Dispatchers.IO) { getIcon(context, mapping.packageName, mapping.appName) }
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
        appIcon = {
            val painter = rememberDrawablePainter(iconDrawable) ?: rememberDrawablePainter(getDefaultIcon(context))
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                )
            }
        },
    )
}

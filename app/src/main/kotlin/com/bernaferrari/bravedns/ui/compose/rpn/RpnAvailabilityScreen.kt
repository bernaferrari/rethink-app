/*
 * Copyright 2025 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.compose.rpn

import Logger
import Logger.LOG_TAG_UI
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.service.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Android server-probe adapter for the shared RPN availability dashboard. */
@Composable
fun RpnAvailabilityScreen(onBackClick: () -> Unit) {
    var items by remember { mutableStateOf<List<RethinkRpnAvailabilityItem>>(emptyList()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        loadFailed = false
        val selected = runCatching {
            withContext(Dispatchers.IO) {
                RpnProxyManager.getWinServers()
                    .filter { it.isEnabled && !it.id.equals("AUTO", ignoreCase = true) }
                    .sortedBy { it.name }
            }
        }.getOrElse {
            Logger.w(LOG_TAG_UI, "RpnAvailabilityScreen failed to load locations: ${it.message}")
            loadFailed = true
            emptyList()
        }
        items = selected.map { it.toAvailabilityItem(RethinkRpnAvailabilityStatus.Loading) }
        selected.forEach { server ->
            val reachable = withContext(Dispatchers.IO) {
                withTimeoutOrNull(8_000L) { VpnController.isRpnReachable(server.key) }
            }
            val status = when (reachable) {
                true -> RethinkRpnAvailabilityStatus.Active
                false -> RethinkRpnAvailabilityStatus.Inactive
                null -> RethinkRpnAvailabilityStatus.Unavailable
            }
            items = items.map { item -> if (item.key == server.key) item.copy(status = status) else item }
            Logger.i(LOG_TAG_UI, "RpnAvailabilityScreen server=${server.key}, reachable=$reachable")
        }
    }

    RethinkRpnAvailabilityScreen(
        items = items,
        loadFailed = loadFailed,
        strings = RethinkRpnAvailabilityStrings(
            title = stringResource(R.string.rpn_availability_title),
            description = stringResource(R.string.rpn_availability_desc),
            noSelected = stringResource(R.string.rpn_availability_no_selected),
            loadFailed = stringResource(R.string.rpn_availability_load_failed),
            retry = stringResource(R.string.rpn_availability_retry),
            active = stringResource(R.string.lbl_active),
            inactive = stringResource(R.string.lbl_inactive),
            unavailable = stringResource(R.string.rpn_availability_check_failed),
        ),
        onRetry = { refreshToken++ },
        onBackClick = onBackClick,
    )
}

private fun CountryConfig.toAvailabilityItem(status: RethinkRpnAvailabilityStatus) =
    RethinkRpnAvailabilityItem(
        key = key,
        name = name.ifBlank { cc },
        status = status,
    )

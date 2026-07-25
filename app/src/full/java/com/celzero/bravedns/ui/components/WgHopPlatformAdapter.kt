/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.celzero.bravedns.ui.components

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import android.widget.Toast
import com.celzero.bravedns.R
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.compose.wireguard.RethinkWireguardHopItem
import com.celzero.bravedns.ui.compose.wireguard.RethinkWireguardHopToggleResult
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.wireguard.Config
import com.celzero.bravedns.wireguard.WgHopManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "WgHopPlatform"

/** Reads Android WireGuard services into the portable hop-picker model. */
suspend fun Config.toRethinkWireguardHopItem(
    context: Context,
    sourceId: Int,
    selectedId: Int?,
): RethinkWireguardHopItem? = withContext(Dispatchers.IO) {
    val mapping = WireguardManager.getConfigFilesById(getId()) ?: return@withContext null
    val proxyId = ID_WG_BASE + getId()
    val supportedIpVersions = VpnController.getSupportedIpVersion(proxyId)
    val isSplitTunnel = getPeers()?.isNotEmpty() == true && VpnController.isSplitTunnelProxy(proxyId, supportedIpVersions)
    val properties = buildString {
        if (mapping.isCatchAll) append(context.getString(R.string.symbol_lightening))
        if (mapping.useOnlyOnMetered) append(context.getString(R.string.symbol_mobile))
        if (mapping.ssidEnabled) append(context.getString(R.string.symbol_id))
    }
    RethinkWireguardHopItem(
        id = getId().toString(),
        name = getName(),
        status = getWireguardHopStatus(context, sourceId, selectedId),
        isActive = mapping.isActive,
        hasIpv4 = supportedIpVersions.first,
        hasIpv6 = supportedIpVersions.second,
        isSplitTunnel = isSplitTunnel,
        isAmnezia = getInterface()?.isAmnezia() == true,
        isHopSource = WgHopManager.getMapBySrc(proxyId).isNotEmpty(),
        isAlreadyHop = WgHopManager.isAlreadyHop(proxyId),
        properties = properties,
    )
}

private suspend fun Config.getWireguardHopStatus(context: Context, sourceId: Int, selectedId: Int?): String {
    val mapping = WireguardManager.getConfigFilesById(getId()) ?: return context.getString(R.string.config_invalid_desc)
    if (selectedId == getId()) {
        val sourceConfig = WireguardManager.getConfigById(sourceId) ?: return context.getString(R.string.lbl_inactive)
        val status = VpnController.hopStatus(ID_WG_BASE + sourceConfig.getId(), ID_WG_BASE + getId())
        return if (status.first != null) context.getString(UIUtils.getProxyStatusStringRes(status.first)) else status.second
    }
    return if (mapping.isActive) context.getString(R.string.lbl_active) else context.getString(R.string.lbl_inactive)
}

/** Runs the Android-only hop validation and persistence requested by the shared picker. */
suspend fun updateWireguardHop(
    context: Context,
    sourceId: Int,
    target: Config,
    shouldSelect: Boolean,
): RethinkWireguardHopToggleResult {
    val source = WireguardManager.getConfigById(sourceId)
    val targetMapping = WireguardManager.getConfigFilesById(target.getId())
    if (source == null || targetMapping == null) {
        Logger.i(LOG_TAG_UI, "$TAG; source config($sourceId) not found to hop")
        showHopToast(context, context.getString(R.string.config_invalid_desc))
        return RethinkWireguardHopToggleResult(false)
    }
    if (targetMapping.useOnlyOnMetered || targetMapping.ssidEnabled) {
        val message = context.getString(R.string.hop_error_toast_msg_3)
        showHopToast(context, message)
        return RethinkWireguardHopToggleResult(false)
    }

    Logger.d(LOG_TAG_UI, "$TAG; hop: ${source.getId()} -> ${target.getId()}, selected? $shouldSelect")
    val sourceProxyId = ID_WG_BASE + source.getId()
    val targetProxyId = ID_WG_BASE + target.getId()
    WgHopManager.getMapBySrc(sourceProxyId).forEach { mapping ->
        if (mapping.hop != targetProxyId && mapping.hop.isNotEmpty()) {
            mapping.hop.substring(ID_WG_BASE.length).toIntOrNull()?.let { existingId ->
                WgHopManager.removeHop(source.getId(), existingId)
            }
        }
    }
    delay(2000)
    if (shouldSelect) {
        val test = VpnController.testHop(sourceProxyId, targetProxyId)
        if (!test.first) {
            val message = test.second ?: context.getString(R.string.unknown_error)
            showHopToast(context, message)
            return RethinkWireguardHopToggleResult(false)
        }
    }

    val result = if (shouldSelect) {
        WgHopManager.hop(source.getId(), target.getId())
    } else {
        WgHopManager.removeHop(source.getId(), target.getId())
    }
    showHopToast(context, result.second)
    return RethinkWireguardHopToggleResult(
        succeeded = result.first,
        selectedId = if (result.first && shouldSelect) target.getId().toString() else null,
    )
}

private suspend fun showHopToast(context: Context, text: String) {
    withContext(Dispatchers.Main) { Utilities.showToastUiCentered(context, text, Toast.LENGTH_LONG) }
}

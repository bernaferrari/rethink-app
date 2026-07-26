/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import android.database.Cursor
import android.database.MatrixCursor

internal fun <T : Any> entitiesToCursor(
    rows: List<T>,
    columns: Array<String>,
    rowMapper: (T) -> Array<Any?>
): Cursor {
    val cursor = MatrixCursor(columns, rows.size)
    for (row in rows) cursor.addRow(rowMapper(row))
    return cursor
}

suspend fun AppInfoRepository.getAppsCursor(): Cursor {
    val rows = getAllAppDetailsForProvider()
    return entitiesToCursor(
        rows,
        arrayOf(
            "packageName", "appName", "uid", "isSystemApp", "firewallStatus", "appCategory",
            "wifiDataUsed", "mobileDataUsed", "connectionStatus", "screenOffAllowed",
            "backgroundAllowed", "uploadBytes", "downloadBytes", "isProxyExcluded",
            "tombstoneTs", "modifiedTs", "tempAllowEnabled", "tempAllowExpiryTime"
        )
    ) { a ->
        arrayOf(
            a.packageName, a.appName, a.uid, if (a.isSystemApp) 1 else 0, a.firewallStatus,
            a.appCategory, a.wifiDataUsed, a.mobileDataUsed, a.connectionStatus,
            if (a.screenOffAllowed) 1 else 0, if (a.backgroundAllowed) 1 else 0,
            a.uploadBytes, a.downloadBytes, if (a.isProxyExcluded) 1 else 0,
            a.tombstoneTs, a.modifiedTs, if (a.tempAllowEnabled) 1 else 0, a.tempAllowExpiryTime
        )
    }
}

suspend fun CustomDomainRepository.getRulesCursor(): Cursor {
    val rows = getRulesForProvider()
    return entitiesToCursor(
        rows,
        arrayOf("domain", "uid", "ips", "status", "type", "proxyId", "proxyCC", "modifiedTs", "deletedTs", "version")
    ) { d ->
        arrayOf(d.domain, d.uid, d.ips, d.status, d.type, d.proxyId, d.proxyCC, d.modifiedTs, d.deletedTs, d.version)
    }
}

private val rethinkLocalFileTagColumns =
    arrayOf(
        "value", "uname", "vname", "group", "subg", "url", "show", "entries", "pack", "level",
        "simpleTagId", "isSelected"
    )

private fun RethinkLocalFileTag.toCursorRow(): Array<Any?> =
    arrayOf(
        value, uname, vname, group, subg, url.joinToString(","), show, entries,
        pack?.joinToString(","), level?.joinToString(","), simpleTagId, if (isSelected) 1 else 0
    )

suspend fun RethinkLocalFileTagRepository.getFileTagsCursor(): Cursor =
    entitiesToCursor(contentGetFileTags(), rethinkLocalFileTagColumns) { it.toCursorRow() }

suspend fun RethinkLocalFileTagRepository.getSelectedFileTagsCursor(): Cursor =
    entitiesToCursor(contentGetSelectedFileTags(), rethinkLocalFileTagColumns) { it.toCursorRow() }

suspend fun RethinkLocalFileTagRepository.getAllFileTagsCursor(): Cursor =
    entitiesToCursor(contentGetAllFileTags(), rethinkLocalFileTagColumns) { it.toCursorRow() }

suspend fun RethinkLocalFileTagRepository.getFileTagByIdCursor(id: Int): Cursor =
    entitiesToCursor(contentGetFileTagById(id), rethinkLocalFileTagColumns) { it.toCursorRow() }

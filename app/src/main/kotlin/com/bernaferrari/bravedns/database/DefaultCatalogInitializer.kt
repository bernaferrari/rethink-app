package com.bernaferrari.bravedns.database

import android.content.Context
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Seeds public endpoint templates when Room creates the current schema, before its first query. */
internal class DefaultCatalogInitializer(context: Context) : RoomDatabase.Callback() {
    private val assets = context.applicationContext.assets

    override suspend fun onCreate(connection: SQLiteConnection) {
        val catalogs = assets.open("database/default-catalogs.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.getValue("catalogs").jsonArray
        }
        // Room runs schema creation and this callback in one transaction. A failed seed rolls back
        // creation as well, so a partially populated database cannot be opened by the VPN service.
        for (element in catalogs) {
            val catalog = element.jsonObject
            val table = catalog.getValue("table").jsonPrimitive.content
            require(table in PUBLIC_CATALOGS) { "Unsupported default catalog: $table" }
            val columns = catalog.getValue("columns").jsonArray.map { it.jsonPrimitive.content }
            require(columns.isNotEmpty() && columns.all { IDENTIFIER.matches(it) })
            val names = columns.joinToString { "\"$it\"" }
            val parameters = columns.joinToString { "?" }
            val sql = "INSERT INTO \"$table\" ($names) VALUES ($parameters)"
            for (row in catalog.getValue("rows").jsonArray) {
                val values = row.jsonArray
                require(values.size == columns.size) { "Invalid default catalog row: $table" }
                connection.prepare(sql).use { statement ->
                    values.forEachIndexed { index, value ->
                        when {
                            value == JsonNull -> statement.bindNull(index + 1)
                            value is JsonPrimitive && value.isString ->
                                statement.bindText(index + 1, value.content)
                            value is JsonPrimitive && value.longOrNull != null ->
                                statement.bindLong(index + 1, requireNotNull(value.longOrNull))
                            else -> error("Unsupported default catalog value: $table")
                        }
                    }
                    statement.step()
                }
            }
        }
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_]*")
        val PUBLIC_CATALOGS = setOf(
            "DoHEndpoint", "DNSCryptEndpoint", "DNSCryptRelayEndpoint", "DNSProxyEndpoint",
            "ProxyEndpoint", "RethinkDnsEndpoint", "DoTEndpoint", "ODoHEndpoint",
        )
    }
}

package com.bernaferrari.bravedns.rpnproxy

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object WinEntitlementFreshness {
    fun isCurrent(bytes: ByteArray?, now: Long = System.currentTimeMillis()): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        val data = try {
            Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return false
        fun field(name: String) = (data[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (field("sessiontoken").isBlank() || field("status").equals("expired", ignoreCase = true)) return false
        val expiry = try {
            OffsetDateTime.parse(field("expiry")).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            return false
        }
        return expiry > now
    }
}

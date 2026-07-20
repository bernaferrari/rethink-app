/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object JsonHelper {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    fun parseObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    fun encodeObject(obj: JsonObject): String = json.encodeToString(JsonObject.serializer(), obj)

    fun getString(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.content

    fun getString(obj: JsonObject, key: String, default: String): String =
        getString(obj, key) ?: default

    fun getInt(obj: JsonObject, key: String, default: Int = 0): Int =
        obj[key]?.jsonPrimitive?.intOrNull ?: default

    fun getLong(obj: JsonObject, key: String, default: Long = 0L): Long =
        obj[key]?.jsonPrimitive?.longOrNull ?: default

    fun getBoolean(obj: JsonObject, key: String, default: Boolean = false): Boolean =
        obj[key]?.jsonPrimitive?.booleanOrNull ?: default

    fun getObject(obj: JsonObject, key: String): JsonObject? =
        obj[key]?.jsonObject
}
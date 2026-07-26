/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.database

import androidx.room3.ColumnTypeConverter
import com.bernaferrari.bravedns.util.JsonHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {

    @ColumnTypeConverter
    fun stringToList(string: String?): List<String> {
        if (string.isNullOrEmpty()) return emptyList()
        return JsonHelper.json.decodeFromString(ListSerializer(String.serializer()), string)
    }

    @ColumnTypeConverter
    fun intToList(string: String?): List<Int> {
        if (string.isNullOrEmpty()) return arrayListOf()
        return JsonHelper.json.decodeFromString(ListSerializer(Int.serializer()), string)
    }

    @ColumnTypeConverter
    fun listToString(set: List<String>?): String {
        if (set == null) return ""
        return JsonHelper.json.encodeToString(ListSerializer(String.serializer()), set)
    }

    @ColumnTypeConverter
    fun listToInt(set: List<Int>?): String {
        if (set == null) return ""
        return JsonHelper.json.encodeToString(ListSerializer(Int.serializer()), set)
    }

    // Event logging type converters
    @ColumnTypeConverter
    fun fromEventType(value: EventType): String = value.name

    @ColumnTypeConverter
    fun toEventType(value: String): EventType =
        try {
            EventType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            EventType.SYSTEM_EVENT
        }

    @ColumnTypeConverter
    fun fromSeverity(value: Severity): String = value.name

    @ColumnTypeConverter
    fun toSeverity(value: String): Severity =
        try {
            Severity.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Severity.LOW
        }

    @ColumnTypeConverter
    fun fromEventSource(value: EventSource): String = value.name

    @ColumnTypeConverter
    fun toEventSource(value: String): EventSource =
        try {
            EventSource.valueOf(value)
        } catch (e: IllegalArgumentException) {
            EventSource.SYSTEM
        }
}
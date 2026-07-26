/*
 * Copyright 2021 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.data

import com.bernaferrari.bravedns.util.JsonHelper
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// do not use as key in map or set, as some fields are mutable
data class FileTag(
    val value: Int,
    val uname: String,
    val vname: String,
    var group: String,
    var subg: String,
    var urls: List<String>,
    val show: Int,
    var pack: List<String> = arrayListOf(),
    var level: List<Int>? = arrayListOf(),
    val entries: Int,
    var simpleTagId: Int = INVALID_SIMPLE_TAG_ID,
    var isSelected: Boolean = false,
)

private const val INVALID_SIMPLE_TAG_ID = -1

object FileTagParser {
    fun parse(element: JsonElement): FileTag {
        val obj = element.jsonObject
        return FileTag(
            value = obj.intValue("value"),
            uname = obj.stringValue("uname"),
            vname = obj.stringValue("vname"),
            group = obj.stringValue("group"),
            subg = obj.stringValue("subg"),
            urls = parseUrls(obj["url"]),
            show = obj.intValue("show"),
            pack = obj.stringListValue("pack"),
            level = obj.intListValue("level"),
            entries = obj.intValue("entries"),
        )
    }

    fun parseMap(jsonString: String): Map<String, FileTag> {
        val root = JsonHelper.parseObject(jsonString)
        return root.mapValues { (_, value) -> parse(value) }
    }

    private fun parseUrls(urlElement: JsonElement?): List<String> {
        return when (urlElement) {
            null, is JsonNull -> emptyList()
            is JsonArray -> urlElement.map { it.jsonPrimitive.content }
            else -> listOf(urlElement.jsonPrimitive.content)
        }
    }

    private fun JsonObject.intValue(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.stringValue(key: String): String = this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.stringListValue(key: String): List<String> =
        this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun JsonObject.intListValue(key: String): List<Int>? =
        this[key]?.jsonArray?.map { it.jsonPrimitive.int }
}

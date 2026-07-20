/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.data

import android.content.Context
import com.celzero.bravedns.R
import com.celzero.bravedns.util.JsonHelper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// [{"name":"pgdd","type":"equal_wildcard"},{"name":"hhjhy","type":"equal_exact"},{"name":"test","type":"notequal_exact"}]
@Serializable
data class SsidItem(
    val name: String,
    @Serializable(with = SsidTypeAsIdSerializer::class)
    val type: SsidType,
) {
    enum class SsidType(val id: String, val isEqual: Boolean, val isExact: Boolean) {
        EQUAL_EXACT("equal_exact", true, true),
        EQUAL_WILDCARD("equal_wildcard", true, false),
        NOTEQUAL_EXACT("notequal_exact", false, true),
        NOTEQUAL_WILDCARD("notequal_wildcard", false, false);

        fun getDisplayName(context: Context): String {
            val actionText =
                if (isEqual) {
                    context.getString(R.string.lbl_connect)
                } else {
                    context
                        .getString(R.string.notification_action_pause_vpn)
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }
                }

            val matchTypeText =
                if (isExact) {
                    context.getString(R.string.wg_ssid_type_exact)
                } else {
                    context.getString(R.string.wg_ssid_type_wildcard)
                }

            return "$actionText - $matchTypeText"
        }

        companion object {
            fun fromIdentifier(identifier: String): SsidType {
                return when (identifier) {
                    "equal_exact" -> EQUAL_EXACT
                    "equal_wildcard" -> EQUAL_WILDCARD
                    "notequal_exact" -> NOTEQUAL_EXACT
                    "notequal_wildcard" -> NOTEQUAL_WILDCARD
                    // Legacy support for old format
                    "exact" -> EQUAL_EXACT
                    "wildcard" -> EQUAL_WILDCARD
                    "notequal" -> NOTEQUAL_EXACT
                    else -> EQUAL_WILDCARD
                }
            }
        }
    }

    companion object {
        fun parseStorageList(storageString: String): List<SsidItem> {
            if (storageString.isBlank()) return emptyList()

            return try {
                JsonHelper.json
                    .decodeFromString<List<SsidItem>>(storageString)
                    .mapNotNull { item ->
                        val trimmedName = item.name.trim()
                        if (trimmedName.isEmpty()) null else item.copy(name = trimmedName)
                    }
                    .distinct()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toStorageList(ssidItems: List<SsidItem>): String {
            return JsonHelper.json.encodeToString(ssidItems)
        }
    }
}

private object SsidTypeAsIdSerializer : KSerializer<SsidItem.SsidType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SsidType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SsidItem.SsidType) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): SsidItem.SsidType {
        return SsidItem.SsidType.fromIdentifier(decoder.decodeString())
    }
}
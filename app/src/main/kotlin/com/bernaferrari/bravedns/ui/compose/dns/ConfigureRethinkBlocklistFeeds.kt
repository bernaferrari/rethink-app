/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.dns

import android.content.Context
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.paging.compose.LazyPagingItems
import com.bernaferrari.bravedns.ui.components.toRethinkGroup
import com.bernaferrari.bravedns.database.LocalBlocklistPacksMap
import com.bernaferrari.bravedns.database.RemoteBlocklistPacksMap
import com.bernaferrari.bravedns.database.RethinkLocalFileTag
import com.bernaferrari.bravedns.database.RethinkRemoteFileTag
import com.bernaferrari.bravedns.service.RethinkBlocklistManager
import com.bernaferrari.bravedns.ui.rethink.RethinkBlocklistState
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.DEAD_PACK
import com.bernaferrari.bravedns.util.Constants.Companion.RETHINK_STAMP_VERSION
import com.bernaferrari.bravedns.util.Utilities.showToastUiCentered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

internal data class RethinkListUpdateUi(
    val isMax: Boolean,
    val updateAvailable: Boolean,
    val checkUpdateVisible: Boolean,
    val redownloadVisible: Boolean,
    val checkUpdateInProgress: Boolean,
    val updateInProgress: Boolean,
)

/** Bridges Android Paging to the target-neutral lazy editor without exposing Paging to commonMain. */
internal class AndroidBlocklistFeed<Source : Any, Ui>(
    private val items: LazyPagingItems<Source>,
    private val map: (Source) -> Ui?,
) : RethinkBlocklistFeed<Ui> {
    override val itemCount: Int get() = items.itemCount
    override fun get(index: Int): Ui? = items[index]?.let(map)
}

internal fun LocalBlocklistPacksMap.toEditorPack(context: Context, selectedTags: Set<Int>): RethinkBlocklistPack? {
    if (pack.isBlank() || pack.contains(DEAD_PACK)) return null
    return RethinkBlocklistPack(
        id = "$pack:$level",
        group = toRethinkGroup(context),
        name = pack,
        blocklistCount = blocklistIds.size,
        selected = selectedTags.containsAll(blocklistIds),
        tagIds = blocklistIds.toSet(),
    )
}

internal fun RemoteBlocklistPacksMap.toEditorPack(context: Context, selectedTags: Set<Int>): RethinkBlocklistPack? {
    if (pack.isBlank() || pack.contains(DEAD_PACK)) return null
    return RethinkBlocklistPack(
        id = "$pack:$level",
        group = toRethinkGroup(context),
        name = pack,
        blocklistCount = blocklistIds.size,
        selected = selectedTags.containsAll(blocklistIds),
        tagIds = blocklistIds.toSet(),
    )
}

internal fun RethinkLocalFileTag.toEditorTag(context: Context, selectedTags: Set<Int>) = RethinkBlocklistFileTag(
    id = value.toString(),
    group = toRethinkGroup(context),
    subgroup = subg,
    name = vname,
    entries = entries,
    level = level?.firstOrNull(),
    url = url.firstOrNull(),
    selected = value in selectedTags,
    tagIds = setOf(value),
)

internal fun RethinkRemoteFileTag.toEditorTag(context: Context, selectedTags: Set<Int>) = RethinkBlocklistFileTag(
    id = value.toString(),
    group = toRethinkGroup(context),
    subgroup = subg,
    name = vname,
    entries = entries,
    level = level?.firstOrNull(),
    url = url.firstOrNull(),
    selected = value in selectedTags,
    tagIds = setOf(value),
)

internal suspend fun updateSelectedFileTags(
    selectedTags: MutableSet<Int>,
    blocklistType: RethinkBlocklistManager.RethinkBlocklistType
) {
    if (selectedTags.isEmpty()) {
        if (blocklistType.isLocal()) {
            RethinkBlocklistManager.clearTagsSelectionLocal()
        } else {
            RethinkBlocklistManager.clearTagsSelectionRemote()
        }
        return
    }

    if (blocklistType.isLocal()) {
        RethinkBlocklistManager.clearTagsSelectionLocal()
        RethinkBlocklistManager.updateFiletagsLocal(selectedTags, 1)
        val list = RethinkBlocklistManager.getSelectedFileTagsLocal().toSet()
        RethinkBlocklistState.updateFileTagList(list)
    } else {
        RethinkBlocklistManager.clearTagsSelectionRemote()
        RethinkBlocklistManager.updateFiletagsRemote(selectedTags, 1)
        val list = RethinkBlocklistManager.getSelectedFileTagsRemote().toSet()
        RethinkBlocklistState.updateFileTagList(list)
    }
}

internal fun formatQuery(q: String): String {
    return "%$q%"
}

internal fun isBase64(stamp: String): Boolean {
    val whitespaceRegex = "\\s"
    val pattern = Pattern.compile(
        "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$"
    )

    val versionSplit = stamp.split(":").getOrNull(1) ?: return false
    if (versionSplit.isEmpty()) return false

    val result = versionSplit.replace(whitespaceRegex, "")
    return pattern.matcher(result).matches()
}

internal fun buildFilterDescription(
    filter: RethinkBlocklistState.Filters,
    filterDescriptionTemplate: String,
    filterDescriptionSubgroupsTemplate: String
): String {
    val text = if (filter.subGroups.isEmpty()) {
        filterDescriptionTemplate.format(filter.filterSelected.name.lowercase())
    } else {
        filterDescriptionSubgroupsTemplate.format(
            filter.filterSelected.name.lowercase(),
            "",
            filter.subGroups
        )
    }
    return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}

internal fun restoreStampIfPresent(
    query: String,
    context: Context,
    scope: CoroutineScope,
    onProcessSelectedFileTags: (String) -> Unit
): Boolean {
    if (!query.contains(Constants.RETHINKDNS_DOMAIN)) return false
    val stamp = query.split("/").firstOrNull { it.contains("$RETHINK_STAMP_VERSION:") && isBase64(it) } ?: return false
    scope.launch(Dispatchers.IO) { onProcessSelectedFileTags(stamp) }
    showToastUiCentered(context, "Blocklists restored", Toast.LENGTH_SHORT)
    return true
}

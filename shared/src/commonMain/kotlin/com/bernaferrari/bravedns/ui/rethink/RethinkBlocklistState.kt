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
package com.bernaferrari.bravedns.ui.rethink

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared filter and selection state for the local and remote Rethink blocklist editors. */
object RethinkBlocklistState {
    val selectedFileTags = MutableStateFlow<Set<Int>>(emptySet())

    fun updateFileTagList(fileTags: Set<Int>) {
        selectedFileTags.value = fileTags.toSet()
    }

    fun getSelectedFileTags(): Set<Int> = selectedFileTags.value

    enum class BlocklistSelectionFilter(val id: Int) {
        ALL(0),
        SELECTED(1)
    }

    class Filters {
        var query: String = "%%"
        var filterSelected: BlocklistSelectionFilter = BlocklistSelectionFilter.ALL
        var subGroups: MutableSet<String> = mutableSetOf()
    }

    enum class BlocklistView(val tag: String) {
        PACKS("1"),
        ADVANCED("2");

        fun isSimple() = this == PACKS

        companion object {
            fun getTag(tag: String): BlocklistView = if (tag == PACKS.tag) PACKS else ADVANCED
        }
    }
}

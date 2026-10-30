package com.bernaferrari.bravedns.rules

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Import mutates only missing rules. Retry is safe after cancellation or a partial database failure. */
internal class ConfirmedRuleImporter(
    private val exists: suspend (String) -> Boolean,
    private val insertBlock: suspend (String) -> Unit,
) {
    suspend fun import(entries: List<String>): Pair<Int, Int> {
        var imported = 0
        var duplicates = 0
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            if (exists(entry)) duplicates++ else { insertBlock(entry); imported++ }
        }
        return imported to duplicates
    }
}

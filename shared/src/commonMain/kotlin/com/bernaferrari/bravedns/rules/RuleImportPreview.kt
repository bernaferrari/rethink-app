package com.bernaferrari.bravedns.rules

/** A bounded preview. Hosts validate/normalize according to the selected rule kind. */
data class RuleImportPreview(
    val entries: List<String>,
    val invalidCount: Int,
    val duplicateCount: Int,
    val limitReached: Boolean,
)

object RuleImportParser {
    const val MAX_ENTRIES = 10_000
    const val MAX_LINE_LENGTH = 4096
    const val MAX_FILE_BYTES = 4 * 1024 * 1024

    fun preview(lines: Sequence<String>, normalize: (String) -> String?): RuleImportPreview {
        val entries = linkedSetOf<String>()
        var invalid = 0
        var duplicates = 0
        var processed = 0
        var limited = false
        for (raw in lines) {
            if (processed++ >= MAX_ENTRIES) { limited = true; break }
            if (raw.length > MAX_LINE_LENGTH) { invalid++; continue }
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val value = normalize(line)
            if (value.isNullOrBlank()) invalid++ else if (!entries.add(value)) duplicates++
        }
        return RuleImportPreview(entries.toList(), invalid, duplicates, limited)
    }
}

package com.bernaferrari.bravedns.rules

/** Canonical domain-rule input, shared by manual entry and file previews. Never accepts paths as hosts. */
object DomainRuleInput {
    fun normalize(input: String): String? {
        val value = input.trim().lowercase()
        val url = value.contains("://")
        val wildcard = value.startsWith("*.")
        val host = if (url) {
            if (!(value.startsWith("http://") || value.startsWith("https://")) || '*' in value) return null
            val authority = value.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@')
            if (':' in authority && authority.substringAfterLast(':').toIntOrNull() !in 0..65535) return null
            authority.substringBefore(':').removePrefix("www.")
        } else value.removePrefix("*.")
        val normalized = host.trimEnd('.')
        val labels = normalized.split('.')
        if (normalized.length > 253 || (!wildcard && labels.size < 2) || labels.any(::invalidLabel)) return null
        return if (wildcard) "*.$normalized" else normalized
    }

    private fun invalidLabel(label: String): Boolean = label.isEmpty() || label.length > 63 ||
        label.startsWith('-') || label.endsWith('-') || label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }
}

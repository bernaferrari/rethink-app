package com.bernaferrari.bravedns.ui.compose.navigation

import kotlinx.serialization.Serializable

/** @deprecated Use [RethinkRoute] — kept for CustomRules tab/mode helpers used by HomeNavRequest. */
@Deprecated("Use RethinkRoute", ReplaceWith("RethinkRoute"))
typealias HomeRoute = RethinkRoute

@Serializable
enum class CustomRulesTab(val value: Int) {
    IP(0),
    DOMAIN(1);

    companion object {
        fun fromValue(value: Int): CustomRulesTab {
            return entries.firstOrNull { it.value == value } ?: IP
        }
    }
}

@Serializable
enum class CustomRulesMode(val value: Int) {
    ALL_RULES(0),
    APP_SPECIFIC(1);

    companion object {
        fun fromValue(value: Int): CustomRulesMode {
            return entries.firstOrNull { it.value == value } ?: APP_SPECIFIC
        }
    }
}

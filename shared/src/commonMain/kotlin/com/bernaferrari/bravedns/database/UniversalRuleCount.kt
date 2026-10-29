package com.bernaferrari.bravedns.database

/** Compact projection for the universal firewall settings counters. */
data class UniversalRuleCount(val blockedByRule: String, val count: Int)

const val UNIVERSAL_RULE_COUNTS_QUERY =
    "SELECT blockedByRule, COUNT(*) AS count FROM ConnectionTracker " +
        "WHERE blockedByRule IN ('Rule #1B', 'Rule #1F', 'Rule #3', 'Rule #4', 'Rule #5', " +
        "'Rule #6', 'Rule #7', 'Http block', 'Universal Lockdown') GROUP BY blockedByRule"

/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic status roles used by firewall, logs, and DNS.
 *
 * Allowed is the semantic green of [RethinkSemanticColors] and blocked is [error], whatever the
 * seed: a coral (or red, or green) brand must never make allow and block look alike.
 * Disabled / idle states use [idle], never [blocked].
 */
object RethinkStatusColors {
    val allowed: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.allowed

    val allowedContainer: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.allowedContainer

    val onAllowedContainer: Color
        @Composable @ReadOnlyComposable get() = LocalRethinkSemanticColors.current.onAllowedContainer

    val blockedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.errorContainer

    val onBlockedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onErrorContainer

    val blocked: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    val mixed: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

    val idle: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * A firewall rule's verdict on an IP, domain or connection. Platform adapters map their
 * rule-manager enums here; [accent] is its one colour everywhere it shows.
 */
enum class RethinkFirewallRuleStatus { Block, Trust, Bypass, None }

@Composable @ReadOnlyComposable
fun RethinkFirewallRuleStatus.accent(): Color = when (this) {
    RethinkFirewallRuleStatus.Block -> RethinkStatusColors.blocked
    RethinkFirewallRuleStatus.Trust -> RethinkStatusColors.allowed
    RethinkFirewallRuleStatus.Bypass -> RethinkStatusColors.mixed
    RethinkFirewallRuleStatus.None -> RethinkStatusColors.idle
}

/** How fast one lookup answered; shared by the DNS latency bars and the log rows' meter. */
enum class RethinkLatencyBand { Unknown, Fast, Ok, Slow }

fun rethinkLatencyBand(ms: Long): RethinkLatencyBand = when {
    ms <= 0 -> RethinkLatencyBand.Unknown
    ms < FAST_LATENCY_MS -> RethinkLatencyBand.Fast
    ms < SLOW_LATENCY_MS -> RethinkLatencyBand.Ok
    else -> RethinkLatencyBand.Slow
}

/** Latency reads as a status: fast is allowed, sluggish mixed, slow blocked, unknown idle. */
@Composable @ReadOnlyComposable
fun rethinkLatencyTone(ms: Long): Color = when (rethinkLatencyBand(ms)) {
    RethinkLatencyBand.Unknown -> RethinkStatusColors.idle
    RethinkLatencyBand.Fast -> RethinkStatusColors.allowed
    RethinkLatencyBand.Ok -> RethinkStatusColors.mixed
    RethinkLatencyBand.Slow -> RethinkStatusColors.blocked
}

private const val FAST_LATENCY_MS = 50L
private const val SLOW_LATENCY_MS = 150L

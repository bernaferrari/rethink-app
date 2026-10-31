/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class RethinkFirewallSettingsStrings(
    /** "Firewall". */
    val title: String,
    /** "Rules that apply to every app." */
    val explanation: String,
    /** "Rules" (the entry into the Rules list). */
    val rulesTitle: String,
    /** "Block or trust domains and IPs, for one app or all." */
    val rulesDescription: String,
    /** "Rules · 12"; null count while loading. */
    val rulesWithCount: (Int) -> String,
    val everydaySection: String,
    val strictSection: String,
    val lockdownSection: String,
    /** "Blocked: 12" next to a rule that blocked something. */
    val blocked: @Composable (Int) -> String,
    val lockdownConfirmTitle: String,
    val lockdownConfirmMessage: String,
    val lockdownConfirm: String,
    val cancel: String,
    val accessibilityTitle: String,
    val accessibilityDescription: String,
    val accessibilityConfirm: String,
    val accessibilityDismiss: String,
)

/**
 * The Firewall screen: one entry into Rules, then the universal toggles in three tiers. Everyday
 * rules most people want; Strict ones that can break apps; Lockdown, which cuts off everything not
 * explicitly allowed and so asks before it turns on (turning it off never asks).
 *
 * Hosts own persistence and permission checks ([onSettingChange] says when accessibility access is
 * missing). [initialFocusKey] is a rule id (scrolls to and briefly highlights that row) or
 * `firewall_rules`.
 */
@Composable
fun RethinkFirewallSettingsScreen(
    strings: RethinkFirewallSettingsStrings,
    everyday: List<RethinkUniversalFirewallSetting>,
    strict: List<RethinkUniversalFirewallSetting>,
    lockdown: RethinkUniversalFirewallSetting?,
    rulesCount: Int?,
    isLoadingStats: Boolean,
    onSettingChange: (String, Boolean) -> RethinkUniversalFirewallChange,
    onRulesClick: () -> Unit,
    onLogsClick: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    initialFocusKey: String? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val initialFocus = initialFocusKey?.trim().orEmpty()
    var activeFocus by remember(initialFocus) { mutableStateOf(initialFocus.ifBlank { null }) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var confirmLockdown by remember { mutableStateOf(false) }

    // Item order below: rules (0), everyday (1), strict (2), lockdown (3).
    LaunchedEffect(initialFocus) {
        if (initialFocus.isBlank()) return@LaunchedEffect
        val target = when (initialFocus) {
            FIREWALL_FOCUS_RULES -> 0 to 0
            else -> listOf(everyday, strict, listOfNotNull(lockdown)).withIndex().firstNotNullOfOrNull { (section, rows) ->
                rows.indexOfFirst { it.id == initialFocus }.takeIf { it >= 0 }?.let { row -> section + 1 to row }
            }
        }
        target?.let { (index, row) ->
            // Rows are about 72dp; land the target row near the top, not the section header.
            val offset = with(density) { (row * 72).dp.toPx().roundToInt() }
            if (motion.reducedMotion) listState.scrollToItem(index, offset) else listState.animateScrollToItem(index, offset)
        }
        delay(1600)
        activeFocus = null
    }

    fun change(id: String, checked: Boolean) {
        if (onSettingChange(id, checked) == RethinkUniversalFirewallChange.RequiresAccessibility) {
            showAccessibilityDialog = true
        }
    }

    val total = (everyday + strict + listOfNotNull(lockdown)).sumOf { it.blockedCount }
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = if (isLoadingStats || total == 0) null else strings.blocked(total),
        onBackClick = onBackClick,
        modifier = modifier,
        listState = listState,
    ) {
        item(key = "rules") {
            Text(
                strings.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs, vertical = SharedDimensions.spacingSm),
            )
            RethinkListGroup {
                RethinkListItem(
                    headline = rulesCount?.let(strings.rulesWithCount) ?: strings.rulesTitle,
                    supporting = strings.rulesDescription,
                    leadingIcon = MaterialSymbols.Filled.Rule,
                    position = CardPosition.Single,
                    highlighted = activeFocus == FIREWALL_FOCUS_RULES,
                    showTrailingChevron = true,
                    onClick = onRulesClick,
                )
            }
        }
        firewallTier(strings.everydaySection, "everyday", everyday, strings, isLoadingStats, activeFocus, ::change, onLogsClick)
        firewallTier(strings.strictSection, "strict", strict, strings, isLoadingStats, activeFocus, ::change, onLogsClick)
        lockdown?.let { rule ->
            firewallTier(strings.lockdownSection, "lockdown", listOf(rule), strings, isLoadingStats, activeFocus, { id, checked ->
                // Turning lockdown on can cut the device off; say so first. Off applies at once.
                if (checked) confirmLockdown = true else change(id, false)
            }, onLogsClick)
        }
    }

    if (confirmLockdown && lockdown != null) {
        RethinkConfirmDialog(
            onDismissRequest = { confirmLockdown = false },
            title = strings.lockdownConfirmTitle,
            message = strings.lockdownConfirmMessage,
            confirmText = strings.lockdownConfirm,
            dismissText = strings.cancel,
            isConfirmDestructive = true,
            onConfirm = { confirmLockdown = false; change(lockdown.id, true) },
        )
    }
    if (showAccessibilityDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = strings.accessibilityTitle,
            message = strings.accessibilityDescription,
            confirmText = strings.accessibilityConfirm,
            dismissText = strings.accessibilityDismiss,
            onConfirm = { showAccessibilityDialog = false; onOpenAccessibilitySettings() },
            onDismiss = { showAccessibilityDialog = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.firewallTier(
    title: String,
    key: String,
    rules: List<RethinkUniversalFirewallSetting>,
    strings: RethinkFirewallSettingsStrings,
    isLoadingStats: Boolean,
    activeFocus: String?,
    onChange: (String, Boolean) -> Unit,
    onLogsClick: (String) -> Unit,
) {
    if (rules.isEmpty()) return
    rethinkSection(title, key = key) {
        RethinkListGroup {
            rules.forEachIndexed { index, rule ->
                RethinkUniversalFirewallRow(
                    setting = rule,
                    position = cardPositionFor(index, rules.lastIndex),
                    highlighted = activeFocus == rule.id,
                    // A count only where something was blocked: "Blocked: 0" on every row is noise.
                    blockedText = if (isLoadingStats || rule.blockedCount == 0) null else strings.blocked(rule.blockedCount),
                    onCheckedChange = { onChange(rule.id, it) },
                    onClick = { onLogsClick(rule.id) },
                )
            }
        }
    }
}

/** Focus key for the Rules entry (Settings search). Rule rows use their rule id. */
const val FIREWALL_FOCUS_RULES = "firewall_rules"
